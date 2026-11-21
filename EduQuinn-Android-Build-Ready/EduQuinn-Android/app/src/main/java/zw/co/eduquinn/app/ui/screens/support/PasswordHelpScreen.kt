package zw.co.eduquinn.app.ui.screens.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.ui.nav.Destinations

/**
 * Native port of forgot-password.html, which — per the real backend — is
 * NOT a self-service reset form (there is no password-reset API at all).
 * It's a static "contact support" page, so this screen is static too,
 * with a button into the native SupportScreen rather than a password
 * field or any reset flow that doesn't exist on the server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordHelpScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Password help") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ACCOUNT RECOVERY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text("Password help", style = MaterialTheme.typography.headlineSmall)
            Text(
                "For account recovery, contact EduQuinn Support using the email address registered to your account.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { navController.navigate(Destinations.SUPPORT) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Contact support") }
            TextButton(onClick = { navController.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Return to login")
            }
        }
    }
}
