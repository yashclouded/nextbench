package com.nextbench.app.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ChatBlockState
import com.nextbench.data.firebase.ChatRepository
import com.nextbench.data.firebase.ChatRoomDetail
import com.nextbench.data.model.Message
import com.nextbench.data.model.MessageType
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
import kotlinx.coroutines.delay
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
    val replyTo: Message? = null,
    val attachment: PreparedChatAttachment? = null,
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val isPreparingAttachment: Boolean = false,
    val isSendingAttachment: Boolean = false,
    val actionMessage: Message? = null,
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
        !viewerId.isNullOrBlank() && room != null && !pendingRequest && !blocked && !isSending && !isSendingAttachment
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRoomViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val mediaStore: ChatMediaStore,
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
    private var typingIdleJob: Job? = null
    private var typingRefreshJob: Job? = null
    private var typingActive = false
    private var noticeId = 0L

    fun syncViewer(user: UserData?) {
        if (viewer?.uid == user?.uid && (viewer == null) == (user == null)) return
        roomJob?.cancel()
        messagesJob?.cancel()
        blockJob?.cancel()
        stopTyping()
        viewer = user
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
        val normalized = value.take(ChatRepository.MessageCharacterLimit)
        _state.update { it.copy(composerText = normalized) }
        if (normalized.isBlank()) {
            stopTyping()
        } else {
            startTyping()
            typingIdleJob?.cancel()
            typingIdleJob = viewModelScope.launch {
                delay(TypingIdleMillis)
                stopTyping()
            }
        }
    }

    fun prepareAttachment(uri: android.net.Uri, kind: ChatAttachmentKind? = null) {
        if (state.value.isPreparingAttachment || state.value.isSendingAttachment) return
        state.value.attachment?.file?.delete()
        _state.update { it.copy(isPreparingAttachment = true, attachment = null) }
        viewModelScope.launch {
            mediaStore.prepare(uri, kind).fold(
                onSuccess = { attachment -> _state.update { it.copy(attachment = attachment, isPreparingAttachment = false) } },
                onFailure = { error ->
                    _state.update { it.copy(isPreparingAttachment = false) }
                    showNotice(error.chatMessage(), ChatNoticeKind.Error)
                },
            )
        }
    }

    fun clearAttachment() {
        state.value.attachment?.file?.delete()
        _state.update { it.copy(attachment = null) }
    }

    fun setReplyTo(message: Message?) = _state.update { it.copy(replyTo = message, actionMessage = null) }

    fun openMessageActions(message: Message) = _state.update { it.copy(actionMessage = message) }

    fun closeMessageActions() = _state.update { it.copy(actionMessage = null) }

    fun markMessageRead(messageId: String) {
        val uid = viewer?.uid ?: return
        viewModelScope.launch {
            repository.markMessageRead(roomId, messageId, uid)
        }
    }

    fun toggleReaction(emoji: String): Boolean {
        val message = state.value.actionMessage ?: return false
        val uid = viewer?.uid ?: return false
        viewModelScope.launch {
            repository.toggleReaction(roomId, message.id, uid, emoji).fold(
                onSuccess = { },
                onFailure = { showNotice(it.chatMessage(), ChatNoticeKind.Error) },
            )
        }
        return true
    }

    fun deleteForMe(): Boolean = messageAction(ownerOnly = false) { repository.deleteForMe(roomId, it.id, requireViewerId()) }

    fun deleteForEveryone(): Boolean = messageAction(ownerOnly = true) { repository.deleteForEveryone(roomId, it.id, requireViewerId()) }

    private fun messageAction(ownerOnly: Boolean, operation: suspend (Message) -> Result<Unit>): Boolean {
        val message = state.value.actionMessage ?: return false
        val uid = viewer?.uid ?: return false
        if (ownerOnly && message.senderId != uid) return false
        viewModelScope.launch {
            operation(message).fold(
                onSuccess = { _state.update { it.copy(actionMessage = null) } },
                onFailure = { showNotice(it.chatMessage(), ChatNoticeKind.Error) },
            )
        }
        return true
    }

    fun sendText(): Boolean {
        val user = viewer ?: return false
        val snapshot = state.value
        val room = snapshot.room?.room ?: return false
        if (snapshot.isSending || snapshot.isSendingAttachment || snapshot.blocked || room.status == "pending") return false
        val text = snapshot.composerText.trim()
        if (text.isEmpty()) return false
        _state.update { it.copy(isSending = true) }
        stopTyping()
        viewModelScope.launch {
            repository.sendText(roomId, user, text, snapshot.replyTo).fold(
                onSuccess = {
                    _state.update { it.copy(composerText = "", replyTo = null, isSending = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isSending = false) }
                    showNotice(error.chatMessage(), ChatNoticeKind.Error)
                },
            )
        }
        return true
    }

    fun sendAttachment(): Boolean {
        val user = viewer ?: return false
        val attachment = state.value.attachment ?: return false
        val snapshot = state.value
        if (snapshot.isSendingAttachment || snapshot.blocked || snapshot.pendingRequest) return false
        _state.update { it.copy(isSendingAttachment = true) }
        viewModelScope.launch {
            val result = when {
                attachment.mimeType.startsWith("image/") -> repository.sendImage(roomId, user, attachment.file, attachment.width, attachment.height, snapshot.composerText, snapshot.replyTo)
                attachment.mimeType.startsWith("video/") -> repository.sendVideo(roomId, user, attachment.file, attachment.width, attachment.height, attachment.durationMs, snapshot.composerText, snapshot.replyTo)
                else -> repository.sendFile(roomId, user, attachment.file, attachment.mimeType, snapshot.composerText, replyTo = snapshot.replyTo)
            }
            result.fold(
                onSuccess = {
                    attachment.file.delete()
                    _state.update { it.copy(attachment = null, composerText = "", replyTo = null, isSendingAttachment = false) }
                },
                onFailure = {
                    _state.update { it.copy(isSendingAttachment = false) }
                    showNotice(it.chatMessage(), ChatNoticeKind.Error)
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

    private fun startTyping() {
        val uid = viewer?.uid ?: return
        if (state.value.blocked || state.value.pendingRequest) return
        if (!typingActive) {
            typingActive = true
            viewModelScope.launch { repository.setTyping(roomId, uid, true) }
        }
        if (typingRefreshJob?.isActive != true) {
            typingRefreshJob = viewModelScope.launch {
                while (typingActive) {
                    delay(TypingRefreshMillis)
                    if (typingActive) repository.setTyping(roomId, uid, true)
                }
            }
        }
    }

    fun stopTyping() {
        typingIdleJob?.cancel()
        typingIdleJob = null
        typingRefreshJob?.cancel()
        typingRefreshJob = null
        if (!typingActive) return
        typingActive = false
        val uid = viewer?.uid ?: return
        viewModelScope.launch { repository.setTyping(roomId, uid, false) }
    }

    private fun showNotice(message: String, kind: ChatNoticeKind) {
        _state.update { it.copy(notice = ChatNotice(++noticeId, message, kind)) }
    }

    private fun showLoadError(error: Throwable) {
        _state.update { it.copy(isLoading = false, error = error.chatMessage()) }
    }

    override fun onCleared() {
        typingIdleJob?.cancel()
        typingRefreshJob?.cancel()
        state.value.attachment?.file?.delete()
        super.onCleared()
    }

    companion object {
        private const val TypingIdleMillis = 3_000L
        private const val TypingRefreshMillis = 2_000L
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
