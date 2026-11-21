package zw.co.eduquinn.app.ui.screens.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.PrimaryButton

private val ROLE_OPTIONS = listOf("Student", "Instructor", "Parent / Guardian", "Organisation", "Visitor")
private val TOPIC_OPTIONS = listOf(
    "Account access", "Account deletion", "Course or learning issue", "Instructor / course publishing",
    "Payments or refunds", "Payouts", "Live classes", "Messages", "Technical problem", "Safety or content report", "Other",
)

/** Native port of support.html's form — same fields, same POST /api/support contract. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(app: EduQuinnApplication, navController: NavHostController) {
    val viewModel: SupportViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SupportViewModel(app.services.supportRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val session by SessionManager.user.collectAsState()

    var name by remember { mutableStateOf(session?.name.orEmpty()) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var role by remember {
        mutableStateOf(
            when (session?.role) {
                "instructor" -> "Instructor"
                "student" -> "Student"
                else -> "Visitor"
            },
        )
    }
    var topic by remember { mutableStateOf(TOPIC_OPTIONS.first()) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(uiState.fallbackMailtoUri) {
        uiState.fallbackMailtoUri?.let {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(it)))
            viewModel.consumeFallbackMailto()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact support") },
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
            Text("EDUQUINN SUPPORT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text("How can we help?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Send your question to the EduQuinn support team. Include enough detail for us to investigate quickly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.successMessage != null) {
                Text(uiState.successMessage!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
            } else {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DropdownField("I am a", role, ROLE_OPTIONS) { role = it }
                DropdownField("Topic", topic, TOPIC_OPTIONS) { topic = it }
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                PrimaryButton(
                    text = "Send to EduQuinn Support",
                    loading = uiState.isSubmitting,
                    onClick = { viewModel.submit(name, email, role, topic, subject, message) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
