package me.yummydroid.app.data

internal fun String.subtitleTimestampMs(): Long? {
    val normalized = trim().replace(',', '.')
    val pieces = normalized.split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size !in 1..2) return null

    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val milliseconds = secondsParts.getOrNull(1)
        ?.padEnd(3, '0')
        ?.take(3)
        ?.toLongOrNull()
        ?: 0L

    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
}

internal fun String.webVttTimestampMs(): Long? {
    val pieces = split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size != 2) return null

    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val milliseconds = secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null

    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
}

internal fun Long.toWebVttTimestamp(): String {
    val safeMs = coerceAtLeast(0L)
    val hours = safeMs / 3_600_000L
    val minutes = (safeMs % 3_600_000L) / 60_000L
    val seconds = (safeMs % 60_000L) / 1_000L
    val milliseconds = safeMs % 1_000L
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
}

internal fun List<String>.toWebVttDocument(): String? {
    if (isEmpty()) return null
    return buildString {
        append("WEBVTT\n\n")
        append(joinToString("\n\n"))
        append('\n')
    }
}
