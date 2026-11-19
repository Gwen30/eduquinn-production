package zw.co.eduquinn.app.data.repository

import kotlinx.serialization.json.JsonElement
import zw.co.eduquinn.app.data.model.Assessment
import zw.co.eduquinn.app.data.model.AssessmentResult
import zw.co.eduquinn.app.data.model.AssessmentSubmitRequest
import zw.co.eduquinn.app.data.model.Enrolment
import zw.co.eduquinn.app.data.model.LearningLesson
import zw.co.eduquinn.app.data.model.LessonProgressRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class LearningRepository(private val api: ApiService) {

    suspend fun enrolments(): Result<List<Enrolment>> = safeApiCall { api.enrolments().enrolments }

    suspend fun lessons(courseId: String): Result<List<LearningLesson>> = safeApiCall {
        api.learningCourse(courseId).lessons
    }

    suspend fun assessments(courseId: String): Result<List<Assessment>> = safeApiCall {
        api.assessments(courseId).assessments
    }

    /**
     * [answers] must be positionally aligned with `assessment.questions`
     * sorted by position — see AssessmentSubmitRequest's kdoc. Each entry
     * is an Int (single/true_false), a List<Int> (multiple), or a String
     * (short_answer) wrapped as JsonElement.
     */
    suspend fun submitAssessment(assessmentId: String, answers: List<JsonElement>): Result<AssessmentResult> =
        safeApiCall { api.submitAssessment(assessmentId, AssessmentSubmitRequest(answers)) }

    suspend fun postProgress(lessonId: String, completed: Boolean, positionSeconds: Int): Result<Int> =
        safeApiCall {
            api.postProgress(lessonId, LessonProgressRequest(completed, positionSeconds)).progress
        }
}
