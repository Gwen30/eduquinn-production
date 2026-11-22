package zw.co.eduquinn.app.ui.screens.instructor

/**
 * Small money/number formatting shared across the Instructor Studio
 * screens. [zw.co.eduquinn.app.ui.components.PriceTag] only takes an Int
 * (course prices), while earnings/payout figures come back as Long cents,
 * so this is a separate plain function rather than a reused component.
 */
fun formatCents(cents: Long, currency: String = "USD"): String {
    val symbol = if (currency == "USD") "$" else "$currency "
    return "%s%.2f".format(symbol, cents / 100.0)
}

fun formatCentsInt(cents: Int, currency: String = "USD"): String = formatCents(cents.toLong(), currency)
