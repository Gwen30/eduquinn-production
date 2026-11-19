package zw.co.eduquinn.app.ui.screens.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Order
import zw.co.eduquinn.app.data.repository.CommerceRepository

data class OrderStatusUiState(
    val order: Order? = null,
    val isLoading: Boolean = true,
    val isRetryingPaynow: Boolean = false,
    val paynowRetryUrl: String? = null,
    val error: String? = null,
)

class OrderStatusViewModel(
    private val orderId: String,
    private val commerceRepository: CommerceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderStatusUiState())
    val uiState: StateFlow<OrderStatusUiState> = _uiState.asStateFlow()

    init {
        pollInitial()
    }

    /** A short automatic poll right after returning from the Paynow Custom
     * Tab, mirroring the web's own order-success polling — then the user
     * can always tap "Check again" manually. */
    private fun pollInitial() {
        viewModelScope.launch {
            repeat(3) { attempt ->
                val result = commerceRepository.refreshOrderStatus(orderId)
                result.fold(
                    onSuccess = { order -> _uiState.value = _uiState.value.copy(order = order, isLoading = false, error = null) },
                    onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
                )
                if (_uiState.value.order?.status == "paid") return@launch
                if (attempt < 2) delay(2000)
            }
        }
    }

    fun checkAgain() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = commerceRepository.refreshOrderStatus(orderId)
            result.fold(
                onSuccess = { order -> _uiState.value = _uiState.value.copy(order = order, isLoading = false, error = null) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    fun retryPaynow() {
        _uiState.value = _uiState.value.copy(isRetryingPaynow = true)
        viewModelScope.launch {
            val result = commerceRepository.retryPaynow(orderId)
            result.fold(
                onSuccess = { url -> _uiState.value = _uiState.value.copy(isRetryingPaynow = false, paynowRetryUrl = url) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isRetryingPaynow = false, error = error.message) },
            )
        }
    }

    fun consumeRetryUrl() {
        _uiState.value = _uiState.value.copy(paynowRetryUrl = null)
    }
}
