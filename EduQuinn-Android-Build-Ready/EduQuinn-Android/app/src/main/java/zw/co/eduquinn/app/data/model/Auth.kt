package zw.co.eduquinn.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NetworkModule's shared Json applies JsonNamingStrategy.SnakeCase
// globally, which is correct for the DB-row-passthrough fields the rest
// of this app's models deal with (price_cents, education_level, ...) but
// WRONG for this file: publicUser() and the login/register/mfa handlers
// in api/index.js are hand-written object literals using plain camelCase
// keys (`emailVerified`, `mfaRequired`, `challengeId`, ...), not derived
// from a snake_case column. Every multi-word field below needs an
// explicit @SerialName naming the exact wire key, or the naming strategy
// silently looks for a snake_case key that's never there and the field
// just decodes to its default - which is exactly how emailVerified has
// always decoded to false, and how a new mfaRequired/challengeId field
// would too without this.
@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val expertise: String? = null,
    @SerialName("emailVerified") val emailVerified: Boolean = false,
)

@Serializable
data class SessionResponse(val user: User? = null)

/**
 * /api/auth/login, /api/auth/register and /api/auth/mfa/verify all share
 * this one response shape on the wire, but a 2xx body can land in any of
 * four states depending on what the backend decides to do next (see
 * startInstructorAuth / the register handler in api/index.js):
 *  - user present          -> a session now exists, done.
 *  - mfaSetupRequired      -> a brand-new instructor with no totp_secret
 *                             yet; challengeId/secret/otpauthUrl/qrDataUrl
 *                             carry the pending enrolment to the next call.
 *  - mfaRequired           -> a returning instructor; only challengeId is
 *                             present, show a plain code-entry step.
 *  - pendingVerification   -> a brand-new student; no session yet, an
 *                             email verification link has been sent.
 * These never combine, but every field is optional so decoding any one of
 * the four shapes never throws - a non-nullable `user` here is exactly
 * what made the native login/register screens blow up with a raw
 * serialization error instead of showing the right next step.
 */
@Serializable
data class AuthResponse(
    val user: User? = null,
    @SerialName("mfaRequired") val mfaRequired: Boolean = false,
    @SerialName("mfaSetupRequired") val mfaSetupRequired: Boolean = false,
    @SerialName("challengeId") val challengeId: String? = null,
    val secret: String? = null,
    @SerialName("otpauthUrl") val otpauthUrl: String? = null,
    @SerialName("qrDataUrl") val qrDataUrl: String? = null,
    @SerialName("backupCodes") val backupCodes: List<String>? = null,
    @SerialName("pendingVerification") val pendingVerification: Boolean = false,
    val email: String? = null,
    val message: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    // "public" matches the web app's own portal value - it's what lets the
    // backend correctly reject admin accounts here ("Administrator sign-in
    // is not available from the public login page") instead of silently
    // letting them through, which is what an unrecognized portal value did.
    val portal: String = "public",
    // Set from LoginScreen's role-choice step ("student"/"instructor").
    // The backend 403s with a clear message when this doesn't match the
    // account's real role (e.g. picking "Teacher" for a student account),
    // which is exactly the guardrail a role picker is supposed to add.
    @SerialName("expectedRole") val expectedRole: String = "",
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val role: String = "student",
    val expertise: String? = null,
    val password: String,
)

@Serializable
data class MfaVerifyRequest(
    @SerialName("challengeId") val challengeId: String,
    val code: String,
)

@Serializable
data class ResendVerificationRequest(
    val email: String,
)
