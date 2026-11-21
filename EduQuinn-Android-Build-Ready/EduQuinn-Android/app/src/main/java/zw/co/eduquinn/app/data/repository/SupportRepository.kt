package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.SupportRequest
import zw.co.eduquinn.app.data.model.SupportResponse
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class SupportRepository(private val api: ApiService) {
    suspend fun submit(
        name: String,
        email: String,
        role: String,
        topic: String,
        subject: String,
        message: String,
    ): Result<SupportResponse> = safeApiCall {
        api.submitSupport(SupportRequest(name, email, role, topic, subject, message))
    }
}
