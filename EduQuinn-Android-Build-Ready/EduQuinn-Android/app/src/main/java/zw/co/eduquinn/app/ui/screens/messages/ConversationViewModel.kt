package zw.co.eduquinn.app.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.Conversation
import zw.co.eduquinn.app.data.model.ConversationMessage
import zw.co.eduquinn.app.data.repository.MessagingRepository

data class ConversationUiState(
    val conversation: Conversation? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
)

class ConversationViewModel(
    private val conversationId: String,
    private val repository: MessagingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // messages.html's chat-head shows the counterpart name/course
            // from the thread list, not from the messages endpoint — so
            // fetch the conversation itself the same way
            // LiveClassRoomViewModel finds its class.
            val conversation = repository.conversations().getOrNull()?.firstOrNull { it.id == conversationId }
            repository.messages(conversationId).fold(
                onSuccess = { messages ->
                    _uiState.value = _uiState.value.copy(conversation = conversation, messages = messages, isLoading = false, error = null)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(conversation = conversation, isLoading = false, error = error.message) },
            )
        }
    }

    fun sendMessage(body: String) {
        if (body.isBlank()) return
        _uiState.value = _uiState.value.copy(isSending = true)
        viewModelScope.launch {
            repository.sendMessage(conversationId, body).fold(
                onSuccess = {
                    repository.messages(conversationId).onSuccess { messages ->
                        _uiState.value = _uiState.value.copy(messages = messages)
                    }
                    _uiState.value = _uiState.value.copy(isSending = false)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isSending = false, error = error.message) },
            )
        }
    }
}
