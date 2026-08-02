package com.nextbench.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.nextbench.data.firebase.AuthFailure
import com.nextbench.data.firebase.AuthFailureKind
import com.nextbench.data.firebase.AuthRepository
import com.nextbench.data.firebase.AuthResult
import com.nextbench.data.firebase.AuthSession
import com.nextbench.data.firebase.StudentSignupData
import com.nextbench.data.firebase.NotificationRepository
import com.nextbench.data.firebase.SessionState
import com.nextbench.data.model.School
import com.nextbench.data.model.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OtpMode { Login, Signup }

enum class AuthStep { Email, Otp }

data class AuthUiState(
    val step: AuthStep = AuthStep.Email,
    val mode: OtpMode = OtpMode.Login,
    val email: String = "",
    val otp: String = "",
    val name: String = "",
    val school: String = "",
    val city: String = "",
    val referralCode: String = "",
    val termsAccepted: Boolean = false,
    val schools: List<School> = emptyList(),
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val canResend: Boolean = false,
    val otpRequestId: Int = 0,
    val error: AuthFailure? = null,
    val accountNotFound: Boolean = false,
    val completedSession: AuthSession? = null,
)

data class SignOutUiState(
    val isLoading: Boolean = false,
    val error: AuthFailure? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _signOutState = MutableStateFlow(SignOutUiState())
    val signOutState: StateFlow<SignOutUiState> = _signOutState.asStateFlow()

    val session: StateFlow<SessionState> = repository.sessionState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SessionState.Loading,
    )
    val currentUser: StateFlow<FirebaseUser?> = session.map { state ->
        (state as? SessionState.SignedIn)?.firebaseUser
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val userData: StateFlow<UserData?> = session.map { state ->
        (state as? SessionState.SignedIn)?.userData
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadSchools()
        viewModelScope.launch {
            session.collect { current ->
                (current as? SessionState.SignedIn)?.firebaseUser?.uid?.let { uid ->
                    notificationRepository.syncMessagingToken(uid)
                }
            }
        }
    }

    fun setMode(mode: OtpMode) = _state.update {
        if (it.mode == mode) {
            it.copy(error = null, accountNotFound = false, completedSession = null)
        } else {
            it.copy(
                mode = mode,
                step = AuthStep.Email,
                otp = "",
                error = null,
                accountNotFound = false,
                completedSession = null,
            )
        }
    }
    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null, accountNotFound = false) }
    fun setOtp(value: String) = _state.update { it.copy(otp = value.filter(Char::isDigit).take(6), error = null) }
    fun setName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun setSchool(value: String) = _state.update { it.copy(school = value, error = null) }
    fun setCity(value: String) = _state.update { it.copy(city = value, error = null) }
    fun setReferralCode(value: String) = _state.update { it.copy(referralCode = value.uppercase(), error = null) }
    fun setTermsAccepted(value: Boolean) = _state.update { it.copy(termsAccepted = value, error = null) }
    fun backToEmail() = _state.update { it.copy(step = AuthStep.Email, otp = "", error = null, canResend = false) }
    fun enableResend() = _state.update { it.copy(canResend = true) }

    suspend fun setPresence(uid: String, online: Boolean) {
        repository.setPresence(uid, online)
    }

    fun validateSignupDetails(): Boolean {
        val failure = signupFailure(state.value) ?: return true
        _state.update { it.copy(error = failure) }
        return false
    }

    fun sendOtp() {
        val snapshot = state.value
        if (snapshot.isLoading || snapshot.isGoogleLoading) return
        if (snapshot.mode == OtpMode.Signup) {
            val failure = signupFailure(snapshot)
            if (failure != null) {
                _state.update { it.copy(error = failure) }
                return
            }
        }
        _state.update { it.copy(isLoading = true, error = null, accountNotFound = false) }
        viewModelScope.launch {
            when (val result = repository.sendOtp(snapshot.email)) {
                is AuthResult.Success -> _state.update {
                    it.copy(
                        step = AuthStep.Otp,
                        isLoading = false,
                        otp = "",
                        canResend = false,
                        otpRequestId = it.otpRequestId + 1,
                    )
                }
                is AuthResult.Failure -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    fun verifyOtp() {
        val snapshot = state.value
        if (snapshot.otp.length != 6 || snapshot.isLoading || snapshot.isGoogleLoading) return
        _state.update { it.copy(isLoading = true, error = null, accountNotFound = false) }
        viewModelScope.launch {
            val signup = if (snapshot.mode == OtpMode.Signup) {
                StudentSignupData(snapshot.name, snapshot.school, snapshot.city, snapshot.referralCode)
            } else null
            when (val result = repository.verifyOtp(snapshot.email, snapshot.otp, signup)) {
                is AuthResult.Success -> _state.update { it.copy(isLoading = false, completedSession = result.data) }
                is AuthResult.Failure -> _state.update {
                    it.copy(
                        isLoading = false,
                        error = result.error,
                        accountNotFound = result.error.kind == AuthFailureKind.NotFound,
                    )
                }
            }
        }
    }

    fun completeGoogleLogin(idToken: String) {
        if (state.value.isLoading || state.value.isGoogleLoading) return
        _state.update { it.copy(isGoogleLoading = true, error = null, accountNotFound = false) }
        viewModelScope.launch {
            when (val result = repository.signInWithGoogleIdToken(idToken)) {
                is AuthResult.Success -> _state.update { it.copy(isGoogleLoading = false, completedSession = result.data) }
                is AuthResult.Failure -> _state.update {
                    it.copy(
                        isGoogleLoading = false,
                        error = result.error,
                        accountNotFound = result.error.kind == AuthFailureKind.NotFound,
                    )
                }
            }
        }
    }

    fun completeGoogleSignup(idToken: String) {
        val snapshot = state.value
        if (snapshot.isLoading || snapshot.isGoogleLoading) return
        val failure = signupFailure(snapshot)
        if (failure != null) {
            _state.update { it.copy(error = failure) }
            return
        }
        _state.update { it.copy(isGoogleLoading = true, error = null) }
        viewModelScope.launch {
            val signup = StudentSignupData(snapshot.name, snapshot.school, snapshot.city, snapshot.referralCode)
            when (val result = repository.signUpStudentWithGoogleIdToken(idToken, signup)) {
                is AuthResult.Success -> _state.update { it.copy(isGoogleLoading = false, completedSession = result.data) }
                is AuthResult.Failure -> _state.update { it.copy(isGoogleLoading = false, error = result.error) }
            }
        }
    }

    fun setExternalError(message: String) = _state.update {
        it.copy(isGoogleLoading = false, error = AuthFailure(AuthFailureKind.Unknown, message))
    }

    fun clearCompletedSession() = _state.update { it.copy(completedSession = null) }

    fun signOut() {
        if (signOutState.value.isLoading) return
        _signOutState.value = SignOutUiState(isLoading = true)
        viewModelScope.launch {
            (session.value as? SessionState.SignedIn)?.firebaseUser?.uid?.let {
                notificationRepository.removeMessagingToken(it)
            }
            when (val result = repository.signOut()) {
                is AuthResult.Success -> _signOutState.value = SignOutUiState()
                is AuthResult.Failure -> _signOutState.value = SignOutUiState(error = result.error)
            }
        }
    }

    fun clearSignOutError() {
        if (!signOutState.value.isLoading) _signOutState.update { it.copy(error = null) }
    }

    private fun loadSchools() {
        viewModelScope.launch {
            when (val result = repository.schools()) {
                is AuthResult.Success -> _state.update { it.copy(schools = result.data) }
                is AuthResult.Failure -> Unit // Signup can still show a manual school field.
            }
        }
    }

    private fun signupFailure(snapshot: AuthUiState): AuthFailure? {
        val usesKnownSchool = snapshot.schools.any {
            it.name.equals(snapshot.school.trim(), ignoreCase = true)
        }
        val message = when {
            snapshot.name.trim().length < 2 -> "Enter your full name."
            snapshot.school.isBlank() -> "Select your school."
            !usesKnownSchool && snapshot.city.isBlank() -> "Enter the city for your institute."
            !snapshot.termsAccepted -> "Accept the Terms and Privacy Policy to continue."
            else -> return null
        }
        return AuthFailure(AuthFailureKind.Validation, message)
    }
}
