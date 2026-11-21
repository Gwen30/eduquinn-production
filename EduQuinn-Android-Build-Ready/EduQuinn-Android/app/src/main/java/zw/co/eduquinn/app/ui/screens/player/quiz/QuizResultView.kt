package zw.co.eduquinn.app.ui.screens.player.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.AssessmentResult

@Composable
fun QuizResultView(
    result: AssessmentResult,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRetake = result.attemptsUsed < result.maxAttempts
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (result.passed) "Passed" else "Not yet passed",
            style = MaterialTheme.typography.titleLarge,
            color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            "Score ${result.score}% (pass mark ${result.passMark}%). Attempt ${result.attemptsUsed} of ${result.maxAttempts}.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (canRetake && !result.passed) {
            Button(onClick = onRetake) { Text("Try again") }
        } else if (!canRetake) {
            Text("No attempts remaining.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
