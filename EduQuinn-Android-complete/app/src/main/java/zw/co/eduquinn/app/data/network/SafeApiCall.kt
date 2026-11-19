package zw.co.eduquinn.app.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException

// Error bodies are hand-written object literals too (e.g. json(403, {
// message, needsVerification: true, email })), with the same camelCase-
// key-vs-global-snake_case-strategy mismatch as Auth.kt - without this
// @SerialName, needsVerification would always decode to false and the
// "resend verification email" UI would never show.
@Serializable
private data class ErrorBody(
    val message: String? = null,
    @SerialName("needsVerification") val needsVerification: Boolean = false,
    val email: String? = null,
)

/**
 * Every repository routes its Retrofit calls through this so ViewModels
 * only ever see [ApiException] (with the backend's own {"message": "..."}
 * text) instead of a raw HttpException/IOException.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val raw = e.response()?.errorBody()?.string()
    val decoded = raw?.let {
        try {
            NetworkModule.json.decodeFromString<ErrorBody>(it)
        } catch (_: Exception) {
            null
        }
    }
    val message = decoded?.message ?: e.message() ?: "Something went wrong."
    Result.failure(ApiException(e.code(), message, decoded?.needsVerification ?: false, decoded?.email))
} catch (e: ApiException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(ApiException(-1, e.message ?: "Could not reach EduQuinn. Check your connection."))
}
