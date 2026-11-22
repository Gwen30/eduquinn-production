package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.model.InstructorEarningsResponse
import zw.co.eduquinn.app.data.repository.InstructorCourseRepository
import zw.co.eduquinn.app.data.repository.InstructorEarningsRepository

data class InstructorDashboardUiState(
    val courses: List<InstructorCourse> = emptyList(),
    val earnings: InstructorEarningsResponse? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    // id of a course mid-delete/status-change, so its row alone shows a
    // spinner rather than the whole screen — same single-flag convention
    // as LiveClassesUiState.pendingReservationId.
    val pendingCourseId: String? = null,
) {
    val publishedCount: Int get() = courses.count { it.status == "published" }
}

class InstructorDashboardViewModel(
    private val courseRepository: InstructorCourseRepository,
    private val earningsRepository: InstructorEarningsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorDashboardUiState())
    val uiState: StateFlow<InstructorDashboardUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val coursesResult = courseRepository.list()
            val earningsResult = earningsRepository.earnings()
            val error = coursesResult.exceptionOrNull()?.message ?: earningsResult.exceptionOrNull()?.message
            _uiState.value = _uiState.value.copy(
                courses = coursesResult.getOrNull() ?: _uiState.value.courses,
                earnings = earningsResult.getOrNull() ?: _uiState.value.earnings,
                isLoading = false,
                error = error,
            )
        }
    }

    fun deleteCourse(course: InstructorCourse) {
        _uiState.value = _uiState.value.copy(pendingCourseId = course.id)
        viewModelScope.launch {
            courseRepository.delete(course.id).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        pendingCourseId = null,
                        courses = _uiState.value.courses.filterNot { it.id == course.id },
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(pendingCourseId = null, error = error.message)
                },
            )
        }
    }
}
