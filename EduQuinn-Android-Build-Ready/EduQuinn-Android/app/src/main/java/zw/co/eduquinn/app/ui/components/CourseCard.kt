package zw.co.eduquinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zw.co.eduquinn.app.data.model.Course

/**
 * The courses endpoints (api/index.js) return no thumbnail/cover image —
 * the schema only has course_assets rows (kind='thumbnail') that are never
 * joined back into /api/courses or /api/courses/:id. The website's own
 * cards lean on a category icon badge instead of a photo for exactly this
 * reason (see app.js's iconFor()); courseIcon() in CategoryIcons.kt ports
 * that same mapping rather than wiring up an image loader against a URL
 * that doesn't exist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseCard(
    course: Course,
    isWishlisted: Boolean,
    isInCart: Boolean,
    onClick: () -> Unit,
    onToggleWishlist: () -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                courseIcon(course.category),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp).align(Alignment.Center),
            )
            IconButton(onClick = onToggleWishlist, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(
                    if (isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isWishlisted) "Remove from wishlist" else "Add to wishlist",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            course.category?.let {
                Text(
                    it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Text(
                course.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            course.instructorName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(6.dp))
            // FlowRow (not Row) for the same half-width-card reason as the
            // price/button row below: the "N students" meta text can be too
            // long to fit beside the star rating once the card is half the
            // screen width in the bento grid, so it wraps instead of
            // clipping or spilling past the card's edge.
            FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RatingStars(course.rating, course.reviewCount)
                // Mirrors the web card's meta line ("4.8 ★ · 312 students") —
                // a separate field from reviewCount, so this is appended here
                // rather than folded into RatingStars itself.
                if (course.students > 0) {
                    Text(
                        " · ${course.students} student${if (course.students == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            // FlowRow instead of a plain Row: at the bento grid's half-card
            // width (2 columns on the home screen) "Add to cart" no longer
            // fits beside the price on one line - a plain SpaceBetween Row
            // would just clip it. FlowRow wraps the button onto its own
            // line instead, which is the behavior the approved mockup's
            // half-width course tiles use.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PriceTag(course.priceCents, course.currency)
                OutlinedButton(
                    onClick = onAddToCart,
                    enabled = !isInCart,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(if (isInCart) "In cart" else "Add to cart", maxLines = 1)
                }
            }
        }
    }
}
