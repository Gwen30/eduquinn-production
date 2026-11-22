package zw.co.eduquinn.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.AuthResponse
import zw.co.eduquinn.app.data.network.ApiException
import zw.co.eduquinn.app.data.repository.AuthRepository

/**
 * A successful /api/auth/login call doesn't always mean a session exists
 * yet (see AuthResponse's doc comment). This mirrors the web app's
 * login.js + mfa.js split: Loading covers the initial password check, and
 * the response can then branch into an unverified-student notice or an
 * instructor 2FA setup/challenge step before any session is created.
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
    data class NeedsVerification(
        val email: String,
        val resending: Boolean = false,
        val resent: Boolean = false,
    ) : LoginUiState
    data class MfaSetup(
        val challengeId: String,
        val secret: String?,
        val qrDataUrl: String?,
        val verifying: Boolean = false,
        val error: String? = null,
    ) : LoginUiState
    data class MfaChallenge(
        val challengeId: String,
        val verifying: Boolean = false,
        val error: String? = null,
    ) : LoginUiState
    data class MfaBackupCodes(val codes: List<String>) : LoginUiState
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // Stashed so the backup-codes step's "continue" button can finish the
    // same login flow onSuccess was passed for - by the time backup codes
    // come back, verifyMfa has already set a real session (see
    // AuthRepository), so this only needs to trigger the caller's nav.
    private var pendingOnSuccess: (() -> Unit)? = null

    fun login(email: String, password: String, expectedRole: String, onSuccess: () -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error("Enter your email and password.")
            return
        }
        pendingOnSuccess = onSuccess
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = authRepository.login(trimmedEmail, password, expectedRole)
            result.fold(
                onSuccess = { response -> handleAuthResponse(response, onSuccess) },
                onFailure = { error ->
                    val apiError = error as? ApiException
                    _uiState.value = if (apiError?.needsVerification == true) {
                        LoginUiState.NeedsVerification(apiError.email ?: trimmedEmail)
                    } else {
                        // A role mismatch (e.g. picking "Teacher" with a
                        // student account) 403s with a clear, specific
                        // message from the backend ("This is not a student
                        // account. Choose Teacher login if you are an
                        // instructor.") — it needs no special handling here,
                        // it's just whatever message the server sent.
                        LoginUiState.Error(apiError?.message ?: "Something went wrong. Please try again.")
                    }
                },
            )
        }
    }

    private fun handleAuthResponse(response: AuthResponse, onSuccess: () -> Unit) {
        _uiState.value = when {
            response.user != null -> {
                onSuccess()
                LoginUiState.Idle
            }
            response.mfaSetupRequired && response.challengeId != null ->
                LoginUiState.MfaSetup(response.challengeId, response.secret, response.qrDataUrl)
            response.mfaRequired && response.challengeId != null ->
                LoginUiState.MfaChallenge(response.challengeId)
            else -> LoginUiState.Error("Something went wrong. Please try again.")
        }
    }

    /** Submits a code for whichever MFA step (setup or challenge) is currently showing. */
    fun submitMfaCode(code: String) {
        val state = _uiState.value
        val challengeId = when (state) {
            is LoginUiState.MfaSetup -> state.challengeId
            is LoginUiState.MfaChallenge -> state.challengeId
            else -> return
        }
        if (code.isBlank()) return
        _uiState.value = when (state) {
            is LoginUiState.MfaSetup -> state.copy(verifying = true, error = null)
            is LoginUiState.MfaChallenge -> state.copy(verifying = true, error = null)
            else -> state
        }
        viewModelScope.launch {
            val result = authRepository.verifyMfa(challengeId, code.trim())
            result.fold(
                onSuccess = { response ->
                    _uiState.value = when {
                        !response.backupCodes.isNullOrEmpty() -> LoginUiState.MfaBackupCodes(response.backupCodes)
                        response.user != null -> {
                            pendingOnSuccess?.invoke()
                            LoginUiState.Idle
                        }
                        else -> LoginUiState.Error("Something went wrong. Please try again.")
                    }
                },
                onFailure = { error ->
                    val message = (error as? ApiException)?.message ?: "That code is incorrect or expired."
                    _uiState.value = when (state) {
                        is LoginUiState.MfaSetup -> state.copy(verifying = false, error = message)
                        is LoginUiState.MfaChallenge -> state.copy(verifying = false, error = message)
                        else -> LoginUiState.Error(message)
                    }
                },
            )
        }
    }

    /** The user has acknowledged their backup codes; finish the login they started. */
    fun acknowledgeBackupCodes() {
        _uiState.value = LoginUiState.Idle
        pendingOnSuccess?.invoke()
    }

    fun resendVerification(email: String) {
        val state = _uiState.value
        if (state !is LoginUiState.NeedsVerification) return
        _uiState.value = state.copy(resending = true)
        viewModelScope.launch {
            authRepository.resendVerification(email)
            _uiState.value = LoginUiState.NeedsVerification(email, resending = false, resent = true)
        }
    }

    fun dismissError() {
        if (_uiState.value is LoginUiState.Error) _uiState.value = LoginUiState.Idle
    }
}
