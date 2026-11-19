package zw.co.eduquinn.app.ui.screens.player.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import zw.co.eduquinn.app.data.model.Assessment
import zw.co.eduquinn.app.data.model.AssessmentResult
import zw.co.eduquinn.app.data.model.LearningLesson

private fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Int -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.filterIsInstance<Int>().map { JsonPrimitive(it) })
    else -> JsonNull
}

/**
 * Submits answers as a flat array positionally aligned with
 * [Assessment.questions] (already sorted by position from the API) — see
 * AssessmentSubmitRequest's kdoc for why this can't be a typed,
 * question-id-keyed payload.
 */
@Composable
fun QuizLessonContent(
    lesson: LearningLesson,
    assessment: Assessment?,
    onSubmit: (assessmentId: String, lessonId: String, answers: List<JsonElement>, onResult: (AssessmentResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (assessment == null) {
        Text(
            "This quiz is not available yet.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier.fillMaxWidth().padding(16.dp),
        )
        return
    }

    var lastResult by remember(assessment.id) { mutableStateOf<AssessmentResult?>(null) }
    var answers by remember(assessment.id) { mutableStateOf<Map<Int, Any?>>(emptyMap()) }
    var isSubmitting by remember(assessment.id) { mutableStateOf(false) }

    val exhausted = assessment.attemptsUsed >= assessment.maxAttempts && lastResult == null

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(assessment.title, style = MaterialTheme.typography.headlineSmall)
        assessment.instructions?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val result = lastResult
        when {
            result != null -> QuizResultView(result = result, onRetake = { lastResult = null; answers = emptyMap() })
            exhausted -> Text(
                "You have used all ${assessment.maxAttempts} attempt${if (assessment.maxAttempts == 1) "" else "s"} for this quiz.",
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                assessment.questions.forEachIndexed { index, question ->
                    QuizQuestionCard(
                        index = index,
                        question = question,
                        answer = answers[index],
                        onAnswerChange = { answers = answers + (index to it) },
                        enabled = !isSubmitting,
                    )
                }
                Text(
                    "Attempt ${assessment.attemptsUsed + 1} of ${assessment.maxAttempts}" +
                        (assessment.timeLimitMinutes?.takeIf { it > 0 }?.let { " · Time limit $it min" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        isSubmitting = true
                        val positional = assessment.questions.indices.map { toJsonElement(answers[it]) }
                        onSubmit(assessment.id, lesson.id, positional) { newResult ->
                            isSubmitting = false
                            lastResult = newResult
                        }
                    },
                ) {
                    Text(if (isSubmitting) "Submitting…" else "Submit answers")
                }
            }
        }
    }
}
