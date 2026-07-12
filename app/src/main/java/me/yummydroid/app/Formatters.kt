package me.yummydroid.app

import java.util.Locale

internal fun formatByteSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f ГБ", safeBytes / 1_073_741_824.0)
        safeBytes >= 1_048_576L -> String.format(Locale.US, "%.1f МБ", safeBytes / 1_048_576.0)
        safeBytes >= 1024L -> String.format(Locale.US, "%.0f КБ", safeBytes / 1024.0)
        else -> "$safeBytes Б"
    }
}
