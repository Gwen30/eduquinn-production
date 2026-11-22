package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.InstructorEarningsResponse
import zw.co.eduquinn.app.data.repository.InstructorEarningsRepository

data class InstructorPayoutsUiState(
    val earnings: InstructorEarningsResponse? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Read-only by design, not an oversight — I checked the real backend and
 * there is no instructor-facing "request payout" endpoint. Payouts are
 * admin-initiated only (`POST /api/admin/payouts/approve`), so this
 * screen is just a view of the same `/api/instructor/earnings` response
 * Dashboard and Analytics also use.
 */
class InstructorPayoutsViewModel(private val repository: InstructorEarningsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorPayoutsUiState())
    val uiState: StateFlow<InstructorPayoutsUiState> = _uiState.asStateFlow()

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
