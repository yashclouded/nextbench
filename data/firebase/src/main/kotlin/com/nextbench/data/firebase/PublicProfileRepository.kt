package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class PublicProfileContent(
    val user: UserData?,
    val listings: List<Product>,
    val posts: List<Post>,
)

@Singleton
class PublicProfileRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val functionsProvider: Provider<NbFunctions>,
) {
    private val auth get() = authProvider.get()
    private val functions get() = functionsProvider.get()

    suspend fun load(userId: String): Result<PublicProfileContent> = runCatching {
        ensureConfigured()
        requireNotNull(auth.currentUser?.uid) { "Sign in to view profiles." }
        require(userId.isNotBlank()) { "Missing profile id." }
        functions.getPublicProfileContent(userId).toPublicProfileContent()
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
