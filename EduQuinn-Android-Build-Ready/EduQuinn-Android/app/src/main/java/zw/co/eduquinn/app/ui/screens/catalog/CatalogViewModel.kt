package zw.co.eduquinn.app.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.model.PublicStats
import zw.co.eduquinn.app.data.repository.CourseRepository
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.data.repository.WishlistRepository

data class CatalogUiState(
    val allCourses: List<Course> = emptyList(),
    val stats: PublicStats? = null,
    val wishlistIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val visibleCourses: List<Course>
        get() = if (searchQuery.isBlank()) {
            allCourses
        } else {
            val q = searchQuery.trim().lowercase()
            allCourses.filter {
                it.title.lowercase().contains(q) ||
                    it.category?.lowercase()?.contains(q) == true ||
                    it.instructorName?.lowercase()?.contains(q) == true
            }
        }
}

class CatalogViewModel(
    private val courseRepository: CourseRepository,
    private val wishlistRepository: WishlistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val coursesResult = courseRepository.courses()
            val statsResult = courseRepository.publicStats()
            val wishlistIds = if (SessionManager.isLoggedIn) {
                wishlistRepository.wishlist().getOrNull()?.map { it.id }?.toSet() ?: emptySet()
            } else {
                emptySet()
            }
            coursesResult.fold(
                onSuccess = { courses ->
                    _uiState.value = _uiState.value.copy(
                        allCourses = courses,
                        stats = statsResult.getOrNull(),
                        wishlistIds = wishlistIds,
                        isLoading = false,
                        error = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /** Returns false (and does nothing) if the user needs to log in first. */
    fun toggleWishlist(courseId: String): Boolean {
        if (!SessionManager.isLoggedIn) return false
        val currentlyWishlisted = courseId in _uiState.value.wishlistIds
        // Optimistic update — the heart should feel instant.
        _uiState.value = _uiState.value.copy(
            wishlistIds = if (currentlyWishlisted) {
                _uiState.value.wishlistIds - courseId
            } else {
                _uiState.value.wishlistIds + courseId
            },
        )
        viewModelScope.launch {
            val result = if (currentlyWishlisted) wishlistRepository.remove(courseId) else wishlistRepository.add(courseId)
            if (result.isFailure) {
                // Revert on failure.
                _uiState.value = _uiState.value.copy(
                    wishlistIds = if (currentlyWishlisted) {
                        _uiState.value.wishlistIds + courseId
                    } else {
                        _uiState.value.wishlistIds - courseId
                    },
                )
            }
        }
        return true
    }
}
