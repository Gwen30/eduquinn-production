package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Same naming-strategy split as Auth.kt/Conversation.kt: most fields here
// are DB-row passthroughs (snake_case on the wire, no annotation needed
// under the global JsonNamingStrategy.SnakeCase), but a handful of
// endpoints build hand-written camelCase object literals instead — those
// get an explicit @SerialName, called out per class below.

// --- Courses (api/instructor/courses) ---

@Serializable
data class InstructorCourse(
    val id: String,
    val title: String,
    val slug: String,
    val subtitle: String? = null,
    val description: String? = null,
    val educationLevel: String? = null,
    val category: String? = null,
    val priceCents: Int = 0,
    val currency: String = "USD",
    val status: String = "draft",
    val qualityScore: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class InstructorCoursesResponse(val courses: List<InstructorCourse> = emptyList())

/**
 * Also used for edits. `status` is null on an ordinary create/edit and
 * only set to request a transition (e.g. "under_review") — see
 * InstructorCourseRepository.submitForReview(). There is no dedicated
 * publish/submit-for-review endpoint on the backend yet; this reuses the
 * existing create/update endpoint rather than inventing a new route, on
 * the assumption the backend will be taught to accept this field (see the
 * approved plan's note on this).
 */
@Serializable
data class CourseUpsertRequest(
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val educationLevel: String = "professional",
    val category: String = "",
    val price: Double = 0.0,
    val status: String? = null,
)

@Serializable
data class CourseUpsertResponse(val id: String, val slug: String? = null, val ok: Boolean = true)

@Serializable
data class CourseDeleteResponse(val ok: Boolean = true, val archived: Boolean = false, val deleted: Boolean = false, val message: String? = null)

// --- Media & lessons (api/instructor/courses/{id}/assets, /lessons) ---

@Serializable
data class CourseAsset(
    val id: String,
    val kind: String,
    val originalName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val createdAt: String? = null,
)

@Serializable
data class CourseAssetsResponse(val assets: List<CourseAsset> = emptyList())

/**
 * The response to the "create upload ticket" call. Issuing this ticket
 * takes NO request body on the backend (it only reads the Content-Type
 * header plus the kind/name query params) — the actual file bytes only
 * go to the raw PUT against [uploadUrl] in step 2 of the upload flow (see
 * InstructorMediaRepository.uploadAsset). [uploadUrl] already carries its
 * own signed token as a query param, so that PUT needs no extra auth
 * header.
 */
@Serializable
data class UploadTicket(
    val assetId: String,
    val uploadUrl: String,
    val token: String? = null,
    val path: String,
    val kind: String,
    val originalName: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class ConfirmAssetRequest(
    val name: String,
    val kind: String,
    val mimeType: String,
    val storedName: String,
    val sizeBytes: Long,
)

@Serializable
data class ConfirmedAsset(
    val id: String,
    val courseId: String? = null,
    val kind: String,
    val originalName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val url: String? = null,
)

@Serializable
data class ConfirmAssetResponse(val asset: ConfirmedAsset)

@Serializable
data class InstructorLesson(
    val id: String,
    val title: String,
    val lessonType: String,
    val position: Int = 0,
    val assetId: String? = null,
    val assetName: String? = null,
    val assetKind: String? = null,
    val body: String? = null,
    val durationSeconds: Int = 0,
    val isPreview: Boolean = false,
)

@Serializable
data class InstructorLessonsResponse(val lessons: List<InstructorLesson> = emptyList())

@Serializable
data class QuizOptionDraft(val text: String, val correct: Boolean = false, val position: Int = 0)

@Serializable
data class QuizQuestionDraft(
    val prompt: String,
    val type: String = "single",
    val options: List<QuizOptionDraft> = emptyList(),
    val marks: Int = 1,
    val explanation: String = "",
    val position: Int = 0,
)

@Serializable
data class QuizDraft(
    val passMark: Int = 70,
    val maxAttempts: Int = 3,
    val timeLimitMinutes: Int = 0,
    val shuffleQuestions: Boolean = false,
    val showAnswers: Boolean = true,
    val instructions: String = "",
    val questions: List<QuizQuestionDraft> = emptyList(),
)

@Serializable
data class AssignmentDraft(val instructions: String, val dueDate: String? = null, val maxMarks: Int = 100)

@Serializable
data class LessonCreateRequest(
    val title: String,
    val lessonType: String,
    val position: Int = 0,
    val assetId: String? = null,
    val body: String = "",
    val durationSeconds: Int = 0,
    val isPreview: Boolean = false,
    val quiz: QuizDraft? = null,
    val assignment: AssignmentDraft? = null,
)

@Serializable
data class LessonCreateResponse(val id: String, val lessonType: String? = null)

// --- Earnings (api/instructor/earnings) — shared by Dashboard, Analytics
// and Payouts, each just presenting a different slice of the same
// response. summary/earnings/payouts are DB-row passthroughs; the rest
// are hand-written camelCase literals the backend builds by hand
// (sharePercent, refundWindowDays, earningsHoldDays, withholdingPercent,
// taxClearance), so those need an explicit @SerialName. ---

@Serializable
data class EarningsSummary(
    val available: Long = 0,
    val pending: Long = 0,
    val lifetime: Long = 0,
    val nextReleaseAt: String? = null,
)

@Serializable
data class EarningRow(
    val id: String,
    val courseId: String? = null,
    val courseTitle: String? = null,
    val grossCents: Int = 0,
    val instructorCents: Int = 0,
    val platformCents: Int = 0,
    val status: String = "pending",
    val availableAt: String? = null,
    val orderDate: String? = null,
)

@Serializable
data class PayoutRow(
    val id: String,
    val amountCents: Int = 0,
    val grossCents: Int? = null,
    val withholdingCents: Int? = null,
    val currency: String = "USD",
    val status: String = "pending",
    val reference: String? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val paidAt: String? = null,
)

@Serializable
data class TaxClearance(
    val taxClearanceNumber: String? = null,
    val taxClearanceExpiresAt: String? = null,
    val cleared: Boolean = false,
)

@Serializable
data class InstructorEarningsResponse(
    val summary: EarningsSummary = EarningsSummary(),
    val earnings: List<EarningRow> = emptyList(),
    val payouts: List<PayoutRow> = emptyList(),
    @SerialName("sharePercent") val sharePercent: Int = 75,
    @SerialName("refundWindowDays") val refundWindowDays: Int = 14,
    @SerialName("earningsHoldDays") val earningsHoldDays: Int = 17,
    @SerialName("withholdingPercent") val withholdingPercent: Double = 0.0,
    @SerialName("taxClearance") val taxClearance: TaxClearance = TaxClearance(),
)

// --- Live classes (instructor side) (api/instructor/live-classes) ---

@Serializable
data class InstructorLiveClass(
    val id: String,
    val courseId: String? = null,
    val courseTitle: String? = null,
    val title: String,
    val description: String? = null,
    val startsAt: String,
    val durationMinutes: Int = 60,
    val capacity: Int = 50,
    val status: String = "scheduled",
    val sessionType: String = "group_class",
    val timezone: String = "Africa/Harare",
    val meetingProvider: String? = null,
    val joinUrl: String? = null,
    val hostUrl: String? = null,
    val recordingUrl: String? = null,
    val allowRecording: Boolean = false,
    val reservedCount: Int = 0,
    val attendedCount: Int = 0,
)

@Serializable
data class InstructorLiveClassesResponse(val classes: List<InstructorLiveClass> = emptyList())

@Serializable
data class LiveClassCreateRequest(
    val title: String,
    val description: String = "",
    val courseId: String? = null,
    val startsAt: String,
    val durationMinutes: Int = 60,
    val capacity: Int = 50,
    val sessionType: String = "group_class",
    val timezone: String = "Africa/Harare",
    val meetingProvider: String = "external",
    val joinUrl: String = "",
    val hostUrl: String = "",
    val allowRecording: Boolean = false,
)

@Serializable
data class LiveClassCreateResponse(val id: String)

/** PATCH body for the instructor "Manage" edit sheet — status and/or a recording URL after the fact. */
@Serializable
data class LiveClassUpdateRequest(
    val status: String? = null,
    val recordingUrl: String? = null,
)

@Serializable
data class AttendanceRow(
    val studentName: String,
    val studentEmail: String? = null,
    val reservedAt: String? = null,
    val joinedAt: String? = null,
    val attendanceMinutes: Int = 0,
)

@Serializable
data class AttendanceResponse(val attendance: List<AttendanceRow> = emptyList())

// --- Announcements (api/announcements) ---

@Serializable
data class AnnouncementRow(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: String? = null,
    val courseTitle: String? = null,
)

@Serializable
data class AnnouncementsResponse(val announcements: List<AnnouncementRow> = emptyList())

// courseId/emailStudents are hand-literal request-body keys the backend
// reads as x.courseId / x.emailStudents, same reasoning as Conversation.kt's
// ConversationCreateRequest.
@Serializable
data class AnnouncementCreateRequest(
    @SerialName("courseId") val courseId: String,
    val title: String,
    val body: String,
    @SerialName("emailStudents") val emailStudents: Boolean = true,
)

@Serializable
data class AnnouncementCreateResponse(val id: String? = null, val recipients: Int = 0)
