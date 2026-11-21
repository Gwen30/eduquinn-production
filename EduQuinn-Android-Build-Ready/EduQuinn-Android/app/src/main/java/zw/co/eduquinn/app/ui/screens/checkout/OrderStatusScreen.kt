package zw.co.eduquinn.app.ui.screens.checkout

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderStatusScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    orderId: String,
) {
    val viewModel: OrderStatusViewModel = viewModel(
        factory = viewModelFactory {
            initializer { OrderStatusViewModel(orderId, app.services.commerceRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.paynowRetryUrl) {
        uiState.paynowRetryUrl?.let {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it))
            viewModel.consumeRetryUrl()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Order status") }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
            uiState.isLoading -> LoadingState()
            uiState.order?.status == "paid" -> {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text("Payment confirmed!", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "You're enrolled — head to My Learning to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(
                    onClick = { navController.navigate(Destinations.MY_LEARNING) { popUpTo(Destinations.CATALOG) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Go to My Learning") }
            }
            uiState.order?.status == "pending_gateway" -> {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(64.dp),
                )
                Text("Waiting for payment", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "We haven't received confirmation yet. If you've completed payment, check again in a moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = viewModel::checkAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Check again")
                }
                OutlinedButton(
                    onClick = viewModel::retryPaynow,
                    enabled = !uiState.isRetryingPaynow,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(if (uiState.isRetryingPaynow) "Opening payment…" else "Retry payment")
                }
            }
            else -> {
                Text("Order status: ${uiState.order?.status ?: "unknown"}", style = MaterialTheme.typography.headlineSmall)
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Button(onClick = viewModel::checkAgain, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Check again")
                }
            }
            }
        }
    }
}
