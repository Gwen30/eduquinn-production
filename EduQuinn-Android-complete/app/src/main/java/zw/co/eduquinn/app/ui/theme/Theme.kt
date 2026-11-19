package zw.co.eduquinn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val EduQuinnLightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Cream,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Wine,
    onTertiary = Cream,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Slate,
    outline = Line,
    error = Wine,
    onError = Cream,
)

private val EduQuinnDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    secondary = Wine,
    onSecondary = Cream,
    tertiary = Burgundy,
    onTertiary = Cream,
    background = Ink,
    onBackground = Cream,
    surface = Slate,
    onSurface = Cream,
    surfaceVariant = Slate,
    onSurfaceVariant = Paper,
    outline = Slate,
    error = Wine,
    onError = Cream,
)

@Composable
fun EduQuinnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) EduQuinnDarkColors else EduQuinnLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EduQuinnTypography,
        content = content,
    )
}
