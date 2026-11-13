package zw.co.eduquinn.app.data.network

/**
 * Every error response from this backend is `{"message": "..."}` (see
 * `json(status, {message})` calls throughout api/index.js). Repositories
 * catch Retrofit's HttpException, decode the body into this shape, and
 * rethrow as ApiException so ViewModels only ever deal with one error type.
 */
class ApiException(val statusCode: Int, message: String) : Exception(message)
