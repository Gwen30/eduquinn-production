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

sealed interface RegisterUiState {
    data object Idle : RegisterUiState
    data object Loading : RegisterUiState
    data class Error(val message: String) : RegisterUiState
    data class PendingVerification(
        val email: String,
        val resending: Boolean = false,
        val resent: Boolean = false,
    ) : RegisterUiState
    // Instructor registration hits the exact same mfaSetupRequired branch
    // the backend's startInstructorAuth() gives a login does — these three
    // mirror LoginUiState's variants field-for-field so both screens can
    // share the plain MfaSetupStep/MfaChallengeStep/MfaBackupCodesStep
    // composables in MfaSteps.kt.
    data class MfaSetup(
        val challengeId: String,
        val secret: String?,
        val qrDataUrl: String?,
        val verifying: Boolean = false,
        val error: String? = null,
    ) : RegisterUiState
    data class MfaChallenge(
        val challengeId: String,
        val verifying: Boolean = false,
        val error: String? = null,
    ) : RegisterUiState
    data class MfaBackupCodes(val codes: List<String>) : RegisterUiState
}

/**
 * Handles both student and instructor registration. A brand-new student
 * account never gets a session straight away: /api/auth/register sends a
 * verification email and returns `pendingVerification` instead of a user.
 * A brand-new instructor account instead goes straight into the same
 * mandatory 2FA enrolment gate login uses (`mfaSetupRequired` with a
 * challengeId/secret/qrDataUrl) — see MfaSteps.kt and LoginViewModel's
 * equivalent handling, which this mirrors.
 */
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    // Same purpose as LoginViewModel's pendingOnSuccess: by the time the
    // backup-codes step's "continue" button fires, verifyMfa has already
    // set a real session, so this just triggers the caller's nav.
    private var pendingOnSuccess: (() -> Unit)? = null

    fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        expertise: String?,
        onSuccess: () -> Unit,
    ) {
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
        if (role == "instructor" && expertise.isNullOrBlank()) {
            _uiState.value = RegisterUiState.Error("Choose your primary area of expertise.")
            return
        }
        pendingOnSuccess = onSuccess
        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            val result = authRepository.register(trimmedName, trimmedEmail, password, role, expertise?.takeIf { role == "instructor" })
            result.fold(
                onSuccess = { response -> handleAuthResponse(response, trimmedEmail, onSuccess) },
                onFailure = { error ->
                    val message = (error as? ApiException)?.message ?: "Something went wrong. Please try again."
                    _uiState.value = RegisterUiState.Error(message)
                },
            )
        }
    }

    private fun handleAuthResponse(response: AuthResponse, trimmedEmail: String, onSuccess: () -> Unit) {
        _uiState.value = when {
            response.user != null -> {
                onSuccess()
                RegisterUiState.Idle
            }
            response.mfaSetupRequired && response.challengeId != null ->
                RegisterUiState.MfaSetup(response.challengeId, response.secret, response.qrDataUrl)
            response.mfaRequired && response.challengeId != null ->
                RegisterUiState.MfaChallenge(response.challengeId)
            response.pendingVerification -> RegisterUiState.PendingVerification(response.email ?: trimmedEmail)
            else -> RegisterUiState.Error("Something went wrong. Please try again.")
        }
    }

    /** Submits a code for whichever MFA step (setup or challenge) is currently showing. */
    fun submitMfaCode(code: String) {
        val state = _uiState.value
        val challengeId = when (state) {
            is RegisterUiState.MfaSetup -> state.challengeId
            is RegisterUiState.MfaChallenge -> state.challengeId
            else -> return
        }
        if (code.isBlank()) return
        _uiState.value = when (state) {
            is RegisterUiState.MfaSetup -> state.copy(verifying = true, error = null)
            is RegisterUiState.MfaChallenge -> state.copy(verifying = true, error = null)
            else -> state
        }
        viewModelScope.launch {
            val result = authRepository.verifyMfa(challengeId, code.trim())
            result.fold(
                onSuccess = { response ->
                    _uiState.value = when {
                        !response.backupCodes.isNullOrEmpty() -> RegisterUiState.MfaBackupCodes(response.backupCodes)
                        response.user != null -> {
                            pendingOnSuccess?.invoke()
                            RegisterUiState.Idle
                        }
                        else -> RegisterUiState.Error("Something went wrong. Please try again.")
                    }
                },
                onFailure = { error ->
                    val message = (error as? ApiException)?.message ?: "That code is incorrect or expired."
                    _uiState.value = when (state) {
                        is RegisterUiState.MfaSetup -> state.copy(verifying = false, error = message)
                        is RegisterUiState.MfaChallenge -> state.copy(verifying = false, error = message)
                        else -> RegisterUiState.Error(message)
                    }
                },
            )
        }
    }

    /** The user has acknowledged their backup codes; finish the registration flow they started. */
    fun acknowledgeBackupCodes() {
        _uiState.value = RegisterUiState.Idle
        pendingOnSuccess?.invoke()
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
