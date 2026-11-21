package zw.co.eduquinn.app.ui.screens.liveclasses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.LiveClass
import zw.co.eduquinn.app.data.repository.LiveClassRepository
import zw.co.eduquinn.app.data.repository.SessionManager

/** Status tabs mirroring live-classes.html's filter chips. "all" shows everything. */
const val LIVE_CLASS_FILTER_ALL = "all"

data class LiveClassesUiState(
    val allClasses: List<LiveClass> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: String = LIVE_CLASS_FILTER_ALL,
    val isLoading: Boolean = true,
    val error: String? = null,
    // classId currently mid-reserve/cancel, so its row can show a spinner
    // instead of the whole list.
    val pendingReservationId: String? = null,
) {
    val visibleClasses: List<LiveClass>
        get() {
            val q = searchQuery.trim().lowercase()
            return allClasses.filter { liveClass ->
                (statusFilter == LIVE_CLASS_FILTER_ALL || liveClass.status.equals(statusFilter, ignoreCase = true)) &&
                    (
                        q.isBlank() ||
                            liveClass.title.lowercase().contains(q) ||
                            liveClass.courseTitle?.lowercase()?.contains(q) == true ||
                            liveClass.instructorName?.lowercase()?.contains(q) == true
                        )
            }
        }
}

class LiveClassesViewModel(private val repository: LiveClassRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveClassesUiState())
    val uiState: StateFlow<LiveClassesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        if (!SessionManager.isLoggedIn) {
            _uiState.value = _uiState.value.copy(isLoading = false, allClasses = emptyList(), error = null)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { classes -> _uiState.value = _uiState.value.copy(allClasses = classes, isLoading = false, error = null) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onStatusFilterSelect(status: String) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
    }

    /** Toggles a reservation. Optimistic, reverted on failure — same pattern as CatalogViewModel's wishlist toggle. */
    fun toggleReservation(liveClass: LiveClass) {
        val wasReserved = liveClass.reserved
        _uiState.value = _uiState.value.copy(
            pendingReservationId = liveClass.id,
            allClasses = _uiState.value.allClasses.map {
                if (it.id == liveClass.id) {
                    it.copy(
                        reserved = !wasReserved,
                        reservedCount = it.reservedCount + if (wasReserved) -1 else 1,
                    )
                } else it
            },
        )
        viewModelScope.launch {
            val result = if (wasReserved) repository.cancelReservation(liveClass.id) else repository.reserve(liveClass.id)
            result.fold(
                onSuccess = { reservedNow ->
                    _uiState.value = _uiState.value.copy(
                        pendingReservationId = null,
                        allClasses = _uiState.value.allClasses.map {
                            if (it.id == liveClass.id) it.copy(reserved = reservedNow) else it
                        },
                    )
                },
                onFailure = { error ->
                    // Revert the optimistic change.
                    _uiState.value = _uiState.value.copy(
                        pendingReservationId = null,
                        error = error.message,
                        allClasses = _uiState.value.allClasses.map {
                            if (it.id == liveClass.id) {
                                it.copy(reserved = wasReserved, reservedCount = liveClass.reservedCount)
                            } else it
                        },
                    )
                },
            )
        }
    }
}
