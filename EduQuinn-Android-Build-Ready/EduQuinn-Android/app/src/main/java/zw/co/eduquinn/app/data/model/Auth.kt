package zw.co.eduquinn.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val status: String,
    val expertise: String? = null,
    val emailVerified: Boolean = false,
)

@Serializable
data class SessionResponse(val user: User? = null)

@Serializable
data class AuthResponse(val user: User)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val portal: String = "student",
    val expectedRole: String = "student",
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val role: String = "student",
    val expertise: String? = null,
    val password: String,
)
