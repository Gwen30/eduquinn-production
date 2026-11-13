package zw.co.eduquinn.app.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException

@Serializable
private data class ErrorBody(val message: String? = null)

/**
 * Every repository routes its Retrofit calls through this so ViewModels
 * only ever see [ApiException] (with the backend's own {"message": "..."}
 * text) instead of a raw HttpException/IOException.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val raw = e.response()?.errorBody()?.string()
    val message = raw?.let {
        try {
            NetworkModule.json.decodeFromString<ErrorBody>(it).message
        } catch (_: Exception) {
            null
        }
    } ?: e.message() ?: "Something went wrong."
    Result.failure(ApiException(e.code(), message))
} catch (e: ApiException) {
    Result.failure(e)
} catch (e: Exception) {
    Result.failure(ApiException(-1, e.message ?: "Could not reach EduQuinn. Check your connection."))
}
