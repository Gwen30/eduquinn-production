package zw.co.eduquinn.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.components.PrimaryButton
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    returnTo: String?,
    onNavigateToRegister: () -> Unit,
) {
    val viewModel: LoginViewModel = viewModel(
        factory = viewModelFactory {
            initializer { LoginViewModel(app.services.authRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Which role card the person tapped ("student" / "instructor") — pure
    // UI-navigation state, held here rather than in the ViewModel, the
    // same way email/password already are. Null means "show the role
    // choice step", matching the real login.html's role-login-choice gate
    // in front of the email/password form.
    var selectedRole by remember { mutableStateOf<String?>(null) }

    val title = when (uiState) {
        is LoginUiState.MfaSetup -> "Set up two-factor authentication"
        is LoginUiState.MfaChallenge -> "Enter your authentication code"
        is LoginUiState.MfaBackupCodes -> "Save your backup codes"
        else -> "Sign in"
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
                is LoginUiState.MfaSetup -> MfaSetupStep(state.secret, state.qrDataUrl, state.verifying, state.error, onSubmit = viewModel::submitMfaCode)
                is LoginUiState.MfaChallenge -> MfaChallengeStep(state.verifying, state.error, onSubmit = viewModel::submitMfaCode)
                is LoginUiState.MfaBackupCodes -> MfaBackupCodesStep(state.codes, onContinue = viewModel::acknowledgeBackupCodes)
                else -> if (selectedRole == null) {
                    RoleChoiceStep(onSelect = { selectedRole = it })
                } else {
                    LoginForm(
                        role = selectedRole!!,
                        onChangeRole = { selectedRole = null; viewModel.dismissError() },
                        email = email,
                        onEmailChange = { email = it; viewModel.dismissError() },
                        password = password,
                        onPasswordChange = { password = it; viewModel.dismissError() },
                        passwordVisible = passwordVisible,
                        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                        uiState = state,
                        onSubmit = {
                            viewModel.login(email, password, selectedRole!!) {
                                navController.popBackStack(returnTo ?: "catalog", inclusive = false)
                            }
                        },
                        onResendVerification = { viewModel.resendVerification(email) },
                        onNavigateToRegister = onNavigateToRegister,
                        onForgotPassword = { navController.navigate(Destinations.PASSWORD_HELP) },
                    )
                }
            }
        }
    }
}

/**
 * The role-choice gate shown before the email/password form — mirrors the
 * approved mockup's two tappable cards (`role-login-choice` in the real
 * login.html). Picking a role threads straight into LoginRequest's
 * existing `expectedRole` field, so a student who accidentally lands on
 * the wrong card gets a clear mismatch error from the backend rather than
 * a confusing generic failure.
 */
@Composable
private fun RoleChoiceStep(onSelect: (String) -> Unit) {
    Text("WELCOME BACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    Text("How are you signing in?", style = MaterialTheme.typography.headlineSmall)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RoleChoiceCard(
            icon = Icons.Filled.School,
            title = "Student",
            subtitle = "Learning, live classes and purchases",
            onClick = { onSelect("student") },
        )
        RoleChoiceCard(
            icon = Icons.Filled.Groups,
            title = "Teacher",
            subtitle = "Courses, students, messages and earnings",
            onClick = { onSelect("instructor") },
        )
    }
}

@Composable
private fun RoleChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoginForm(
    role: String,
    onChangeRole: () -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    uiState: LoginUiState,
    onSubmit: () -> Unit,
    onResendVerification: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("WELCOME BACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(if (role == "instructor") "Teacher login" else "Student login", style = MaterialTheme.typography.headlineSmall)
        }
        TextButton(onClick = onChangeRole) { Text("← Change") }
    }

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        // contentType tells Android's Credential Manager/autofill this is the
        // sign-in identifier — without it, the shared-credentials setup we
        // just verified on the website side (assetlinks.json's
        // get_login_creds relation) has nothing to hook into on the app's
        // own login form.
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onTogglePasswordVisible) {
                Icon(
                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onForgotPassword) {
            Text("Forgot password?")
        }
    }

    if (uiState is LoginUiState.NeedsVerification) {
        Text(
            "Please verify your email address before logging in.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onResendVerification, enabled = !uiState.resending) {
            Text(
                when {
                    uiState.resending -> "Sending…"
                    uiState.resent -> "Email sent — check your inbox"
                    else -> "Resend verification email"
                },
            )
        }
    }

    if (uiState is LoginUiState.Error) {
        Text(
            uiState.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    PrimaryButton(
        text = "Sign in",
        loading = uiState is LoginUiState.Loading,
        onClick = onSubmit,
    )

    TextButton(onClick = onNavigateToRegister) {
        Text("Don't have an account? Create one")
    }
}
