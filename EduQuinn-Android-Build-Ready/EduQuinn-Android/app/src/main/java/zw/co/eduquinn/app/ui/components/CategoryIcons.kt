package zw.co.eduquinn.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** One category pill, mirroring public/app.js's `categories` array exactly (icon key, label). */
data class CategoryOption(val iconKey: String, val label: String)

val CATALOG_CATEGORIES = listOf(
    CategoryOption("code", "Development"),
    CategoryOption("briefcase", "Business"),
    CategoryOption("palette", "Design"),
    CategoryOption("megaphone", "Marketing"),
    CategoryOption("monitor", "IT & Software"),
    CategoryOption("leaf", "Personal Growth"),
    CategoryOption("chart", "Finance"),
    CategoryOption("teacher", "Teaching"),
)

/** One education-stage card, mirroring public/app.js's `educationStages` array (value, label). */
data class StageOption(val value: String, val label: String)

val CATALOG_STAGES = listOf(
    StageOption("primary", "Primary"),
    StageOption("secondary", "Secondary"),
    StageOption("a-level", "A-Level"),
    StageOption("tertiary", "Tertiary"),
    StageOption("professional", "Professional"),
)

/** Direct icon-key -> icon, used by the fixed category pills (not a guess, unlike [courseIcon]). */
fun pillIcon(iconKey: String): ImageVector = when (iconKey) {
    "code" -> Icons.Filled.Code
    "briefcase" -> Icons.Filled.Work
    "palette" -> Icons.Filled.Palette
    "megaphone" -> Icons.Filled.Campaign
    "monitor" -> Icons.Filled.Computer
    "leaf" -> Icons.Filled.Eco
    "chart" -> Icons.Filled.BarChart
    "teacher" -> Icons.Filled.School
    else -> Icons.Filled.School
}

/**
 * A stage's icon: tertiary gets a graduation cap, every other stage a book —
 * ports app.js's `iconSvg(v==='tertiary'?'graduation':'book')` exactly.
 */
fun stageIcon(value: String): ImageVector = if (value == "tertiary") Icons.Filled.School else Icons.Filled.MenuBook

/**
 * Course-card badge icon, guessed from the course's own free-text category
 * field — ports app.js's `iconFor(c)` exactly: same substring checks, same
 * order, same fallback (web's "graduation" default, here the School icon).
 * There is no thumbnail/cover image anywhere in the API (see CourseCard.kt),
 * so this icon badge is the actual course "image" on both web and native.
 */
fun courseIcon(category: String?): ImageVector {
    val v = category.orEmpty().lowercase()
    return when {
        "design" in v -> Icons.Filled.Palette
        "business" in v || "finance" in v -> Icons.Filled.Work
        "marketing" in v -> Icons.Filled.Campaign
        "teach" in v || "education" in v -> Icons.Filled.MenuBook
        "development" in v || "software" in v || "computer" in v -> Icons.Filled.Code
        else -> Icons.Filled.School
    }
}
