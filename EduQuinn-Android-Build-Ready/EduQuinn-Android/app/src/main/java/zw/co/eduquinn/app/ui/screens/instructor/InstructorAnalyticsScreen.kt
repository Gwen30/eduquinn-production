package zw.co.eduquinn.app.ui.screens.instructor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import zw.co.eduquinn.app.ui.components.ErrorState
import zw.co.eduquinn.app.ui.components.LoadingState

@Composable
fun InstructorAnalyticsScreen(app: EduQuinnApplication) {
    val viewModel: InstructorAnalyticsViewModel = viewModel(
        factory = viewModelFactory { initializer { InstructorAnalyticsViewModel(app.services.instructorEarningsRepository) } },
    )
    val uiState by viewModel.uiState.collectAsState()
    val earnings = uiState.earnings

    when {
        uiState.isLoading -> LoadingState()
        uiState.error != null && earnings == null -> ErrorState(uiState.error ?: "Could not load analytics.", onRetry = viewModel::load)
        else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsTile("Lifetime earnings", formatCents(earnings?.summary?.lifetime ?: 0), modifier = Modifier.weight(1f))
                AnalyticsTile("Available now", formatCents(earnings?.summary?.available ?: 0), modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnalyticsTile("Pending clearance", formatCents(earnings?.summary?.pending ?: 0), modifier = Modifier.weight(1f))
                AnalyticsTile("Your share", "${earnings?.sharePercent ?: 75}%", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalyticsTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
