package com.nextbench.app.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ChatBlockState
import com.nextbench.data.firebase.ChatRepository
import com.nextbench.data.firebase.ChatRoomDetail
import com.nextbench.data.model.Message
import com.nextbench.data.model.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class ChatNoticeKind { Info, Success, Error }

@Immutable
data class ChatNotice(
    val id: Long,
    val message: String,
    val kind: ChatNoticeKind,
)

@Immutable
data class ChatRoomUiState(
    val room: ChatRoomDetail? = null,
    val messages: List<Message> = emptyList(),
    val blockState: ChatBlockState = ChatBlockState(),
    val composerText: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isActingOnRequest: Boolean = false,
    val error: String? = null,
    val notice: ChatNotice? = null,
) {
    val pendingRequest: Boolean get() = room?.room?.status == "pending"
    val pendingRequester: String? get() = room?.room?.requestedBy
    val blocked: Boolean get() = blockState.isBlocked

    fun canRespondToRequest(viewerId: String?): Boolean =
        pendingRequest && !viewerId.isNullOrBlank() && pendingRequester != viewerId

    fun canSend(viewerId: String?): Boolean =
        !viewerId.isNullOrBlank() && room != null && !pendingRequest && !blocked && !isSending
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
) : ViewModel() {
    private val roomId: String = requireNotNull(savedStateHandle["roomId"]) {
        "Conversation requires a roomId navigation argument."
    }
    private val _state = MutableStateFlow(ChatRoomUiState())
    val state: StateFlow<ChatRoomUiState> = _state.asStateFlow()

    private var viewer: UserData? = null
    private var roomJob: Job? = null
    private var messagesJob: Job? = null
    private var blockJob: Job? = null
    private var noticeId = 0L

    fun syncViewer(user: UserData?) {
        if (viewer?.uid == user?.uid && (viewer == null) == (user == null)) return
        viewer = user
        roomJob?.cancel()
        messagesJob?.cancel()
        blockJob?.cancel()
        _state.value = ChatRoomUiState(isLoading = user != null)
        val uid = user?.uid ?: return

        roomJob = viewModelScope.launch {
            repository.observeRoom(roomId, uid)
                .catch { error -> showLoadError(error) }
                .collect { room ->
                    _state.update { it.copy(room = room, isLoading = false, error = null) }
                    if (uid in room?.room?.unreadBy.orEmpty()) repository.markRead(roomId, uid)
                }
        }
        messagesJob = viewModelScope.launch {
            repository.observeMessages(roomId, uid)
                .catch { error -> showLoadError(error) }
                .collect { messages ->
                    _state.update { it.copy(messages = messages, isLoading = false, error = null) }
                }
        }
        blockJob = viewModelScope.launch {
            _state
                .map { detail -> detail.room?.room?.participants?.firstOrNull { it != uid } }
                .distinctUntilChanged()
                .flatMapLatest { otherId ->
                    if (otherId.isNullOrBlank()) flowOf(ChatBlockState())
                    else repository.observeBlockState(uid, otherId)
                }
                .catch { error -> showLoadError(error) }
                .collect { blockState ->
                    _state.update { it.copy(blockState = blockState) }
                }
        }
    }

    fun setComposerText(value: String) {
        _state.update { it.copy(composerText = value.take(ChatRepository.MessageCharacterLimit)) }
    }

    fun sendText(): Boolean {
        val user = viewer ?: return false
        val snapshot = state.value
        val room = snapshot.room?.room ?: return false
        if (snapshot.isSending || snapshot.blocked || room.status == "pending") return false
        val text = snapshot.composerText.trim()
        if (text.isEmpty()) return false
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            repository.sendText(roomId, user, text).fold(
                onSuccess = {
                    _state.update { it.copy(composerText = "", isSending = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isSending = false) }
                    showNotice(error.chatMessage(), ChatNoticeKind.Error)
                },
            )
        }
        return true
    }

    fun acceptRequest(): Boolean = requestAction { repository.acceptRequest(roomId, requireViewerId()) }

    fun declineRequest(): Boolean = requestAction { repository.declineRequest(roomId, requireViewerId()) }

    fun dismissNotice(id: Long) {
        _state.update { current -> if (current.notice?.id == id) current.copy(notice = null) else current }
    }

    private fun requestAction(operation: suspend () -> Result<Unit>): Boolean {
        val user = viewer ?: return false
        val snapshot = state.value
        if (!snapshot.pendingRequest || snapshot.isActingOnRequest || snapshot.blocked) return false
        if (snapshot.pendingRequester == user.uid) return false
        _state.update { it.copy(isActingOnRequest = true) }
        viewModelScope.launch {
            operation().fold(
                onSuccess = {
                    _state.update { it.copy(isActingOnRequest = false) }
                    showNotice("Conversation updated", ChatNoticeKind.Success)
                },
                onFailure = { error ->
                    _state.update { it.copy(isActingOnRequest = false) }
                    showNotice(error.chatMessage(), ChatNoticeKind.Error)
                },
            )
        }
        return true
    }

    private fun requireViewerId(): String = requireNotNull(viewer?.uid)

    private fun showNotice(message: String, kind: ChatNoticeKind) {
        _state.update { it.copy(notice = ChatNotice(++noticeId, message, kind)) }
    }

    private fun showLoadError(error: Throwable) {
        _state.update { it.copy(isLoading = false, error = error.chatMessage()) }
    }
}

internal fun Throwable.chatMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("not configured", ignoreCase = true) ->
            "Firebase is not configured for this build. Add google-services.json to load messages."
        raw.contains("network", ignoreCase = true) || raw.contains("UNAVAILABLE", ignoreCase = true) ->
            "No internet connection. Check your network and try again."
        raw.contains("session expired", ignoreCase = true) || raw.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Your session expired. Sign in and try again."
        raw.contains("blocked", ignoreCase = true) || raw.contains("cannot message", ignoreCase = true) ->
            "You cannot message this user."
        raw.contains("pending", ignoreCase = true) || raw.contains("Accept", ignoreCase = true) -> raw
        raw.contains("no longer available", ignoreCase = true) || raw.contains("NOT_FOUND", ignoreCase = true) ->
            "This conversation is no longer available."
        raw.isNotBlank() -> raw
        else -> "Something went wrong. Please try again."
    }
}
