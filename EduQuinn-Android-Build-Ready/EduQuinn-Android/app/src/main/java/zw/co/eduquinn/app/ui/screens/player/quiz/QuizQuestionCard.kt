package zw.co.eduquinn.app.ui.screens.player.quiz

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.AssessmentQuestion

/**
 * [answer] / [onAnswerChange] carry a type that depends on
 * [AssessmentQuestion.questionType]: Int? for single/true_false (the
 * selected option index), List<Int> for multiple, String for short_answer
 * — matching exactly what the backend's submit handler expects
 * positionally (see QuizLessonContent's kdoc).
 */
@Composable
fun QuizQuestionCard(
    index: Int,
    question: AssessmentQuestion,
    answer: Any?,
    onAnswerChange: (Any?) -> Unit,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            "${index + 1}. ${question.prompt}  (${question.marks} mark${if (question.marks == 1) "" else "s"})",
            style = MaterialTheme.typography.titleSmall,
        )
        when (question.questionType) {
            "short_answer" -> OutlinedTextField(
                value = answer as? String ?: "",
                onValueChange = { onAnswerChange(it) },
                enabled = enabled,
                label = { Text("Your answer") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            "multiple" -> {
                @Suppress("UNCHECKED_CAST")
                val selected = (answer as? List<Int>) ?: emptyList()
                question.options.forEachIndexed { optionIndex, option ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = optionIndex in selected,
                            enabled = enabled,
                            onCheckedChange = { checked ->
                                onAnswerChange(if (checked) selected + optionIndex else selected - optionIndex)
                            },
                        )
                        Text(option.text)
                    }
                }
            }
            else -> { // single or true_false
                val selected = answer as? Int
                question.options.forEachIndexed { optionIndex, option ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selected == optionIndex,
                            enabled = enabled,
                            onClick = { onAnswerChange(optionIndex) },
                        )
                        Text(option.text)
                    }
                }
            }
        }
    }
}
