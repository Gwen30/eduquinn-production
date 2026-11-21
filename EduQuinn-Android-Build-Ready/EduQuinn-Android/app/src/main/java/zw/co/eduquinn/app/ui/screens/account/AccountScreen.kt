package zw.co.eduquinn.app.ui.screens.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.SignedOutPrompt
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
) {
    val user by SessionManager.user.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Account") }) },
    ) { padding ->
        if (user != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(user!!.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(user!!.email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    user!!.role.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            app.services.authRepository.logout()
                            navController.navigate(Destinations.CATALOG) { popUpTo(Destinations.CATALOG) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("Log out") }
            }
        } else {
            SignedOutPrompt(
                onSignIn = { navController.navigate(Destinations.login(Destinations.ACCOUNT)) },
                onCreateAccount = { navController.navigate(Destinations.REGISTER) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}
