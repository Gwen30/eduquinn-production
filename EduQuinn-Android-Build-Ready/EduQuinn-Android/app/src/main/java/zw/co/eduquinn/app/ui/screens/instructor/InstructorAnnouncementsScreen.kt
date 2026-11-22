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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.AnnouncementRow
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorAnnouncementsScreen(app: EduQuinnApplication) {
    val viewModel: InstructorAnnouncementsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InstructorAnnouncementsViewModel(app.services.announcementRepository, app.services.instructorCourseRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var composing by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (uiState.courses.isNotEmpty()) {
                FloatingActionButton(onClick = { composing = true }) { Icon(Icons.Filled.Add, contentDescription = "New announcement") }
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.error != null && uiState.announcements.isEmpty() -> ErrorState(uiState.error ?: "Could not load your announcements.", onRetry = viewModel::load)
            uiState.courses.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Create a course first — announcements go out to a course's enrolled students.", style = MaterialTheme.typography.bodyMedium)
            }
            uiState.announcements.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("No announcements yet. Tap + to let your students know something.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.announcements, key = { it.id }) { announcement -> AnnouncementCard(announcement) }
            }
        }
    }

    if (composing) {
        ComposeAnnouncementSheet(
            courses = uiState.courses,
            sending = uiState.sending,
            error = uiState.sendError,
            onDismiss = { composing = false; viewModel.dismissSendError() },
            onSend = { courseId, title, body, emailStudents -> viewModel.send(courseId, title, body, emailStudents) { composing = false } },
        )
    }
}

@Composable
private fun AnnouncementCard(announcement: AnnouncementRow) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(announcement.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            announcement.courseTitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(announcement.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeAnnouncementSheet(
    courses: List<InstructorCourse>,
    sending: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSend: (courseId: String, title: String, body: String, emailStudents: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var courseId by remember { mutableStateOf(courses.first().id) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // The backend's own default (emailStudents !== false) is "yes, email them" — matched here.
    var emailStudents by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("New announcement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CourseDropdown(courses = courses, selectedId = courseId, onSelect = { courseId = it })
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = emailStudents, onCheckedChange = { emailStudents = it })
                Text("Also email enrolled students", modifier = Modifier.weight(1f))
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = { onSend(courseId, title, body, emailStudents) },
                    enabled = !sending,
                    modifier = Modifier.weight(1f),
                ) {
                    if (sending) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDropdown(courses: List<InstructorCourse>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = courses.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.title ?: "Choose a course",
            onValueChange = {},
            readOnly = true,
            label = { Text("Course") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            courses.forEach { course ->
                DropdownMenuItem(text = { Text(course.title) }, onClick = { onSelect(course.id); expanded = false })
            }
        }
    }
}
