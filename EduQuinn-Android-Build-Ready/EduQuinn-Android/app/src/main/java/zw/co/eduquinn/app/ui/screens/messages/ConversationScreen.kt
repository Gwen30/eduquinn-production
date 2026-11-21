package zw.co.eduquinn.app.ui.screens.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.ConversationMessage
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native port of a conversation thread (messages.html's .chat-panel) — chat bubbles + compose, no polling (matches the website's own on-load/on-send-only refresh). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(app: EduQuinnApplication, navController: NavHostController, conversationId: String) {
    val viewModel: ConversationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ConversationViewModel(conversationId, app.services.messagingRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = SessionManager.user.collectAsState().value?.id
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.conversation?.instructorName ?: "Conversation", style = MaterialTheme.typography.titleMedium)
                        uiState.conversation?.courseTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f))
                uiState.error != null && uiState.messages.isEmpty() ->
                    ErrorState(uiState.error ?: "Could not load this conversation.", modifier = Modifier.weight(1f), onRetry = viewModel::load)
                uiState.messages.isEmpty() -> Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No messages yet. Start the conversation below.", style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(message = message, isMine = message.senderId == currentUserId)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Write a message to your instructor...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (draft.isNotBlank() && !uiState.isSending) {
                            viewModel.sendMessage(draft)
                            draft = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (uiState.isSending) "Sending…" else "Send message") }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ConversationMessage, isMine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Card {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatMessageTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun formatMessageTime(isoDate: String): String = try {
    val parsed = java.time.Instant.parse(isoDate)
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date.from(parsed))
} catch (_: Exception) {
    ""
}
