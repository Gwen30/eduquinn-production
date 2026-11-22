package zw.co.eduquinn.app.ui.screens.instructor

import java.io.InputStream
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.AssignmentDraft
import zw.co.eduquinn.app.data.model.CourseAsset
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.model.InstructorLesson
import zw.co.eduquinn.app.data.model.LessonCreateRequest
import zw.co.eduquinn.app.data.model.QuizDraft
import zw.co.eduquinn.app.data.repository.InstructorCourseRepository
import zw.co.eduquinn.app.data.repository.InstructorMediaRepository

data class InstructorMediaUiState(
    val courses: List<InstructorCourse> = emptyList(),
    val isLoadingCourses: Boolean = true,
    val coursesError: String? = null,
    val selectedCourseId: String? = null,
    val assets: List<CourseAsset> = emptyList(),
    val lessons: List<InstructorLesson> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    // 0f..1f while a file is actively being sent; null the rest of the time.
    val uploadProgress: Float? = null,
    val uploadError: String? = null,
    val savingLesson: Boolean = false,
    val lessonError: String? = null,
)

/**
 * Backs the "Media & Lessons" Studio section: pick a course, see its
 * uploaded assets and lessons, upload new media, and add non-quiz lesson
 * types (video/article/assignment/resource — the quiz builder is its own
 * screen, Stage 7 of the plan, kept out of this ViewModel on purpose).
 */
class InstructorMediaViewModel(
    private val courseRepository: InstructorCourseRepository,
    private val mediaRepository: InstructorMediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InstructorMediaUiState())
    val uiState: StateFlow<InstructorMediaUiState> = _uiState.asStateFlow()

    init { loadCourses() }

    fun loadCourses() {
        _uiState.value = _uiState.value.copy(isLoadingCourses = true, coursesError = null)
        viewModelScope.launch {
            courseRepository.list().fold(
                onSuccess = { courses ->
                    val keepSelected = _uiState.value.selectedCourseId?.takeIf { id -> courses.any { it.id == id } }
                    val autoSelected = keepSelected ?: courses.firstOrNull()?.id
                    _uiState.value = _uiState.value.copy(courses = courses, isLoadingCourses = false, coursesError = null, selectedCourseId = autoSelected)
                    autoSelected?.let { loadCourseDetail(it) }
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoadingCourses = false, coursesError = error.message) },
            )
        }
    }

    fun selectCourse(courseId: String) {
        if (courseId == _uiState.value.selectedCourseId) return
        _uiState.value = _uiState.value.copy(selectedCourseId = courseId, assets = emptyList(), lessons = emptyList())
        loadCourseDetail(courseId)
    }

    private fun loadCourseDetail(courseId: String) {
        _uiState.value = _uiState.value.copy(isLoadingDetail = true, detailError = null)
        viewModelScope.launch {
            val assetsResult = mediaRepository.listAssets(courseId)
            val lessonsResult = mediaRepository.listLessons(courseId)
            val error = assetsResult.exceptionOrNull()?.message ?: lessonsResult.exceptionOrNull()?.message
            _uiState.value = _uiState.value.copy(
                assets = assetsResult.getOrNull() ?: _uiState.value.assets,
                lessons = lessonsResult.getOrNull() ?: _uiState.value.lessons,
                isLoadingDetail = false,
                detailError = error,
            )
        }
    }

    /**
     * Runs the full 3-step signed-upload flow for the currently selected
     * course: request a ticket, stream the bytes to it, then confirm. The
     * caller (the screen) resolves the picked content Uri down to a plain
     * [openStream]/mimeType/sizeBytes/displayName first, so this ViewModel
     * never needs to hold a Context.
     */
    fun uploadAsset(kind: String, displayName: String, mimeType: String, sizeBytes: Long, openStream: () -> InputStream) {
        val courseId = _uiState.value.selectedCourseId ?: return
        _uiState.value = _uiState.value.copy(uploadProgress = 0f, uploadError = null)
        viewModelScope.launch {
            val ticketResult = mediaRepository.requestUploadTicket(courseId, kind, displayName)
            val ticket = ticketResult.getOrNull()
            if (ticket == null) {
                _uiState.value = _uiState.value.copy(uploadProgress = null, uploadError = ticketResult.exceptionOrNull()?.message ?: "Could not start the upload.")
                return@launch
            }
            val uploadResult = mediaRepository.uploadBytes(ticket, mimeType, sizeBytes, openStream) { fraction ->
                _uiState.value = _uiState.value.copy(uploadProgress = fraction)
            }
            if (uploadResult.isFailure) {
                _uiState.value = _uiState.value.copy(uploadProgress = null, uploadError = uploadResult.exceptionOrNull()?.message ?: "The upload failed.")
                return@launch
            }
            val confirmResult = mediaRepository.confirmAsset(
                courseId = courseId,
                assetId = ticket.assetId,
                name = displayName,
                kind = kind,
                mimeType = mimeType,
                storedName = ticket.path,
                sizeBytes = sizeBytes,
            )
            _uiState.value = _uiState.value.copy(uploadProgress = null)
            if (confirmResult.isFailure) {
                _uiState.value = _uiState.value.copy(uploadError = confirmResult.exceptionOrNull()?.message ?: "Could not confirm the upload.")
            } else {
                loadCourseDetail(courseId)
            }
        }
    }

    fun dismissUploadError() {
        _uiState.value = _uiState.value.copy(uploadError = null)
    }

    fun addLesson(
        title: String,
        lessonType: String,
        body: String,
        durationSeconds: Int,
        isPreview: Boolean,
        assetId: String?,
        assignment: AssignmentDraft?,
        quiz: QuizDraft?,
        onSaved: () -> Unit,
    ) {
        val courseId = _uiState.value.selectedCourseId ?: return
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(lessonError = "Give the lesson a title.")
            return
        }
        _uiState.value = _uiState.value.copy(savingLesson = true, lessonError = null)
        val nextPosition = _uiState.value.lessons.size + 1
        viewModelScope.launch {
            mediaRepository.createLesson(
                courseId,
                LessonCreateRequest(
                    title = title.trim(),
                    lessonType = lessonType,
                    position = nextPosition,
                    assetId = assetId,
                    body = body,
                    durationSeconds = durationSeconds,
                    isPreview = isPreview,
                    assignment = assignment,
                    quiz = quiz,
                ),
            ).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(savingLesson = false)
                    loadCourseDetail(courseId)
                    onSaved()
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(savingLesson = false, lessonError = error.message) },
            )
        }
    }

    fun dismissLessonError() {
        _uiState.value = _uiState.value.copy(lessonError = null)
    }
}
