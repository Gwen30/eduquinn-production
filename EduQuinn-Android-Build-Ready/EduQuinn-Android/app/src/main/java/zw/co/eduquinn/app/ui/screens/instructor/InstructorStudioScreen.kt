package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerValue
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.SignedOutPrompt
import zw.co.eduquinn.app.ui.nav.Destinations

/**
 * The 8 Instructor Studio sections. Every one of these is really a panel
 * within one dashboard, not an independent drill-down flow (the real
 * instructor.html's own hamburger nav just swaps which `<section>` is
 * visible — no section pushes another onto a back stack there either),
 * so this is ONE Destinations route + an internal drawer + section-state
 * switch, not 8 separate nav routes. Modals *within* a section (course
 * edit, lesson/quiz builder, schedule-class form, attendance view) are
 * ModalBottomSheet/dialog state scoped to that section's own screen, never
 * their own nav routes either.
 */
enum class StudioSection(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Filled.Dashboard),
    Courses("Courses", Icons.Filled.School),
    Media("Media & Lessons", Icons.Filled.MenuBook),
    Live("Live Classes", Icons.Filled.VideoCameraFront),
    Analytics("Analytics", Icons.Filled.QueryStats),
    Messages("Communication", Icons.Filled.Chat),
    Announcements("Announcements", Icons.Filled.Campaign),
    Payouts("Payouts", Icons.Filled.Payments),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructorStudioScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
) {
    val user by SessionManager.user.collectAsState()

    when {
        !SessionManager.isLoggedIn -> SignedOutPrompt(
            onSignIn = { navController.navigate(Destinations.login(Destinations.INSTRUCTOR_STUDIO)) },
            onCreateAccount = { navController.navigate(Destinations.register(role = "instructor")) },
        )
        user?.role != "instructor" -> BecomeInstructorPrompt(
            onApply = { navController.navigate(Destinations.register(role = "instructor")) },
        )
        else -> InstructorStudioShell(app = app, navController = navController)
    }
}

@Composable
private fun BecomeInstructorPrompt(onApply: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Become an instructor", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Instructor Studio is only available to instructor accounts. Apply to start teaching and you'll be guided through setting up two-factor authentication, which every instructor account requires.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) {
            Text("Apply to teach")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorStudioShell(app: EduQuinnApplication, navController: NavHostController) {
    var selected by remember { mutableStateOf(StudioSection.Dashboard) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(
                        "INSTRUCTOR STUDIO",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    StudioSection.entries.forEach { section ->
                        NavigationDrawerItem(
                            icon = { Icon(section.icon, contentDescription = null) },
                            label = { Text(section.label) },
                            selected = section == selected,
                            onClick = {
                                selected = section
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selected.label, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Sections")
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (selected) {
                    StudioSection.Dashboard -> InstructorDashboardScreen(app, navController, onManageCourses = { selected = StudioSection.Courses })
                    StudioSection.Courses -> InstructorCoursesScreen(app)
                    StudioSection.Media -> InstructorMediaScreen(app)
                    StudioSection.Live -> InstructorLiveScreen(app)
                    StudioSection.Analytics -> InstructorAnalyticsScreen(app)
                    StudioSection.Messages -> InstructorMessagesPanel(app, navController)
                    StudioSection.Announcements -> InstructorAnnouncementsScreen(app)
                    StudioSection.Payouts -> InstructorPayoutsScreen(app)
                }
            }
        }
    }
}
