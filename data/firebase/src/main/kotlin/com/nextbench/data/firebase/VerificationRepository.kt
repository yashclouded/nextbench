package com.nextbench.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.nextbench.data.model.UserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

enum class VerificationStage {
    Uploading,
    Reviewing,
    Complete,
}

data class VerificationOutcome(
    val status: String,
    val reason: String? = null,
    val automated: Boolean = false,
)

/** Bridges the web verification endpoint while keeping upload and review states observable. */
@Singleton
class VerificationRepository @Inject constructor(
    private val authProvider: Provider<FirebaseAuth>,
    private val uploader: CloudinaryUploader,
    private val authRepository: AuthRepository,
) {

    private val auth get() = authProvider.get()

    suspend fun submit(
        user: UserData,
        idCard: File,
        selfie: File,
        onStage: suspend (VerificationStage) -> Unit = {},
    ): Result<VerificationOutcome> = runCatching {
        require(user.uid.isNotBlank()) { "Your session has expired. Please sign in again." }
        require(idCard.isFile && idCard.length() > 0) { "Choose a student ID photo." }
        require(selfie.isFile && selfie.length() > 0) { "Choose a selfie holding your ID." }

        onStage(VerificationStage.Uploading)
        val (idUpload, selfieUpload) = coroutineScope {
            val id = async { uploader.upload(idCard, "nextbench/ids", CloudinaryResourceType.Image) }
            val selfieResult = async { uploader.upload(selfie, "nextbench/ids", CloudinaryResourceType.Image) }
            id.await() to selfieResult.await()
        }
        when (val update = authRepository.updateVerificationSubmission(user.uid, idUpload.url, selfieUpload.url)) {
            is AuthResult.Success -> Unit
            is AuthResult.Failure -> error(update.error.message)
        }

        onStage(VerificationStage.Reviewing)
        val token = requireNotNull(auth.currentUser) { "Your session has expired. Please sign in again." }
            .getIdToken(false)
            .await()
            .token
            .orEmpty()
        val outcome = review(
            token = token,
            uid = user.uid,
            profileName = user.name,
            schoolName = user.school,
            idCardUrl = idUpload.url,
            selfieUrl = selfieUpload.url,
        )
        onStage(VerificationStage.Complete)
        outcome
    }

    private suspend fun review(
        token: String,
        uid: String,
        profileName: String,
        schoolName: String,
        idCardUrl: String,
        selfieUrl: String,
    ): VerificationOutcome = withContext(Dispatchers.IO) {
        val connection = (URL("https://nextbench.in/api/verify").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val body = JSONObject()
                .put("uid", uid)
                .put("profileName", profileName)
                .put("schoolName", schoolName)
                .put("idCardUrl", idCardUrl)
                .put("selfieUrl", selfieUrl)
                .toString()
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = response.takeIf(String::isNotBlank)?.let(::JSONObject)
            if (code !in 200..299) {
                throw IllegalStateException(json?.optString("error") ?: "Verification service unavailable.")
            }
            val status = json?.optString("verificationStatus").orEmpty().ifBlank { "pending" }
            VerificationOutcome(
                status = status,
                reason = json?.optString("rejectionReason")?.takeIf(String::isNotBlank)
                    ?: json?.optString("reason")?.takeIf(String::isNotBlank),
                automated = json?.optBoolean("success") == true,
            )
        } catch (_: Exception) {
            // The website deliberately queues manual review when automation is unavailable.
            VerificationOutcome(
                status = "pending",
                reason = "Automated review is unavailable right now. Your submission is queued for manual review.",
            )
        } finally {
            connection.disconnect()
        }
    }
}
