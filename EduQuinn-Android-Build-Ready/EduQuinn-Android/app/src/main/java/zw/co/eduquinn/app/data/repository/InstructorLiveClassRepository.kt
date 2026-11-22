package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.AttendanceRow
import zw.co.eduquinn.app.data.model.InstructorLiveClass
import zw.co.eduquinn.app.data.model.LiveClassCreateRequest
import zw.co.eduquinn.app.data.model.LiveClassCreateResponse
import zw.co.eduquinn.app.data.model.LiveClassUpdateRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

/**
 * Instructor-side scheduling/attendance — a sibling to the student-facing
 * [LiveClassRepository], not an extension of it: different endpoints,
 * different role gate, different response shape ([InstructorLiveClass]
 * vs [zw.co.eduquinn.app.data.model.LiveClass]).
 */
class InstructorLiveClassRepository(private val api: ApiService) {

    suspend fun list(): Result<List<InstructorLiveClass>> = safeApiCall { api.instructorLiveClasses().classes }

    suspend fun create(body: LiveClassCreateRequest): Result<LiveClassCreateResponse> = safeApiCall {
        api.createInstructorLiveClass(body)
    }

    suspend fun update(id: String, status: String? = null, recordingUrl: String? = null): Result<Unit> = safeApiCall {
        api.updateInstructorLiveClass(id, LiveClassUpdateRequest(status = status, recordingUrl = recordingUrl))
    }

    suspend fun cancel(id: String): Result<Unit> = safeApiCall { api.cancelInstructorLiveClass(id) }

    suspend fun attendance(id: String): Result<List<AttendanceRow>> = safeApiCall { api.instructorLiveClassAttendance(id).attendance }
}
