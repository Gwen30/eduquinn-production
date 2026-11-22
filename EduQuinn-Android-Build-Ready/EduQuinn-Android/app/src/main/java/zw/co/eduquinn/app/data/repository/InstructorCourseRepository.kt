package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.CourseDeleteResponse
import zw.co.eduquinn.app.data.model.CourseUpsertRequest
import zw.co.eduquinn.app.data.model.CourseUpsertResponse
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class InstructorCourseRepository(private val api: ApiService) {

    suspend fun list(): Result<List<InstructorCourse>> = safeApiCall { api.instructorCourses().courses }

    suspend fun create(body: CourseUpsertRequest): Result<CourseUpsertResponse> = safeApiCall {
        api.createInstructorCourse(body)
    }

    suspend fun update(id: String, body: CourseUpsertRequest): Result<CourseUpsertResponse> = safeApiCall {
        api.updateInstructorCourse(id, body)
    }

    suspend fun delete(id: String): Result<CourseDeleteResponse> = safeApiCall { api.deleteInstructorCourse(id) }

    /**
     * There is no dedicated publish/submit-for-review endpoint on the
     * backend yet (see the plan's note on this) — this reuses the same
     * update endpoint with just a `status` transition, so the backend
     * only needs to learn to accept that one extra field rather than
     * expose a whole new route.
     */
    suspend fun requestStatus(id: String, current: InstructorCourse, newStatus: String): Result<CourseUpsertResponse> = safeApiCall {
        api.updateInstructorCourse(
            id,
            CourseUpsertRequest(
                title = current.title,
                subtitle = current.subtitle.orEmpty(),
                description = current.description.orEmpty(),
                educationLevel = current.educationLevel ?: "professional",
                category = current.category.orEmpty(),
                price = current.priceCents / 100.0,
                status = newStatus,
            ),
        )
    }
}
