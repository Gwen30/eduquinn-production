package zw.co.eduquinn.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CommerceConfig(
    val currency: String,
    val instructorSharePercent: Int,
    val paymentSandbox: Boolean,
    val methods: List<PaymentMethodOption>,
)

@Serializable
data class PaymentMethodOption(val id: String, val label: String, val configured: Boolean)

@Serializable
data class CouponRequest(val code: String, val subtotalCents: Int)

@Serializable
data class CouponResponse(val code: String? = null, val discountCents: Int = 0)

@Serializable
data class OrderCreateRequest(
    val courseIds: List<String>,
    val paymentMethod: String,
    val couponCode: String? = null,
)

@Serializable
data class Order(
    val id: String,
    val status: String,
    val totalCents: Int,
    val subtotalCents: Int = 0,
    val discountCents: Int = 0,
    val currency: String = "USD",
)

// The backend returns bank-transfer instructions as a single preformatted
// string (see api/index.js: BANK_TRANSFER_INSTRUCTIONS env var or a default
// message) — not a structured object.
@Serializable
data class OrderCreateResponse(
    val order: Order,
    val requiresGateway: Boolean,
    val paynowRedirectUrl: String? = null,
    val paynowError: String? = null,
    val bankInstructions: String? = null,
)

@Serializable
data class OrderResponse(val order: Order)

@Serializable
data class PaynowRedirectResponse(val redirectUrl: String)
