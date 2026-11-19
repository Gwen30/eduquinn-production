package zw.co.eduquinn.app.ui.screens.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.Course
import zw.co.eduquinn.app.data.model.PublicStats
import zw.co.eduquinn.app.ui.components.CATALOG_CATEGORIES
import zw.co.eduquinn.app.ui.components.CATALOG_STAGES
import zw.co.eduquinn.app.ui.components.CategoryOption
import zw.co.eduquinn.app.ui.components.PriceTag
import zw.co.eduquinn.app.ui.components.RatingStars
import zw.co.eduquinn.app.ui.components.StageOption
import zw.co.eduquinn.app.ui.components.courseIcon
import zw.co.eduquinn.app.ui.components.pillIcon
import zw.co.eduquinn.app.ui.components.stageIcon

/**
 * The hero section from index.html: eyebrow, headline, subhead, a search
 * field, and the 3-number stats card ("Students" / "Published courses" /
 * "Instructors") fed by /api/public/stats — ported 1:1 rather than
 * reinventing the copy.
 */
@Composable
fun CatalogHero(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    stats: PublicStats?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "LEARNING WITHOUT LIMITS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Learn skills. Advance your future.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Explore practical courses, join live classes and learn from trusted instructors.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("What do you want to learn today?") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (stats != null) {
            Spacer(Modifier.size(12.dp))
            Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem(stats.students, "Students")
                    StatItem(stats.courses, "Published courses")
                    StatItem(stats.instructors, "Instructors")
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The 8 fixed category pills from app.js's hardcoded `categories` array. */
@Composable
fun CategoryPillsRow(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(CATALOG_CATEGORIES, key = { it.label }) { option: CategoryOption ->
            Pill(
                label = option.label,
                icon = pillIcon(option.iconKey),
                selected = selected == option.label,
                onClick = { onSelect(option.label) },
            )
        }
    }
}

/** The 5 fixed education-stage cards from app.js's hardcoded stages array. */
@Composable
fun StageStripRow(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(CATALOG_STAGES, key = { it.value }) { option: StageOption ->
            Pill(
                label = option.label,
                icon = stageIcon(option.value),
                selected = selected == option.value,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * The compact "Popular courses" list — same course set as the grid above,
 * ranked by student count, deliberately unaffected by the category/stage/
 * search filters (matches app.js's renderCourses(), which recomputes this
 * from the full unfiltered course list).
 */
@Composable
fun PopularCoursesList(courses: List<Course>, onCourseClick: (Course) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        courses.forEach { course ->
            Card(onClick = { onCourseClick(course) }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            courseIcon(course.category),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            course.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        RatingStars(course.rating, course.reviewCount)
                    }
                    Spacer(Modifier.size(8.dp))
                    PriceTag(course.priceCents, course.currency)
                }
            }
        }
    }
}

/** Static "Become an Instructor" / "Live Classes" promo cards — no live data, same copy as index.html. */
@Composable
fun PromoCardsRow(onInstructorClick: () -> Unit, onLiveClassesClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SHARE KNOWLEDGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Become an Instructor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Teach what you know and earn on EduQuinn.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = onInstructorClick) { Text("Start teaching") }
                }
            }
        }
        Card {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("REAL-TIME LEARNING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Live Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Interact with expert instructors in scheduled sessions.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = onLiveClassesClick) { Text("View upcoming classes") }
                }
            }
        }
    }
}

private data class Benefit(val icon: ImageVector, val title: String, val subtitle: String)

private val BENEFITS = listOf(
    Benefit(Icons.Filled.Sell, "Affordable", "Quality education at fair prices"),
    Benefit(Icons.Filled.Schedule, "Flexible learning", "Learn at your own pace"),
    Benefit(Icons.Filled.Groups, "Expert instructors", "Learn from verified professionals"),
    Benefit(Icons.Filled.Star, "Certificates", "Showcase your achievement"),
    Benefit(Icons.Filled.Shield, "Secure payments", "Safe and reliable checkout"),
)

/** Static 5-item benefits strip — same copy/order as index.html's .benefits section. */
@Composable
fun BenefitsStrip(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BENEFITS.forEach { benefit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(benefit.icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(benefit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(benefit.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
