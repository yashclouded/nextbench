package com.nextbench.data.firebase

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Typed wrappers around the ~20 callable Cloud Functions the Android app uses.
 * All functions are fire-and-forget or return a Map; callers cast to the expected shape.
 * Suspend functions throw on non-2xx; callers should wrap in [runCatchingNb].
 */
@Singleton
class NbFunctions @Inject constructor(private val functions: FirebaseFunctions) {

    private suspend fun call(name: String, data: Any? = null): Any? =
        functions.getHttpsCallable(name).call(data).await().data

    suspend fun createNotification(params: Map<String, Any?>) = call("createNotification", params)

    @Suppress("UNCHECKED_CAST")
    suspend fun getDiscoveryFeed(params: Map<String, Any?>): List<Map<String, Any?>> =
        (call("getDiscoveryFeed", params) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend fun getRecommendedProducts(params: Map<String, Any?>): List<Map<String, Any?>> =
        (call("getRecommendedProducts", params) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend fun getSuggestedUsers(params: Map<String, Any?>): List<Map<String, Any?>> =
        (call("getSuggestedUsers", params) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend fun searchDiscovery(params: Map<String, Any?>): Map<String, Any?> =
        (call("searchDiscovery", params) as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    suspend fun searchPublicUsers(params: Map<String, Any?>): List<Map<String, Any?>> =
        (call("searchPublicUsers", params) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend fun getPublicProfile(uid: String): Map<String, Any?> =
        (call("getPublicProfile", mapOf("uid" to uid)) as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    suspend fun getPublicProfileContent(params: Map<String, Any?>): Map<String, Any?> =
        (call("getPublicProfileContent", params) as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    suspend fun getPostReplies(params: Map<String, Any?>): List<Map<String, Any?>> =
        (call("getPostReplies", params) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    suspend fun getProductReviews(productId: String): List<Map<String, Any?>> =
        (call("getProductReviews", mapOf("productId" to productId)) as? List<*>)
            ?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

    suspend fun createProductReview(params: Map<String, Any?>) =
        call("createProductReview", params)

    @Suppress("UNCHECKED_CAST")
    suspend fun createInviteCode(clubId: String): String =
        (call("createInviteCode", mapOf("clubId" to clubId)) as? Map<*, *>)
            ?.get("code")?.toString() ?: ""

    suspend fun submitInviteCode(code: String) =
        call("submitInviteCode", mapOf("code" to code))

    @Suppress("UNCHECKED_CAST")
    suspend fun lookupReferralCode(code: String): Map<String, Any?> =
        (call("lookupReferralCode", mapOf("code" to code)) as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    suspend fun isReferralCodeAvailable(code: String): Boolean =
        (call("isReferralCodeAvailable", mapOf("code" to code)) as? Map<*, *>)
            ?.get("available") as? Boolean ?: false

    suspend fun sendAuthOtpEmail(email: String) =
        call("sendAuthOtpEmail", mapOf("email" to email))

    suspend fun verifyAuthOtpEmail(params: Map<String, Any?>) =
        call("verifyAuthOtpEmail", params)

    @Suppress("UNCHECKED_CAST")
    suspend fun getLandingStats(): Map<String, Any?> =
        (call("getLandingStats") as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value } ?: emptyMap()

    suspend fun deletePostCascade(postId: String) =
        call("deletePostCascade", mapOf("postId" to postId))
}
