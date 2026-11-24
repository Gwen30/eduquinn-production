package zw.co.eduquinn.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    initialRole: String? = null,
) {
    val viewModel: RegisterViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RegisterViewModel(app.services.authRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // "student" / "instructor" — pre-selected when a caller (the Catalog
    // "Become an Instructor" promo card, the More tab's Instructor Studio
    // row) navigates here with ?role=instructor already set.
    var role by remember { mutableStateOf(if (initialRole == "instructor") "instructor" else "student") }
    var expertise by remember { mutableStateOf("") }

    val title = when (uiState) {
        is RegisterUiState.MfaSetup -> "Set up two-factor authentication"
        is RegisterUiState.MfaChallenge -> "Enter your authentication code"
        is RegisterUiState.MfaBackupCodes -> "Save your backup codes"
        else -> "Create account"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
            when (val state = uiState) {
                is RegisterUiState.MfaSetup -> MfaSetupStep(state.secret, state.qrDataUrl, state.verifying, state.error, onSubmit = viewModel::submitMfaCode)
                is RegisterUiState.MfaChallenge -> MfaChallengeStep(state.verifying, state.error, onSubmit = viewModel::submitMfaCode)
                is RegisterUiState.MfaBackupCodes -> MfaBackupCodesStep(state.codes, onContinue = viewModel::acknowledgeBackupCodes)
                else -> {
                    val pending = state as? RegisterUiState.PendingVerification
                    if (pending != null) {
                        Text("EMAIL VERIFICATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Check your email", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "We've sent a verification link to ${pending.email}. Click it, then come back and log in.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { viewModel.resendVerification(pending.email) }, enabled = !pending.resending) {
                            Text(
                                when {
                                    pending.resending -> "Sending…"
                                    pending.resent -> "Email sent — check your inbox"
                                    else -> "Resend verification email"
                                },
                            )
                        }
                    } else {
                        Text("JOIN EDUQUINN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Create your account", style = MaterialTheme.typography.headlineSmall)

                        RoleDropdown(role = role, onRoleChange = { role = it; viewModel.dismissError() })

                        if (role == "instructor") {
                            ExpertiseDropdown(expertise = expertise, onExpertiseChange = { expertise = it; viewModel.dismissError() })
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; viewModel.dismissError() },
                            label = { Text("Full name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; viewModel.dismissError() },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; viewModel.dismissError() },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = { Text("At least 10 characters, with a letter and a number.") },
                            // NewPassword (not Password) is what tells Android this is an
                            // account-creation field — it's what triggers "suggest a strong
                            // password" and saves it as a new credential rather than trying
                            // to match an existing one.
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                        )

                        if (role == "instructor") {
                            Text(
                                "Instructor accounts require two-factor authentication — you'll set this up right after creating your account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (state is RegisterUiState.Error) {
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        PrimaryButton(
                            text = "Create account",
                            loading = state is RegisterUiState.Loading,
                            onClick = {
                                viewModel.register(name, email, password, role, expertise.takeIf { role == "instructor" }) {
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(role: String, onRoleChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (role == "instructor") "Instructor — I want to teach" else "Student — I want to learn"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("I want to join as") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Student — I want to learn") }, onClick = { onRoleChange("student"); expanded = false })
            DropdownMenuItem(text = { Text("Instructor — I want to teach") }, onClick = { onRoleChange("instructor"); expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpertiseDropdown(expertise: String, onExpertiseChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = expertise,
            onValueChange = {},
            readOnly = true,
            label = { Text("Primary expertise") },
            placeholder = { Text("Choose an area") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            InstructorExpertise.GROUPS.forEach { (group, options) ->
                Text(
                    group.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onExpertiseChange(option); expanded = false })
                }
                HorizontalDivider()
            }
        }
    }
}
