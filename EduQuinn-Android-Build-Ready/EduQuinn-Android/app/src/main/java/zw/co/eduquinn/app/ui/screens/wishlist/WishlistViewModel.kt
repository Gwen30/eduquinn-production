package zw.co.eduquinn.app.ui.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.data.repository.WishlistRepository

data class WishlistUiState(
    val courses: List<Course> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class WishlistViewModel(private val wishlistRepository: WishlistRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!SessionManager.isLoggedIn) {
            // Same login-gate pattern AccountScreen uses — nothing to fetch
            // until there's a session; the screen itself renders the
            // sign-in prompt for this state.
            _uiState.value = _uiState.value.copy(isLoading = false, courses = emptyList(), error = null)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            wishlistRepository.wishlist().fold(
                onSuccess = { courses -> _uiState.value = _uiState.value.copy(courses = courses, isLoading = false, error = null) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    /** Optimistic remove, same idiom as CatalogViewModel.toggleWishlist. */
    fun remove(courseId: String) {
        val previous = _uiState.value.courses
        _uiState.value = _uiState.value.copy(courses = previous.filterNot { it.id == courseId })
        viewModelScope.launch {
            val result = wishlistRepository.remove(courseId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(courses = previous)
            }
        }
    }
}
