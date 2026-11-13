package zw.co.eduquinn.app.ui.screens.coursedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.cart.CartStore
import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.model.Review
import zw.co.eduquinn.app.data.repository.CourseRepository
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.data.repository.WishlistRepository

data class CourseDetailUiState(
    val course: Course? = null,
    val reviews: List<Review> = emptyList(),
    val isWishlisted: Boolean = false,
    val isInCart: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val addedToCartMessage: String? = null,
)

class CourseDetailViewModel(
    private val courseId: String,
    private val courseRepository: CourseRepository,
    private val wishlistRepository: WishlistRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val detailResult = courseRepository.courseDetail(courseId)
            val wishlisted = if (SessionManager.isLoggedIn) {
                wishlistRepository.wishlist().getOrNull()?.any { it.id == courseId } ?: false
            } else {
                false
            }
            detailResult.fold(
                onSuccess = { detail ->
                    _uiState.value = _uiState.value.copy(
                        course = detail.course,
                        reviews = detail.reviews,
                        isWishlisted = wishlisted,
                        isInCart = CartStore.contains(courseId),
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

    /** Returns false (does nothing) if the user needs to log in first. */
    fun toggleWishlist(): Boolean {
        if (!SessionManager.isLoggedIn) return false
        val currentlyWishlisted = _uiState.value.isWishlisted
        _uiState.value = _uiState.value.copy(isWishlisted = !currentlyWishlisted)
        viewModelScope.launch {
            val result = if (currentlyWishlisted) wishlistRepository.remove(courseId) else wishlistRepository.add(courseId)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(isWishlisted = currentlyWishlisted)
            }
        }
        return true
    }

    fun addToCart() {
        val course = _uiState.value.course ?: return
        CartStore.add(course)
        _uiState.value = _uiState.value.copy(isInCart = true, addedToCartMessage = "Added to cart")
    }

    fun dismissCartMessage() {
        _uiState.value = _uiState.value.copy(addedToCartMessage = null)
    }
}
