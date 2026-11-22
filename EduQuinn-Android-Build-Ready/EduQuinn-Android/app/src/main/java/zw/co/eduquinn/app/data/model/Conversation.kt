package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/conversations returns `{ conversations: [...] }`, each row a
 * raw SQL-row spread (snake_case, auto-mapped). The messages endpoints
 * use `body` for the message text (different from live-class chat, which
 * uses `message` — kept exactly as the backend names them).
 */
@Serializable
data class Conversation(
    val id: String,
    val courseId: String? = null,
    val updatedAt: String,
    val courseTitle: String? = null,
    val studentId: String,
    val studentName: String,
    val studentEmail: String? = null,
    val instructorId: String,
    val instructorName: String,
    val instructorEmail: String? = null,
    val lastMessage: String? = null,
)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation>)

/**
 * The backend serves this same endpoint to both roles (a `Conversation`
 * already carries both `studentName` and `instructorName`), so the only
 * thing that needs to flip for an instructor viewer is which side is "the
 * other person" — a student sees the instructor's name, an instructor
 * sees the student's.
 */
fun Conversation.counterpartName(myRole: String): String = if (myRole == "instructor") studentName else instructorName

// courseId here is a hand-written literal camelCase request body key
// (api/index.js reads `x.courseId` directly), not a DB column name, so
// it needs @SerialName — the global SnakeCase strategy would otherwise
// encode this property as "course_id".
@Serializable
data class ConversationCreateRequest(@SerialName("courseId") val courseId: String)

// Same reason: conversationId is a hand-written response key.
@Serializable
data class ConversationCreateResponse(@SerialName("conversationId") val conversationId: String)

@Serializable
data class ConversationMessage(
    val id: String,
    val body: String,
    val createdAt: String,
    val readAt: String? = null,
    val senderId: String,
    val senderName: String? = null,
    val senderRole: String? = null,
)

@Serializable
data class ConversationMessagesResponse(val messages: List<ConversationMessage>)

@Serializable
data class ConversationMessageRequest(val body: String)

@Serializable
data class ConversationMessageSentResponse(val id: String)
