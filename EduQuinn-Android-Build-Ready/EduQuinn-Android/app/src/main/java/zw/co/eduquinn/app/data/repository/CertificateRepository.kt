package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.model.Certificate
import zw.co.eduquinn.app.data.model.CertificateIssueRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class CertificateRepository(private val api: ApiService) {

    /** Idempotent on the backend — safe to call every time the screen opens. */
    suspend fun issue(courseId: String): Result<Certificate> = safeApiCall {
        api.issueCertificate(CertificateIssueRequest(courseId)).certificate
    }
}
