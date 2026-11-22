package zw.co.eduquinn.app.data.repository

import zw.co.eduquinn.app.data.local.PersistentCookieJar
import zw.co.eduquinn.app.data.model.AuthResponse
import zw.co.eduquinn.app.data.model.LoginRequest
import zw.co.eduquinn.app.data.model.MfaVerifyRequest
import zw.co.eduquinn.app.data.model.RegisterRequest
import zw.co.eduquinn.app.data.model.ResendVerificationRequest
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

    /**
     * Returns the raw [AuthResponse] rather than Unit - a 2xx body here can
     * be an actual session (user != null), a 2FA setup/challenge step, or
     * neither, and only the caller's UI knows which screen to show next.
     * SessionManager is updated here (not by the caller) only when a user
     * is actually present, so a half-finished MFA flow never looks logged
     * in.
     */
    suspend fun login(email: String, password: String, expectedRole: String = ""): Result<AuthResponse> = safeApiCall {
        val response = api.login(LoginRequest(email = email, password = password, expectedRole = expectedRole))
        response.user?.let { SessionManager.set(it) }
        response
    }

    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String = "student",
        expertise: String? = null,
    ): Result<AuthResponse> = safeApiCall {
        val response = api.register(RegisterRequest(name = name, email = email, password = password, role = role, expertise = expertise))
        response.user?.let { SessionManager.set(it) }
        response
    }

    /** Completes either an MFA setup enrolment or a plain login challenge. */
    suspend fun verifyMfa(challengeId: String, code: String): Result<AuthResponse> = safeApiCall {
        val response = api.verifyMfa(MfaVerifyRequest(challengeId = challengeId, code = code))
        response.user?.let { SessionManager.set(it) }
        response
    }

    /** Same response whether or not the email needs it - the backend never reveals that. */
    suspend fun resendVerification(email: String): Result<Unit> = safeApiCall {
        api.resendVerification(ResendVerificationRequest(email = email))
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
