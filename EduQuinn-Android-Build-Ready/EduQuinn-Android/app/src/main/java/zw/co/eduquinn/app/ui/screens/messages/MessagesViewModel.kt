package zw.co.eduquinn.app.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Conversation
import zw.co.eduquinn.app.data.model.Enrolment
import zw.co.eduquinn.app.data.repository.LearningRepository
import zw.co.eduquinn.app.data.repository.MessagingRepository
import zw.co.eduquinn.app.data.repository.SessionManager

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val enrolledCourses: List<Enrolment> = emptyList(),
    val isNewMessageDialogOpen: Boolean = false,
    val isStartingConversation: Boolean = false,
    // Non-null for exactly one frame, consumed by the screen to navigate
    // into the fresh conversation.
    val newConversationId: String? = null,
) {
    // Matches messages.html's renderThreads(q) filter: instructor name or
    // course title, case-insensitive.
    val visibleConversations: List<Conversation>
        get() {
            val q = searchQuery.trim().lowercase()
            if (q.isBlank()) return conversations
            return conversations.filter {
                it.instructorName.lowercase().contains(q) || it.courseTitle?.lowercase()?.contains(q) == true
            }
        }
}

class MessagesViewModel(
    private val messagingRepository: MessagingRepository,
    private val learningRepository: LearningRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        if (!SessionManager.isLoggedIn) {
            _uiState.value = _uiState.value.copy(isLoading = false, conversations = emptyList(), error = null)
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            messagingRepository.conversations().fold(
                onSuccess = { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversations = conversations.sortedByDescending { it.updatedAt },
                        isLoading = false,
                        error = null,
                    )
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isLoading = false, error = error.message) },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun openNewMessageDialog() {
        _uiState.value = _uiState.value.copy(isNewMessageDialogOpen = true)
        viewModelScope.launch {
            learningRepository.enrolments().onSuccess { courses ->
                _uiState.value = _uiState.value.copy(enrolledCourses = courses)
            }
        }
    }

    fun dismissNewMessageDialog() {
        _uiState.value = _uiState.value.copy(isNewMessageDialogOpen = false)
    }

    fun startConversation(courseId: String) {
        _uiState.value = _uiState.value.copy(isStartingConversation = true)
        viewModelScope.launch {
            messagingRepository.startConversation(courseId).fold(
                onSuccess = { conversationId ->
                    _uiState.value = _uiState.value.copy(
                        isStartingConversation = false,
                        isNewMessageDialogOpen = false,
                        newConversationId = conversationId,
                    )
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isStartingConversation = false, error = error.message) },
            )
        }
    }

    fun consumeNewConversationId() {
        _uiState.value = _uiState.value.copy(newConversationId = null)
    }
}
