package zw.co.eduquinn.app.ui.screens.mylearning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Enrolment
import zw.co.eduquinn.app.data.repository.LearningRepository

data class MyLearningUiState(
    val enrolments: List<Enrolment> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Set briefly while resolving which lesson "Continue learning" should
    // open for a given course (needs an extra lessons fetch per tap).
    val openingCourseId: String? = null,
)

class MyLearningViewModel(private val learningRepository: LearningRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MyLearningUiState())
    val uiState: StateFlow<MyLearningUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = learningRepository.enrolments()
            result.fold(
                onSuccess = { enrolments ->
                    _uiState.value = _uiState.value.copy(enrolments = enrolments, isLoading = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                },
            )
        }
    }

    /** Resolves the first-incomplete (or first) lesson id for [courseId], then calls [onResolved]. */
    fun openCourse(courseId: String, onResolved: (lessonId: String) -> Unit) {
        _uiState.value = _uiState.value.copy(openingCourseId = courseId)
        viewModelScope.launch {
            val result = learningRepository.lessons(courseId)
            _uiState.value = _uiState.value.copy(openingCourseId = null)
            result.fold(
                onSuccess = { lessons ->
                    val sorted = lessons.sortedBy { it.position }
                    val target = sorted.firstOrNull { !it.completed } ?: sorted.firstOrNull()
                    target?.let { onResolved(it.id) }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                },
            )
        }
    }
}
