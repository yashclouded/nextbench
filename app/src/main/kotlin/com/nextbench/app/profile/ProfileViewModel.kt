package com.nextbench.app.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.ProfileRepository
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
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

enum class ProfileTab { Listings, Posts }

@Immutable
data class ProfileUiState(
    val user: UserData? = null,
    val listings: List<Product> = emptyList(),
    val posts: List<Post> = emptyList(),
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val tab: ProfileTab = ProfileTab.Listings,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var viewerUid: String? = null
    private var observeJob: Job? = null

    fun syncViewer(user: UserData?) {
        val uid = user?.uid?.takeIf(String::isNotBlank)
        if (viewerUid == uid && (uid == null || state.value.user != null)) return
        viewerUid = uid
        observeJob?.cancel()
        _state.value = ProfileUiState(user = user, isLoading = uid != null)
        if (uid == null) return

        observeJob = viewModelScope.launch {
            repository.observeProfile(uid)
                .catch { error ->
                    _state.update { it.copy(isLoading = false, error = error.profileMessage()) }
                }
                .collect { content ->
                    _state.update {
                        it.copy(
                            user = content.user ?: it.user,
                            listings = content.listings,
                            posts = content.posts,
                            followersCount = content.followersCount,
                            followingCount = content.followingCount,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
        }
    }

    fun selectTab(tab: ProfileTab) {
        if (tab != state.value.tab) _state.update { it.copy(tab = tab) }
    }

    fun retry() {
        val uid = viewerUid ?: return
        viewerUid = null
        syncViewer(state.value.user?.copy(uid = uid))
    }

    fun setFollowersOnly(enabled: Boolean) {
        val uid = viewerUid ?: return
        _state.update { current -> current.copy(user = current.user?.copy(chatPrivacy = current.user.chatPrivacy?.copy(followersOnly = enabled) ?: com.nextbench.data.model.ChatPrivacy(enabled))) }
        viewModelScope.launch {
            repository.updateFollowersOnly(uid, enabled).onFailure {
                _state.update { current -> current.copy(user = current.user?.copy(chatPrivacy = current.user.chatPrivacy?.copy(followersOnly = !enabled))) }
            }
        }
    }
}

internal fun Throwable.profileMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("not configured", ignoreCase = true) ->
            "Firebase is not configured for this build. Add google-services.json to load your profile."
        raw.contains("network", ignoreCase = true) || raw.contains("UNAVAILABLE", ignoreCase = true) ->
            "No internet connection. Check your network and try again."
        raw.contains("session expired", ignoreCase = true) || raw.contains("UNAUTHENTICATED", ignoreCase = true) ->
            "Your session expired. Sign in and try again."
        raw.isNotBlank() -> raw
        else -> "Unable to load your profile. Please try again."
    }
}
