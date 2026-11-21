package zw.co.eduquinn.app.ui.screens.liveclasses

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.LiveClassMessage

/**
 * Native port of live-class-room.html: a presenter card (avatar/initials,
 * name, "Open video meeting"), a Chat/Participants tab strip, and the
 * in-room chat, polled every 10s — matching the website's own
 * setInterval(loadMessages, 10000) rather than adding websockets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassRoomScreen(app: EduQuinnApplication, navController: NavHostController, classId: String) {
    val viewModel: LiveClassRoomViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LiveClassRoomViewModel(classId, app.services.liveClassRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var showingChat by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    DisposableEffect(classId) {
        onDispose { viewModel.leaveRoom() }
    }

    LaunchedEffect(uiState.joinUrl) {
        uiState.joinUrl?.let {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it))
            viewModel.consumeJoinUrl()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.liveClass?.title ?: "Live class", style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(uiState.liveClass?.courseTitle ?: "EduQuinn Live", uiState.liveClass?.instructorName)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave room")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initialsOf(uiState.liveClass?.instructorName),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(uiState.liveClass?.instructorName ?: "EduQuinn instructor", style = MaterialTheme.typography.titleMedium)
                    if (uiState.hasMeetingUrl) {
                        Button(onClick = viewModel::requestJoin, modifier = Modifier.fillMaxWidth()) {
                            Text("Open video meeting")
                        }
                    } else {
                        Text(
                            uiState.error
                                ?: "The instructor has not added a video meeting URL yet. EduQuinn attendance and chat remain available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showingChat = true }) {
                    Text("Chat", fontWeight = if (showingChat) FontWeight.Bold else FontWeight.Normal)
                }
                TextButton(onClick = { showingChat = false }) {
                    Text("Participants", fontWeight = if (!showingChat) FontWeight.Bold else FontWeight.Normal)
                }
            }

            if (showingChat) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { message -> ChatBubble(message) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Write a message…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (draft.isNotBlank() && !uiState.isSending) {
                                viewModel.sendMessage(draft)
                                draft = ""
                            }
                        },
                    ) { Text("Send") }
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Attendance is tracked automatically when learners join this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun initialsOf(name: String?): String =
    (name ?: "?").trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("")

@Composable
private fun ChatBubble(message: LiveClassMessage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        message.name?.let {
            Text(
                "$it${if (message.role == "instructor") " · Instructor" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card {
            Text(message.message, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
