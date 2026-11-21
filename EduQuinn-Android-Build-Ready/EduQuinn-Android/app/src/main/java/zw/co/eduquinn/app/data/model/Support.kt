package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors support.html's form fields exactly — POST /api/support. */
@Serializable
data class SupportRequest(
    val name: String,
    val email: String,
    val role: String,
    val topic: String,
    val subject: String,
    val message: String,
)

// ticketId/emailDelivery are written as literal camelCase JS keys in
// receiveSupport() (api/index.js), not raw DB-row spreads — the global
// SnakeCase naming strategy would otherwise look for ticket_id/
// email_delivery and silently leave these null, same reason Auth.kt
// needs @SerialName on emailVerified.
@Serializable
data class SupportResponse(
    val message: String? = null,
    @SerialName("ticketId") val ticketId: String? = null,
    @SerialName("emailDelivery") val emailDelivery: String? = null,
    val fallback: Boolean = false,
    val email: String? = null,
)
