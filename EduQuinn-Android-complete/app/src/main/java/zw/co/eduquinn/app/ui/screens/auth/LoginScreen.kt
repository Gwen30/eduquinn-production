package zw.co.eduquinn.app.ui.screens.auth

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
                is LoginUiState.MfaSetup -> MfaSetupStep(state, onSubmit = viewModel::submitMfaCode)
                is LoginUiState.MfaChallenge -> MfaChallengeStep(state, onSubmit = viewModel::submitMfaCode)
                is LoginUiState.MfaBackupCodes -> MfaBackupCodesStep(state, onContinue = viewModel::acknowledgeBackupCodes)
                else -> LoginForm(
                    email = email,
                    onEmailChange = { email = it; viewModel.dismissError() },
                    password = password,
                    onPasswordChange = { password = it; viewModel.dismissError() },
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                    uiState = state,
                    onSubmit = {
                        viewModel.login(email, password) {
                            navController.popBackStack(returnTo ?: "catalog", inclusive = false)
                        }
                    },
                    onResendVerification = { viewModel.resendVerification(email) },
                    onNavigateToRegister = onNavigateToRegister,
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
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
) {
    Text("WELCOME BACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    Text("Sign in", style = MaterialTheme.typography.headlineSmall)

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
    )

    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = {
                CustomTabsIntent.Builder().build()
                    .launchUrl(context, Uri.parse("https://eduquinn.co.zw/forgot-password.html"))
            },
        ) {
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

@Composable
private fun MfaCodeField(code: String, onCodeChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 10) onCodeChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MfaSetupStep(state: LoginUiState.MfaSetup, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text(
        "EduQuinn requires instructors to protect their account with an authenticator app " +
            "(Google Authenticator, Authy, 1Password, etc).",
        style = MaterialTheme.typography.bodyMedium,
    )
    qrBitmap(state.qrDataUrl)?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Scan this QR code in your authenticator app",
            modifier = Modifier.size(200.dp),
        )
    }
    if (state.secret != null) {
        Text("Can't scan it? Enter this code manually in your app:", style = MaterialTheme.typography.bodyMedium)
        Text(state.secret, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
    MfaCodeField(code, { code = it }, "6-digit code from your app", state.error)
    PrimaryButton(text = "Confirm and enable", loading = state.verifying, onClick = { onSubmit(code) })
}

@Composable
private fun MfaChallengeStep(state: LoginUiState.MfaChallenge, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text(
        "Enter the 6-digit code from your authenticator app, or one of your backup codes if you don't have it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    MfaCodeField(code, { code = it }, "Authentication code", state.error)
    PrimaryButton(text = "Verify", loading = state.verifying, onClick = { onSubmit(code) })
}

@Composable
private fun MfaBackupCodesStep(state: LoginUiState.MfaBackupCodes, onContinue: () -> Unit) {
    Text(
        "If you ever lose access to your authenticator app, use one of these one-time codes to " +
            "sign in instead. Save them somewhere safe — they will not be shown again.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.codes.forEach { codeText ->
            Text(codeText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        }
    }
    PrimaryButton(text = "I've saved these — continue", onClick = onContinue)
}

/** Decodes the backend's `data:image/png;base64,...` QR code into a drawable bitmap. */
private fun qrBitmap(dataUrl: String?): ImageBitmap? {
    if (dataUrl == null) return null
    return try {
        val base64 = dataUrl.substringAfter(",", "")
        if (base64.isEmpty()) return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
