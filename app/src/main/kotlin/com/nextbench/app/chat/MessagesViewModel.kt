package com.nextbench.app.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ChatRepository
import com.nextbench.data.firebase.ChatRoomListItem
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

@Immutable
data class MessagesUiState(
    val rooms: List<ChatRoomListItem> = emptyList(),
    val query: String = "",
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val viewerId: String? = null,
    val busyRoomIds: Set<String> = emptySet(),
    val notice: ChatNotice? = null,
) {
    val visibleRooms: List<ChatRoomListItem>
        get() = rooms.filter { item ->
            val viewerId = viewerId ?: return@filter false
            if (item.deleted) return@filter false
            if (showArchived != item.archived) return@filter false
            val search = query.trim()
            search.isBlank() || listOfNotNull(
                item.otherUser?.name,
                item.room.productTitle,
                item.room.lastMessage,
            ).any { it.contains(search, ignoreCase = true) }
        }

    val archivedCount: Int
        get() = rooms.count { item -> !item.deleted && item.archived }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    private var viewer: UserData? = null
    private var roomsJob: Job? = null
    private var noticeId = 0L

    fun syncViewer(user: UserData?) {
        if (viewer?.uid == user?.uid && (viewer == null) == (user == null)) return
        viewer = user
        roomsJob?.cancel()
        _state.value = MessagesUiState(
            isLoading = user != null,
            viewerId = user?.uid,
        )
        val uid = user?.uid ?: return
        roomsJob = viewModelScope.launch {
            repository.observeRooms(uid)
                .catch { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.chatMessage())
                    }
                }
                .collect { rooms ->
                    _state.update {
                        it.copy(
                            rooms = rooms,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value.take(MaxSearchLength)) }
    }

    fun toggleArchived() {
        _state.update { it.copy(showArchived = !it.showArchived) }
    }

    fun toggleArchive(item: ChatRoomListItem): Boolean = roomAction(
        item = item,
        successMessage = if (item.archived) "Conversation restored" else "Conversation archived",
    ) { roomId, uid -> repository.setArchived(roomId, uid, !item.archived) }

    fun toggleMute(item: ChatRoomListItem): Boolean = roomAction(
        item = item,
        successMessage = if (item.muted) "Notifications unmuted" else "Notifications muted",
    ) { roomId, uid -> repository.setMuted(roomId, uid, !item.muted) }

    fun togglePin(item: ChatRoomListItem): Boolean = roomAction(
        item = item,
        successMessage = if (item.pinned) "Conversation unpinned" else "Conversation pinned",
    ) { roomId, uid -> repository.setPinned(roomId, uid, !item.pinned) }

    fun toggleRead(item: ChatRoomListItem): Boolean = roomAction(
        item = item,
        successMessage = if (item.hasUnreadActivity) "Marked as read" else "Marked as unread",
    ) { roomId, uid -> repository.setUnread(roomId, uid, !item.hasUnreadActivity) }

    fun delete(item: ChatRoomListItem): Boolean = roomAction(
        item = item,
        successMessage = "Conversation removed",
    ) { roomId, uid -> repository.deleteForUser(roomId, uid) }

    fun dismissNotice(id: Long) {
        _state.update { current -> if (current.notice?.id == id) current.copy(notice = null) else current }
    }

    fun retry() {
        val current = viewer
        viewer = null
        syncViewer(current)
    }

    private fun roomAction(
        item: ChatRoomListItem,
        successMessage: String,
        operation: suspend (roomId: String, uid: String) -> Result<Unit>,
    ): Boolean {
        val uid = viewer?.uid ?: return false
        val roomId = item.room.id
        if (roomId.isBlank() || roomId in state.value.busyRoomIds) return false
        _state.update { it.copy(busyRoomIds = it.busyRoomIds + roomId) }
        viewModelScope.launch {
            operation(roomId, uid).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            busyRoomIds = it.busyRoomIds - roomId,
                            notice = ChatNotice(++noticeId, successMessage, ChatNoticeKind.Success),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busyRoomIds = it.busyRoomIds - roomId,
                            notice = ChatNotice(++noticeId, error.chatMessage(), ChatNoticeKind.Error),
                        )
                    }
                },
            )
        }
        return true
    }

    companion object {
        private const val MaxSearchLength = 80
    }
}
