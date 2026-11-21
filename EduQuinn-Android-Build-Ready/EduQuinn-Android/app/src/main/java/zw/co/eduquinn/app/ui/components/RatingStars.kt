package zw.co.eduquinn.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RatingStars(rating: Double?, reviewCount: Int = 0, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (rating != null) "%.1f".format(rating) else "New",
            style = MaterialTheme.typography.labelMedium,
        )
        if (reviewCount > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "($reviewCount)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
