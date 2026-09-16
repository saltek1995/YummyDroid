package me.yummydroid.app.data

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** An absolute deadline, so callers never shorten a server-requested pause. */
fun httpRetryAfterEpochMs(headers: Map<String, List<String>>, nowEpochMs: Long): Long? {
    val value = headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }
        ?.value?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    value.toLongOrNull()?.let { seconds ->
        if (seconds < 0) return null
        val remaining = Long.MAX_VALUE - nowEpochMs.coerceAtLeast(0)
        return if (seconds > remaining / 1000) Long.MAX_VALUE else nowEpochMs + seconds * 1000
    }
    return runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()?.coerceAtLeast(nowEpochMs)
}

open class PlaybackHttpException(
    val statusCode: Int,
    val retryAtEpochMs: Long? = null,
) : IOException("Source returned HTTP $statusCode")
