package zw.co.eduquinn.app.ui.screens.more

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class MoreLink(val icon: ImageVector, val label: String, val url: String, val livePill: Boolean = false)

/**
 * The rest of the website's header nav (public/index.html's #mainNav)
 * that doesn't have a native screen: Categories lives on the Catalog
 * screen itself and My Learning already has its own bottom tab, so this
 * covers everything else. None of these are rebuilt natively — same
 * website hand-off via Custom Tabs already used by CatalogScreen's promo
 * cards and the Paynow checkout flow.
 */
private val MORE_LINKS = listOf(
    MoreLink(Icons.Filled.VideoCameraFront, "Live Classes", "https://eduquinn.co.zw/live-classes.html", livePill = true),
    MoreLink(Icons.Filled.Chat, "Messages", "https://eduquinn.co.zw/messages.html"),
    MoreLink(Icons.Filled.Receipt, "Purchases", "https://eduquinn.co.zw/purchases.html"),
    MoreLink(Icons.Filled.AccountBalance, "EduQuinn Business", "https://eduquinn.co.zw/business.html"),
    MoreLink(Icons.Filled.School, "Instructor Studio", "https://eduquinn.co.zw/instructor.html"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen() {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(MORE_LINKS) { link ->
                Surface(
                    onClick = {
                        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(link.url))
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(link.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(16.dp))
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(link.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            if (link.livePill) {
                                Spacer(Modifier.size(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Text(
                                        "Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
