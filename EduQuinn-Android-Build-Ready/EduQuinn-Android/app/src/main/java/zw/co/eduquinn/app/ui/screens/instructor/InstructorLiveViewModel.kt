package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.AttendanceRow
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.model.InstructorLiveClass
import zw.co.eduquinn.app.data.model.LiveClassCreateRequest
import zw.co.eduquinn.app.data.repository.InstructorCourseRepository
import zw.co.eduquinn.app.data.repository.InstructorLiveClassRepository

data class InstructorLiveUiState(
    val classes: List<InstructorLiveClass> = emptyList(),
    val courses: List<InstructorCourse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val pendingId: String? = null,
    val saveError: String? = null,
    val attendanceFor: InstructorLiveClass? = null,
    val attendance: List<AttendanceRow>? = null,
    val attendanceError: String? = null,
)

class InstructorLiveViewModel(
    private val repository: InstructorLiveClassRepository,
    private val courseRepository: InstructorCourseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorLiveUiState())
    val uiState: StateFlow<InstructorLiveUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val classesResult = repository.list()
            val coursesResult = courseRepository.list()
            val error = classesResult.exceptionOrNull()?.message
            _uiState.value = _uiState.value.copy(
                classes = classesResult.getOrNull() ?: emptyList(),
                courses = coursesResult.getOrNull() ?: _uiState.value.courses,
                isLoading = false,
                error = error,
            )
        }
    }

    fun schedule(body: LiveClassCreateRequest, onSaved: () -> Unit) {
        _uiState.value = _uiState.value.copy(saveError = null, pendingId = "new")
        viewModelScope.launch {
            repository.create(body).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(pendingId = null)
                    load()
                    onSaved()
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(pendingId = null, saveError = error.message) },
            )
        }
    }

    /** The user's "also allow editing after scheduling" choice — PATCHes status and/or recordingUrl on an already-scheduled class. */
    fun updateClass(liveClass: InstructorLiveClass, status: String?, recordingUrl: String?, onSaved: () -> Unit) {
        _uiState.value = _uiState.value.copy(saveError = null, pendingId = liveClass.id)
        viewModelScope.launch {
            repository.update(liveClass.id, status, recordingUrl).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(pendingId = null)
                    load()
                    onSaved()
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(pendingId = null, saveError = error.message) },
            )
        }
    }

    fun dismissSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }

    fun cancelClass(liveClass: InstructorLiveClass) {
        _uiState.value = _uiState.value.copy(pendingId = liveClass.id)
        viewModelScope.launch {
            repository.cancel(liveClass.id).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(pendingId = null); load() },
                onFailure = { error -> _uiState.value = _uiState.value.copy(pendingId = null, error = error.message) },
            )
        }
    }

    fun viewAttendance(liveClass: InstructorLiveClass) {
        _uiState.value = _uiState.value.copy(attendanceFor = liveClass, attendance = null, attendanceError = null)
        viewModelScope.launch {
            repository.attendance(liveClass.id).fold(
                onSuccess = { rows -> _uiState.value = _uiState.value.copy(attendance = rows) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(attendanceError = error.message) },
            )
        }
    }

    fun dismissAttendance() {
        _uiState.value = _uiState.value.copy(attendanceFor = null, attendance = null, attendanceError = null)
    }
}
