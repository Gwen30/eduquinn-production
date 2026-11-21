package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/live-classes returns `{ classes: [...] }` where each row is a
 * raw SQL-row spread (snake_case columns — auto-mapped by the global
 * SnakeCase naming strategy, see NetworkModule.json) plus a hand-added
 * `reserved` flag for the current viewer.
 */
@Serializable
data class LiveClass(
    val id: String,
    val courseId: String? = null,
    val title: String,
    val description: String? = null,
    val startsAt: String,
    val durationMinutes: Int = 60,
    val capacity: Int = 0,
    val status: String, // "upcoming" | "live" | "completed" | "cancelled"
    val sessionType: String? = null,
    val meetingProvider: String? = null,
    val recordingUrl: String? = null,
    val instructorName: String? = null,
    val courseTitle: String? = null,
    val reservedCount: Int = 0,
    val reserved: Boolean = false,
)

@Serializable
data class LiveClassesResponse(val classes: List<LiveClass>)

@Serializable
data class LiveClassReservedResponse(val reserved: Boolean)

// classId/joinUrl/roomUrl are hand-written literal camelCase keys in the
// /join handler (api/index.js), not a DB-row spread — needs @SerialName
// for the same reason as Support.kt's ticketId.
@Serializable
data class LiveClassJoinResponse(
    @SerialName("classId") val classId: String? = null,
    val title: String? = null,
    @SerialName("joinUrl") val joinUrl: String? = null,
    val provider: String? = null,
    @SerialName("roomUrl") val roomUrl: String? = null,
)

/** The in-room chat. Backend field is literally `message` (not `body`). */
@Serializable
data class LiveClassMessage(
    val id: String,
    val message: String,
    val createdAt: String,
    val name: String? = null,
    val role: String? = null,
)

@Serializable
data class LiveClassMessagesResponse(val messages: List<LiveClassMessage>)

@Serializable
data class LiveClassMessageRequest(val message: String)
