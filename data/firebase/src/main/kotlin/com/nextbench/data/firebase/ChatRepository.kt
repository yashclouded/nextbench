package com.nextbench.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.nextbench.data.model.ChatRoom
import com.nextbench.data.model.Club
import com.nextbench.data.model.ClubSettings
import com.nextbench.data.model.FileAttachment
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageStatus
import com.nextbench.data.model.MessageType
import com.nextbench.data.model.UserData
import com.nextbench.data.model.VideoAttachment
import java.util.Date
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class ChatRoomListItem(
    val room: ChatRoom,
    val otherUser: UserData?,
    val viewerId: String,
) {
    val unread: Boolean get() = viewerId in room.unreadBy
    val archived: Boolean get() = viewerId in room.archivedBy
    val deleted: Boolean get() = viewerId in room.deletedBy && !unread
}

data class ChatRoomDetail(
    val room: ChatRoom,
    val otherUser: UserData?,
)

data class ChatBlockState(
    val blockedByViewer: Boolean = false,
    val blockedViewer: Boolean = false,
) {
    val isBlocked: Boolean get() = blockedByViewer || blockedViewer
}

/**
 * Shared Firestore boundary for direct conversations. The field names and write order mirror
 * the web client so a room created on one platform is immediately usable on the other.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val refsProvider: Provider<FirestoreRefs>,
) {
    private val auth get() = authProvider.get()
    private val refs get() = refsProvider.get()

    fun observeRooms(uid: String): Flow<List<ChatRoomListItem>> = configuredFlow(uid) {
        combine(
            refs.chatRooms.whereArrayContains("participants", uid).snapshotFlow(),
            refs.blocks.whereEqualTo("blockerId", uid).snapshotFlow(),
            refs.blocks.whereEqualTo("blockedId", uid).snapshotFlow(),
        ) { roomSnapshot, blockedSnapshot, blockedBySnapshot ->
            val blockedIds = blockedSnapshot.documents.mapNotNull { it.getString("blockedId") }.toSet()
            val blockedByIds = blockedBySnapshot.documents.mapNotNull { it.getString("blockerId") }.toSet()
            Triple(roomSnapshot, blockedIds, blockedByIds)
        }.map { (snapshot, blockedIds, blockedByIds) ->
                val blocked = blockedIds + blockedByIds
                val rooms = snapshot.documents.mapNotNull(DocumentSnapshot::toChatRoom)
                val otherIds = rooms
                    .flatMap { it.participants }
                    .filter { it.isNotBlank() && it != uid && it !in blocked }
                    .distinct()
                val users = coroutineScope {
                    otherIds.map { id ->
                        async { id to runCatching { loadUser(id) }.getOrNull() }
                    }.awaitAll().toMap()
                }
                rooms
                    .filter { uid in it.participants }
                    .filter { room -> room.participants.none { it != uid && it in blocked } }
                    .map { room ->
                        ChatRoomListItem(
                            room = room,
                            otherUser = room.participants
                                .firstOrNull { it != uid }
                                ?.let(users::get),
                            viewerId = uid,
                        )
                    }
                    .sortedWith(
                        compareByDescending<ChatRoomListItem> {
                            it.room.pinnedBy.contains(uid)
                        }.thenByDescending {
                            it.room.updatedAt?.toDate()?.time ?: Long.MIN_VALUE
                        },
                    )
            }
    }

    fun observeRoom(roomId: String, viewerId: String): Flow<ChatRoomDetail?> = configuredFlow(viewerId) {
        refs.chatRoom(roomId)
            .snapshotFlow()
            .map { snapshot ->
                val room = snapshot.toChatRoom() ?: return@map null
                if (viewerId !in room.participants) return@map null
                val otherId = room.participants.firstOrNull { it != viewerId }
                ChatRoomDetail(
                    room = room,
                    otherUser = otherId?.let { runCatching { loadUser(it) }.getOrNull() },
                )
            }
    }

    fun observeMessages(roomId: String, viewerId: String): Flow<List<Message>> = configuredFlow(viewerId) {
        refs.messages(roomId)
            .orderBy("createdAt")
            .limitToLast(MessageWindowSize)
            .snapshotFlow()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull(DocumentSnapshot::toMessage)
                    .filterNot { viewerId in it.deletedFor }
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }
    }

    fun observeBlockState(viewerId: String, otherUserId: String): Flow<ChatBlockState> = configuredFlow(viewerId) {
        val viewerBlocked = refs.blocks.document("${viewerId}_${otherUserId}").snapshotFlow()
        val blockedViewer = refs.blocks.document("${otherUserId}_${viewerId}").snapshotFlow()
        combine(viewerBlocked, blockedViewer) { first, second ->
            ChatBlockState(
                blockedByViewer = first.exists(),
                blockedViewer = second.exists(),
            )
        }
    }

    suspend fun sendText(
        roomId: String,
        sender: UserData,
        text: String,
    ): Result<Message> = runCatching {
        ensureConfigured()
        requireAuthenticated(sender.uid)
        val normalized = text.trim()
        require(normalized.isNotEmpty()) { "Write a message first." }
        require(normalized.length <= MessageCharacterLimit) {
            "Messages can be up to $MessageCharacterLimit characters."
        }

        val roomRef = refs.chatRoom(roomId)
        val roomSnapshot = roomRef.get().await()
        val room = roomSnapshot.toChatRoom()
            ?: throw FirebaseFirestoreException("This conversation is no longer available.", FirebaseFirestoreException.Code.NOT_FOUND)
        require(sender.uid in room.participants) { "You are not a member of this conversation." }
        require(room.status != "pending") { "Accept this chat request before replying." }
        val otherIds = room.participants.filter { it != sender.uid && it.isNotBlank() }
        require(!hasBlockRelationship(sender.uid, otherIds)) { "Cannot message this user." }

        val messageRef = refs.messages(roomId).document()
        val payload = textMessagePayload(
            sender = sender,
            messageId = messageRef.id,
            text = normalized,
        )
        val roomUpdate = roomMetadataPayload(
            senderId = sender.uid,
            lastMessage = normalized,
            recipientIds = otherIds,
        )
        val batch = roomRef.firestore.batch()
        batch.set(messageRef, payload)
        batch.update(roomRef, roomUpdate)
        batch.commit().await()

        Message(
            id = messageRef.id,
            senderId = sender.uid,
            senderName = sender.name.ifBlank { "Student" },
            senderAvatar = sender.profilePicture,
            text = normalized,
            type = MessageType.Text.raw,
            createdAt = Timestamp.now(),
            clientMessageId = "android_${messageRef.id}",
            status = MessageStatus.Sent.raw,
        )
    }

    suspend fun markRead(roomId: String, uid: String): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        refs.chatRoom(roomId).update(
            mapOf(
                "unreadBy" to FieldValue.arrayRemove(uid),
                "deletedBy" to FieldValue.arrayRemove(uid),
            ),
        ).await()
    }

    suspend fun acceptRequest(roomId: String, uid: String): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val roomRef = refs.chatRoom(roomId)
        val room = roomRef.get().await().toChatRoom()
            ?: throw FirebaseFirestoreException("This conversation is no longer available.", FirebaseFirestoreException.Code.NOT_FOUND)
        require(uid in room.participants && room.status == "pending" && room.requestedBy != uid) {
            "This chat request is no longer waiting for you."
        }
        roomRef.update(
            mapOf(
                "status" to "active",
                "requestedBy" to FieldValue.delete(),
                "unreadBy" to FieldValue.arrayRemove(uid),
                "deletedBy" to FieldValue.arrayRemove(uid),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun declineRequest(roomId: String, uid: String): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        val roomRef = refs.chatRoom(roomId)
        val room = roomRef.get().await().toChatRoom()
            ?: throw FirebaseFirestoreException("This conversation is no longer available.", FirebaseFirestoreException.Code.NOT_FOUND)
        require(uid in room.participants && room.status == "pending" && room.requestedBy != uid) {
            "This chat request is no longer waiting for you."
        }
        val batch = roomRef.firestore.batch()
        refs.messages(roomId).get().await().documents.forEach { batch.delete(it.reference) }
        batch.delete(roomRef)
        batch.commit().await()
    }

    private suspend fun loadUser(uid: String): UserData? =
        refs.user(uid).get().await().takeIf { it.exists() }?.toObject(UserData::class.java)?.copy(uid = uid)

    private suspend fun hasBlockRelationship(viewerId: String, otherIds: List<String>): Boolean =
        otherIds.any { otherId ->
            refs.blocks.document("${viewerId}_${otherId}").get().await().exists() ||
                refs.blocks.document("${otherId}_${viewerId}").get().await().exists()
        }

    private fun requireAuthenticated(uid: String) {
        require(auth.currentUser?.uid == uid) { "Your session expired. Sign in and try again." }
    }

    private fun ensureConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) throw ChatConfigurationException()
    }

    private fun <T> configuredFlow(uid: String, stream: () -> Flow<T>): Flow<T> = flow {
        ensureConfigured()
        requireAuthenticated(uid)
        emitAll(stream())
    }

    companion object {
        const val MessageCharacterLimit = 2_000
        const val MessageWindowSize = 100L
    }
}

internal fun DocumentSnapshot.toChatRoom(): ChatRoom? =
    takeIf(DocumentSnapshot::exists)?.data?.toChatRoom(id)

internal fun DocumentSnapshot.toMessage(): Message? =
    takeIf(DocumentSnapshot::exists)?.data?.toChatMessage(id)

internal fun Map<String, Any?>.toChatRoom(id: String): ChatRoom = ChatRoom(
    id = id,
    participants = chatStringList("participants"),
    type = chatString("type", "dm"),
    productId = chatNullableString("productId"),
    productTitle = chatNullableString("productTitle"),
    lastMessage = chatNullableString("lastMessage"),
    lastSenderId = chatNullableString("lastSenderId"),
    status = chatString("status", "active"),
    requestedBy = chatNullableString("requestedBy"),
    updatedAt = chatTimestamp("updatedAt"),
    unreadBy = chatStringList("unreadBy"),
    mutedBy = chatStringList("mutedBy"),
    archivedBy = chatStringList("archivedBy"),
    pinnedBy = chatStringList("pinnedBy"),
    deletedBy = chatStringList("deletedBy"),
)

internal fun DocumentSnapshot.toClub(): Club? =
    takeIf(DocumentSnapshot::exists)?.data?.toClub(id)

internal fun Map<String, Any?>.toClub(id: String): Club = Club(
    id = id,
    name = chatString("name"),
    description = chatNullableString("description"),
    avatar = chatNullableString("avatar") ?: chatNullableString("imageUrl"),
    school = chatString("school"),
    city = chatNullableString("city"),
    type = chatString("type", "public"),
    leadId = chatNullableString("leadId") ?: chatStringList("leadIds").firstOrNull(),
    coLeadIds = chatStringList("coLeadIds"),
    memberIds = chatStringList("memberIds"),
    inviteCode = chatNullableString("inviteCode"),
    memberCount = (get("memberCount") as? Number)?.toInt()?.coerceAtLeast(0)
        ?: chatStringList("memberIds").size,
    lastMessage = chatNullableString("lastMessage"),
    lastSenderId = chatNullableString("lastSenderId"),
    lastSenderName = chatNullableString("lastSenderName"),
    tags = chatStringList("tags"),
    pinnedMessageId = chatNullableString("pinnedMessageId"),
    pinnedMessageText = chatNullableString("pinnedMessageText"),
    updatedAt = chatTimestamp("updatedAt"),
    unreadBy = chatStringList("unreadBy"),
    mutedBy = chatStringList("mutedBy"),
    archivedBy = chatStringList("archivedBy"),
    pinnedBy = chatStringList("pinnedBy"),
    deletedBy = chatStringList("deletedBy"),
    settings = chatMap("settings").toClubSettings(),
    createdAt = chatTimestamp("createdAt"),
)

private fun Map<String, Any?>.toClubSettings(): ClubSettings = ClubSettings(
    hideMembersAbove50 = get("hideMembersAbove50") as? Boolean ?: false,
    onlyLeadsCanPost = get("onlyLeadsCanPost") as? Boolean ?: false,
    slowMode = (get("slowMode") as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
    muteNotifications = get("muteNotifications") as? Boolean ?: false,
)

internal fun Map<String, Any?>.toChatMessage(id: String): Message? {
    val senderId = chatString("senderId")
    if (senderId.isBlank()) return null
    val videoMap = chatMap("video")
    val fileMap = chatMap("file")
    val imageValue = get("image")
    val imageUrl = when (imageValue) {
        is String -> imageValue.takeIf(String::isNotBlank)
        is Map<*, *> -> imageValue.entries
            .firstOrNull { it.key?.toString() == "url" }
            ?.value?.toString()?.takeIf(String::isNotBlank)
        else -> null
    }
    return Message(
        id = id,
        senderId = senderId,
        senderName = chatNullableString("senderName"),
        senderAvatar = chatNullableString("senderAvatar"),
        text = chatNullableString("text"),
        image = imageUrl,
        type = chatString(
            "type",
            when {
                videoMap.isNotEmpty() -> MessageType.Video.raw
                fileMap.isNotEmpty() -> MessageType.File.raw
                chatNullableString("audioUrl") != null -> MessageType.Voice.raw
                imageUrl != null -> MessageType.Image.raw
                else -> MessageType.Text.raw
            },
        ),
        audioUrl = chatNullableString("audioUrl"),
        duration = chatLong("duration"),
        video = videoMap.takeIf(Map<*, *>::isNotEmpty)?.let { video ->
            VideoAttachment(
                url = video.chatString("url"),
                poster = video.chatNullableString("poster"),
                w = video.chatInt("w") ?: 0,
                h = video.chatInt("h") ?: 0,
                duration = video.chatLong("duration") ?: 0L,
            )
        },
        file = fileMap.takeIf(Map<*, *>::isNotEmpty)?.let { file ->
            FileAttachment(
                url = file.chatString("url"),
                name = file.chatString("name"),
                size = file.chatLong("size") ?: 0L,
                mime = file.chatString("mime"),
                pages = file.chatInt("pages"),
            )
        },
        createdAt = chatTimestamp("createdAt"),
        replyToMessageId = chatNullableString("replyToMessageId") ?: chatNullableString("replyToId"),
        replyToText = chatNullableString("replyToText"),
        replyToSenderName = chatNullableString("replyToSenderName"),
        replyToType = chatNullableString("replyToType"),
        deletedFor = chatStringList("deletedFor"),
        isDeletedForEveryone = get("isDeletedForEveryone") as? Boolean ?: false,
        reactions = chatReactions("reactions"),
        readBy = chatStringList("readBy"),
        clientMessageId = chatNullableString("clientMessageId"),
        status = chatString("status", MessageStatus.Sent.raw),
        forwardedFrom = chatForwardedFrom("forwardedFrom"),
    )
}

internal fun textMessagePayload(
    sender: UserData,
    messageId: String,
    text: String,
): Map<String, Any?> = mapOf(
    "senderId" to sender.uid,
    "senderName" to sender.name.ifBlank { "Student" },
    "senderAvatar" to sender.profilePicture,
    "text" to text,
    "type" to MessageType.Text.raw,
    "createdAt" to FieldValue.serverTimestamp(),
    "clientMessageId" to "android_$messageId",
    "status" to MessageStatus.Sent.raw,
)

internal fun roomMetadataPayload(
    senderId: String,
    lastMessage: String,
    recipientIds: List<String>,
): Map<String, Any?> = buildMap {
    put("lastMessage", lastMessage)
    put("lastSenderId", senderId)
    put("updatedAt", FieldValue.serverTimestamp())
    put("deletedBy", FieldValue.arrayRemove(senderId))
    if (recipientIds.isNotEmpty()) {
        put("unreadBy", FieldValue.arrayUnion(*recipientIds.toTypedArray()))
    }
}

private class ChatConfigurationException : IllegalStateException(
    "Firebase is not configured for this build. Add app/google-services.json and rebuild.",
)

private fun Map<String, Any?>.chatString(key: String, fallback: String = ""): String =
    get(key)?.toString()?.takeUnless { it == "null" } ?: fallback

private fun Map<String, Any?>.chatNullableString(key: String): String? =
    chatString(key).takeIf(String::isNotBlank)

private fun Map<String, Any?>.chatStringList(key: String): List<String> =
    (get(key) as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()

private fun Map<String, Any?>.chatLong(key: String): Long? = (get(key) as? Number)?.toLong()

private fun Map<String, Any?>.chatInt(key: String): Int? = (get(key) as? Number)?.toInt()

private fun Map<String, Any?>.chatTimestamp(key: String): Timestamp? = when (val value = get(key)) {
    is Timestamp -> value
    is Date -> Timestamp(value)
    is Number -> Timestamp(Date(value.toLong()))
    else -> null
}

private fun Map<String, Any?>.chatMap(key: String): Map<String, Any?> =
    (get(key) as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty()

private fun Map<String, Any?>.chatReactions(key: String): Map<String, List<String>> =
    chatMap(key).mapValues { (_, value) ->
        (value as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
    }

private fun Map<String, Any?>.chatForwardedFrom(key: String): String? = when (val value = get(key)) {
    is String -> value.takeIf(String::isNotBlank)
    is Map<*, *> -> value.entries
        .firstOrNull { it.key?.toString() == "senderName" }
        ?.value?.toString()?.takeIf(String::isNotBlank)
    else -> null
}
