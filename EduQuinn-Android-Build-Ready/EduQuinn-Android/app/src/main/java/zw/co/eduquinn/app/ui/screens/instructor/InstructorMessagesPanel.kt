package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.nav.Destinations

/**
 * The Studio drawer's "Communication" section. The backend already serves
 * both roles on the exact same conversation endpoints (a `Conversation`
 * carries both studentName and instructorName, and the role-aware display
 * is handled by `Conversation.counterpartName`), so this isn't a second
 * messages screen — it just hands off to the existing `MessagesScreen` via
 * a normal nav route, the same screen a student uses. A button rather
 * than an automatic navigate-on-select, so popping back out of Messages
 * returns to the Studio shell instead of bouncing straight back in.
 */
@Composable
fun InstructorMessagesPanel(app: EduQuinnApplication, navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Student conversations", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reply to students who've messaged you about your courses.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { navController.navigate(Destinations.MESSAGES) }) {
            Text("Open Messages")
        }
    }
}
