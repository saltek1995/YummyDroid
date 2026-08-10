package me.yummydroid.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scheduleTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val detailedTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

internal fun formatScheduleTimestamp(seconds: Long): String {
    return Instant.ofEpochSecond(seconds).formatAtSystemZone(scheduleTimestampFormatter)
}

internal fun formatCommentTimestamp(seconds: Long): String {
    return formatFlexibleTimestamp(seconds)
}

internal fun formatNotificationTimestamp(seconds: Long): String {
    return formatFlexibleTimestamp(seconds)
}

private fun formatFlexibleTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val instant = if (timestamp > MAX_EPOCH_SECONDS) {
        Instant.ofEpochMilli(timestamp)
    } else {
        Instant.ofEpochSecond(timestamp)
    }
    return instant.formatAtSystemZone(detailedTimestampFormatter)
}

private fun Instant.formatAtSystemZone(formatter: DateTimeFormatter): String {
    return atZone(ZoneId.systemDefault()).format(formatter)
}

private const val MAX_EPOCH_SECONDS = 10_000_000_000L
