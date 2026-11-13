package zw.co.eduquinn.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@Composable
fun PriceTag(priceCents: Int, currency: String = "USD", modifier: Modifier = Modifier) {
    val symbol = if (currency == "USD") "$" else "$currency "
    val amount = priceCents / 100.0
    Text(
        text = if (priceCents == 0) "Free" else "%s%.2f".format(symbol, amount),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}
