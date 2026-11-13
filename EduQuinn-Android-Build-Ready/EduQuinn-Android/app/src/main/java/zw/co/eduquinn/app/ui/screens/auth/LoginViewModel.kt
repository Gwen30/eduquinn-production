package zw.co.eduquinn.app.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.network.ApiException
import zw.co.eduquinn.app.data.repository.AuthRepository

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = LoginUiState.Error("Enter your email and password.")
            return
        }
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = authRepository.login(trimmedEmail, password)
            result.fold(
                onSuccess = {
                    _uiState.value = LoginUiState.Idle
                    onSuccess()
                },
                onFailure = { error ->
                    val message = (error as? ApiException)?.message ?: "Something went wrong. Please try again."
                    _uiState.value = LoginUiState.Error(message)
                },
            )
        }
    }

    fun dismissError() {
        if (_uiState.value is LoginUiState.Error) _uiState.value = LoginUiState.Idle
    }
}
