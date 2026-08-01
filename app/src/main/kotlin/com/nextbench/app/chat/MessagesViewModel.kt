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

    fun retry() {
        val current = viewer
        viewer = null
        syncViewer(current)
    }

    companion object {
        private const val MaxSearchLength = 80
    }
}
