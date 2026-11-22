package zw.co.eduquinn.app.ui.screens.more

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.ui.nav.Destinations

/**
 * Every row below is now natively built — Instructor Studio was the last
 * Chrome Custom Tabs hand-off left in this list (it used to open
 * instructor.html) and is now the native InstructorStudioScreen, gated
 * inside that screen itself on whether the signed-in user is actually an
 * instructor.
 */
private data class MoreLink(
    val icon: ImageVector,
    val label: String,
    val route: String,
    val livePill: Boolean = false,
)

private val MORE_LINKS = listOf(
    MoreLink(Icons.Filled.VideoCameraFront, "Live Classes", route = Destinations.LIVE_CLASSES, livePill = true),
    MoreLink(Icons.Filled.Chat, "Messages", route = Destinations.MESSAGES),
    MoreLink(Icons.Filled.Receipt, "Purchases", route = Destinations.PURCHASES),
    MoreLink(Icons.Filled.AccountBalance, "EduQuinn Business", route = Destinations.BUSINESS),
    MoreLink(Icons.Filled.School, "Instructor Studio", route = Destinations.INSTRUCTOR_STUDIO),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(navController: NavHostController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(MORE_LINKS) { link ->
                Surface(
                    onClick = { navController.navigate(link.route) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(link.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(16.dp))
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(link.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            if (link.livePill) {
                                Spacer(Modifier.size(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Text(
                                        "Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
