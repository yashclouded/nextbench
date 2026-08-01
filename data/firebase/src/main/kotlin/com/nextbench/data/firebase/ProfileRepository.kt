package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class ProfileContent(
    val user: UserData?,
    val listings: List<Product>,
    val posts: List<Post>,
)

@Singleton
class ProfileRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val refsProvider: Provider<FirestoreRefs>,
) {
    private val auth get() = authProvider.get()
    private val refs get() = refsProvider.get()

    fun observeProfile(uid: String): Flow<ProfileContent> = configuredFlow(uid) {
        combine(
            refs.user(uid).snapshotFlow(),
            refs.products.whereEqualTo("sellerId", uid).snapshotFlow(),
            refs.posts.whereEqualTo("authorId", uid).snapshotFlow(),
        ) { userSnapshot, listingSnapshot, postSnapshot ->
            buildProfileContent(
                user = userSnapshot.takeIf(DocumentSnapshot::exists)
                    ?.toObject(UserData::class.java)
                    ?.copy(uid = uid),
                listings = listingSnapshot.documents.mapNotNull(DocumentSnapshot::toMarketplaceProduct),
                posts = postSnapshot.documents.mapNotNull(DocumentSnapshot::toProfilePost),
            )
        }
    }

    private fun requireAuthenticated(uid: String) {
        require(uid.isNotBlank() && auth.currentUser?.uid == uid) {
            "Your session expired. Sign in and try again."
        }
    }

    private fun ensureConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) throw ProfileConfigurationException()
    }

    private fun <T> configuredFlow(uid: String, stream: () -> Flow<T>): Flow<T> = flow {
        ensureConfigured()
        requireAuthenticated(uid)
        emitAll(stream())
    }
}

internal fun buildProfileContent(
    user: UserData?,
    listings: List<Product>,
    posts: List<Post>,
): ProfileContent = ProfileContent(
    user = user,
    listings = listings
        .sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
        .distinctBy(Product::id),
    posts = posts
        .sortedByDescending { it.createdAt?.toDate()?.time ?: Long.MIN_VALUE }
        .distinctBy(Post::id),
)

private fun DocumentSnapshot.toProfilePost(): Post? = data?.plus("id" to id)?.toPost()

private class ProfileConfigurationException : IllegalStateException(
    "Firebase is not configured for this build. Add app/google-services.json and rebuild.",
)
