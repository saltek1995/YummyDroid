package me.yummydroid.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val scheduleTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val commentTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val watchedAtTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")

internal fun formatByteSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f ГБ", safeBytes / 1_073_741_824.0)
        safeBytes >= 1_048_576L -> String.format(Locale.US, "%.1f МБ", safeBytes / 1_048_576.0)
        safeBytes >= 1024L -> String.format(Locale.US, "%.0f КБ", safeBytes / 1024.0)
        else -> "$safeBytes Б"
    }
}

internal fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / 60
    val rest = seconds % 60
    return "%d:%02d".format(Locale.US, minutes, rest)
}

internal fun formatViews(views: Long): String {
    return when {
        views >= 10_000_000 -> String.format(Locale.US, "%.0f млн", views / 1_000_000.0)
        views >= 1_000_000 -> String.format(Locale.US, "%.1f млн", views / 1_000_000.0)
        views >= 100_000 -> String.format(Locale.US, "%.0f тыс", views / 1_000.0)
        views >= 1_000 -> String.format(Locale.US, "%.1f тыс", views / 1_000.0)
        else -> "$views"
    }
}

internal fun formatRating(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

internal fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3_600
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

internal fun formatScheduleTimestamp(seconds: Long): String {
    return Instant.ofEpochSecond(seconds)
        .atZone(ZoneId.systemDefault())
        .format(scheduleTimestampFormatter)
}

internal fun formatCommentTimestamp(seconds: Long): String {
    if (seconds <= 0L) return ""
    val instant = if (seconds > 10_000_000_000L) {
        Instant.ofEpochMilli(seconds)
    } else {
        Instant.ofEpochSecond(seconds)
    }
    return instant
        .atZone(ZoneId.systemDefault())
        .format(commentTimestampFormatter)
}

internal fun formatWatchedAtTimestamp(timestampMs: Long): String? {
    return timestampMs
        .takeIf { it > 0L }
        ?.let { Instant.ofEpochMilli(it) }
        ?.atZone(ZoneId.systemDefault())
        ?.format(watchedAtTimestampFormatter)
}
