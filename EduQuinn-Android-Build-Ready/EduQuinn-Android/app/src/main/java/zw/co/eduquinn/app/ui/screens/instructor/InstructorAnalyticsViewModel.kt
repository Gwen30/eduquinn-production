package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.InstructorEarningsResponse
import zw.co.eduquinn.app.data.repository.InstructorEarningsRepository

data class InstructorAnalyticsUiState(
    val earnings: InstructorEarningsResponse? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Same `/api/instructor/earnings` call Dashboard and Payouts use, just a different 4 numbers shown — the real site has no charts here either. */
class InstructorAnalyticsViewModel(private val repository: InstructorEarningsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorAnalyticsUiState())
    val uiState: StateFlow<InstructorAnalyticsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.earnings().fold(
                onSuccess = { earnings -> _uiState.value = _uiState.value.copy(earnings = earnings, isLoading = false, error = null) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }
}
