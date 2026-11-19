package zw.co.eduquinn.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.network.ApiException
import zw.co.eduquinn.app.data.repository.AuthRepository

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Error(val message: String) : RegisterUiState
    data class PendingVerification(
        val email: String,
        val resending: Boolean = false,
        val resent: Boolean = false,
    ) : RegisterUiState
}

/**
 * Student registration only for this native pass — the API's `role` field
 * also supports "instructor" (with an expertise picker and an immediate
 * 2FA enrolment gate), but instructor onboarding stays on the web app for
 * now, matching the native rewrite's v1 scope. A brand-new student
 * account never gets a session straight away though: /api/auth/register
 * sends a verification email and returns `pendingVerification` instead of
 * a user, so this has to branch on the response rather than assume
 * success always means "logged in".
 */
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        if (trimmedName.length < 2) {
            _uiState.value = RegisterUiState.Error("Enter your full name.")
            return
        }
        if (trimmedEmail.isEmpty()) {
            _uiState.value = RegisterUiState.Error("Enter a valid email address.")
            return
        }
        if (password.length < 10 || !password.any { it.isDigit() } || !password.any { it.isLetter() }) {
            _uiState.value = RegisterUiState.Error("Password must be at least 10 characters and include both a letter and a number.")
            return
        }
        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            val result = authRepository.register(trimmedName, trimmedEmail, password)
            result.fold(
                onSuccess = { response ->
                    _uiState.value = when {
                        response.user != null -> {
                            onSuccess()
                            RegisterUiState.Idle
                        }
                        response.pendingVerification -> RegisterUiState.PendingVerification(response.email ?: trimmedEmail)
                        else -> RegisterUiState.Error("Something went wrong. Please try again.")
                    }
                },
                onFailure = { error ->
                    val message = (error as? ApiException)?.message ?: "Something went wrong. Please try again."
                    _uiState.value = RegisterUiState.Error(message)
                },
            )
        }
    }

    fun resendVerification(email: String) {
        val state = _uiState.value
        if (state !is RegisterUiState.PendingVerification) return
        _uiState.value = state.copy(resending = true)
        viewModelScope.launch {
            authRepository.resendVerification(email)
            _uiState.value = RegisterUiState.PendingVerification(email, resending = false, resent = true)
        }
    }

    fun dismissError() {
        if (_uiState.value is RegisterUiState.Error) _uiState.value = RegisterUiState.Idle
    }
}
