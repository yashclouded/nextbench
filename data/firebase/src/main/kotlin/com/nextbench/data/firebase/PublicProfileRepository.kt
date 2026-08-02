package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

data class PublicProfileContent(
    val user: UserData?,
    val listings: List<Product>,
    val posts: List<Post>,
    val stats: PublicProfileStats = PublicProfileStats(),
)

data class PublicProfileStats(
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val followers: List<UserData> = emptyList(),
    val following: List<UserData> = emptyList(),
    val mutuals: List<UserData> = emptyList(),
    val mutualCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false,
)

@Singleton
class PublicProfileRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val refsProvider: Provider<FirestoreRefs>,
    private val functionsProvider: Provider<NbFunctions>,
) {
    private val auth get() = authProvider.get()
    private val refs get() = refsProvider.get()
    private val functions get() = functionsProvider.get()

    suspend fun load(userId: String): Result<PublicProfileContent> = runCatching {
        ensureConfigured()
        requireNotNull(auth.currentUser?.uid) { "Sign in to view profiles." }
        require(userId.isNotBlank()) { "Missing profile id." }
        val content = functions.getPublicProfileContent(userId).toPublicProfileContent()
        if (content.user == null) content else content.copy(stats = loadStats(userId, auth.currentUser?.uid.orEmpty()))
    }

    suspend fun resolveUsername(username: String): Result<String?> = runCatching {
        ensureConfigured()
        requireNotNull(auth.currentUser?.uid) { "Sign in to view profiles." }
        val normalized = username.trim().removePrefix("@").lowercase()
        require(normalized.isNotBlank()) { "Missing username." }
        functions.searchPublicUsers(mapOf("query" to "@$normalized", "limit" to 1))
            .firstOrNull { result -> result["username"]?.toString()?.equals(normalized, ignoreCase = true) == true }
            ?.get("id")
            ?.toString()
            ?.takeIf(String::isNotBlank)
    }

    private fun ensureConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) throw PublicProfileConfigurationException()
    }

    private fun requireAuthenticated(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) { "Your session expired. Sign in and try again." }
    }

    private suspend fun hasBlockRelationship(firstId: String, secondId: String): Boolean =
        refs.blocks.document("${firstId}_$secondId").get().await().exists() ||
            refs.blocks.document("${secondId}_$firstId").get().await().exists()

    private suspend fun loadStats(targetId: String, viewerId: String): PublicProfileStats = coroutineScope {
        val followersDeferred = async { refs.follows.whereEqualTo("followingId", targetId).get().await() }
        val followingDeferred = async { refs.follows.whereEqualTo("followerId", targetId).get().await() }
        val followersSnap = followersDeferred.await()
        val followingSnap = followingDeferred.await()
        val followerIds = followersSnap.documents.mapNotNull { it.getString("followerId") }.distinct()
        val followingIds = followingSnap.documents.mapNotNull { it.getString("followingId") }.distinct()
        val viewerFollowing = async { refs.follows.whereEqualTo("followerId", viewerId).get().await() }
        val viewerFollowers = async { refs.follows.whereEqualTo("followingId", viewerId).get().await() }
        val viewerNetwork = (viewerFollowing.await().documents.mapNotNull { it.getString("followingId") } + viewerFollowers.await().documents.mapNotNull { it.getString("followerId") }).toSet()
        val targetNetwork = (followerIds + followingIds).toSet()
        val mutualIds = targetNetwork.intersect(viewerNetwork).filter { it != viewerId }.toList()
        val displayIds = (followerIds.take(MaxListUsers) + followingIds.take(MaxListUsers) + mutualIds.take(MaxMutualUsers)).distinct()
        val users = displayIds.chunked(MaxUserBatch)
            .flatMap { ids -> functions.getPublicUsers(ids).mapNotNull(Map<String, Any?>::toPublicUser) }
            .associateBy(UserData::uid)
        PublicProfileStats(
            followersCount = followerIds.size,
            followingCount = followingIds.size,
            followers = followerIds.take(MaxListUsers).mapNotNull(users::get),
            following = followingIds.take(MaxListUsers).mapNotNull(users::get),
            mutuals = mutualIds.take(MaxMutualUsers).mapNotNull(users::get),
            mutualCount = mutualIds.size,
            isFollowing = viewerId in followerIds,
            isFollowedBy = viewerId in followingIds,
        )
    }

    suspend fun toggleFollow(targetId: String, viewer: UserData): Result<Boolean> = runCatching {
        ensureConfigured()
        requireAuthenticated(viewer.uid)
        require(viewer.verified) { "Verify your profile before following people." }
        require(targetId.isNotBlank() && targetId != viewer.uid) { "This profile cannot be followed." }
        require(!hasBlockRelationship(viewer.uid, targetId)) { "You cannot follow this member." }

        val existing = refs.follows
            .whereEqualTo("followerId", viewer.uid)
            .whereEqualTo("followingId", targetId)
            .get()
            .await()
        if (!existing.isEmpty) {
            val batch = refs.follows.firestore.batch()
            existing.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
            false
        } else {
            refs.follows.document().set(
                mapOf(
                    "followerId" to viewer.uid,
                    "followingId" to targetId,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            runCatching {
                functions.createNotification(
                    mapOf(
                        "userId" to targetId,
                        "type" to "new_message",
                        "title" to "New follower",
                        "message" to "${viewer.name.ifBlank { "Someone" }} started following you.",
                        "link" to "/profile/${viewer.uid}",
                    ),
                )
            }
            true
        }
    }

    suspend fun updateFollowersOnly(uid: String, enabled: Boolean): Result<Unit> = runCatching {
        ensureConfigured()
        requireAuthenticated(uid)
        refs.user(uid).update("chatPrivacy.followersOnly", enabled).await()
    }

    companion object {
        private const val MaxListUsers = 50
        private const val MaxMutualUsers = 3
        private const val MaxUserBatch = 30
    }
}

internal fun Map<String, Any?>.toPublicProfileContent(): PublicProfileContent = PublicProfileContent(
    user = this["user"].asStringMap().toPublicUser(),
    listings = mapList("products").mapNotNull(Map<String, Any?>::toProduct),
    posts = mapList("posts").mapNotNull(Map<String, Any?>::toPost),
)

internal fun Map<String, Any?>.toPublicUser(): UserData? {
    val uid = string("id")
    if (uid.isBlank()) return null
    return UserData(
        uid = uid,
        name = string("name"),
        username = nullableString("username"),
        school = string("school"),
        city = string("city"),
        about = nullableString("about"),
        profilePicture = nullableString("profilePicture"),
        coverPhoto = nullableString("coverPhoto"),
        verified = this["verified"] as? Boolean ?: false,
        reputation = (this["reputation"] as? Number)?.toDouble() ?: 0.0,
        accountType = string("accountType", "student"),
        orgName = nullableString("orgName"),
    )
}

private fun Map<String, Any?>.string(key: String, fallback: String = ""): String =
    get(key)?.toString()?.takeUnless { it == "null" } ?: fallback

private fun Map<String, Any?>.nullableString(key: String): String? =
    get(key)?.toString()?.takeUnless { it == "null" || it.isBlank() }

private class PublicProfileConfigurationException : IllegalStateException(
    "Firebase is not configured for this build. Add app/google-services.json and rebuild.",
)
