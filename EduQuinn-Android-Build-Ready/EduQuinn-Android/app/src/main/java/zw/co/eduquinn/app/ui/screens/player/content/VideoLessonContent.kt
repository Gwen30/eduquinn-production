package zw.co.eduquinn.app.ui.screens.player.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import zw.co.eduquinn.app.data.model.LearningLesson
import zw.co.eduquinn.app.data.network.NetworkModule

private const val PROGRESS_REPORT_INTERVAL_MS = 15_000L

/**
 * A real native upgrade over the web app's plain `<video>` tag: this
 * actually persists playback position via /api/learning/lessons/:id/progress
 * (the web app hardcodes positionSeconds to 0 and never resumes). Built on
 * ExoPlayer + an OkHttpDataSource wrapping the SAME authenticated
 * OkHttpClient the rest of the app uses — without that, ExoPlayer's
 * default HTTP stack sends no eq_session cookie and /api/media/:assetId
 * requests fail outright.
 */
@Composable
fun VideoLessonContent(
    okHttpClient: OkHttpClient,
    lesson: LearningLesson,
    onProgress: (completed: Boolean, positionSeconds: Int) -> Unit,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onProgressState = rememberUpdatedState(onProgress)

    if (lesson.assetId == null) {
        Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This lesson has no media attached.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onToggleComplete) {
                Text(if (lesson.completed) "Mark incomplete" else "Mark complete")
            }
        }
        return
    }

    val player = remember(lesson.assetId) {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri("${NetworkModule.BASE_URL}api/media/${lesson.assetId}"))
                if (lesson.positionSeconds > 0) seekTo(lesson.positionSeconds * 1000L)
                prepare()
            }
    }

    DisposableEffect(player) {
        onDispose {
            val finalPositionSeconds = (player.currentPosition / 1000L).toInt().coerceAtLeast(0)
            if (finalPositionSeconds > 0) onProgressState.value(lesson.completed, finalPositionSeconds)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_REPORT_INTERVAL_MS)
            if (player.isPlaying) {
                val positionSeconds = (player.currentPosition / 1000L).toInt().coerceAtLeast(0)
                onProgressState.value(lesson.completed, positionSeconds)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        OutlinedButton(onClick = onToggleComplete, modifier = Modifier.padding(16.dp)) {
            Text(if (lesson.completed) "Mark incomplete" else "Mark complete")
        }
    }
}
