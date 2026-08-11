package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.Log
import androidx.annotation.StringRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import me.yummydroid.app.data.ContentLanguage

// AppLog
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message, throwable)
        }
    }
}

// ByteSizeFormatter
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

// CompactNumberFormatter
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

// DurationFormatter
internal fun formatDuration(seconds: Int?): String? {
    if (seconds == null || seconds <= 0) return null
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainingSeconds = seconds % SECONDS_PER_MINUTE
    return "%d:%02d".format(Locale.US, minutes, remainingSeconds)
}

internal fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val hours = totalSeconds / SECONDS_PER_HOUR
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_HOUR = 3_600L

// LocalizedResources
internal fun Context.localizedString(@StringRes resId: Int, language: ContentLanguage): String {
    return localizedConfigurationContext(language).getString(resId)
}

internal fun Context.localizedString(
    @StringRes resId: Int,
    language: ContentLanguage,
    vararg formatArgs: Any,
): String {
    return localizedConfigurationContext(language).getString(resId, *formatArgs)
}

private fun Context.localizedConfigurationContext(language: ContentLanguage): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(Locale.forLanguageTag(language.languageTag)))
    return createConfigurationContext(configuration)
}

private val ContentLanguage.languageTag: String
    get() = when (this) {
        ContentLanguage.Russian -> "ru"
        ContentLanguage.English -> "en"
        ContentLanguage.Ukrainian -> "uk"
    }

// TimestampFormatter
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
