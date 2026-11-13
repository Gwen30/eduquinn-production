package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.local.PersistentCookieJar
import zw.co.eduquinn.app.data.model.LoginRequest
import zw.co.eduquinn.app.data.model.RegisterRequest
import zw.co.eduquinn.app.data.network.ApiService
import zw.co.eduquinn.app.data.network.safeApiCall

class AuthRepository(
    private val api: ApiService,
    private val cookieJar: PersistentCookieJar,
) {
    /** Call once on cold start, before the nav host renders any gated screen. */
    suspend fun refreshSession(): Result<Unit> = safeApiCall {
        val response = api.session()
        SessionManager.set(response.user)
    }.also { SessionManager.markInitialized() }

    suspend fun login(email: String, password: String, expectedRole: String = "student"): Result<Unit> =
        safeApiCall {
            val response = api.login(LoginRequest(email = email, password = password, expectedRole = expectedRole))
            SessionManager.set(response.user)
        }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = safeApiCall {
        val response = api.register(RegisterRequest(name = name, email = email, password = password))
        SessionManager.set(response.user)
    }

    suspend fun logout(): Result<Unit> = safeApiCall {
        try {
            api.logout()
        } finally {
            // Clear local state even if the network call fails — the user
            // tapped "log out" and expects it to take effect immediately.
            cookieJar.clear()
            SessionManager.set(null)
        }
    }
}
