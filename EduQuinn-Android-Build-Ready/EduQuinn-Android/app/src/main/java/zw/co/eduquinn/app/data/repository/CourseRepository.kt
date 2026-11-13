package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.model.CourseDetailResponse
import zw.co.eduquinn.app.data.model.PublicStats
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class CourseRepository(private val api: ApiService) {

    suspend fun courses(level: String? = null): Result<List<Course>> = safeApiCall {
        api.courses(level).courses
    }

    suspend fun publicStats(): Result<PublicStats> = safeApiCall { api.publicStats() }

    suspend fun courseDetail(id: String): Result<CourseDetailResponse> = safeApiCall {
        api.courseDetail(id)
    }
}
