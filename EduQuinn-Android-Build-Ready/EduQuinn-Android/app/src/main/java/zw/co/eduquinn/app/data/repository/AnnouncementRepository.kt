package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.AnnouncementCreateRequest
import zw.co.eduquinn.app.data.model.AnnouncementCreateResponse
import zw.co.eduquinn.app.data.model.AnnouncementRow
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class AnnouncementRepository(private val api: ApiService) {

    suspend fun list(): Result<List<AnnouncementRow>> = safeApiCall { api.announcements().announcements }

    suspend fun create(courseId: String, title: String, body: String, emailStudents: Boolean): Result<AnnouncementCreateResponse> = safeApiCall {
        api.createAnnouncement(AnnouncementCreateRequest(courseId = courseId, title = title, body = body, emailStudents = emailStudents))
    }
}
