package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.InstructorEarningsResponse
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

/** One endpoint, shared by Dashboard, Analytics and Payouts — each just shows a different slice of the same response. */
class InstructorEarningsRepository(private val api: ApiService) {
    suspend fun earnings(): Result<InstructorEarningsResponse> = safeApiCall { api.instructorEarnings() }
}
