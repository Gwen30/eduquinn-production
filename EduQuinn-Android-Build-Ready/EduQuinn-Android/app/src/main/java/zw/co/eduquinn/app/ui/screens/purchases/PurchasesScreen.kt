package zw.co.eduquinn.app.ui.screens.purchases

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.OrderHistoryItem
import zw.co.eduquinn.app.data.network.NetworkModule
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.PriceTag
import zw.co.eduquinn.app.ui.components.SignedOutPrompt
import zw.co.eduquinn.app.ui.nav.Destinations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Native port of purchases.html — order history, "Pay now" and "Request refund". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(app: EduQuinnApplication, navController: NavHostController) {
    val viewModel: PurchasesViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PurchasesViewModel(app.services.commerceRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Purchases") }) },
    ) { padding ->
        when {
            !SessionManager.isLoggedIn -> SignedOutPrompt(
                onSignIn = { navController.navigate(Destinations.login(Destinations.PURCHASES)) },
                onCreateAccount = { navController.navigate(Destinations.REGISTER) },
                modifier = Modifier.padding(padding),
            )
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.error != null && uiState.orders.isEmpty() ->
                ErrorState(uiState.error ?: "Could not load your purchases.", modifier = Modifier.padding(padding), onRetry = viewModel::load)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                        Text("MY EDUQUINN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Purchase history", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Review your orders and payment status.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (uiState.orders.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No orders yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Courses you buy will show up here, with receipts and refund requests.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Column(modifier = Modifier.padding(bottom = 14.dp)) {
                            Text(
                                "Refunds are available within ${uiState.refundWindowDays} days of purchase, as long as you " +
                                    "haven't completed more than ${uiState.refundMaxProgressPercent}% of the course.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    CustomTabsIntent.Builder().build()
                                        .launchUrl(context, Uri.parse("${NetworkModule.BASE_URL}refund-policy.html"))
                                },
                                modifier = Modifier.padding(start = 0.dp),
                            ) { Text("View refund policy") }
                        }
                    }
                    items(uiState.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            onPayNow = { navController.navigate(Destinations.orderStatus(order.id)) },
                            onRequestRefund = { viewModel.openRefundDialog(order.id) },
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }

        if (uiState.refundDialogOrderId != null) {
            RefundDialog(
                isSubmitting = uiState.isSubmittingRefund,
                onDismiss = viewModel::dismissRefundDialog,
                onConfirm = { reason -> viewModel.submitRefund(uiState.refundDialogOrderId!!, reason) },
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: OrderHistoryItem,
    onPayNow: () -> Unit,
    onRequestRefund: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.id.take(8).uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                order.createdAt?.let {
                    Text(formatOrderDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LabelValueRow("Courses", "${order.items.size} course${if (order.items.size == 1) "" else "s"}")
            LabelValueRow("Payment", order.paymentMethod?.replaceFirstChar { it.uppercase() } ?: "—")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PriceTag(order.totalCents, order.currency)
                StatusPill(order.status)
            }

            when {
                order.status == "pending_gateway" -> Button(onClick = onPayNow, modifier = Modifier.fillMaxWidth()) { Text("Pay now") }
                order.status == "paid" && order.refundEligible -> OutlinedButton(onClick = onRequestRefund, modifier = Modifier.fillMaxWidth()) {
                    Text("Request refund")
                }
                order.status == "paid" && order.refundReason != null -> Text(
                    if (order.refundReason == "progress_exceeded") "Not eligible — course in progress" else "Refund window closed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** Colors match the website's `.status` badge rules: green/paid, amber/pending, grey/refunded, red/refund-requested. */
@Composable
private fun StatusPill(status: String) {
    val (bg, fg, label) = when (status) {
        "paid" -> Triple(Color(0xFFE8F6EC), Color(0xFF267842), "Paid")
        "pending_gateway" -> Triple(Color(0xFFFFF0D7), Color(0xFF8B5A00), "Awaiting payment")
        "refund_requested" -> Triple(Color(0xFFFEE8E8), Color(0xFFA12626), "Refund requested")
        "refunded" -> Triple(Color(0xFFEEEEEE), Color(0xFF555555), "Refunded")
        "failed" -> Triple(Color(0xFFFEE8E8), Color(0xFFA12626), "Failed")
        else -> Triple(Color(0xFFEEEEEE), Color(0xFF555555), status.replaceFirstChar { it.uppercase() })
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RefundDialog(isSubmitting: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a refund") },
        text = {
            Column {
                Text("Briefly tell us why you'd like a refund (optional):", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }, enabled = !isSubmitting) {
                Text(if (isSubmitting) "Requesting…" else "Request refund")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatOrderDate(isoDate: String): String = try {
    val parsed = java.time.Instant.parse(isoDate)
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date.from(parsed))
} catch (_: Exception) {
    isoDate
}
