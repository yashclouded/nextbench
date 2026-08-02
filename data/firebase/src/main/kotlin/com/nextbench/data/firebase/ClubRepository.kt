package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.nextbench.data.model.Club
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageStatus
import com.nextbench.data.model.MessageType
import com.nextbench.data.model.UserData
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Singleton
class ClubRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val refsProvider: Provider<FirestoreRefs>,
) {
    private val auth get() = authProvider.get()
    private val refs get() = refsProvider.get()

    fun observeMemberClubs(uid: String): Flow<List<Club>> = configuredFlow(uid) {
        refs.clubs
            .whereArrayContains("memberIds", uid)
            .snapshotFlow()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { it.toClub() }
                    .sortedByDescending { it.clubActivityMillis() }
            }
    }

    fun observePublicClubs(uid: String, school: String?, city: String?): Flow<List<Club>> = configuredFlow(uid) {
        refs.clubs
            .whereEqualTo("type", "public")
            .snapshotFlow()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { it.toClub() }
                    .filterNot { uid in it.memberIds }
                    .sortedWith(
                        compareByDescending<Club> { it.school.isNotBlank() && it.school.equals(school.orEmpty(), ignoreCase = true) }
                            .thenByDescending { it.city?.equals(city.orEmpty(), ignoreCase = true) == true }
                            .thenByDescending { it.memberCount }
                            .thenByDescending { it.clubActivityMillis() },
                    )
                    .take(PublicClubLimit)
            }
    }

    fun observeClub(clubId: String, uid: String): Flow<Club?> = configuredFlow(uid) {
        refs.club(clubId).snapshotFlow().map { it.toClub() }
    }

    fun observeMessages(clubId: String, uid: String): Flow<List<Message>> = configuredFlow(uid) {
        refs.clubMessages(clubId)
            .orderBy("createdAt")
            .limitToLast(ClubMessageWindowSize)
            .snapshotFlow()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { it.toMessage() }
                    .filterNot { uid in it.deletedFor }
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
    }

    suspend fun joinByInviteCode(uid: String, code: String): Result<Club?> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val normalized = code.trim()
        require(normalized.isNotBlank()) { "Enter a club invite code first." }
        val query = refs.clubs.whereEqualTo("inviteCode", normalized).limit(1).get().await()
        val clubSnapshot = query.documents.firstOrNull() ?: return@runCatching null
        val clubRef = clubSnapshot.reference
        refs.clubs.firestore.runTransaction { transaction ->
            val snapshot = transaction.get(clubRef)
            val club = snapshot.toClub() ?: return@runTransaction null
            if (uid in club.memberIds) return@runTransaction club
            transaction.update(clubRef, clubJoinUpdatePayload(uid))
            club.copy(memberIds = club.memberIds + uid, memberCount = club.memberCount + 1)
        }.await()
    }

    suspend fun findByInviteCode(uid: String, code: String): Result<Club?> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val normalized = code.trim()
        require(normalized.isNotBlank()) { "Enter a club invite code first." }
        refs.clubs.whereEqualTo("inviteCode", normalized).limit(1).get().await().documents.firstOrNull()?.toClub()
    }

    suspend fun joinPublicClub(uid: String, clubId: String): Result<Club?> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val clubRef = refs.club(clubId)
        refs.clubs.firestore.runTransaction { transaction ->
            val snapshot = transaction.get(clubRef)
            val club = snapshot.toClub() ?: return@runTransaction null
            require(club.type.equals("public", ignoreCase = true)) { "This club is invite-only." }
            if (uid in club.memberIds) return@runTransaction club
            transaction.update(clubRef, clubJoinUpdatePayload(uid))
            club.copy(memberIds = club.memberIds + uid, memberCount = club.memberCount + 1)
        }.await()
    }

    suspend fun leaveClub(uid: String, clubId: String): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val clubRef = refs.club(clubId)
        refs.clubs.firestore.runTransaction { transaction ->
            val snapshot = transaction.get(clubRef)
            val club = snapshot.toClub() ?: error("This club is no longer available.")
            require(uid in club.memberIds) { "You are not a member of this club." }
            transaction.update(clubRef, clubLeaveUpdatePayload(uid, club.memberCount))
        }.await()
    }

    suspend fun sendText(clubId: String, sender: UserData, text: String): Result<Message> = runCatching {
        ensureConfigured()
        requireAuthenticated(sender.uid)
        val normalized = text.trim()
        require(normalized.isNotEmpty()) { "Write a message first." }
        require(normalized.length <= ChatRepository.MessageCharacterLimit) {
            "Messages can be up to ${ChatRepository.MessageCharacterLimit} characters."
        }

        val clubRef = refs.club(clubId)
        val club = clubRef.get().await().toClub() ?: error("This club is no longer available.")
        require(sender.uid in club.memberIds) { "Join this club before posting." }
        val isLead = sender.uid == club.leadId || sender.uid in club.coLeadIds
        require(!club.settings.onlyLeadsCanPost || isLead) { "Only club leads can post right now." }

        val messageRef = refs.clubMessages(clubId).document()
        val messagePayload = textMessagePayload(sender, messageRef.id, normalized)
        val recipients = club.memberIds.filter { it != sender.uid && it.isNotBlank() }
        val batch = clubRef.firestore.batch()
        batch.set(messageRef, messagePayload)
        batch.update(clubRef, clubMessageMetadataPayload(sender, normalized, recipients))
        batch.commit().await()

        Message(
            id = messageRef.id,
            senderId = sender.uid,
            senderName = sender.name.ifBlank { "Student" },
            senderAvatar = sender.profilePicture,
            text = normalized,
            type = MessageType.Text.raw,
            createdAt = com.google.firebase.Timestamp.now(),
            clientMessageId = "android_${messageRef.id}",
            status = MessageStatus.Sent.raw,
        )
    }

    suspend fun markRead(clubId: String, uid: String): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        refs.club(clubId).update(
            mapOf(
                "unreadBy" to FieldValue.arrayRemove(uid),
                "deletedBy" to FieldValue.arrayRemove(uid),
            ),
        ).await()
    }

    private fun requireAuthenticated(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) {
            "Your session expired. Sign in and try again."
        }
    }

    private fun ensureConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) throw ClubConfigurationException()
    }

    private fun <T> configuredFlow(uid: String, stream: () -> Flow<T>): Flow<T> = flow {
        ensureConfigured()
        requireAuthenticated(uid)
        emitAll(stream())
    }

    companion object {
        internal const val PublicClubLimit = 20
        internal const val ClubMessageWindowSize = 100L
    }
}

internal fun clubJoinUpdatePayload(uid: String): Map<String, Any?> = mapOf(
    "memberIds" to FieldValue.arrayUnion(uid),
    "memberCount" to FieldValue.increment(1),
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun clubLeaveUpdatePayload(uid: String, memberCount: Int): Map<String, Any?> = mapOf(
    "memberIds" to FieldValue.arrayRemove(uid),
    "coLeadIds" to FieldValue.arrayRemove(uid),
    "memberCount" to (memberCount - 1).coerceAtLeast(0),
    "updatedAt" to FieldValue.serverTimestamp(),
)

internal fun clubMessageMetadataPayload(
    sender: UserData,
    lastMessage: String,
    recipientIds: List<String>,
): Map<String, Any?> = buildMap {
    put("lastMessage", lastMessage)
    put("lastSenderId", sender.uid)
    put("lastSenderName", sender.name.ifBlank { "Student" })
    put("updatedAt", FieldValue.serverTimestamp())
    put("deletedBy", FieldValue.arrayRemove(sender.uid))
    if (recipientIds.isNotEmpty()) {
        put("unreadBy", FieldValue.arrayUnion(*recipientIds.toTypedArray()))
    }
}

internal fun Club.clubActivityMillis(): Long =
    updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: Long.MIN_VALUE

internal fun normalizeClubInviteCode(value: String): String =
    value.filterNot(Char::isWhitespace).take(ClubInviteCodeLength)

internal const val ClubInviteCodeLength = 8

private class ClubConfigurationException : IllegalStateException(
    "Firebase is not configured for this build. Add app/google-services.json and rebuild.",
)
