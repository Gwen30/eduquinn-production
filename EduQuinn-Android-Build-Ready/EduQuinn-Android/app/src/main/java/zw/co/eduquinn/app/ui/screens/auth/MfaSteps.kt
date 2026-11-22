package zw.co.eduquinn.app.ui.screens.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.ui.components.PrimaryButton

/**
 * Shared two-factor-authentication steps for both Login and Register.
 *
 * These used to live as `private fun`s inside LoginScreen.kt — only Login
 * had a 2FA flow because only instructor *sign-in* existed natively.
 * Registering as an instructor now hits the exact same
 * `mfaSetupRequired`/`mfaRequired` branch on the backend (both paths call
 * `startInstructorAuth()`), so Register needs the identical setup →
 * challenge → backup-codes steps. Rather than duplicate ~150 lines of UI,
 * both screens' `AuthResponse`-state machines (LoginUiState/RegisterUiState
 * — intentionally kept as two separate sealed types, matching this
 * codebase's no-shared-ViewModel-base-class convention) call into these
 * plain, state-agnostic composables.
 */
@Composable
fun MfaCodeField(code: String, onCodeChange: (String) -> Unit, label: String, error: String?) {
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
fun MfaSetupStep(
    secret: String?,
    qrDataUrl: String?,
    verifying: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Text(
        "EduQuinn requires instructors to protect their account with an authenticator app " +
            "(Google Authenticator, Authy, 1Password, etc).",
        style = MaterialTheme.typography.bodyMedium,
    )
    qrBitmap(qrDataUrl)?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = "Scan this QR code in your authenticator app",
            modifier = Modifier.size(200.dp),
        )
    }
    if (secret != null) {
        Text("Can't scan it? Enter this code manually in your app:", style = MaterialTheme.typography.bodyMedium)
        Text(secret, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
    MfaCodeField(code, { code = it }, "6-digit code from your app", error)
    PrimaryButton(text = "Confirm and enable", loading = verifying, onClick = { onSubmit(code) })
}

@Composable
fun MfaChallengeStep(verifying: Boolean, error: String?, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    Text(
        "Enter the 6-digit code from your authenticator app, or one of your backup codes if you don't have it.",
        style = MaterialTheme.typography.bodyMedium,
    )
    MfaCodeField(code, { code = it }, "Authentication code", error)
    PrimaryButton(text = "Verify", loading = verifying, onClick = { onSubmit(code) })
}

@Composable
fun MfaBackupCodesStep(codes: List<String>, onContinue: () -> Unit) {
    Text(
        "If you ever lose access to your authenticator app, use one of these one-time codes to " +
            "sign in instead. Save them somewhere safe — they will not be shown again.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        codes.forEach { codeText ->
            Text(codeText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        }
    }
    PrimaryButton(text = "I've saved these — continue", onClick = onContinue)
}

/** Decodes the backend's `data:image/png;base64,...` QR code into a drawable bitmap. */
fun qrBitmap(dataUrl: String?): ImageBitmap? {
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
