package com.nextbench.app.clubs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ClubRepository
import com.nextbench.data.model.Club
import com.nextbench.data.model.Message
import com.nextbench.data.model.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ClubChatNoticeKind { Info, Success, Error }

@Immutable
data class ClubChatNotice(val id: Long, val message: String, val kind: ClubChatNoticeKind)

@Immutable
data class ClubChatUiState(
    val club: Club? = null,
    val messages: List<Message> = emptyList(),
    val composerText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isLeaving: Boolean = false,
    val error: String? = null,
    val notice: ClubChatNotice? = null,
) {
    fun isMember(viewerId: String?): Boolean = !viewerId.isNullOrBlank() && viewerId in club?.memberIds.orEmpty()
    fun canPost(viewerId: String?): Boolean {
        val current = club ?: return false
        val member = viewerId in current.memberIds
        val lead = viewerId == current.leadId || viewerId in current.coLeadIds
        return member && (!current.settings.onlyLeadsCanPost || lead)
    }

    fun canSend(viewerId: String?): Boolean = canPost(viewerId) && !isSending && composerText.trim().isNotEmpty()
}

@HiltViewModel
class ClubChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ClubRepository,
) : ViewModel() {
    private val clubId: String = requireNotNull(savedStateHandle["clubId"]) { "Club chat requires a clubId." }
    private val _state = MutableStateFlow(ClubChatUiState())
    val state: StateFlow<ClubChatUiState> = _state.asStateFlow()

    private var viewer: UserData? = null
    private var clubJob: Job? = null
    private var messagesJob: Job? = null
    private var noticeId = 0L

    fun syncViewer(user: UserData?) {
        if (viewer?.uid == user?.uid && (viewer == null) == (user == null)) return
        viewer = user
        clubJob?.cancel()
        messagesJob?.cancel()
        _state.value = ClubChatUiState(isLoading = user != null)
        val uid = user?.uid ?: return

        clubJob = viewModelScope.launch {
            repository.observeClub(clubId, uid)
                .catch { error -> showLoadError(error) }
                .collect { club ->
                    _state.update { it.copy(club = club, isLoading = false, error = if (club == null) "This club is no longer available." else null) }
                    if (club?.let { uid in it.memberIds } == true && uid in club.unreadBy) repository.markRead(clubId, uid)
                }
        }
        messagesJob = viewModelScope.launch {
            repository.observeMessages(clubId, uid)
                .catch { error -> showLoadError(error) }
                .collect { messages -> _state.update { it.copy(messages = messages, isLoading = false, error = null) } }
        }
    }

    fun setComposerText(value: String) = _state.update { it.copy(composerText = value.take(2_000)) }

    fun sendText(): Boolean {
        val sender = viewer ?: return false
        val snapshot = state.value
        if (!snapshot.canSend(sender.uid)) return false
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            repository.sendText(clubId, sender, snapshot.composerText).fold(
                onSuccess = { _state.update { it.copy(composerText = "", isSending = false) } },
                onFailure = { error -> _state.update { it.copy(isSending = false) }; showNotice(error.clubMessage(), ClubChatNoticeKind.Error) },
            )
        }
        return true
    }

    fun leaveClub(): Boolean {
        val uid = viewer?.uid ?: return false
        if (state.value.isLeaving || !state.value.isMember(uid)) return false
        _state.update { it.copy(isLeaving = true) }
        viewModelScope.launch {
            repository.leaveClub(uid, clubId).fold(
                onSuccess = { _state.update { it.copy(isLeaving = false) }; showNotice("You left the club.", ClubChatNoticeKind.Success) },
                onFailure = { error -> _state.update { it.copy(isLeaving = false) }; showNotice(error.clubMessage(), ClubChatNoticeKind.Error) },
            )
        }
        return true
    }

    fun dismissNotice(id: Long) = _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun showNotice(message: String, kind: ClubChatNoticeKind) = _state.update { it.copy(notice = ClubChatNotice(++noticeId, message, kind)) }
    private fun showLoadError(error: Throwable) = _state.update { it.copy(isLoading = false, error = error.clubMessage()) }
}
