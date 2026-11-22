package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.PayoutRow
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

@Composable
fun InstructorPayoutsScreen(app: EduQuinnApplication) {
    val viewModel: InstructorPayoutsViewModel = viewModel(
        factory = viewModelFactory { initializer { InstructorPayoutsViewModel(app.services.instructorEarningsRepository) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    val earnings = uiState.earnings

    when {
        uiState.isLoading -> LoadingState()
        uiState.error != null && earnings == null -> ErrorState(uiState.error ?: "Could not load payouts.", onRetry = viewModel::load)
        else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PayoutMetric("Available", formatCents(earnings?.summary?.available ?: 0), modifier = Modifier.weight(1f))
                        PayoutMetric("Pending", formatCents(earnings?.summary?.pending ?: 0), modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PayoutMetric(
                            "Lifetime (${earnings?.sharePercent ?: 75}% share)",
                            formatCents(earnings?.summary?.lifetime ?: 0),
                            modifier = Modifier.weight(1f),
                        )
                        PayoutMetric(
                            "Tax status",
                            if (earnings?.taxClearance?.cleared == true) "Cleared" else "Not cleared",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        "Earnings become available ${earnings?.earningsHoldDays ?: 17} days after a sale, once the ${earnings?.refundWindowDays ?: 14}-day refund window has passed. Payouts are reviewed and released by EduQuinn — there's nothing to request here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                Text(
                    "Payout history",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }
            val payouts = earnings?.payouts.orEmpty()
            if (payouts.isEmpty()) {
                item { Text("No payouts yet.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(payouts, key = { it.id }) { payout -> PayoutRowCard(payout) }
        }
    }
}

@Composable
private fun PayoutMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PayoutRowCard(payout: PayoutRow) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(formatCentsInt(payout.amountCents, payout.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(payout.periodStart, payout.periodEnd).joinToString(" – ").ifBlank { payout.reference ?: "" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                payout.status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
