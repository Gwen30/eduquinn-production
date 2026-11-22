package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.AttendanceRow
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.model.InstructorLiveClass
import zw.co.eduquinn.app.data.model.LiveClassCreateRequest
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

private val SESSION_TYPES = listOf("group_class" to "Group class", "one_on_one" to "One-on-one", "workshop" to "Workshop", "office_hours" to "Office hours")
private val LIVE_STATUSES = listOf("scheduled" to "Scheduled", "completed" to "Completed", "cancelled" to "Cancelled")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorLiveScreen(app: EduQuinnApplication) {
    val viewModel: InstructorLiveViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InstructorLiveViewModel(app.services.instructorLiveClassRepository, app.services.instructorCourseRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var scheduling by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<InstructorLiveClass?>(null) }
    var cancelling by remember { mutableStateOf<InstructorLiveClass?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { scheduling = true }) { Icon(Icons.Filled.Add, contentDescription = "Schedule a class") }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.error != null && uiState.classes.isEmpty() -> ErrorState(uiState.error ?: "Could not load your live classes.", onRetry = viewModel::load)
            uiState.classes.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No live classes scheduled yet. Tap + to schedule one.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.classes, key = { it.id }) { liveClass ->
                    LiveClassRow(
                        liveClass = liveClass,
                        pending = uiState.pendingId == liveClass.id,
                        onManage = { managing = liveClass },
                        onAttendance = { viewModel.viewAttendance(liveClass) },
                        onCancel = { cancelling = liveClass },
                    )
                }
            }
        }
    }

    if (scheduling) {
        ScheduleClassSheet(
            courses = uiState.courses,
            saving = uiState.pendingId == "new",
            error = uiState.saveError,
            onDismiss = { scheduling = false; viewModel.dismissSaveError() },
            onSchedule = { body -> viewModel.schedule(body) { scheduling = false } },
        )
    }
    managing?.let { liveClass ->
        ManageClassSheet(
            liveClass = liveClass,
            saving = uiState.pendingId == liveClass.id,
            error = uiState.saveError,
            onDismiss = { managing = null; viewModel.dismissSaveError() },
            onSave = { status, recordingUrl -> viewModel.updateClass(liveClass, status, recordingUrl) { managing = null } },
        )
    }
    cancelling?.let { liveClass ->
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel \"${liveClass.title}\"?") },
            text = { Text("Reserved students will see this session as cancelled.") },
            confirmButton = { TextButton(onClick = { viewModel.cancelClass(liveClass); cancelling = null }) { Text("Cancel class") } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep it") } },
        )
    }
    uiState.attendanceFor?.let { liveClass ->
        AttendanceDialog(liveClass = liveClass, attendance = uiState.attendance, error = uiState.attendanceError, onDismiss = viewModel::dismissAttendance)
    }
}

@Composable
private fun LiveClassRow(
    liveClass: InstructorLiveClass,
    pending: Boolean,
    onManage: () -> Unit,
    onAttendance: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(liveClass.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${formatClassDateTime(liveClass.startsAt)} · ${liveClass.reservedCount}/${liveClass.capacity} reserved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                liveClass.status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onManage) { Text("Manage") }
                    OutlinedButton(onClick = onAttendance) { Text("Attendance") }
                    if (liveClass.status == "scheduled") {
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleClassSheet(
    courses: List<InstructorCourse>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSchedule: (LiveClassCreateRequest) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var courseId by remember { mutableStateOf<String?>(null) }
    var sessionType by remember { mutableStateOf(SESSION_TYPES.first().first) }
    var startsAt by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("60") }
    var capacityText by remember { mutableStateOf("50") }
    var meetingProvider by remember { mutableStateOf("") }
    var joinUrl by remember { mutableStateOf("") }
    var hostUrl by remember { mutableStateOf("") }
    var allowRecording by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Schedule a class", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CourseSelectDropdown(courses = courses, selectedId = courseId, onSelect = { courseId = it }, allowNone = true)
            SessionTypeDropdown(type = sessionType, onTypeChange = { sessionType = it })
            OutlinedTextField(
                value = startsAt,
                onValueChange = { startsAt = it },
                label = { Text("Starts at") },
                placeholder = { Text("2026-12-01T18:00") },
                supportingText = { Text("24-hour date and time, in your local timezone.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Duration (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it },
                    label = { Text("Capacity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = meetingProvider, onValueChange = { meetingProvider = it }, label = { Text("Meeting provider") }, placeholder = { Text("Zoom, Google Meet…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = joinUrl, onValueChange = { joinUrl = it }, label = { Text("Join URL (for students)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hostUrl, onValueChange = { hostUrl = it }, label = { Text("Host URL (for you)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Allow recording", modifier = Modifier.weight(1f))
                Switch(checked = allowRecording, onCheckedChange = { allowRecording = it })
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = {
                        onSchedule(
                            LiveClassCreateRequest(
                                title = title.trim(),
                                description = description.trim(),
                                courseId = courseId,
                                startsAt = isoWithSeconds(startsAt),
                                durationMinutes = durationText.toIntOrNull() ?: 60,
                                capacity = capacityText.toIntOrNull() ?: 50,
                                sessionType = sessionType,
                                meetingProvider = meetingProvider.ifBlank { "external" },
                                joinUrl = joinUrl,
                                hostUrl = hostUrl,
                                allowRecording = allowRecording,
                            ),
                        )
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Schedule")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageClassSheet(
    liveClass: InstructorLiveClass,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (status: String?, recordingUrl: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var status by remember { mutableStateOf(liveClass.status) }
    var recordingUrl by remember { mutableStateOf(liveClass.recordingUrl.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Manage \"${liveClass.title}\"", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            StatusDropdown(status = status, onStatusChange = { status = it })
            OutlinedTextField(
                value = recordingUrl,
                onValueChange = { recordingUrl = it },
                label = { Text("Recording URL") },
                placeholder = { Text("Paste a link once the recording is ready") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = {
                        val newStatus = status.takeIf { it != liveClass.status }
                        val newRecordingUrl = recordingUrl.trim().ifBlank { null }?.takeIf { it != liveClass.recordingUrl }
                        onSave(newStatus, newRecordingUrl)
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AttendanceDialog(
    liveClass: InstructorLiveClass,
    attendance: List<AttendanceRow>?,
    error: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance — ${liveClass.title}") },
        text = {
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                attendance == null -> CircularProgressIndicator()
                attendance.isEmpty() -> Text("No one has reserved this session yet.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    attendance.forEach { row ->
                        Column {
                            Text(row.studentName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (row.joinedAt != null) "Joined · ${row.attendanceMinutes} min" else "Reserved, not yet joined",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseSelectDropdown(courses: List<InstructorCourse>, selectedId: String?, onSelect: (String?) -> Unit, allowNone: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val current = courses.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.title ?: "General (no course)",
            onValueChange = {},
            readOnly = true,
            label = { Text("Course") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) DropdownMenuItem(text = { Text("General (no course)") }, onClick = { onSelect(null); expanded = false })
            courses.forEach { course ->
                DropdownMenuItem(text = { Text(course.title) }, onClick = { onSelect(course.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTypeDropdown(type: String, onTypeChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = SESSION_TYPES.firstOrNull { it.first == type }?.second ?: type
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Session type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SESSION_TYPES.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onTypeChange(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(status: String, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = LIVE_STATUSES.firstOrNull { it.first == status }?.second ?: status
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LIVE_STATUSES.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onStatusChange(value); expanded = false })
            }
        }
    }
}

/** Appends seconds (and a best-effort local offset isn't needed — the backend just needs a parseable instant) so a plain "2026-12-01T18:00" entry becomes "2026-12-01T18:00:00". */
private fun isoWithSeconds(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.count { it == ':' } >= 2) trimmed else "$trimmed:00"
}

private fun formatClassDateTime(isoDate: String): String = try {
    val parsed = java.time.Instant.parse(isoDate)
    SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.getDefault()).format(Date.from(parsed))
} catch (_: Exception) {
    try {
        val parsed = java.time.LocalDateTime.parse(isoDate)
        SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.getDefault()).format(Date.from(parsed.atZone(java.time.ZoneId.systemDefault()).toInstant()))
    } catch (_: Exception) {
        isoDate
    }
}
