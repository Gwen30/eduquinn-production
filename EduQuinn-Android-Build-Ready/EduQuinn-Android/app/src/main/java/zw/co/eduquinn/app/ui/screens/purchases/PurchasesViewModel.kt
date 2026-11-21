package zw.co.eduquinn.app.ui.screens.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.OrderHistoryItem
import zw.co.eduquinn.app.data.repository.CommerceRepository
import zw.co.eduquinn.app.data.repository.SessionManager

data class PurchasesUiState(
    val orders: List<OrderHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val refundWindowDays: Int = 0,
    val refundMaxProgressPercent: Int = 0,
    // Order id whose "Request refund" dialog is open, if any.
    val refundDialogOrderId: String? = null,
    val isSubmittingRefund: Boolean = false,
)

class PurchasesViewModel(private val commerceRepository: CommerceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchasesUiState())
    val uiState: StateFlow<PurchasesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (!SessionManager.isLoggedIn) {
            _uiState.value = _uiState.value.copy(isLoading = false, orders = emptyList(), error = null)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            commerceRepository.myOrders().fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        orders = response.orders,
                        refundWindowDays = response.refundWindowDays,
                        refundMaxProgressPercent = response.refundMaxProgressPercent,
                        isLoading = false,
                        error = null,
                    )
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    fun openRefundDialog(orderId: String) {
        _uiState.value = _uiState.value.copy(refundDialogOrderId = orderId)
    }

    fun dismissRefundDialog() {
        _uiState.value = _uiState.value.copy(refundDialogOrderId = null)
    }

    fun submitRefund(orderId: String, reason: String) {
        _uiState.value = _uiState.value.copy(isSubmittingRefund = true)
        viewModelScope.launch {
            commerceRepository.requestRefund(orderId, reason).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingRefund = false,
                        refundDialogOrderId = null,
                        orders = _uiState.value.orders.map { order ->
                            if (order.id == orderId) order.copy(status = "refund_requested") else order
                        },
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isSubmittingRefund = false, error = error.message)
                },
            )
        }
    }
}
