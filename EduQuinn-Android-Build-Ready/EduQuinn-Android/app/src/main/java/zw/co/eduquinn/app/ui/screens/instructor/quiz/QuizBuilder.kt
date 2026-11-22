package zw.co.eduquinn.app.ui.screens.instructor.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.QuizDraft
import zw.co.eduquinn.app.data.model.QuizOptionDraft
import zw.co.eduquinn.app.data.model.QuizQuestionDraft

/**
 * The 4 question types the real quiz builder (studio.js's quiz editor)
 * supports, with the backend's own wire values as [value].
 */
enum class QuizQuestionType(val value: String, val label: String) {
    Single("single", "Single choice"),
    Multiple("multiple", "Multiple choice"),
    TrueFalse("true_false", "True / False"),
    ShortAnswer("short_answer", "Short answer"),
}

class QuizOptionDraftState(text: String = "", correct: Boolean = false) {
    var text by mutableStateOf(text)
    var correct by mutableStateOf(correct)
}

/**
 * One question's editable state. [rebuildOptionsForType] is the fix for
 * the 3 bugs already found and fixed in the approved HTML mockup: it
 * always CLEARS and rebuilds the option list from scratch on a type
 * change, rather than reinterpreting whatever options happened to be
 * there already — true_false becomes exactly 2 fixed, non-removable,
 * pre-filled True/False rows (never free text, so they can't drift from
 * "True"/"False"); short_answer becomes a list of accepted-answer text
 * fields with no correct-marker at all (a lone radio button there was
 * meaningless); single/multiple become 2 blank, freely add/removable rows.
 */
class QuizQuestionDraftState(
    prompt: String = "",
    type: QuizQuestionType = QuizQuestionType.Single,
    marks: Int = 1,
) {
    var prompt by mutableStateOf(prompt)
    var type by mutableStateOf(type)
        private set
    var marks by mutableStateOf(marks)
    var explanation by mutableStateOf("")
    val options = mutableStateListOf<QuizOptionDraftState>()

    init { rebuildOptionsForType() }

    fun changeType(newType: QuizQuestionType) {
        type = newType
        rebuildOptionsForType()
    }

    private fun rebuildOptionsForType() {
        options.clear()
        when (type) {
            QuizQuestionType.TrueFalse -> {
                options.add(QuizOptionDraftState("True", correct = true))
                options.add(QuizOptionDraftState("False", correct = false))
            }
            QuizQuestionType.ShortAnswer -> {
                options.add(QuizOptionDraftState())
            }
            QuizQuestionType.Single, QuizQuestionType.Multiple -> {
                options.add(QuizOptionDraftState())
                options.add(QuizOptionDraftState())
            }
        }
    }

    fun addOption() {
        if (type == QuizQuestionType.ShortAnswer) options.add(QuizOptionDraftState()) // another accepted answer
        else if (type == QuizQuestionType.Single || type == QuizQuestionType.Multiple) options.add(QuizOptionDraftState())
    }

    fun removeOption(option: QuizOptionDraftState) {
        // true_false's 2 rows are fixed and never removable.
        if (type == QuizQuestionType.TrueFalse) return
        options.remove(option)
    }

    /** Single-choice and true/false are radio-exclusive: picking one clears every other option's correct flag. */
    fun setCorrect(option: QuizOptionDraftState, correct: Boolean) {
        if (type == QuizQuestionType.Single || type == QuizQuestionType.TrueFalse) {
            if (correct) options.forEach { it.correct = false }
            option.correct = correct
        } else {
            option.correct = correct
        }
    }
}

/**
 * Backs the whole quiz builder sheet. Question numbers shown in the UI are
 * ALWAYS `questions.indexOf(q) + 1`, computed at render time — never a
 * separately stored counter — so deleting question 2 of 3 instantly
 * renumbers question 3 down to 2 rather than leaving a stale "Question 3"
 * label, the exact renumbering bug already caught and fixed in the
 * approved mockup (`studioRenumberQuizQuestions()`).
 */
class QuizBuilderState {
    var passMark by mutableStateOf(70)
    var maxAttempts by mutableStateOf(3)
    var timeLimitMinutes by mutableStateOf(0)
    var shuffleQuestions by mutableStateOf(false)
    var showAnswers by mutableStateOf(true)
    var instructions by mutableStateOf("")
    val questions = mutableStateListOf<QuizQuestionDraftState>()

    fun addQuestion() {
        questions.add(QuizQuestionDraftState())
    }

    fun removeQuestion(question: QuizQuestionDraftState) {
        questions.remove(question)
    }

    /** Fails fast client-side before this ever reaches the backend's own validation. Returns the first problem found, or null if the draft is submittable. */
    fun validate(): String? {
        if (questions.isEmpty()) return "Add at least one question."
        questions.forEachIndexed { index, q ->
            val n = index + 1
            if (q.prompt.isBlank()) return "Question $n needs a prompt."
            when (q.type) {
                QuizQuestionType.ShortAnswer -> {
                    if (q.options.all { it.text.isBlank() }) return "Question $n needs at least one accepted answer."
                }
                QuizQuestionType.TrueFalse -> {
                    if (q.options.count { it.correct } != 1) return "Question $n needs exactly one correct answer."
                }
                QuizQuestionType.Single -> {
                    if (q.options.size < 2) return "Question $n needs at least 2 options."
                    if (q.options.any { it.text.isBlank() }) return "Question $n has a blank option."
                    if (q.options.count { it.correct } != 1) return "Question $n needs exactly one correct answer."
                }
                QuizQuestionType.Multiple -> {
                    if (q.options.size < 2) return "Question $n needs at least 2 options."
                    if (q.options.any { it.text.isBlank() }) return "Question $n has a blank option."
                    if (q.options.none { it.correct }) return "Question $n needs at least one correct answer."
                }
            }
        }
        return null
    }

    fun toDraft(): QuizDraft = QuizDraft(
        passMark = passMark,
        maxAttempts = maxAttempts,
        timeLimitMinutes = timeLimitMinutes,
        shuffleQuestions = shuffleQuestions,
        showAnswers = showAnswers,
        instructions = instructions,
        questions = questions.mapIndexed { index, q ->
            QuizQuestionDraft(
                prompt = q.prompt.trim(),
                type = q.type.value,
                options = q.options.mapIndexed { oi, o -> QuizOptionDraft(text = o.text.trim(), correct = o.correct, position = oi + 1) },
                marks = q.marks,
                explanation = q.explanation,
                position = index + 1,
            )
        },
    )
}

@Composable
fun QuizBuilderSection(state: QuizBuilderState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.instructions,
            onValueChange = { state.instructions = it },
            label = { Text("Instructions for students") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.passMark.toString(),
                onValueChange = { state.passMark = it.toIntOrNull() ?: state.passMark },
                label = { Text("Pass mark %") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.maxAttempts.toString(),
                onValueChange = { state.maxAttempts = it.toIntOrNull() ?: state.maxAttempts },
                label = { Text("Max attempts") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = state.timeLimitMinutes.toString(),
            onValueChange = { state.timeLimitMinutes = it.toIntOrNull() ?: state.timeLimitMinutes },
            label = { Text("Time limit (minutes, 0 = none)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Shuffle questions", modifier = Modifier.weight(1f))
            Switch(checked = state.shuffleQuestions, onCheckedChange = { state.shuffleQuestions = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Show answers after submit", modifier = Modifier.weight(1f))
            Switch(checked = state.showAnswers, onCheckedChange = { state.showAnswers = it })
        }

        Text("Questions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        state.questions.forEachIndexed { index, question ->
            QuestionCard(
                number = index + 1,
                question = question,
                onRemove = { state.removeQuestion(question) },
            )
        }
        OutlinedButton(onClick = state::addQuestion, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Add question")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionCard(number: Int, question: QuizQuestionDraftState, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Question $number", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove question $number") }
            }
            QuestionTypeDropdown(type = question.type, onTypeChange = question::changeType)
            OutlinedTextField(
                value = question.prompt,
                onValueChange = { question.prompt = it },
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = question.marks.toString(),
                onValueChange = { question.marks = it.toIntOrNull() ?: question.marks },
                label = { Text("Marks") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            when (question.type) {
                QuizQuestionType.TrueFalse -> {
                    // Fixed rows: text is never editable, correctness is the only thing that moves.
                    question.options.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = option.correct, onClick = { question.setCorrect(option, true) })
                            Text(option.text, modifier = Modifier.weight(1f))
                        }
                    }
                }
                QuizQuestionType.ShortAnswer -> {
                    Text("Accepted answers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    question.options.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = option.text,
                                onValueChange = { option.text = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (question.options.size > 1) {
                                IconButton(onClick = { question.removeOption(option) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove accepted answer")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = question::addOption, modifier = Modifier.fillMaxWidth()) { Text("Add accepted answer") }
                }
                QuizQuestionType.Single, QuizQuestionType.Multiple -> {
                    question.options.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (question.type == QuizQuestionType.Single) {
                                RadioButton(selected = option.correct, onClick = { question.setCorrect(option, true) })
                            } else {
                                Checkbox(checked = option.correct, onCheckedChange = { question.setCorrect(option, it) })
                            }
                            OutlinedTextField(
                                value = option.text,
                                onValueChange = { option.text = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (question.options.size > 2) {
                                IconButton(onClick = { question.removeOption(option) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove option")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = question::addOption, modifier = Modifier.fillMaxWidth()) { Text("Add option") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionTypeDropdown(type: QuizQuestionType, onTypeChange: (QuizQuestionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = type.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Question type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuizQuestionType.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { onTypeChange(option); expanded = false })
            }
        }
    }
}
