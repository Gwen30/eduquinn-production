package zw.co.eduquinn.app.ui.screens.mylearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.EmptyState
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLearningScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
) {
    LaunchedEffect(Unit) {
        if (!SessionManager.isLoggedIn) {
            navController.navigate(Destinations.login(Destinations.MY_LEARNING)) {
                popUpTo(Destinations.CATALOG)
            }
        }
    }
    if (!SessionManager.isLoggedIn) return

    val viewModel: MyLearningViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MyLearningViewModel(app.services.learningRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("My Learning") }) },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.enrolments.isEmpty() ->
                ErrorState(uiState.error ?: "Could not load your courses.", modifier = Modifier.padding(padding), onRetry = viewModel::load)
            uiState.enrolments.isEmpty() ->
                EmptyState(
                    "You haven't enrolled in any courses yet. Your purchased courses will appear here after payment confirmation.",
                    modifier = Modifier.padding(padding),
                    action = {
                        Button(onClick = { navController.navigate(Destinations.CATALOG) }) { Text("Browse courses") }
                    },
                )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "MY EDUQUINN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text("My Learning", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Continue your enrolled courses and track your progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                items(uiState.enrolments, key = { it.courseId }) { enrolment ->
                    val isCompleted = enrolment.completedAt != null
                    val isOpening = uiState.openingCourseId == enrolment.courseId
                    val openCourse = {
                        if (!isOpening) {
                            viewModel.openCourse(enrolment.courseId) { lessonId ->
                                navController.navigate(Destinations.lessonPlayer(enrolment.courseId, lessonId))
                            }
                        }
                    }
                    Card(
                        onClick = {
                            if (isCompleted) {
                                navController.navigate(Destinations.certificate(enrolment.courseId))
                            } else {
                                openCourse()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            enrolment.category?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(enrolment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(
                                progress = { enrolment.progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (isCompleted) "Completed" else "${enrolment.progress}% complete" + if (isOpening) " · opening…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isCompleted) {
                                    OutlinedButton(onClick = { navController.navigate(Destinations.certificate(enrolment.courseId)) }) {
                                        Text("Certificate")
                                    }
                                } else {
                                    Button(onClick = openCourse) {
                                        Text(if (enrolment.progress > 0) "Continue learning" else "Start learning")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
