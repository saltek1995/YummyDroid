package me.yummydroid.app

import java.util.Locale

internal fun formatByteSize(bytes: Long): String {
    return formatByteSize(
        bytes = bytes,
        byteUnit = "B",
        kilobyteUnit = "KB",
        megabyteUnit = "MB",
        gigabyteUnit = "GB",
    )
}

internal fun formatByteSize(
    bytes: Long,
    byteUnit: String,
    kilobyteUnit: String,
    megabyteUnit: String,
    gigabyteUnit: String,
): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= BYTES_PER_GIGABYTE -> String.format(
            Locale.US,
            "%.1f %s",
            safeBytes / BYTES_PER_GIGABYTE.toDouble(),
            gigabyteUnit,
        )
        safeBytes >= BYTES_PER_MEGABYTE -> String.format(
            Locale.US,
            "%.1f %s",
            safeBytes / BYTES_PER_MEGABYTE.toDouble(),
            megabyteUnit,
        )
        safeBytes >= BYTES_PER_KILOBYTE -> String.format(
            Locale.US,
            "%.0f %s",
            safeBytes / BYTES_PER_KILOBYTE.toDouble(),
            kilobyteUnit,
        )
        else -> "$safeBytes $byteUnit"
    }
}

private const val BYTES_PER_KILOBYTE = 1024L
private const val BYTES_PER_MEGABYTE = 1_048_576L
private const val BYTES_PER_GIGABYTE = 1_073_741_824L
