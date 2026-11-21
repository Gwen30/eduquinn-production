package zw.co.eduquinn.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose counterpart of index.html's `#splash` overlay (badge + "EduQuinn"
 * wordmark + "LEARN · GROW · SUCCEED" tagline + three loading dots). The
 * platform SplashScreen API (installSplashScreen() in NativeActivity) can
 * only show an icon on a flat background, so it's released as soon as the
 * first Compose frame exists; this screen is what actually fills the ~3-5s
 * branded loading window afterwards, and is built to match that same
 * layout rather than invent a new one.
 */
@Composable
fun BrandSplashScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "EQ",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.size(18.dp))
            Text(
                "EduQuinn",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "LEARN  ·  GROW  ·  SUCCEED",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.size(28.dp))
            SplashDots()
        }
    }
}

/** Three dots, one pulsing brighter at a time — the Compose equivalent of the website's CSS dot-loader. */
@Composable
private fun SplashDots() {
    val transition = rememberInfiniteTransition(label = "splash-dots")
    Row {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = InfiniteRepeatableSpec(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
            )
            if (index != 2) Spacer(Modifier.size(8.dp))
        }
    }
}
