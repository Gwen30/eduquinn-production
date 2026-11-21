package zw.co.eduquinn.app.ui.screens.liveclasses

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.LiveClass
import zw.co.eduquinn.app.data.network.NetworkModule
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.SignedOutPrompt
import zw.co.eduquinn.app.ui.nav.Destinations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Same tabs, in the same order, as live-classes.html's #liveTabs — no
// "Cancelled" tab on the student-facing page.
private val STATUS_TABS = listOf(
    LIVE_CLASS_FILTER_ALL to "All classes",
    "live" to "Live now",
    "upcoming" to "Upcoming",
    "completed" to "Completed",
)

/** Native port of live-classes.html — status-tab list, search, reserve/cancel/join. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveClassesScreen(app: EduQuinnApplication, navController: NavHostController) {
    val viewModel: LiveClassesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LiveClassesViewModel(app.services.liveClassRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live classes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            !SessionManager.isLoggedIn -> SignedOutPrompt(
                onSignIn = { navController.navigate(Destinations.login(Destinations.LIVE_CLASSES)) },
                onCreateAccount = { navController.navigate(Destinations.REGISTER) },
                modifier = Modifier.padding(padding),
            )
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.allClasses.isEmpty() ->
                ErrorState(uiState.error ?: "Could not load live classes.", modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("LIVE LEARNING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Learn with instructors in real time", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Reserve a seat, join interactive lessons and revisit completed sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search live classes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(STATUS_TABS) { (value, label) ->
                        FilterChip(
                            selected = uiState.statusFilter == value,
                            onClick = { viewModel.onStatusFilterSelect(value) },
                            label = { Text(label) },
                        )
                    }
                }

                if (uiState.visibleClasses.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("No matching live classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Try another filter or search term.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.visibleClasses, key = { it.id }) { liveClass ->
                            LiveClassCard(
                                liveClass = liveClass,
                                isPending = uiState.pendingReservationId == liveClass.id,
                                onToggleReservation = { viewModel.toggleReservation(liveClass) },
                                onJoin = { navController.navigate(Destinations.liveClassRoom(liveClass.id)) },
                                onOpenExternal = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveClassCard(
    liveClass: LiveClass,
    isPending: Boolean,
    onToggleReservation: () -> Unit,
    onJoin: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // live-card-top: icon chip + status pill, on the paper-colored strip.
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                StatusPill(liveClass.status)
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    liveClass.courseTitle ?: "EduQuinn Live",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(liveClass.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MetaRow(Icons.Filled.Person, liveClass.instructorName ?: "EduQuinn instructor")
                    MetaRow(Icons.Filled.CalendarMonth, formatClassDateTime(liveClass.startsAt))
                    MetaRow(Icons.Filled.Schedule, "${liveClass.durationMinutes} minutes")
                    MetaRow(Icons.Filled.Group, "${liveClass.reservedCount}/${liveClass.capacity} seats reserved")
                }

                liveClass.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (liveClass.status) {
                        // live-classes.html shows "Join now" for any live
                        // session, regardless of whether the student
                        // reserved a seat beforehand.
                        "live" -> Button(onClick = onJoin, modifier = Modifier.weight(1f)) { Text("Join now") }
                        "upcoming" -> if (liveClass.reserved) {
                            OutlinedButton(onClick = onToggleReservation, enabled = !isPending, modifier = Modifier.weight(1f)) {
                                Text(if (isPending) "Cancelling…" else "Cancel reservation")
                            }
                        } else {
                            Button(onClick = onToggleReservation, enabled = !isPending, modifier = Modifier.weight(1f)) {
                                Text(if (isPending) "Reserving…" else "Reserve seat")
                            }
                        }
                    }
                    if (liveClass.status == "completed" && liveClass.recordingUrl != null) {
                        OutlinedButton(onClick = { onOpenExternal(liveClass.recordingUrl) }, modifier = Modifier.weight(1f)) {
                            Text("Watch recording")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onOpenExternal("${NetworkModule.BASE_URL}api/live-classes/${liveClass.id}/calendar.ics") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add to calendar") }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Colors match live-classes.html's `.live-status` rules exactly: green by
 * default (live), amber for upcoming, grey for completed.
 */
@Composable
private fun StatusPill(status: String) {
    val (bg, fg, label) = when (status) {
        "upcoming" -> Triple(Color(0xFFFFF0D7), Color(0xFF8B5A00), "Upcoming")
        "completed" -> Triple(Color(0xFFEEEEEE), Color(0xFF555555), "Completed")
        "cancelled" -> Triple(Color(0xFFFEE8E8), Color(0xFFA12626), "Cancelled")
        else -> Triple(Color(0xFFE8F6EC), Color(0xFF267842), "Live now")
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
    }
}

private fun formatClassDateTime(isoDate: String): String = try {
    val parsed = java.time.Instant.parse(isoDate)
    SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.getDefault()).format(Date.from(parsed))
} catch (_: Exception) {
    isoDate
}
