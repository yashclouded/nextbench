package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctionsException
import com.nextbench.data.model.School
import com.nextbench.data.model.UserData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class StudentSignupData(
    val name: String,
    val school: String,
    val city: String,
    val referralCode: String = "",
)

data class OrganizationSignupData(
    val name: String,
    val type: String,
    val city: String,
    val documentUrl: String,
    val website: String = "",
    val description: String = "",
    val referralCode: String = "",
)

data class AuthSession(
    val firebaseUser: FirebaseUser,
    val isNewUser: Boolean,
)

enum class AuthFailureKind {
    Validation,
    NotFound,
    RateLimited,
    Network,
    Configuration,
    Unknown,
}

data class AuthFailure(
    val kind: AuthFailureKind,
    val message: String,
)

sealed interface AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>
    data class Failure(val error: AuthFailure) : AuthResult<Nothing>
}

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(
        val firebaseUser: FirebaseUser,
        val userData: UserData?,
    ) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val refsProvider: Provider<FirestoreRefs>,
    private val functionsProvider: Provider<NbFunctions>,
) {

    private val auth get() = authProvider.get()
    private val refs get() = refsProvider.get()
    private val functions get() = functionsProvider.get()

    val sessionState: Flow<SessionState> = if (!BuildConfig.FIREBASE_CONFIGURED) {
        flowOf(SessionState.SignedOut)
    } else {
        callbackFlow {
            var profileListener: ListenerRegistration? = null
            val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                profileListener?.remove()
                val user = firebaseAuth.currentUser
                if (user == null) {
                    trySend(SessionState.SignedOut)
                } else {
                    trySend(SessionState.Loading)
                    profileListener = refs.user(user.uid).addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            close(error)
                            return@addSnapshotListener
                        }
                        trySend(
                            SessionState.SignedIn(
                                firebaseUser = user,
                                userData = snapshot?.takeIf { it.exists() }?.toObject(UserData::class.java),
                            ),
                        )
                    }
                }
            }
            auth.addAuthStateListener(authListener)
            awaitClose {
                profileListener?.remove()
                auth.removeAuthStateListener(authListener)
            }
        }.distinctUntilChanged()
    }

    val authState: Flow<FirebaseUser?> = sessionState.map { state ->
        (state as? SessionState.SignedIn)?.firebaseUser
    }.distinctUntilChanged()

    fun userData(uid: String): Flow<UserData?> = if (!BuildConfig.FIREBASE_CONFIGURED) {
        flowOf(null)
    } else {
        refs.user(uid).snapshotFlow()
            .map { snapshot -> if (snapshot.exists()) snapshot.toObject(UserData::class.java) else null }
            .distinctUntilChanged()
    }

    suspend fun schools(): AuthResult<List<School>> = authResult {
        ensureConfigured()
        refs.schools.orderBy("name").get().await().toObjects(School::class.java)
    }

    suspend fun sendOtp(email: String): AuthResult<Unit> = authResult {
        ensureConfigured()
        require(email.isValidEmail()) { "Please enter a valid email address." }
        functions.sendAuthOtpEmail(email.normalizedEmail())
        Unit
    }

    suspend fun verifyOtp(
        email: String,
        otp: String,
        signupData: StudentSignupData? = null,
    ): AuthResult<AuthSession> = authResult {
        ensureConfigured()
        val response = functions.verifyAuthOtpEmail(
            otpVerificationPayload(email, otp, signupData),
        )
        val session = signInFromResponse(response)
        if (signupData == null && !refs.user(session.firebaseUser.uid).get().await().exists()) {
            auth.signOut()
            throw NoProfileException()
        }
        session
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult<AuthSession> = authResult {
        ensureConfigured()
        val result = googleAuth(idToken)
        val firebaseUser = requireNotNull(result.user) { "Google sign-in did not return a user." }
        if (!refs.user(firebaseUser.uid).get().await().exists()) {
            auth.signOut()
            throw NoProfileException()
        }
        AuthSession(firebaseUser, result.additionalUserInfo?.isNewUser == true)
    }

    suspend fun signUpStudentWithGoogleIdToken(
        idToken: String,
        signupData: StudentSignupData,
    ): AuthResult<AuthSession> = authResult {
        ensureConfigured()
        require(signupData.name.trim().length >= 2) { "Enter your full name." }
        require(signupData.school.isNotBlank()) { "Select your school." }
        val result = googleAuth(idToken)
        val firebaseUser = requireNotNull(result.user) { "Google sign-in did not return a user." }
        ensureStudentProfile(firebaseUser, signupData)
        AuthSession(firebaseUser, result.additionalUserInfo?.isNewUser == true)
    }

    suspend fun signUpOrganizationWithGoogleIdToken(
        idToken: String,
        signupData: OrganizationSignupData,
    ): AuthResult<AuthSession> = authResult {
        ensureConfigured()
        require(signupData.name.trim().length >= 2) { "Enter the organization name." }
        require(signupData.city.isNotBlank()) { "Enter the organization city." }
        require(signupData.documentUrl.isNotBlank()) { "Upload a verification document." }
        require(signupData.type in OrganizationTypes) { "Select a valid organization type." }
        val result = googleAuth(idToken)
        val firebaseUser = requireNotNull(result.user) { "Google sign-in did not return a user." }
        ensureOrganizationProfile(firebaseUser, signupData)
        AuthSession(firebaseUser, result.additionalUserInfo?.isNewUser == true)
    }

    suspend fun signOut(): AuthResult<Unit> = authResult {
        ensureConfigured()
        auth.signOut()
        Unit
    }

    suspend fun updateVerificationSubmission(
        uid: String,
        idCardUrl: String,
        selfieUrl: String,
    ): AuthResult<Unit> = authResult {
        ensureConfigured()
        require(idCardUrl.isNotBlank() && selfieUrl.isNotBlank()) { "Verification photos are required." }
        auth.currentUser?.getIdToken(true)?.await()
        refs.user(uid).update(
            mapOf(
                "idCardUrl" to idCardUrl,
                "selfieUrl" to selfieUrl,
                "verificationStatus" to "pending",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Unit
    }

    private suspend fun googleAuth(idToken: String) =
        auth.signInWithCredential(
            GoogleAuthProvider.getCredential(
                idToken.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("Google sign-in did not return an identity token."),
                null,
            ),
        ).await()

    private suspend fun ensureStudentProfile(user: FirebaseUser, data: StudentSignupData) {
        val userRef = refs.user(user.uid)
        if (userRef.get().await().exists()) return

        val referrerId = data.referralCode.trim()
            .takeIf(String::isNotBlank)
            ?.let { functions.lookupReferralCode(it.uppercase()) }
        val batch = userRef.firestore.batch()
        val userData = mutableMapOf<String, Any?>(
            "name" to data.name.trim(),
            "email" to user.email.orEmpty(),
            "school" to data.school.trim(),
            "city" to data.city.trim().ifBlank { "Lucknow" },
            "verified" to false,
            "verificationStatus" to "pending",
            "reputation" to 5.0,
            "isAdmin" to false,
            "profilePicture" to user.photoUrl?.toString(),
            "idCardUrl" to null,
            "selfieUrl" to null,
            "about" to null,
            "accountType" to "student",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (referrerId != null) {
            userData["referredBy"] = referrerId
            batch.set(
                refs.users.document(referrerId).collection("referrals").document(user.uid),
                mapOf("timestamp" to FieldValue.serverTimestamp()),
            )
        }
        batch.set(userRef, userData)
        batch.commit().await()
    }

    private suspend fun ensureOrganizationProfile(user: FirebaseUser, data: OrganizationSignupData) {
        val userRef = refs.user(user.uid)
        if (userRef.get().await().exists()) return

        val referrerId = data.referralCode.trim()
            .takeIf(String::isNotBlank)
            ?.let { functions.lookupReferralCode(it.uppercase()) }
        val batch = userRef.firestore.batch()
        val userData = mutableMapOf<String, Any?>(
            "name" to data.name.trim(),
            "email" to user.email.orEmpty(),
            "school" to data.name.trim(),
            "city" to data.city.trim(),
            "verified" to false,
            "verificationStatus" to "pending",
            "reputation" to 5.0,
            "isAdmin" to false,
            "profilePicture" to user.photoUrl?.toString(),
            "idCardUrl" to null,
            "selfieUrl" to null,
            "about" to data.description.trim().ifBlank { null },
            "accountType" to "organization",
            "orgName" to data.name.trim(),
            "orgType" to data.type,
            "orgDocumentUrl" to data.documentUrl,
            "orgWebsite" to data.website.trim().ifBlank { null },
            "orgDescription" to data.description.trim().ifBlank { null },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (referrerId != null) {
            userData["referredBy"] = referrerId
            batch.set(
                refs.users.document(referrerId).collection("referrals").document(user.uid),
                mapOf("timestamp" to FieldValue.serverTimestamp()),
            )
        }
        batch.set(userRef, userData)
        batch.commit().await()
    }

    private suspend fun signInFromResponse(response: Map<String, Any?>): AuthSession {
        val customToken = response["customToken"]?.toString().orEmpty()
        val result = if (customToken.isNotBlank()) {
            auth.signInWithCustomToken(customToken).await()
        } else {
            val email = response["email"]?.toString().orEmpty()
            val password = response["loginPassword"]?.toString().orEmpty()
            require(email.isNotBlank() && password.isNotBlank()) { "Authentication service returned an incomplete response." }
            auth.signInWithEmailAndPassword(email, password).await()
        }
        return AuthSession(
            firebaseUser = requireNotNull(result.user) { "Authentication did not return a user." },
            isNewUser = response["isNewUser"] as? Boolean ?: false,
        )
    }

    private fun ensureConfigured() {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            throw FirebaseConfigurationException()
        }
    }
}

internal fun otpVerificationPayload(
    email: String,
    otp: String,
    signupData: StudentSignupData?,
): Map<String, Any?> {
    val digits = otp.filter(Char::isDigit)
    require(email.isValidEmail()) { "Please enter a valid email address." }
    require(digits.length == 6) { "Enter the 6-digit code." }
    return buildMap {
        put("email", email.normalizedEmail())
        put("otp", digits)
        if (signupData != null) {
            require(signupData.name.trim().length >= 2) { "Enter your full name." }
            require(signupData.school.isNotBlank()) { "Select your school." }
            put("isSignup", true)
            put(
                "signupData",
                mapOf(
                    "name" to signupData.name.trim(),
                    "school" to signupData.school.trim(),
                    "city" to signupData.city.trim().ifBlank { "Lucknow" },
                    "referralCode" to signupData.referralCode.trim(),
                ),
            )
        }
    }
}

private suspend fun <T> authResult(block: suspend () -> T): AuthResult<T> = try {
    AuthResult.Success(block())
} catch (error: Exception) {
    AuthResult.Failure(error.toAuthFailure())
}

private fun Exception.toAuthFailure(): AuthFailure {
    val detailsMessage = (this as? FirebaseFunctionsException)
        ?.details
        .let { it as? Map<*, *> }
        ?.get("message")
        ?.toString()
    val rawMessage = detailsMessage ?: message
    val message = rawMessage
        ?.removePrefix("Error: ")
        ?.substringAfter("] ")
        ?.takeIf(String::isNotBlank)
        ?: "Something went wrong. Please try again."
    val functionsCode = (this as? FirebaseFunctionsException)?.code
    val authCode = (this as? FirebaseAuthException)?.errorCode.orEmpty()
    val kind = when {
        this is IllegalArgumentException -> AuthFailureKind.Validation
        this is FirebaseConfigurationException -> AuthFailureKind.Configuration
        this is NoProfileException -> AuthFailureKind.NotFound
        functionsCode == FirebaseFunctionsException.Code.NOT_FOUND -> AuthFailureKind.NotFound
        functionsCode == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AuthFailureKind.RateLimited
        functionsCode == FirebaseFunctionsException.Code.UNAVAILABLE -> AuthFailureKind.Network
        authCode.contains("NETWORK", ignoreCase = true) -> AuthFailureKind.Network
        else -> AuthFailureKind.Unknown
    }
    return AuthFailure(kind, message)
}

private fun String.normalizedEmail(): String = trim().lowercase()

private fun String.isValidEmail(): Boolean =
    Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalizedEmail())

private class NoProfileException : IllegalStateException(
    "No NextBench profile exists for this Google account. Sign up first.",
)

private class FirebaseConfigurationException : IllegalStateException(
    "Firebase is not configured for this build. Add app/google-services.json and rebuild.",
)

private val OrganizationTypes = setOf("company", "school", "coaching", "ngo", "other")
