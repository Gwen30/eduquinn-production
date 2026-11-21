package zw.co.eduquinn.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import zw.co.eduquinn.app.data.model.Assessment
import zw.co.eduquinn.app.data.model.AssessmentResult
import zw.co.eduquinn.app.data.model.LearningLesson
import zw.co.eduquinn.app.data.repository.LearningRepository

data class LessonPlayerUiState(
    val lessons: List<LearningLesson> = emptyList(),
    val assessments: List<Assessment> = emptyList(),
    val currentLessonId: String? = null,
    val courseProgress: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val currentLesson: LearningLesson? get() = lessons.firstOrNull { it.id == currentLessonId }
    val currentAssessment: Assessment?
        get() = currentLesson?.let { lesson -> assessments.firstOrNull { it.lessonId == lesson.id } }
}

class LessonPlayerViewModel(
    private val courseId: String,
    initialLessonId: String,
    private val learningRepository: LearningRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonPlayerUiState(currentLessonId = initialLessonId))
    val uiState: StateFlow<LessonPlayerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val lessonsDeferred = async { learningRepository.lessons(courseId) }
            val assessmentsDeferred = async { learningRepository.assessments(courseId) }
            val lessonsResult = lessonsDeferred.await()
            val assessmentsResult = assessmentsDeferred.await()

            lessonsResult.fold(
                onSuccess = { lessons ->
                    val sorted = lessons.sortedBy { it.position }
                    val progress = if (sorted.isEmpty()) 0 else (sorted.count { it.completed } * 100) / sorted.size
                    _uiState.value = _uiState.value.copy(
                        lessons = sorted,
                        assessments = assessmentsResult.getOrNull() ?: emptyList(),
                        currentLessonId = _uiState.value.currentLessonId ?: sorted.firstOrNull()?.id,
                        courseProgress = progress,
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                },
            )
        }
    }

    fun selectLesson(lessonId: String) {
        _uiState.value = _uiState.value.copy(currentLessonId = lessonId)
    }

    /** Called by content composables (video periodic ticks, article/resource/assignment "Mark complete"). */
    fun reportProgress(lessonId: String, completed: Boolean, positionSeconds: Int = 0) {
        viewModelScope.launch {
            val result = learningRepository.postProgress(lessonId, completed, positionSeconds)
            result.onSuccess { pct ->
                _uiState.value = _uiState.value.copy(
                    lessons = _uiState.value.lessons.map {
                        if (it.id == lessonId) it.copy(completed = completed, positionSeconds = positionSeconds) else it
                    },
                    courseProgress = pct,
                )
            }
        }
    }

    fun submitQuiz(assessmentId: String, lessonId: String, answers: List<JsonElement>, onResult: (AssessmentResult) -> Unit) {
        viewModelScope.launch {
            val result = learningRepository.submitAssessment(assessmentId, answers)
            result.fold(
                onSuccess = { assessmentResult ->
                    // The submit endpoint only records the attempt — it never touches
                    // lesson_progress, so a passing attempt has to explicitly mark the
                    // lesson complete for the course's overall % (and certificate
                    // eligibility) to ever reflect it.
                    if (assessmentResult.passed) {
                        reportProgress(lessonId, completed = true)
                    } else {
                        // Refresh attemptsUsed on the cached assessment so the UI reflects
                        // the used attempt even without completing the lesson.
                        _uiState.value = _uiState.value.copy(
                            assessments = _uiState.value.assessments.map {
                                if (it.id == assessmentId) it.copy(attemptsUsed = assessmentResult.attemptsUsed) else it
                            },
                        )
                    }
                    onResult(assessmentResult)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                },
            )
        }
    }
}
