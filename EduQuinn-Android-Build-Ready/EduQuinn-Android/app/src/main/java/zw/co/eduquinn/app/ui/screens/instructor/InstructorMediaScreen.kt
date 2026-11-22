package zw.co.eduquinn.app.ui.screens.instructor

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.AssignmentDraft
import zw.co.eduquinn.app.data.model.CourseAsset
import zw.co.eduquinn.app.data.model.InstructorCourse
import zw.co.eduquinn.app.data.model.InstructorLesson
import zw.co.eduquinn.app.data.model.QuizDraft
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.screens.instructor.quiz.QuizBuilderSection
import zw.co.eduquinn.app.ui.screens.instructor.quiz.QuizBuilderState

/** The 5 asset "kind" values the real upload form offers — see public/media.js's #mediaKind select, option-for-option. */
private data class MediaKindOption(val value: String, val label: String, val icon: ImageVector)
private val MEDIA_KINDS = listOf(
    MediaKindOption("video", "Video", Icons.Filled.PlayCircle),
    MediaKindOption("document", "Document / PDF", Icons.Filled.Description),
    MediaKindOption("audio", "Audio", Icons.Filled.Audiotrack),
    MediaKindOption("thumbnail", "Thumbnail", Icons.Filled.Image),
    MediaKindOption("other", "Other resource", Icons.Filled.InsertDriveFile),
)

/** Lesson types this screen's "Add lesson" sheet can create. */
private val LESSON_TYPES = listOf(
    "video" to "Video",
    "article" to "Article",
    "resource" to "Resource",
    "assignment" to "Assignment",
    "quiz" to "Quiz",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorMediaScreen(app: EduQuinnApplication) {
    val viewModel: InstructorMediaViewModel = viewModel(
        factory = viewModelFactory {
            initializer { InstructorMediaViewModel(app.services.instructorCourseRepository, app.services.instructorMediaRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingUploadKind by remember { mutableStateOf<String?>(null) }
    var addingLesson by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val kind = pendingUploadKind
        pendingUploadKind = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        var displayName = "upload"
        var sizeBytes = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"
        viewModel.uploadAsset(kind, displayName, mimeType, sizeBytes) {
            resolver.openInputStream(uri) ?: error("Could not open the selected file.")
        }
    }

    Scaffold(
        floatingActionButton = {
            if (uiState.selectedCourseId != null) {
                FloatingActionButton(onClick = { addingLesson = true }) { Icon(Icons.Filled.Add, contentDescription = "Add lesson") }
            }
        },
    ) { padding ->
        when {
            uiState.isLoadingCourses -> LoadingState()
            uiState.coursesError != null && uiState.courses.isEmpty() -> ErrorState(uiState.coursesError ?: "Could not load your courses.", onRetry = viewModel::loadCourses)
            uiState.courses.isEmpty() -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Create a course first, then come back here to add its videos, documents and lessons.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                item {
                    CourseSelector(
                        courses = uiState.courses,
                        selectedId = uiState.selectedCourseId,
                        onSelect = viewModel::selectCourse,
                    )
                }

                if (uiState.isLoadingDetail) {
                    item { LoadingState() }
                } else {
                    item {
                        Text("Media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MEDIA_KINDS.forEach { kindOption ->
                                OutlinedButton(
                                    onClick = { pendingUploadKind = kindOption.value; filePicker.launch("*/*") },
                                    enabled = uiState.uploadProgress == null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Upload ${kindOption.label.lowercase()}")
                                }
                            }
                        }
                    }
                    uiState.uploadProgress?.let { progress ->
                        item {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("Uploading…", style = MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                    }
                    uiState.uploadError?.let { error ->
                        item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (uiState.assets.isEmpty()) {
                        item { Text("No media uploaded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(uiState.assets, key = { it.id }) { asset -> AssetRow(asset) }

                    item {
                        Text("Lessons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    }
                    if (uiState.lessons.isEmpty()) {
                        item { Text("No lessons yet. Tap + to add one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(uiState.lessons, key = { it.id }) { lesson -> LessonRow(lesson) }
                }
            }
        }
    }

    if (addingLesson) {
        AddLessonSheet(
            assets = uiState.assets,
            saving = uiState.savingLesson,
            error = uiState.lessonError,
            onDismiss = { addingLesson = false; viewModel.dismissLessonError() },
            onSave = { title, type, body, duration, preview, assetId, assignment, quiz ->
                viewModel.addLesson(title, type, body, duration, preview, assetId, assignment, quiz) { addingLesson = false }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseSelector(courses: List<InstructorCourse>, selectedId: String?, onSelect: (String) -> Unit) {
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
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            courses.forEach { course ->
                DropdownMenuItem(text = { Text(course.title) }, onClick = { onSelect(course.id); expanded = false })
            }
        }
    }
}

@Composable
private fun AssetRow(asset: CourseAsset) {
    val kind = MEDIA_KINDS.firstOrNull { it.value == asset.kind }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(kind?.icon ?: Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.originalName ?: "Untitled file", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(kind?.label ?: asset.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: InstructorLesson) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.padding(end = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    lesson.lessonType.replaceFirstChar { it.uppercase() } + if (lesson.isPreview) " · Preview" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLessonSheet(
    assets: List<CourseAsset>,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, lessonType: String, body: String, durationSeconds: Int, isPreview: Boolean, assetId: String?, assignment: AssignmentDraft?, quiz: QuizDraft?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var lessonType by remember { mutableStateOf(LESSON_TYPES.first().first) }
    var body by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("") }
    var isPreview by remember { mutableStateOf(false) }
    var selectedAssetId by remember { mutableStateOf<String?>(null) }
    var assignmentInstructions by remember { mutableStateOf("") }
    var assignmentMaxMarks by remember { mutableStateOf("100") }
    val quizState = remember { QuizBuilderState() }
    var quizError by remember { mutableStateOf<String?>(null) }

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
            Text("New lesson", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            LessonTypeDropdown(type = lessonType, onTypeChange = { lessonType = it })

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when (lessonType) {
                "video" -> {
                    AssetDropdown(assets = assets.filter { it.kind == "video" }, selectedId = selectedAssetId, onSelect = { selectedAssetId = it })
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it },
                        label = { Text("Duration (seconds)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "article" -> OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Article content") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                "resource" -> AssetDropdown(assets = assets, selectedId = selectedAssetId, onSelect = { selectedAssetId = it })
                "assignment" -> {
                    OutlinedTextField(
                        value = assignmentInstructions,
                        onValueChange = { assignmentInstructions = it },
                        label = { Text("Instructions") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = assignmentMaxMarks,
                        onValueChange = { assignmentMaxMarks = it },
                        label = { Text("Maximum marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "quiz" -> {
                    QuizBuilderSection(state = quizState)
                    quizError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isPreview, onCheckedChange = { isPreview = it })
                Text("Allow as a free preview", style = MaterialTheme.typography.bodyMedium)
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                OutlinedButton(
                    onClick = {
                        if (lessonType == "quiz") {
                            val problem = quizState.validate()
                            if (problem != null) {
                                quizError = problem
                                return@OutlinedButton
                            }
                            quizError = null
                        }
                        val assignment = if (lessonType == "assignment") {
                            AssignmentDraft(instructions = assignmentInstructions, maxMarks = assignmentMaxMarks.toIntOrNull() ?: 100)
                        } else null
                        val quiz = if (lessonType == "quiz") quizState.toDraft() else null
                        onSave(title, lessonType, body, durationText.toIntOrNull() ?: 0, isPreview, selectedAssetId, assignment, quiz)
                    },
                    enabled = !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Add lesson")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonTypeDropdown(type: String, onTypeChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = LESSON_TYPES.firstOrNull { it.first == type }?.second ?: type
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Lesson type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LESSON_TYPES.forEach { (value, optionLabel) ->
                DropdownMenuItem(text = { Text(optionLabel) }, onClick = { onTypeChange(value); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetDropdown(assets: List<CourseAsset>, selectedId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = assets.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current?.originalName ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Attach uploaded file") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
            if (assets.isNotEmpty()) HorizontalDivider()
            assets.forEach { asset ->
                DropdownMenuItem(text = { Text(asset.originalName ?: asset.id) }, onClick = { onSelect(asset.id); expanded = false })
            }
        }
    }
}
