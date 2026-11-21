package zw.co.eduquinn.app.ui.screens.checkout

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.PaymentMethodOption
import zw.co.eduquinn.app.data.repository.SessionManager
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState
import zw.co.eduquinn.app.ui.components.PriceTag
import zw.co.eduquinn.app.ui.nav.Destinations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    app: EduQuinnApplication,
    navController: NavHostController,
    courseIds: List<String>,
) {
    val viewModel: CheckoutViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CheckoutViewModel(courseIds, app.services.courseRepository, app.services.commerceRepository) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!SessionManager.isLoggedIn) {
            navController.navigate(Destinations.login(Destinations.checkout(courseIds.joinToString(",")))) {
                popUpTo(Destinations.CATALOG)
            }
        }
    }

    LaunchedEffect(uiState.result) {
        val response = uiState.result ?: return@LaunchedEffect
        when {
            response.paynowRedirectUrl != null -> {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(response.paynowRedirectUrl))
                navController.navigate(Destinations.orderStatus(response.order.id)) {
                    popUpTo(Destinations.CATALOG)
                }
            }
            response.bankInstructions == null -> {
                // Free or sandbox-paid: the order is already settled and the
                // enrolment exists — go straight to My Learning.
                navController.navigate(Destinations.MY_LEARNING) {
                    popUpTo(Destinations.CATALOG)
                }
            }
            // bank_transfer: instructions render inline below, no navigation.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            uiState.courses.isEmpty() && uiState.error != null ->
                ErrorState(uiState.error ?: "Could not load checkout.", modifier = Modifier.padding(padding))
            uiState.result?.bankInstructions != null -> BankTransferInstructions(
                instructions = uiState.result!!.bankInstructions!!,
                onDone = { navController.navigate(Destinations.MY_LEARNING) { popUpTo(Destinations.CATALOG) } },
                modifier = Modifier.padding(padding),
            )
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
                    item {
                        Text(
                            "CHECKOUT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text("Secure checkout", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Review your selected courses and payment method.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                    }
                    items(uiState.courses, key = { it.id }) { course ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(course.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            PriceTag(course.priceCents, course.currency)
                        }
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.couponCode,
                                onValueChange = viewModel::onCouponCodeChange,
                                label = { Text("Coupon code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::applyCoupon) { Text("Apply") }
                        }
                        uiState.couponError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        if (uiState.discountCents > 0) {
                            Text("Discount: -${uiState.discountCents / 100.0}", style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Payment method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                    }
                    items(uiState.paymentMethods, key = { it.id }) { method ->
                        PaymentMethodRow(
                            method = method,
                            selected = uiState.selectedMethod == method.id,
                            onSelect = { viewModel.selectMethod(method.id) },
                        )
                    }
                    item {
                        if (uiState.paymentMethods.isEmpty()) {
                            Text(
                                "No payment methods are currently available.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        uiState.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        }
                        Text(
                            "Your order is processed using your selected payment method. Refunds are available within our " +
                                "standard refund window, provided the course hasn't been substantially completed — see our " +
                                "Refund Policy for details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                            PriceTag(uiState.subtotalCents)
                        }
                        if (uiState.discountCents > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Discount", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "-${PriceTagText(uiState.discountCents)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            PriceTag(uiState.totalCents)
                        }
                    }
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = !uiState.isSubmitting && uiState.selectedMethod != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(if (uiState.isSubmitting) "Placing order…" else "Place order · ${PriceTagText(uiState.totalCents)}")
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodRow(method: PaymentMethodOption, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.size(8.dp))
            Icon(paymentIcon(method), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Text(method.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun paymentIcon(method: PaymentMethodOption): ImageVector {
    val key = "${method.id} ${method.label}".lowercase()
    return when {
        "paynow" in key -> Icons.Filled.Smartphone
        "bank" in key -> Icons.Filled.AccountBalance
        "card" in key || "visa" in key || "stripe" in key -> Icons.Filled.CreditCard
        else -> Icons.Filled.Payment
    }
}

/** Plain-text price formatting for places PriceTag's own composable can't be used inline (e.g. inside another Text's string). */
private fun PriceTagText(cents: Int, currency: String = "USD"): String =
    if (cents <= 0) "Free" else "$${"%.2f".format(cents / 100.0)}"

@Composable
private fun BankTransferInstructions(
    instructions: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "PAYMENT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Bank transfer instructions", style = MaterialTheme.typography.headlineSmall)
        Text(
            instructions,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Got it")
        }
    }
}
