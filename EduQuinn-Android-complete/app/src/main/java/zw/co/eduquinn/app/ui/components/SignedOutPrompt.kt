package zw.co.eduquinn.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The "You're not signed in" panel — previously duplicated verbatim
 * between AccountScreen and WishlistScreen. Any screen that's only
 * useful once there's a session (Wishlist, My Learning, Checkout) can
 * reuse this instead of re-writing the same copy/buttons.
 */
@Composable
fun SignedOutPrompt(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("You're not signed in", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sign in to track your learning, wishlist courses, and buy new ones.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
        OutlinedButton(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth()) { Text("Create an account") }
    }
}
