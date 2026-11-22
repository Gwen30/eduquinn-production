package zw.co.eduquinn.app.data.repository

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import zw.co.eduquinn.app.data.model.ConfirmAssetRequest
import zw.co.eduquinn.app.data.model.ConfirmedAsset
import zw.co.eduquinn.app.data.model.CourseAsset
import zw.co.eduquinn.app.data.model.InstructorLesson
import zw.co.eduquinn.app.data.model.LessonCreateRequest
import zw.co.eduquinn.app.data.model.LessonCreateResponse
import zw.co.eduquinn.app.data.model.UploadTicket
import zw.co.eduquinn.app.data.network.ApiException
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

/**
 * The 3-step signed-upload flow this app has never needed before (first
 * file-picker/upload code in the codebase): (1) a Retrofit call gets a
 * signed ticket, (2) a raw PUT of the file bytes straight to the ticket's
 * own `uploadUrl` (already carries its signed token as a query param, so
 * no extra auth header), (3) a Retrofit call confirms the upload. Step 2
 * drops to the shared [okHttpClient] directly, the same "non-Retrofit-
 * shaped call" pattern [MediaLinkResolver] already uses for its one raw
 * call, since Retrofit has no clean way to stream an arbitrary byte
 * source with upload-progress callbacks.
 */
class InstructorMediaRepository(
    private val api: ApiService,
    private val okHttpClient: OkHttpClient,
) {
    suspend fun listAssets(courseId: String): Result<List<CourseAsset>> = safeApiCall {
        api.courseAssets(courseId).assets
    }

    suspend fun requestUploadTicket(courseId: String, kind: String, name: String): Result<UploadTicket> = safeApiCall {
        api.createUploadTicket(courseId, kind, name)
    }

    /**
     * Streams from [openStream] rather than buffering the whole file in
     * memory, so a large video upload doesn't blow up on a low-memory
     * device. [onProgress] is called with a 0f..1f fraction as bytes go
     * out; not wrapped in [safeApiCall] since that's Retrofit/HttpException
     * specific — this is a raw OkHttp call, so IOExceptions are caught by
     * hand and mapped to the same [ApiException] shape, matching
     * [MediaLinkResolver]'s existing manual try/catch style.
     */
    suspend fun uploadBytes(
        ticket: UploadTicket,
        mimeType: String,
        sizeBytes: Long,
        openStream: () -> InputStream,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = ProgressRequestBody(
                mediaType = mimeType.toMediaTypeOrNull(),
                contentLength = sizeBytes,
                openStream = openStream,
                onProgress = onProgress,
            )
            val request = Request.Builder().url(ticket.uploadUrl).put(body).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(ApiException(response.code, "The upload did not complete — please try again."))
                } else {
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.failure(ApiException(-1, e.message ?: "Could not reach EduQuinn. Check your connection."))
        }
    }

    suspend fun confirmAsset(
        courseId: String,
        assetId: String,
        name: String,
        kind: String,
        mimeType: String,
        storedName: String,
        sizeBytes: Long,
    ): Result<ConfirmedAsset> = safeApiCall {
        api.confirmAsset(courseId, assetId, ConfirmAssetRequest(name, kind, mimeType, storedName, sizeBytes)).asset
    }

    suspend fun listLessons(courseId: String): Result<List<InstructorLesson>> = safeApiCall {
        api.instructorLessons(courseId).lessons
    }

    suspend fun createLesson(courseId: String, body: LessonCreateRequest): Result<LessonCreateResponse> = safeApiCall {
        api.createLesson(courseId, body)
    }
}

private class ProgressRequestBody(
    private val mediaType: okhttp3.MediaType?,
    private val contentLength: Long,
    private val openStream: () -> InputStream,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType() = mediaType
    override fun contentLength() = contentLength

    override fun writeTo(sink: BufferedSink) {
        openStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var uploaded = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploaded += read
                if (contentLength > 0) onProgress((uploaded.toFloat() / contentLength).coerceIn(0f, 1f))
            }
        }
    }
}
