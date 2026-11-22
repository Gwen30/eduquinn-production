package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.CourseUpsertRequest
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.repository.InstructorCourseRepository

data class InstructorCoursesUiState(
    val courses: List<InstructorCourse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Single id of whichever course is mid-save/delete/status-change, so
    // only that row shows a spinner — matches the pendingReservationId/
    // pendingCourseId convention used elsewhere in this app.
    val pendingCourseId: String? = null,
    val saveError: String? = null,
)

class InstructorCoursesViewModel(private val repository: InstructorCourseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorCoursesUiState())
    val uiState: StateFlow<InstructorCoursesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.list().fold(
                onSuccess = { courses -> _uiState.value = _uiState.value.copy(courses = courses, isLoading = false, error = null) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    /** `existingId == null` creates a new course; otherwise edits that course in place. */
    fun saveCourse(
        existingId: String?,
        title: String,
        subtitle: String,
        description: String,
        educationLevel: String,
        category: String,
        priceDollars: Double,
        onSaved: () -> Unit,
    ) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(saveError = "Give the course a title.")
            return
        }
        _uiState.value = _uiState.value.copy(saveError = null, pendingCourseId = existingId ?: "new")
        val body = CourseUpsertRequest(
            title = title.trim(),
            subtitle = subtitle.trim(),
            description = description.trim(),
            educationLevel = educationLevel,
            category = category,
            price = priceDollars,
        )
        viewModelScope.launch {
            val result = if (existingId != null) repository.update(existingId, body) else repository.create(body)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(pendingCourseId = null)
                    load()
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(pendingCourseId = null, saveError = error.message)
                },
            )
        }
    }

    fun dismissSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }

    fun deleteCourse(course: InstructorCourse) {
        _uiState.value = _uiState.value.copy(pendingCourseId = course.id)
        viewModelScope.launch {
            repository.delete(course.id).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        pendingCourseId = null,
                        courses = _uiState.value.courses.filterNot { it.id == course.id },
                    )
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(pendingCourseId = null, error = error.message) },
            )
        }
    }

    /**
     * Reuses the ordinary update endpoint with just a status transition —
     * see InstructorCourseRepository.requestStatus's doc comment. A draft
     * that isn't accepted yet (because the backend hasn't been taught
     * this field) just surfaces whatever error the server sends back
     * through the normal error state, same as any other failed save.
     */
    fun submitForReview(course: InstructorCourse) {
        _uiState.value = _uiState.value.copy(pendingCourseId = course.id)
        viewModelScope.launch {
            repository.requestStatus(course.id, course, "under_review").fold(
                onSuccess = { _uiState.value = _uiState.value.copy(pendingCourseId = null); load() },
                onFailure = { error -> _uiState.value = _uiState.value.copy(pendingCourseId = null, error = error.message) },
            )
        }
    }
}
