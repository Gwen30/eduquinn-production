package zw.co.eduquinn.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Enrolment(
    val courseId: String,
    val title: String,
    val slug: String,
    val educationLevel: String? = null,
    val category: String? = null,
    val progress: Int = 0,
    val enrolledAt: String,
    val completedAt: String? = null,
)

@Serializable
data class EnrolmentsResponse(val enrolments: List<Enrolment>)

@Serializable
data class LearningLesson(
    val id: String,
    val title: String,
    val lessonType: String,
    val position: Int = 0,
    val durationSeconds: Int? = null,
    val isPreview: Boolean = false,
    val assetId: String? = null,
    val body: String? = null,
    val completed: Boolean = false,
    val positionSeconds: Int = 0,
)

@Serializable
data class LearningLessonsResponse(val lessons: List<LearningLesson>)

@Serializable
data class QuizOption(val text: String, val position: Int = 0)

@Serializable
data class AssessmentQuestion(
    val id: String,
    val prompt: String,
    // Empty for short_answer questions (they have no option list).
    val options: List<QuizOption> = emptyList(),
    val position: Int = 0,
    val questionType: String = "single",
    val marks: Int = 1,
)

@Serializable
data class Assessment(
    val id: String,
    val lessonId: String? = null,
    val title: String,
    val passMark: Int,
    val instructions: String? = null,
    val maxAttempts: Int = 3,
    val timeLimitMinutes: Int? = null,
    val shuffleQuestions: Boolean = false,
    val showAnswers: Boolean = true,
    val questions: List<AssessmentQuestion> = emptyList(),
    val attemptsUsed: Int = 0,
)

@Serializable
data class AssessmentsResponse(val assessments: List<Assessment>)

// The backend expects a flat, POSITIONAL array — answers[i] must line up
// with `assessment.questions` sorted by position (which is exactly the
// order GET .../assessments already returns them in). Each element is a
// plain Int (option index) for single/true_false, a List<Int> for
// multiple, or a String for short_answer — hence JsonElement rather than a
// typed wrapper keyed by question id.
@Serializable
data class AssessmentSubmitRequest(val answers: List<JsonElement>)

@Serializable
data class AssessmentResult(
    val attemptId: String,
    val score: Int,
    val passed: Boolean,
    val passMark: Int,
    val perQuestion: List<Boolean>? = null,
    val attemptsUsed: Int,
    val maxAttempts: Int,
)

@Serializable
data class LessonProgressRequest(val completed: Boolean, val positionSeconds: Int)

@Serializable
data class LessonProgressResponse(val progress: Int)
