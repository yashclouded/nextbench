package com.nextbench.app.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextbench.data.firebase.PublicProfileContent
import com.nextbench.data.firebase.PublicProfileRepository
import com.nextbench.data.firebase.PublicProfileStats
import com.nextbench.data.model.Post
import com.nextbench.data.model.Product
import com.nextbench.data.model.UserData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class PublicProfileUiState(
    val user: UserData? = null,
    val listings: List<Product> = emptyList(),
    val posts: List<Post> = emptyList(),
    val tab: ProfileTab = ProfileTab.Listings,
    val isLoading: Boolean = true,
    val error: String? = null,
    val resolvedId: String? = null,
    val stats: PublicProfileStats = PublicProfileStats(),
)

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val repository: PublicProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    fun load(key: String, username: Boolean) {
        if (key.isBlank() || (state.value.resolvedId == key && state.value.user != null)) return
        _state.value = PublicProfileUiState(isLoading = true)
        viewModelScope.launch {
            val result = if (username) repository.resolveUsername(key) else Result.success(key)
            result.fold(
                onSuccess = { resolved ->
                    if (resolved.isNullOrBlank()) {
                        _state.value = PublicProfileUiState(isLoading = false, error = "That profile could not be found.")
                    } else {
                        repository.load(resolved).fold(
                            onSuccess = { content -> _state.value = content.toUiState(resolved) },
                            onFailure = { error -> _state.value = PublicProfileUiState(isLoading = false, error = error.publicProfileMessage()) },
                        )
                    }
                },
                onFailure = { error -> _state.value = PublicProfileUiState(isLoading = false, error = error.publicProfileMessage()) },
            )
        }
    }

    fun retry(key: String, username: Boolean) {
        _state.value = PublicProfileUiState()
        load(key, username)
    }

    fun selectTab(tab: ProfileTab) {
        _state.value = _state.value.copy(tab = tab)
    }
}

private fun PublicProfileContent.toUiState(id: String) = PublicProfileUiState(
    user = user,
    listings = listings,
    posts = posts,
    stats = stats,
    isLoading = false,
    resolvedId = id,
)

internal fun Throwable.publicProfileMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("not configured", ignoreCase = true) -> "Firebase is not configured for this build. Add google-services.json to view profiles."
        raw.contains("network", ignoreCase = true) || raw.contains("UNAVAILABLE", ignoreCase = true) -> "No internet connection. Check your network and try again."
        raw.isNotBlank() -> raw
        else -> "This profile could not be loaded. Please try again."
    }
}
