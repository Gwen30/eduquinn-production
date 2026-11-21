package zw.co.eduquinn.app.data.network

/**
 * Every error response from this backend is `{"message": "..."}` (see
 * `json(status, {message})` calls throughout api/index.js). Repositories
 * catch Retrofit's HttpException, decode the body into this shape, and
 * rethrow as ApiException so ViewModels only ever deal with one error type.
 *
 * A student login that's blocked for not verifying their email is still an
 * error response (403), but it carries two extra fields the UI needs to
 * show a "resend verification email" action instead of a dead-end error
 * message: `needsVerification` and the `email` it applies to.
 */
class ApiException(
    val statusCode: Int,
    message: String,
    val needsVerification: Boolean = false,
    val email: String? = null,
) : Exception(message)
