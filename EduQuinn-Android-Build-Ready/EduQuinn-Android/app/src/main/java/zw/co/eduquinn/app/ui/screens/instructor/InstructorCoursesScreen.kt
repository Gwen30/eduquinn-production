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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.ui.components.CATALOG_CATEGORIES
import zw.co.eduquinn.app.ui.components.CATALOG_STAGES
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorCoursesScreen(app: EduQuinnApplication) {
    val viewModel: InstructorCoursesViewModel = viewModel(
        factory = viewModelFactory { initializer { InstructorCoursesViewModel(app.services.instructorCourseRepository) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<InstructorCourse?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InstructorCourse?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { creatingNew = true }) { Icon(Icons.Filled.Add, contentDescription = "New course") }
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.error != null && uiState.courses.isEmpty() -> ErrorState(uiState.error ?: "Could not load your courses.", onRetry = viewModel::load)
            uiState.courses.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("You haven't created a course yet. Tap + to start your first one.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(uiState.courses, key = { it.id }) { course ->
                    CourseListRow(
                        course = course,
                        pending = uiState.pendingCourseId == course.id,
                        onEdit = { editing = course },
                        onDeleteRequest = { pendingDelete = course },
                        onSubmitForReview = { viewModel.submitForReview(course) },
                    )
                }
            }
        }
    }

    if (creatingNew) {
        CourseFormSheet(
            existing = null,
            saveError = uiState.saveError,
            saving = uiState.pendingCourseId == "new",
            onDismiss = { creatingNew = false; viewModel.dismissSaveError() },
            onSave = { title, subtitle, description, level, category, price ->
                viewModel.saveCourse(null, title, subtitle, description, level, category, price) { creatingNew = false }
            },
        )
    }
    editing?.let { course ->
        CourseFormSheet(
            existing = course,
            saveError = uiState.saveError,
            saving = uiState.pendingCourseId == course.id,
            onDismiss = { editing = null; viewModel.dismissSaveError() },
            onSave = { title, subtitle, description, level, category, price ->
                viewModel.saveCourse(course.id, title, subtitle, description, level, category, price) { editing = null }
            },
        )
    }
    pendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${course.title}\"?") },
            text = {
                Text("If any students are enrolled, this course will be archived instead of deleted outright, so their access and records are preserved.")
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
private fun CourseListRow(
    course: InstructorCourse,
    pending: Boolean,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    onSubmitForReview: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
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
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit \"${course.title}\"") }
                    IconButton(onClick = onDeleteRequest) { Icon(Icons.Filled.Delete, contentDescription = "Delete \"${course.title}\"") }
                }
            }
            // "Submit for review" only makes sense for a draft, and only
            // moves the needle once the backend learns the status field on
            // the update endpoint (see CourseUpsertRequest's doc comment) —
            // until then this just surfaces whatever error the server sends.
            if (course.status == "draft" && !pending) {
                OutlinedButton(onClick = onSubmitForReview, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Submit for review")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseFormSheet(
    existing: InstructorCourse?,
    saveError: String?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, subtitle: String, description: String, educationLevel: String, category: String, priceDollars: Double) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var subtitle by remember { mutableStateOf(existing?.subtitle.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var educationLevel by remember { mutableStateOf(existing?.educationLevel ?: CATALOG_STAGES.first().value) }
    var category by remember { mutableStateOf(existing?.category ?: CATALOG_CATEGORIES.first().label) }
    var priceText by remember { mutableStateOf(existing?.let { (it.priceCents / 100.0).let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() } } ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (existing == null) "New course" else "Edit course",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Subtitle") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryDropdown(category = category, onCategoryChange = { category = it })
            EducationLevelDropdown(level = educationLevel, onLevelChange = { educationLevel = it })
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price (USD)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (saveError != null) {
                Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = {
                        onSave(title, subtitle, description, educationLevel, category, priceText.toDoubleOrNull() ?: 0.0)
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text(if (existing == null) "Create" else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(category: String, onCategoryChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CATALOG_CATEGORIES.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onCategoryChange(option.label); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationLevelDropdown(level: String, onLevelChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = CATALOG_STAGES.firstOrNull { it.value == level }?.label ?: level
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Education level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CATALOG_STAGES.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onLevelChange(option.value); expanded = false })
            }
        }
    }
}
