package zw.co.eduquinn.app.ui.screens.player.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.LearningLesson

/**
 * The backend stores article bodies as plain text (paragraphs separated
 * by a blank line) rather than HTML — confirmed from the web app's own
 * renderLessonBody(), which just splits on /\n{2,}/ and escapes the rest.
 * So this renders plain Text per paragraph; no HTML/Markdown parser needed.
 */
@Composable
fun ArticleLessonContent(
    lesson: LearningLesson,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paragraphs = lesson.body?.trim()?.split(Regex("\n{2,}"))?.filter { it.isNotBlank() } ?: emptyList()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (paragraphs.isEmpty()) {
            Text(
                "The instructor has not added article content yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            paragraphs.forEach { paragraph ->
                Text(paragraph, style = MaterialTheme.typography.bodyLarge)
            }
        }
        OutlinedButton(onClick = onToggleComplete) {
            Text(if (lesson.completed) "Mark incomplete" else "Mark complete")
        }
    }
}
