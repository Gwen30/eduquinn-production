package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
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

// --- Purchases / order history (GET /api/orders/mine) ---

// courseId/priceCents come from a hand-built `json_build_object('courseId',
// ..., 'priceCents', ...)` in the SQL itself (api/index.js), not a raw
// column spread, so they need @SerialName — same reasoning as the other
// hand-written-key responses in this app (see Support.kt/LiveClass.kt).
@Serializable
data class OrderItemLine(
    @SerialName("courseId") val courseId: String,
    val title: String,
    @SerialName("priceCents") val priceCents: Int,
)

// The order row itself is a raw `o.*` spread (snake_case, auto-mapped),
// but refundEligible/refundDeadline/refundReason are added afterward as
// literal camelCase keys in a JS object spread — same @SerialName need.
@Serializable
data class OrderHistoryItem(
    val id: String,
    val status: String,
    val totalCents: Int,
    val subtotalCents: Int = 0,
    val discountCents: Int = 0,
    val currency: String = "USD",
    val paymentMethod: String? = null,
    val createdAt: String? = null,
    val paidAt: String? = null,
    val items: List<OrderItemLine> = emptyList(),
    @SerialName("refundEligible") val refundEligible: Boolean = false,
    @SerialName("refundDeadline") val refundDeadline: String? = null,
    @SerialName("refundReason") val refundReason: String? = null,
)

@Serializable
data class OrdersMineResponse(
    val orders: List<OrderHistoryItem>,
    @SerialName("refundWindowDays") val refundWindowDays: Int = 0,
    @SerialName("refundMaxProgressPercent") val refundMaxProgressPercent: Int = 0,
)

@Serializable
data class RefundRequestBody(val reason: String)

@Serializable
data class RefundRequestResponse(
    @SerialName("refundId") val refundId: String? = null,
    val status: String? = null,
)
