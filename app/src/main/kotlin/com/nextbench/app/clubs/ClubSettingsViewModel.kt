package com.nextbench.app.clubs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ClubRepository
import com.nextbench.data.model.Club
import com.nextbench.data.model.ClubSettings
import com.nextbench.data.model.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ClubSettingsNotice(val id: Long, val message: String, val isError: Boolean = false)

@Immutable
data class ClubSettingsUiState(
    val club: Club? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLeaving: Boolean = false,
    val leftClub: Boolean = false,
    val error: String? = null,
    val notice: ClubSettingsNotice? = null,
) {
    fun isLead(uid: String?): Boolean = !uid.isNullOrBlank() && uid == club?.leadId
    fun isMember(uid: String?): Boolean = !uid.isNullOrBlank() && uid in club?.memberIds.orEmpty()
}

@HiltViewModel
class ClubSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ClubRepository,
) : ViewModel() {
    private val clubId: String = requireNotNull(savedStateHandle["clubId"]) { "Club settings requires a clubId." }
    private val _state = MutableStateFlow(ClubSettingsUiState())
    val state: StateFlow<ClubSettingsUiState> = _state.asStateFlow()

    private var viewer: UserData? = null
    private var clubJob: Job? = null
    private var noticeId = 0L

    fun syncViewer(user: UserData?) {
        if (viewer?.uid == user?.uid && (viewer == null) == (user == null)) return
        viewer = user
        clubJob?.cancel()
        _state.value = ClubSettingsUiState(isLoading = user != null)
        val uid = user?.uid ?: return
        clubJob = viewModelScope.launch {
            repository.observeClub(clubId, uid)
                .catch { error -> _state.update { it.copy(isLoading = false, error = error.clubMessage()) } }
                .collect { club ->
                    _state.update {
                        it.copy(
                            club = club,
                            isLoading = false,
                            error = if (club == null) "This club is no longer available." else null,
                        )
                    }
                }
        }
    }

    fun updateSettings(transform: (ClubSettings) -> ClubSettings): Boolean {
        val uid = viewer?.uid ?: return false
        val snapshot = state.value
        val club = snapshot.club ?: return false
        if (!snapshot.isLead(uid) || snapshot.isSaving) return false
        val next = transform(club.settings)
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.updateSettings(uid, clubId, next).fold(
                onSuccess = { _state.update { it.copy(isSaving = false) }; showNotice("Club settings updated.") },
                onFailure = { error -> _state.update { it.copy(isSaving = false) }; showNotice(error.clubMessage(), true) },
            )
        }
        return true
    }

    fun updateVisibility(): Boolean {
        val uid = viewer?.uid ?: return false
        val club = state.value.club ?: return false
        if (!state.value.isLead(uid) || state.value.isSaving) return false
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.updateVisibility(uid, clubId, if (club.type == "public") "private" else "public").fold(
                onSuccess = { _state.update { it.copy(isSaving = false) }; showNotice("Club visibility updated.") },
                onFailure = { error -> _state.update { it.copy(isSaving = false) }; showNotice(error.clubMessage(), true) },
            )
        }
        return true
    }

    fun leaveClub(): Boolean {
        val uid = viewer?.uid ?: return false
        val snapshot = state.value
        if (!snapshot.isMember(uid) || snapshot.isLead(uid) || snapshot.isLeaving) return false
        _state.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            repository.leaveClub(uid, clubId).fold(
                onSuccess = { _state.update { it.copy(isLeaving = false, leftClub = true) } },
                onFailure = { error -> _state.update { it.copy(isLeaving = false) }; showNotice(error.clubMessage(), true) },
            )
        }
        return true
    }

    fun dismissNotice(id: Long) = _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun showNotice(message: String, isError: Boolean = false) {
        _state.update { it.copy(notice = ClubSettingsNotice(++noticeId, message, isError)) }
    }
}
