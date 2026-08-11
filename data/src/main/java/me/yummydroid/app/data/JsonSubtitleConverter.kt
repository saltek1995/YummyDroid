package me.yummydroid.app.data

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun String.jsonSubtitleToWebVtt(): String? {
    val first = firstOrNull { !it.isWhitespace() } ?: return null
    if (first != '{' && first != '[') return null
    val element = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(this) }.getOrNull() ?: return null
    val cues = element.collectJsonSubtitleCues()
        .distinctBy { cue -> "${cue.startMs}:${cue.endMs}:${cue.settings}:${cue.text}" }
        .sortedWith(compareBy<JsonSubtitleCue> { it.startMs }.thenBy { it.endMs })
        .map { cue ->
            buildString {
                append(cue.startMs.toWebVttTimestamp())
                append(" --> ")
                append(cue.endMs.toWebVttTimestamp())
                cue.settings.takeIf { it.isNotBlank() }?.let { settings ->
                    append(' ')
                    append(settings)
                }
                append('\n')
                append(cue.text)
            }
        }
    return cues.toWebVttDocument()
}

private data class JsonSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val settings: String = "",
)

private fun JsonElement.collectJsonSubtitleCues(): List<JsonSubtitleCue> {
    return when (this) {
        is JsonArray -> flatMap { element -> element.collectJsonSubtitleCues() }
        is JsonObject -> collectJsonSubtitleCuesFromObject()
        else -> emptyList()
    }
}

private fun JsonObject.collectJsonSubtitleCuesFromObject(): List<JsonSubtitleCue> {
    val start = firstSubtitleTimeMs(jsonSubtitleStartKeys)
    val end = firstSubtitleTimeMs(jsonSubtitleEndKeys)
    val duration = firstSubtitleTimeMs(jsonSubtitleDurationKeys)
    val text = firstSubtitleCueText()
    val settings = subtitleCueSettings()
    val selfCue = if (start != null && text != null) {
        val resolvedEnd = end ?: duration?.let(start::plus)
        resolvedEnd
            ?.takeIf { it > start }
            ?.let { JsonSubtitleCue(startMs = start, endMs = it, text = text, settings = settings) }
    } else {
        null
    }

    return listOfNotNull(selfCue) + values.flatMap { element -> element.collectJsonSubtitleCues() }
}

private fun JsonObject.firstSubtitleTimeMs(keys: Set<String>): Long? {
    return entries.firstNotNullOfOrNull { (key, value) ->
        key.jsonSubtitleKeyIdentity()
            .takeIf(keys::contains)
            ?.let { identity -> (value as? JsonPrimitive)?.subtitleTimeMs(identity) }
    }
}

private fun JsonPrimitive.subtitleTimeMs(keyIdentity: String): Long? {
    val raw = contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
    raw.subtitleTimestampMs()?.let { return it }
    val numeric = raw.toDoubleOrNull() ?: return null
    val isMilliseconds = "ms" in keyIdentity || numeric >= JSON_SUBTITLE_MILLISECONDS_THRESHOLD
    return if (isMilliseconds) {
        numeric.roundToLong()
    } else {
        (numeric * 1000.0).roundToLong()
    }.coerceAtLeast(0L)
}

private fun JsonObject.firstSubtitleCueText(): String? {
    firstSubtitleCueText(jsonSubtitlePrimaryTextKeys)?.let { return it }
    if (subtitleCueSettings().isNotBlank()) return null
    return firstSubtitleCueText(jsonSubtitleFallbackTextKeys)
}

private fun JsonObject.firstSubtitleCueText(keys: Set<String>): String? {
    return entries.firstNotNullOfOrNull { (key, value) ->
        key.jsonSubtitleKeyIdentity()
            .takeIf(keys::contains)
            ?.let { (value as? JsonPrimitive)?.contentOrNull }
            ?.subtitleCuePlainText()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun JsonObject.subtitleCueSettings(): String {
    val settings = mutableListOf<String>()
    entries.forEach { (key, value) ->
        val identity = key.jsonSubtitleKeyIdentity()
        val content = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (content.isBlank()) return@forEach
        when {
            identity in jsonSubtitleSettingsKeys -> content
                .toWebVttCueSettings()
                ?.let(settings::add)
            identity == "line" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "line:$it" }
            identity == "position" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "position:$it" }
            identity == "size" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "size:$it" }
            identity == "align" -> content
                .toWebVttCueAlign()
                ?.let { settings += "align:$it" }
            identity == "vertical" -> content
                .takeIf { it == "rl" || it == "lr" }
                ?.let { settings += "vertical:$it" }
            identity == "region" -> content
                .takeIf { it.isWebVttCueSettingToken() }
                ?.let { settings += "region:$it" }
        }
    }
    return settings.distinct().joinToString(" ")
}

private fun String.subtitleCuePlainText(): String {
    return replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""\\[Nn]"""), "\n")
        .visibleSubtitleText()
}

private fun String.toWebVttCueSettings(): String? {
    val settings = trim()
    if (settings.isBlank()) return null
    return settings
        .split(Regex("""\s+"""))
        .mapNotNull { token ->
            val name = token.substringBefore(':').jsonSubtitleKeyIdentity()
            val value = token.substringAfter(':', "").trim()
            when (name) {
                "line" -> value.toWebVttCueSettingValue()?.let { "line:$it" }
                "position" -> value.toWebVttCueSettingValue()?.let { "position:$it" }
                "size" -> value.toWebVttCueSettingValue()?.let { "size:$it" }
                "align" -> value.lowercase()
                    .toWebVttCueAlign()
                    ?.let { "align:$it" }
                "vertical" -> value.lowercase()
                    .takeIf { it == "rl" || it == "lr" }
                    ?.let { "vertical:$it" }
                "region" -> value
                    .takeIf { it.isWebVttCueSettingToken() }
                    ?.let { "region:$it" }
                else -> null
            }
        }
        .distinct()
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
}

private fun String.toWebVttCueSettingValue(): String? {
    val value = trim().replace(',', '.')
    return value.takeIf { setting ->
        setting.equals("auto", ignoreCase = true) ||
            setting.matches(Regex("""-?\d+(?:\.\d+)?%?(?:,(?:start|center|end|line-left|line-right))?"""))
    }
}

private fun String.isWebVttCueSettingToken(): Boolean {
    return matches(Regex("""[A-Za-z0-9_-]+"""))
}

private fun String.toWebVttCueAlign(): String? {
    return when (lowercase()) {
        "middle" -> "center"
        in webVttCueAlignValues -> lowercase()
        else -> null
    }
}

private fun String.jsonSubtitleKeyIdentity(): String {
    return lowercase().replace(Regex("""[^a-z0-9]"""), "")
}

private val jsonSubtitleStartKeys = setOf(
    "start",
    "starttime",
    "begin",
    "from",
    "time",
    "startms",
)

private val jsonSubtitleEndKeys = setOf(
    "end",
    "endtime",
    "stop",
    "finish",
    "to",
    "endms",
)

private val jsonSubtitleDurationKeys = setOf(
    "duration",
    "dur",
    "length",
    "durationms",
)

private val jsonSubtitlePrimaryTextKeys = setOf(
    "text",
    "content",
    "caption",
    "subtitle",
    "body",
    "value",
)

private val jsonSubtitleFallbackTextKeys = setOf("line")

private val jsonSubtitleSettingsKeys = setOf(
    "settings",
    "cuesettings",
    "vttsettings",
    "webvttsettings",
)

private val webVttCueAlignValues = setOf("start", "center", "end", "left", "right")

private const val JSON_SUBTITLE_MILLISECONDS_THRESHOLD = 10_000.0
