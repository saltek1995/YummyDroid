package me.yummydroid.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun String.extractAllohaRuntimeStreams(baseUrl: String): List<AllohaRuntimeStream> {
    val payload = runCatching { VideoStreamResolver.json.parseToJsonElement(this) as? JsonObject }.getOrNull()
        ?: return emptyList()
    val sources = payload["hlsSource"] as? JsonArray ?: return emptyList()
    return sources
        .flatMap sourceMap@ { source ->
            val qualities = (source as? JsonObject)?.get("quality") as? JsonObject
                ?: return@sourceMap emptyList()
            qualities.flatMap qualityMap@ { (qualityLabel, value) ->
                val height = qualityLabel.filter(Char::isDigit).toIntOrNull()
                    ?: return@qualityMap emptyList()
                (value as? JsonPrimitive)
                    ?.contentOrNull
                    ?.extractDirectStreamUrls(baseUrl)
                    .orEmpty()
                    .mapIndexed { mirrorIndex, url ->
                        AllohaRuntimeStream(url = url, height = height, mirrorIndex = mirrorIndex)
                    }
            }
        }
        .distinctBy { it.url }
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
