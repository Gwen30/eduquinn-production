package zw.co.eduquinn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * joined back into /api/courses or /api/courses/:id. So this card leans
 * on a branded color block + initials instead of a photo, rather than
 * wiring up an image loader against a URL that doesn't exist.
 */
@Composable
fun CourseCard(
    course: Course,
    isWishlisted: Boolean,
    onClick: () -> Unit,
    onToggleWishlist: () -> Unit,
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
            Text(
                text = initialsFor(course.title),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center),
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
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RatingStars(course.rating, course.reviewCount)
                PriceTag(course.priceCents, course.currency)
            }
        }
    }
}

private fun initialsFor(title: String): String =
    title.trim().split(" ").filter { it.isNotEmpty() }.take(2).map { it.first().uppercaseChar() }.joinToString("")
