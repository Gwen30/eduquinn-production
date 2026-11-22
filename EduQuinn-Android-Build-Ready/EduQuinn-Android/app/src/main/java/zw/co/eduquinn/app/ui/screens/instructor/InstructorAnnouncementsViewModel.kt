package zw.co.eduquinn.app.ui.screens.instructor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.AnnouncementRow
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.repository.AnnouncementRepository
import zw.co.eduquinn.app.data.repository.InstructorCourseRepository

data class InstructorAnnouncementsUiState(
    val announcements: List<AnnouncementRow> = emptyList(),
    val courses: List<InstructorCourse> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val sending: Boolean = false,
    val sendError: String? = null,
)

class InstructorAnnouncementsViewModel(
    private val repository: AnnouncementRepository,
    private val courseRepository: InstructorCourseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorAnnouncementsUiState())
    val uiState: StateFlow<InstructorAnnouncementsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val announcementsResult = repository.list()
            val coursesResult = courseRepository.list()
            _uiState.value = _uiState.value.copy(
                announcements = announcementsResult.getOrNull() ?: emptyList(),
                courses = coursesResult.getOrNull() ?: _uiState.value.courses,
                isLoading = false,
                error = announcementsResult.exceptionOrNull()?.message,
            )
        }
    }

    fun send(courseId: String, title: String, body: String, emailStudents: Boolean, onSent: () -> Unit) {
        if (title.isBlank() || body.isBlank()) {
            _uiState.value = _uiState.value.copy(sendError = "An announcement needs both a title and a message.")
            return
        }
        _uiState.value = _uiState.value.copy(sending = true, sendError = null)
        viewModelScope.launch {
            repository.create(courseId, title.trim(), body.trim(), emailStudents).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(sending = false)
                    load()
                    onSent()
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(sending = false, sendError = error.message) },
            )
        }
    }

    fun dismissSendError() {
        _uiState.value = _uiState.value.copy(sendError = null)
    }
}
