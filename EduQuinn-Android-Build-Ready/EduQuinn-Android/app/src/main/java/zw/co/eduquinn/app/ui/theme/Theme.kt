package zw.co.eduquinn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Every role is set explicitly here on purpose. lightColorScheme()/
// darkColorScheme() silently fill in any role you don't pass with
// Material3's own baseline (purple-leaning) default tones - that includes
// primaryContainer/secondaryContainer/tertiaryContainer, every
// surfaceContainer tier, surfaceTint, the inverse* roles and scrim. Those
// defaults are exactly what was leaking a pink/lavender cast into the
// bottom nav's selected indicator, card surfaces and app bar tinting
// instead of the site's flat cream/burgundy look - leaving any of them
// unset reopens that bug.
private val EduQuinnLightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Cream,
    primaryContainer = RoseTint,
    onPrimaryContainer = Burgundy,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldTint,
    onSecondaryContainer = Ink,
    tertiary = Wine,
    onTertiary = Cream,
    tertiaryContainer = WineTint,
    onTertiaryContainer = Burgundy,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = Slate,
    surfaceTint = Burgundy,
    surfaceContainerLowest = Cream,
    surfaceContainerLow = Cream,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    inversePrimary = Gold,
    outline = Line,
    outlineVariant = Paper,
    scrim = Scrim,
    error = Wine,
    onError = Cream,
    errorContainer = WineTint,
    onErrorContainer = Burgundy,
)

private val EduQuinnDarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = GoldDeep,
    onPrimaryContainer = Gold,
    secondary = Wine,
    onSecondary = Cream,
    secondaryContainer = WineDeep,
    onSecondaryContainer = Cream,
    tertiary = Burgundy,
    onTertiary = Cream,
    tertiaryContainer = BurgundyDeep,
    onTertiaryContainer = Cream,
    background = Ink,
    onBackground = Cream,
    surface = Slate,
    onSurface = Cream,
    surfaceVariant = Slate,
    onSurfaceVariant = Paper,
    surfaceTint = Gold,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Ink,
    surfaceContainer = Slate,
    surfaceContainerHigh = Slate,
    surfaceContainerHighest = Slate,
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    inversePrimary = Burgundy,
    outline = Slate,
    outlineVariant = Slate,
    scrim = Scrim,
    error = Wine,
    onError = Cream,
    errorContainer = WineDeep,
    onErrorContainer = Cream,
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
