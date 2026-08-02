package com.nextbench.app.auth

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.AuthRepository
import com.nextbench.data.firebase.AuthResult
import com.nextbench.data.firebase.AuthSession
import com.nextbench.data.firebase.CloudinaryResourceType
import com.nextbench.data.firebase.CloudinaryUploader
import com.nextbench.data.firebase.OrganizationSignupData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class OrganizationSignupStep { Type, Details, Document, Review }

@Immutable
data class OrganizationTypeOption(
    val id: String,
    val label: String,
    val description: String,
    val documentHint: String,
)

internal val OrganizationTypeOptions = listOf(
    OrganizationTypeOption("company", "Company or business", "Registered businesses, startups, and enterprises", "GSTIN certificate or business registration"),
    OrganizationTypeOption("school", "School or college", "Schools, colleges, universities, and education groups", "UDISE proof or affiliation certificate"),
    OrganizationTypeOption("coaching", "Coaching centre", "Tutoring centres, institutes, and training academies", "Registration certificate or trade licence"),
    OrganizationTypeOption("ngo", "NGO, club, or society", "Non-profits, student clubs, and registered societies", "Trust deed, society registration, or 12A/80G certificate"),
    OrganizationTypeOption("other", "Other organization", "Event organizers and other verified community partners", "An official registration or identity document"),
)

@Immutable
data class OrganizationSignupUiState(
    val step: OrganizationSignupStep = OrganizationSignupStep.Type,
    val type: String = "",
    val name: String = "",
    val website: String = "",
    val city: String = "",
    val description: String = "",
    val referralCode: String = "",
    val document: PreparedOrganizationDocument? = null,
    val termsAccepted: Boolean = false,
    val isPreparingDocument: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val completedSession: AuthSession? = null,
) {
    val selectedType: OrganizationTypeOption?
        get() = OrganizationTypeOptions.firstOrNull { it.id == type }
}

@HiltViewModel
class OrganizationSignupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val uploader: CloudinaryUploader,
    private val documentStore: OrganizationDocumentStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OrganizationSignupUiState())
    val state: StateFlow<OrganizationSignupUiState> = _state.asStateFlow()

    fun selectType(value: String) = _state.update { state ->
        state.copy(type = value.takeIf { id -> OrganizationTypeOptions.any { it.id == id } }.orEmpty(), error = null)
    }

    fun setName(value: String) = updateText { copy(name = value.take(100), error = null) }
    fun setWebsite(value: String) = updateText { copy(website = value.take(200), error = null) }
    fun setCity(value: String) = updateText { copy(city = value.take(80), error = null) }
    fun setDescription(value: String) = updateText { copy(description = value.take(500), error = null) }
    fun setReferralCode(value: String) = updateText { copy(referralCode = value.uppercase().take(24), error = null) }
    fun setTermsAccepted(value: Boolean) = _state.update { it.copy(termsAccepted = value, error = null) }

    fun continueForward(): Boolean {
        val snapshot = state.value
        organizationStepError(snapshot)?.let { message ->
            _state.update { it.copy(error = message) }
            return false
        }
        val next = OrganizationSignupStep.entries.getOrNull(snapshot.step.ordinal + 1) ?: return false
        _state.update { it.copy(step = next, error = null) }
        return true
    }

    fun goBack(): Boolean {
        val snapshot = state.value
        val previous = OrganizationSignupStep.entries.getOrNull(snapshot.step.ordinal - 1) ?: return false
        _state.update { it.copy(step = previous, error = null) }
        return true
    }

    fun prepareDocument(uri: Uri) {
        if (state.value.isPreparingDocument || state.value.isSubmitting) return
        _state.update { it.copy(isPreparingDocument = true, error = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { documentStore.prepare(uri) }
            result.fold(
                onSuccess = { prepared ->
                    val previous = state.value.document
                    _state.update { it.copy(document = prepared, isPreparingDocument = false, error = null) }
                    if (previous?.file != prepared.file) previous?.file?.delete()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isPreparingDocument = false,
                            error = error.message ?: "The selected document could not be prepared.",
                        )
                    }
                },
            )
        }
    }

    fun registerWithGoogle(idToken: String) {
        val snapshot = state.value
        if (snapshot.isSubmitting || snapshot.isPreparingDocument) return
        organizationSubmissionError(snapshot)?.let { message ->
            _state.update { it.copy(error = message) }
            return
        }
        val document = requireNotNull(snapshot.document)
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val upload = uploader.upload(
                    file = document.file,
                    folder = "nextbench/org_documents",
                    resourceType = CloudinaryResourceType.Auto,
                )
                val signup = OrganizationSignupData(
                    name = snapshot.name,
                    type = snapshot.type,
                    city = snapshot.city,
                    documentUrl = upload.url,
                    website = snapshot.website,
                    description = snapshot.description,
                    referralCode = snapshot.referralCode,
                )
                when (val result = authRepository.signUpOrganizationWithGoogleIdToken(idToken, signup)) {
                    is AuthResult.Success -> result.data
                    is AuthResult.Failure -> error(result.error.message)
                }
            }.fold(
                onSuccess = { session ->
                    document.file.delete()
                    _state.update { it.copy(isSubmitting = false, completedSession = session) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = error.organizationSignupMessage(),
                        )
                    }
                },
            )
        }
    }

    fun setExternalError(message: String) = _state.update { it.copy(isSubmitting = false, error = message) }
    fun clearCompletedSession() = _state.update { it.copy(completedSession = null) }

    override fun onCleared() {
        state.value.document?.file?.delete()
        super.onCleared()
    }

    private inline fun updateText(transform: OrganizationSignupUiState.() -> OrganizationSignupUiState) {
        _state.update(transform)
    }
}

internal fun organizationStepError(state: OrganizationSignupUiState): String? = when (state.step) {
    OrganizationSignupStep.Type -> if (state.selectedType == null) "Select your organization type." else null
    OrganizationSignupStep.Details -> when {
        state.name.trim().length < 2 -> "Enter the organization name."
        state.city.isBlank() -> "Enter the city where the organization is based."
        else -> null
    }
    OrganizationSignupStep.Document -> if (state.document == null) "Upload an official verification document." else null
    OrganizationSignupStep.Review -> if (!state.termsAccepted) "Accept the Terms and Privacy Policy to continue." else null
}

internal fun organizationSubmissionError(state: OrganizationSignupUiState): String? {
    for (step in OrganizationSignupStep.entries) {
        organizationStepError(state.copy(step = step))?.let { return it }
    }
    return null
}

private fun Throwable.organizationSignupMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Cloudinary", ignoreCase = true) -> "Document uploads are not configured for this build."
        raw.contains("network", ignoreCase = true) -> "Check your connection and try again."
        raw.isNotBlank() -> raw
        else -> "Organization registration could not be completed. Please try again."
    }
}
