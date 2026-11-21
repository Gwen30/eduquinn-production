package zw.co.eduquinn.app.ui.screens.player.content

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import zw.co.eduquinn.app.EduQuinnApplication
import zw.co.eduquinn.app.data.model.LearningLesson
import zw.co.eduquinn.app.data.network.MediaLinkResolver
import zw.co.eduquinn.app.data.network.NetworkModule

@Composable
fun ResourceLessonContent(
    app: EduQuinnApplication,
    lesson: LearningLesson,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isOpening by remember(lesson.id) { mutableStateOf(false) }
    var openError by remember(lesson.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val text = lesson.body?.trim().orEmpty()
        Text(
            text.ifBlank { "No downloadable file was attached to this resource." },
            style = MaterialTheme.typography.bodyLarge,
        )
        if (lesson.assetId != null) {
            Button(
                onClick = {
                    isOpening = true
                    openError = null
                    scope.launch {
                        val result = MediaLinkResolver.resolveSignedUrl(app.services.okHttpClient, NetworkModule.BASE_URL, lesson.assetId)
                        isOpening = false
                        result.fold(
                            onSuccess = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                            onFailure = { error -> openError = error.message },
                        )
                    }
                },
            ) {
                Text(if (isOpening) "Opening…" else "Open resource")
            }
        }
        openError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = onToggleComplete) {
            Text(if (lesson.completed) "Mark incomplete" else "Mark complete")
        }
    }
}
