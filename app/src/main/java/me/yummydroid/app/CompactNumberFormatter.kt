package me.yummydroid.app

import java.util.Locale

internal fun formatViews(views: Long): String {
    return formatCompactCount(
        value = views,
        thousandSuffix = "K",
        millionSuffix = "M",
    )
}

internal fun formatCompactCount(
    value: Long,
    thousandSuffix: String,
    millionSuffix: String,
): String {
    return when {
        value >= 10_000_000 -> String.format(Locale.US, "%.0f %s", value / 1_000_000.0, millionSuffix)
        value >= 1_000_000 -> String.format(Locale.US, "%.1f %s", value / 1_000_000.0, millionSuffix)
        value >= 100_000 -> String.format(Locale.US, "%.0f %s", value / 1_000.0, thousandSuffix)
        value >= 1_000 -> String.format(Locale.US, "%.1f %s", value / 1_000.0, thousandSuffix)
        else -> "$value"
    }
}

internal fun formatRating(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}
