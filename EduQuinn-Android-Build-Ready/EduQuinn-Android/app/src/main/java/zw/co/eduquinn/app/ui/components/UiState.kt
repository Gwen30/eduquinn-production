package zw.co.eduquinn.app.ui.components

/** Shared Loading/Success/Error shape reused by most screen ViewModels. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
