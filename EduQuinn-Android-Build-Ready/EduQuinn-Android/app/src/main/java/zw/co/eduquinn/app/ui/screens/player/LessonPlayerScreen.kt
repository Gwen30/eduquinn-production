package zw.co.eduquinn.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.screens.player.content.ArticleLessonContent
import zw.co.eduquinn.app.ui.screens.player.content.AssignmentLessonContent
import zw.co.eduquinn.app.ui.screens.player.content.ResourceLessonContent
import zw.co.eduquinn.app.ui.screens.player.content.VideoLessonContent
import zw.co.eduquinn.app.ui.screens.player.quiz.QuizLessonContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonPlayerScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    courseId: String,
    initialLessonId: String,
) {
    val viewModel: LessonPlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LessonPlayerViewModel(courseId, initialLessonId, app.services.learningRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            LessonListSidebar(
                lessons = uiState.lessons,
                currentLessonId = uiState.currentLessonId,
                onSelect = {
                    viewModel.selectLesson(it)
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uiState.currentLesson?.title ?: "Lesson") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Course content")
                        }
                    },
                )
            },
        ) { padding ->
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
                uiState.lessons.isEmpty() -> ErrorState(
                    uiState.error ?: "No lessons have been published yet.",
                    modifier = Modifier.padding(padding),
                    onRetry = viewModel::load,
                )
                else -> {
                    val lesson = uiState.currentLesson
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        LinearProgressIndicator(
                            progress = { uiState.courseProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (lesson == null) {
                            Text("No lessons available", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                        } else {
                            when (lesson.lessonType) {
                                "video" -> VideoLessonContent(
                                    okHttpClient = app.services.okHttpClient,
                                    lesson = lesson,
                                    onProgress = { completed, position -> viewModel.reportProgress(lesson.id, completed, position) },
                                    onToggleComplete = { viewModel.reportProgress(lesson.id, !lesson.completed, lesson.positionSeconds) },
                                )
                                "article" -> ArticleLessonContent(
                                    lesson = lesson,
                                    onToggleComplete = { viewModel.reportProgress(lesson.id, !lesson.completed) },
                                )
                                "resource" -> ResourceLessonContent(
                                    app = app,
                                    lesson = lesson,
                                    onToggleComplete = { viewModel.reportProgress(lesson.id, !lesson.completed) },
                                )
                                "assignment" -> AssignmentLessonContent(
                                    lesson = lesson,
                                    onToggleComplete = { viewModel.reportProgress(lesson.id, !lesson.completed) },
                                )
                                "quiz" -> QuizLessonContent(
                                    lesson = lesson,
                                    assessment = uiState.currentAssessment,
                                    onSubmit = { assessmentId, lessonId, answers, onResult ->
                                        viewModel.submitQuiz(assessmentId, lessonId, answers, onResult)
                                    },
                                )
                                else -> Text("This lesson type is not supported yet.", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
