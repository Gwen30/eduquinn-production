package zw.co.eduquinn.app.ui.screens.player.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import zw.co.eduquinn.app.data.model.LearningLesson

private data class AssignmentDetails(val instructions: String, val dueDate: String?, val maxMarks: String)

private fun parseAssignment(body: String?): AssignmentDetails? {
    return try {
        val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(body ?: "{}").jsonObject
        val dueDateElement = obj["dueDate"]
        AssignmentDetails(
            instructions = obj["instructions"]?.jsonPrimitive?.content ?: "No instructions provided.",
            dueDate = if (dueDateElement == null || dueDateElement == JsonNull) null else dueDateElement.jsonPrimitive.content,
            maxMarks = obj["maxMarks"]?.jsonPrimitive?.content ?: "100",
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Treated the same as an article lesson for v1 (per the native-rewrite
 * plan's decision): the API has no dedicated assignment-submission
 * endpoint, only the generic lesson-progress call, and the web app itself
 * just points students at Messages to submit work, then "Mark complete"
 * — messaging is out of scope for this native pass, so that link is
 * dropped and the instructions/metadata are shown read-only.
 */
@Composable
fun AssignmentLessonContent(
    lesson: LearningLesson,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = parseAssignment(lesson.body)
    if (details == null) {
        ArticleLessonContent(lesson, onToggleComplete, modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(details.instructions, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("DUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(details.dueDate ?: "No due date", style = MaterialTheme.typography.bodyMedium)
            }
            Column {
                Text("MARKS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(details.maxMarks, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            "Submit your work to your instructor, then mark this lesson complete.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onToggleComplete) {
            Text(if (lesson.completed) "Mark incomplete" else "Mark complete")
        }
    }
}
