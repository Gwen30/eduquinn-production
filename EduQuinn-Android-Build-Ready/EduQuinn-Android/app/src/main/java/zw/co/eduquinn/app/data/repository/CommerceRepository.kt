package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.CommerceConfig
import zw.co.eduquinn.app.data.model.CouponRequest
import zw.co.eduquinn.app.data.model.CouponResponse
import zw.co.eduquinn.app.data.model.Order
import zw.co.eduquinn.app.data.model.OrderCreateRequest
import zw.co.eduquinn.app.data.model.OrderCreateResponse
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class CommerceRepository(private val api: ApiService) {

    suspend fun config(): Result<CommerceConfig> = safeApiCall { api.commerceConfig() }

    suspend fun validateCoupon(code: String, subtotalCents: Int): Result<CouponResponse> = safeApiCall {
        api.validateCoupon(CouponRequest(code = code, subtotalCents = subtotalCents))
    }

    suspend fun createOrder(
        courseIds: List<String>,
        paymentMethod: String,
        couponCode: String?,
    ): Result<OrderCreateResponse> = safeApiCall {
        api.createOrder(OrderCreateRequest(courseIds = courseIds, paymentMethod = paymentMethod, couponCode = couponCode))
    }

    suspend fun refreshOrderStatus(orderId: String): Result<Order> = safeApiCall {
        api.refreshOrderStatus(orderId).order
    }

    suspend fun retryPaynow(orderId: String): Result<String> = safeApiCall {
        api.retryPaynow(orderId).redirectUrl
    }
}
