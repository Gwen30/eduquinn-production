package zw.co.eduquinn.app.ui.screens.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.LearningLesson

@Composable
fun LessonListSidebar(
    lessons: List<LearningLesson>,
    currentLessonId: String?,
    onSelect: (String) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            "Course content",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        lessons.forEach { lesson ->
            NavigationDrawerItem(
                label = {
                    Row {
                        Text(lesson.title, modifier = Modifier.fillMaxWidth())
                    }
                },
                selected = lesson.id == currentLessonId,
                onClick = { onSelect(lesson.id) },
                icon = { Icon(iconFor(lesson), contentDescription = null) },
                badge = {
                    if (lesson.completed) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Not completed")
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

private fun iconFor(lesson: LearningLesson): ImageVector = when (lesson.lessonType) {
    "video" -> Icons.Filled.PlayCircle
    "quiz" -> Icons.Filled.Quiz
    "assignment" -> Icons.Filled.Assignment
    "resource" -> Icons.Filled.Description
    else -> Icons.Filled.Article
}
