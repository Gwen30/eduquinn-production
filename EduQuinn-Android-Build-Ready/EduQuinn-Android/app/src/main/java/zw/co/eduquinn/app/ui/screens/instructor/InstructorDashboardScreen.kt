package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

@Composable
fun InstructorDashboardScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    onManageCourses: () -> Unit,
) {
    val viewModel: InstructorDashboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InstructorDashboardViewModel(app.services.instructorCourseRepository, app.services.instructorEarningsRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<InstructorCourse?>(null) }

    when {
        uiState.isLoading -> LoadingState()
        uiState.error != null && uiState.courses.isEmpty() -> ErrorState(uiState.error ?: "Could not load your dashboard.", onRetry = viewModel::load)
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            // A plain 2x2 Row/Column grid rather than a nested
            // LazyVerticalGrid — there are always exactly 4 tiles, and a
            // lazy layout nested inside another lazy layout's item needs
            // its own bounded height or it crashes, which a fixed-size
            // grid doesn't need to deal with.
            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Your courses", "${uiState.courses.size}", modifier = Modifier.weight(1f))
                        MetricTile("Published", "${uiState.publishedCount}", modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile("Available earnings", formatCents(uiState.earnings?.summary?.available ?: 0), modifier = Modifier.weight(1f))
                        MetricTile("Lifetime earnings", formatCents(uiState.earnings?.summary?.lifetime ?: 0), modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)) {
                    Text("Your courses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (uiState.courses.isEmpty()) {
                item { Text("You haven't created a course yet. Head to Courses to start one.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(uiState.courses, key = { it.id }) { course ->
                CourseRow(
                    course = course,
                    pending = uiState.pendingCourseId == course.id,
                    onManage = onManageCourses,
                    onDeleteRequest = { pendingDelete = course },
                )
            }
        }
    }

    pendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${course.title}\"?") },
            text = {
                Text(
                    "If any students are enrolled, this course will be archived instead of deleted outright, so their access and records are preserved.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCourse(course); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CourseRow(
    course: InstructorCourse,
    pending: Boolean,
    onManage: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(course.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    course.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (pending) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            } else {
                IconButton(onClick = onManage) { Icon(Icons.Filled.Edit, contentDescription = "Manage \"${course.title}\"") }
                IconButton(onClick = onDeleteRequest) { Icon(Icons.Filled.Delete, contentDescription = "Delete \"${course.title}\"") }
            }
        }
    }
}
