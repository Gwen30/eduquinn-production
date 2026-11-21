package zw.co.eduquinn.app.ui.screens.support

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.repository.SupportRepository

data class SupportUiState(
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null,
    // Non-null when the backend has no email delivery configured and the
    // client should fall back to opening the user's own mail app — exactly
    // what support.html's own onsubmit handler does with its `fallback`
    // response field.
    val fallbackMailtoUri: String? = null,
)

class SupportViewModel(private val repository: SupportRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    fun submit(name: String, email: String, role: String, topic: String, subject: String, message: String) {
        _uiState.value = _uiState.value.copy(isSubmitting = true, error = null, successMessage = null, fallbackMailtoUri = null)
        viewModelScope.launch {
            repository.submit(name, email, role, topic, subject, message).fold(
                onSuccess = { response ->
                    if (response.fallback) {
                        val address = response.email ?: "support@eduquinn.co.zw"
                        val mailSubject = Uri.encode("[EduQuinn Support] $topic — $subject")
                        val body = Uri.encode("Name: $name\nEmail: $email\nRole: $role\nTopic: $topic\n\n$message")
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            error = "Email delivery is not configured on this server, so your email app has been opened instead.",
                            fallbackMailtoUri = "mailto:$address?subject=$mailSubject&body=$body",
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            successMessage = response.message ?: "Your support request has been sent.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isSubmitting = false, error = error.message ?: "The support request could not be sent.")
                },
            )
        }
    }

    fun consumeFallbackMailto() {
        _uiState.value = _uiState.value.copy(fallbackMailtoUri = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
