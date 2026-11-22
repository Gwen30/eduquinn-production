package zw.co.eduquinn.app.ui.screens.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.R
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
        Spacer(Modifier.size(12.dp))
        // Bento redesign: the photo card and the live-stats card used to
        // stack full-width, one above the other. The approved mockup puts
        // them side by side as two tiles within the hero's own grid span —
        // this Row is that change, not a new component.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroIllustrationCard(modifier = Modifier.weight(1f))
            if (stats != null) {
                Card(
                    modifier = Modifier.weight(1f).height(150.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StatItem(stats.students, "Students")
                        StatItem(stats.courses, "Published courses")
                        StatItem(stats.instructors, "Instructors")
                    }
                }
            }
        }
    }
}

/**
 * The hero's decorative `.portrait-card` from index.html: a photo with a
 * floating "Keep learning." note, shown above the stats card. The real
 * site keeps this visible at every width (its mobile media query only
 * resizes it, never hides it), so this isn't optional polish — leaving it
 * out was an actual gap from the website's layout. R.drawable.hero_illustration
 * is the person-at-a-laptop photo supplied for this card specifically.
 */
@Composable
private fun HeroIllustrationCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_illustration),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("Keep learning.", style = MaterialTheme.typography.labelSmall)
                Text("You're doing great!", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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

/**
 * "Become an Instructor" / "Live Classes" promo cards — no live data, same
 * copy as index.html. Split into two standalone composables (this used to
 * be one PromoCardsRow stacking both in a Column) so the bento home grid
 * can place each as its own half-width grid cell instead of a single
 * full-width block.
 */
@Composable
fun InstructorPromoCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("SHARE KNOWLEDGE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("Become an Instructor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Teach what you know and earn on EduQuinn.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onClick) { Text("Start teaching") }
        }
    }
}

@Composable
fun LiveClassesPromoCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(10.dp))
            Text("REAL-TIME LEARNING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text("Live Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Interact with expert instructors in scheduled sessions.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.size(8.dp))
            OutlinedButton(onClick = onClick) { Text("View upcoming classes") }
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

private data class BusinessFeature(val icon: ImageVector, val title: String, val subtitle: String)

private val BUSINESS_FEATURES = listOf(
    BusinessFeature(Icons.Filled.Business, "Organisation accounts", "Manage teams in one place"),
    BusinessFeature(Icons.Filled.BarChart, "Progress analytics", "Track learning outcomes"),
    BusinessFeature(Icons.Filled.School, "Assigned learning", "Create focused learning paths"),
)

/**
 * The dark "EduQuinn Business" CTA block from index.html's `.business`
 * section — sits between the benefits strip and the footer on the real
 * site, and was missing from the native home screen entirely. Uses the
 * theme's inverse* roles (set explicitly in Theme.kt) rather than new
 * color tokens, since those already give the flat near-black-on-cream
 * flip this card needs.
 */
@Composable
fun BusinessPromoSection(onExploreClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "EDUQUINN BUSINESS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inversePrimary,
            )
            Spacer(Modifier.size(8.dp))
            Text("Build stronger teams through learning", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(8.dp))
            Text(
                "Assign courses, track progress and develop workplace skills from one platform.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(16.dp))
            Button(onClick = onExploreClick) { Text("Explore business plans") }
            Spacer(Modifier.size(20.dp))
            BUSINESS_FEATURES.forEach { feature ->
                Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(feature.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(feature.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(feature.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
