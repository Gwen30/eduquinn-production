package zw.co.eduquinn.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GET /api/media/:assetId 302-redirects to a short-lived signed Supabase
 * Storage URL (see api/index.js) once the eq_session cookie proves we're
 * allowed to read it. A plain external Intent.ACTION_VIEW can't carry that
 * cookie, so this follows the redirect with our OWN authenticated
 * OkHttpClient first and hands back the final (unauthenticated, signed)
 * URL — that's what's safe to open in a browser/Custom Tab or hand to a
 * download manager. ExoPlayer instead uses this same client directly via
 * media3-datasource-okhttp, since it needs streaming + Range support, not
 * just the resolved URL.
 */
object MediaLinkResolver {
    suspend fun resolveSignedUrl(okHttpClient: OkHttpClient, baseUrl: String, assetId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("${baseUrl}api/media/$assetId").build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Result.failure(ApiException(response.code, "Could not open this file."))
                    } else {
                        Result.success(response.request.url.toString())
                    }
                }
            } catch (e: Exception) {
                Result.failure(ApiException(-1, e.message ?: "Could not reach EduQuinn."))
            }
        }
}
