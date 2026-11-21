package zw.co.eduquinn.app.ui.theme

import androidx.compose.ui.graphics.Color

// Core brand tokens — burgundy/cream/ink already live in res/values/colors.xml
// for the splash/status-bar theme; wine/gold/slate/paper/line were previously
// only defined inline in the web app's offline.html CSS. These Compose
// tokens are the single source of truth for everything drawn inside Compose.
val Burgundy = Color(0xFF702632)
val Wine = Color(0xFF912F40)
val Gold = Color(0xFFC8922F)
val Slate = Color(0xFF40434E)
val Cream = Color(0xFFFFFFFA)
val Paper = Color(0xFFF7F2ED)
val Line = Color(0xFFE8DDD5)
val Ink = Color(0xFF080705)

// Pale tonal tints for Material3's "container" roles (primaryContainer,
// secondaryContainer, etc.) and the dark theme's deep equivalents. The
// website has no such concept — every surface is flat cream/white or
// flat burgundy — so these only exist to stop Material3 from silently
// falling back to its own baked-in default (purple-ish) tones wherever
// a component reads a container/surface-container/inverse role we
// don't otherwise use. Keeping them as muted, on-brand tints rather
// than leaving them unset is what keeps the bottom nav, card surfaces
// and any elevation tinting from drifting away from the site's look.
val RoseTint = Color(0xFFF4DCE1)
val GoldTint = Color(0xFFFBEEDA)
val WineTint = Color(0xFFF6DEE2)
val BurgundyDeep = Color(0xFF4A1620)
val WineDeep = Color(0xFF3D1019)
val GoldDeep = Color(0xFF4A3B12)
val Scrim = Color(0xFF000000)
