package me.yummydroid.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// AllohaRuntimeStreams
internal fun String.extractAllohaRuntimeStreams(baseUrl: String): List<AllohaRuntimeStream> {
    val payload = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(this) as? JsonObject }.getOrNull()
        ?: return emptyList()
    return payload.allohaRuntimeSourceContainers()
        .flatMap { source -> source.collectAllohaRuntimeStreams(baseUrl, inheritedHeight = null) }
        .distinctBy { it.url }
}

internal fun String.isAllohaRuntimeStatePayload(): Boolean {
    val payload = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(this) as? JsonObject }.getOrNull()
        ?: return false
    return payload.keys.any { it in ALLOHA_RUNTIME_STATE_KEYS }
}

private fun JsonObject.allohaRuntimeSourceContainers(): List<JsonElement> {
    return listOfNotNull(
        this["hlsSource"],
        this["sources"],
        this["source"],
        this["currentSource"],
    ).ifEmpty { listOf(this) }
}

private fun JsonElement.collectAllohaRuntimeStreams(
    baseUrl: String,
    inheritedHeight: Int?,
): List<AllohaRuntimeStream> {
    return when (this) {
        is JsonArray -> flatMapIndexed { sourceIndex, source ->
            source.collectAllohaRuntimeStreams(baseUrl, inheritedHeight)
                .map { stream -> stream.copy(mirrorIndex = stream.mirrorIndex + sourceIndex * ALLOHA_MIRROR_INDEX_BLOCK) }
        }
        is JsonObject -> collectAllohaRuntimeObjectStreams(baseUrl, inheritedHeight)
        is JsonPrimitive -> collectAllohaRuntimePrimitiveStreams(baseUrl, inheritedHeight)
    }
}

private fun JsonObject.collectAllohaRuntimeObjectStreams(
    baseUrl: String,
    inheritedHeight: Int?,
): List<AllohaRuntimeStream> {
    (this["quality"] as? JsonObject)
        ?.entries
        ?.flatMap { (qualityLabel, qualityValue) ->
            val qualityHeight = qualityLabel.allohaQualityHeight()
                ?: qualityValue.allohaRuntimeHeight()
                ?: inheritedHeight
            qualityValue.collectAllohaRuntimeStreams(baseUrl, qualityHeight)
        }
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    val height = inheritedHeight ?: allohaRuntimeHeight()
    val directStreams = ALLOHA_RUNTIME_STREAM_KEYS
        .flatMap { key -> get(key)?.collectAllohaRuntimeStreams(baseUrl, height).orEmpty() }
        .takeIf { height != null && it.isNotEmpty() }
    if (directStreams != null) return directStreams

    return entries
        .filterNot { (key, _) -> key.isAllohaSubtitleMetadataKey() }
        .filter { (key, _) -> key in ALLOHA_RUNTIME_CONTAINER_KEYS }
        .flatMap { (_, value) -> value.collectAllohaRuntimeStreams(baseUrl, height) }
}

private fun JsonPrimitive.collectAllohaRuntimePrimitiveStreams(
    baseUrl: String,
    inheritedHeight: Int?,
): List<AllohaRuntimeStream> {
    val height = inheritedHeight?.validVideoQualityHeight() ?: return emptyList()
    return contentOrNull
        ?.extractDirectStreamUrls(baseUrl)
        .orEmpty()
        .mapIndexed { mirrorIndex, url ->
            AllohaRuntimeStream(url = url, height = height, mirrorIndex = mirrorIndex)
        }
}

private fun JsonElement.allohaRuntimeHeight(): Int? {
    return when (this) {
        is JsonObject -> entries.firstNotNullOfOrNull { (key, value) ->
            key.takeIf { it in ALLOHA_RUNTIME_QUALITY_KEYS }
                ?.let { (value as? JsonPrimitive)?.contentOrNull?.allohaQualityHeight() }
        }
        is JsonPrimitive -> contentOrNull?.allohaQualityHeight()
        else -> null
    }
}

private fun String.allohaQualityHeight(): Int? {
    return AllohaQualityHeightPattern.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        .validVideoQualityHeight()
}

private val AllohaQualityHeightPattern =
    Regex("""(?:^|[^\d])(2160|1440|1080|720|576|540|480|360|240|144)p?(?:[^\d]|$)""")

private fun String.isAllohaSubtitleMetadataKey(): Boolean {
    val key = lowercase()
    return "subtitle" in key || "caption" in key || "texttrack" in key
}

internal fun List<AllohaRuntimeStream>.sortedForPreferredQuality(
    preferredQuality: PreferredQuality,
): List<AllohaRuntimeStream> {
    val remaining = toMutableList()
    val sorted = mutableListOf<AllohaRuntimeStream>()
    while (remaining.isNotEmpty()) {
        val selected = remaining.selectForPreferredQuality(
            preferredQuality = preferredQuality,
            height = AllohaRuntimeStream::height,
            priority = { -it.mirrorIndex },
        ) ?: break
        sorted += selected
        remaining.remove(selected)
    }
    return sorted.distinctBy { it.url }
}

private const val ALLOHA_MIRROR_INDEX_BLOCK = 1_000

private val ALLOHA_RUNTIME_CONTAINER_KEYS = setOf(
    "hlsSource",
    "sources",
    "source",
    "currentSource",
    "items",
    "files",
    "videos",
    "quality",
)

private val ALLOHA_RUNTIME_STATE_KEYS = ALLOHA_RUNTIME_CONTAINER_KEYS + setOf(
    "textTracks",
    "captions",
)

private val ALLOHA_RUNTIME_STREAM_KEYS = setOf(
    "file",
    "src",
    "url",
    "hls",
    "m3u8",
    "stream",
    "link",
)

private val ALLOHA_RUNTIME_QUALITY_KEYS = setOf(
    "quality",
    "label",
    "title",
    "name",
    "height",
    "resolution",
)
