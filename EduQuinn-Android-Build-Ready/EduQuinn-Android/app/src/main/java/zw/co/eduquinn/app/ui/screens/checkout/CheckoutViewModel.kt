package zw.co.eduquinn.app.ui.screens.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.cart.CartStore
import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.model.OrderCreateResponse
import zw.co.eduquinn.app.data.model.PaymentMethodOption
import zw.co.eduquinn.app.data.repository.CommerceRepository
import zw.co.eduquinn.app.data.repository.CourseRepository

data class CheckoutUiState(
    val courses: List<Course> = emptyList(),
    val paymentMethods: List<PaymentMethodOption> = emptyList(),
    val selectedMethod: String? = null,
    val couponCode: String = "",
    val couponAppliedCode: String? = null,
    val discountCents: Int = 0,
    val couponError: String? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val result: OrderCreateResponse? = null,
) {
    val subtotalCents: Int get() = courses.sumOf { it.priceCents }
    val totalCents: Int get() = (subtotalCents - discountCents).coerceAtLeast(0)
}

class CheckoutViewModel(
    private val courseIds: List<String>,
    private val courseRepository: CourseRepository,
    private val commerceRepository: CommerceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val courseDeferreds = courseIds.map { id -> async { courseRepository.courseDetail(id) } }
            val courseResults = courseDeferreds.awaitAll()
            val courses = courseResults.mapNotNull { it.getOrNull()?.course }
            val configResult = commerceRepository.config()

            if (courses.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "These courses are no longer available.")
                return@launch
            }
            configResult.fold(
                onSuccess = { config ->
                    val available = config.methods.filter { it.configured }
                    _uiState.value = _uiState.value.copy(
                        courses = courses,
                        paymentMethods = available,
                        selectedMethod = available.firstOrNull()?.id,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                },
            )
        }
    }

    fun onCouponCodeChange(code: String) {
        _uiState.value = _uiState.value.copy(couponCode = code, couponError = null)
    }

    fun applyCoupon() {
        val code = _uiState.value.couponCode.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            val result = commerceRepository.validateCoupon(code, _uiState.value.subtotalCents)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        couponAppliedCode = response.code,
                        discountCents = response.discountCents,
                        couponError = null,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        couponAppliedCode = null,
                        discountCents = 0,
                        couponError = error.message,
                    )
                },
            )
        }
    }

    fun selectMethod(methodId: String) {
        _uiState.value = _uiState.value.copy(selectedMethod = methodId)
    }

    fun submit() {
        val method = _uiState.value.selectedMethod ?: return
        _uiState.value = _uiState.value.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = commerceRepository.createOrder(
                courseIds = courseIds,
                paymentMethod = method,
                couponCode = _uiState.value.couponAppliedCode,
            )
            result.fold(
                onSuccess = { response ->
                    // The order now exists (free/sandbox-paid, awaiting bank transfer, or
                    // awaiting Paynow) — clear whatever of these courses were sitting in
                    // the cart since they're committed to an order either way.
                    courseIds.forEach { CartStore.remove(it) }
                    _uiState.value = _uiState.value.copy(isSubmitting = false, result = response)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = error.message)
                },
            )
        }
    }
}
