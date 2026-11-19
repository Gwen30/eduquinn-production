package zw.co.eduquinn.app.ui.screens.certificate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Certificate
import zw.co.eduquinn.app.data.repository.CertificateRepository
import zw.co.eduquinn.app.data.repository.CourseRepository
import zw.co.eduquinn.app.data.repository.SessionManager

data class CertificateUiState(
    val certificate: Certificate? = null,
    val courseTitle: String = "",
    val instructorName: String? = null,
    val studentName: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
)

class CertificateViewModel(
    private val courseId: String,
    private val certificateRepository: CertificateRepository,
    private val courseRepository: CourseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CertificateUiState())
    val uiState: StateFlow<CertificateUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // Idempotent on the backend: reopening this screen never creates a duplicate certificate.
            val certificateDeferred = async { certificateRepository.issue(courseId) }
            val courseDeferred = async { courseRepository.courseDetail(courseId) }
            val certificateResult = certificateDeferred.await()
            val courseResult = courseDeferred.await()

            certificateResult.fold(
                onSuccess = { certificate ->
                    _uiState.value = _uiState.value.copy(
                        certificate = certificate,
                        courseTitle = courseResult.getOrNull()?.course?.title ?: "",
                        instructorName = courseResult.getOrNull()?.course?.instructorName,
                        studentName = SessionManager.user.value?.name ?: "Learner",
                        isLoading = false,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                },
            )
        }
    }
}
