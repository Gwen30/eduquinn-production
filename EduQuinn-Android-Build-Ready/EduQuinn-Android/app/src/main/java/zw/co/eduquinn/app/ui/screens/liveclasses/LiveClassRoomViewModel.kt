package zw.co.eduquinn.app.ui.screens.liveclasses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.data.model.LiveClass
import zw.co.eduquinn.app.data.model.LiveClassMessage
import zw.co.eduquinn.app.data.repository.LiveClassRepository

private const val CHAT_POLL_INTERVAL_MS = 10_000L

data class LiveClassRoomUiState(
    val liveClass: LiveClass? = null,
    val messages: List<LiveClassMessage> = emptyList(),
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    // Set once the initial, silent join() call on room entry resolves —
    // mirrors live-class-room.html's initRoom(), which joins immediately
    // on page load and only shows "Open video meeting" if a URL came
    // back, with the provider notice otherwise.
    val hasMeetingUrl: Boolean = false,
    // Non-null for exactly one frame, consumed by a LaunchedEffect that
    // opens the external meeting URL — same one-shot pattern as
    // OrderStatusViewModel's paynowRetryUrl.
    val joinUrl: String? = null,
)

class LiveClassRoomViewModel(private val classId: String, private val repository: LiveClassRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveClassRoomUiState())
    val uiState: StateFlow<LiveClassRoomUiState> = _uiState.asStateFlow()

    init {
        load()
        startPolling()
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val listResult = repository.list()
            val liveClass = listResult.getOrNull()?.firstOrNull { it.id == classId }
            refreshMessages()
            // Join silently on entry, same as the website does, so the
            // "Open video meeting" button only appears once we actually
            // know a meeting URL exists.
            val hasUrl = repository.join(classId).getOrNull()?.let { it.joinUrl ?: it.roomUrl } != null
            _uiState.value = _uiState.value.copy(
                liveClass = liveClass,
                isLoading = false,
                hasMeetingUrl = hasUrl,
                error = if (listResult.isFailure) listResult.exceptionOrNull()?.message else null,
            )
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                delay(CHAT_POLL_INTERVAL_MS)
                refreshMessages()
            }
        }
    }

    private suspend fun refreshMessages() {
        repository.messages(classId).onSuccess { messages ->
            _uiState.value = _uiState.value.copy(messages = messages)
        }
    }

    fun requestJoin() {
        viewModelScope.launch {
            repository.join(classId).fold(
                onSuccess = { response ->
                    val url = response.joinUrl ?: response.roomUrl
                    if (url != null) {
                        _uiState.value = _uiState.value.copy(joinUrl = url)
                    } else {
                        _uiState.value = _uiState.value.copy(error = "This class doesn't have a meeting link yet.")
                    }
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(error = error.message) },
            )
        }
    }

    fun consumeJoinUrl() {
        _uiState.value = _uiState.value.copy(joinUrl = null)
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        _uiState.value = _uiState.value.copy(isSending = true)
        viewModelScope.launch {
            repository.sendMessage(classId, message).fold(
                onSuccess = {
                    refreshMessages()
                    _uiState.value = _uiState.value.copy(isSending = false)
                },
                onFailure = { error -> _uiState.value = _uiState.value.copy(isSending = false, error = error.message) },
            )
        }
    }

    fun leaveRoom() {
        viewModelScope.launch { repository.leave(classId) }
    }
}
