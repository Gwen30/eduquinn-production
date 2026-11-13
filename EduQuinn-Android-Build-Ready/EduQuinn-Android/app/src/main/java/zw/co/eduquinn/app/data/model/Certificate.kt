package zw.co.eduquinn.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CertificateIssueRequest(val courseId: String)

@Serializable
data class Certificate(
    val id: String,
    val certificateCode: String,
    val studentId: String,
    val courseId: String,
    val issuedAt: String,
)

@Serializable
data class CertificateResponse(val certificate: Certificate)
