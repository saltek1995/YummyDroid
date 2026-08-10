package me.yummydroid.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class SubtitleStructuredMetadataParser(
    private val json: Json,
    private val trackClassifier: SubtitleTrackClassifier,
) {
    fun extractTracks(body: String, baseUrl: String): List<ResolvedSubtitleTrack> {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        return element.collectTracks(baseUrl).normalizedSubtitleTracks()
    }

    private fun JsonElement.collectTracks(
        baseUrl: String,
        subtitleContext: Boolean = false,
        inheritedLabel: String = "",
        inheritedLanguage: String? = null,
    ): List<ResolvedSubtitleTrack> {
        return when (this) {
            is JsonArray -> flatMap { item ->
                item.collectTracks(
                    baseUrl = baseUrl,
                    subtitleContext = subtitleContext,
                    inheritedLabel = inheritedLabel,
                    inheritedLanguage = inheritedLanguage,
                )
            }
            is JsonObject -> collectObjectTracks(
                baseUrl = baseUrl,
                subtitleContext = subtitleContext,
                inheritedLabel = inheritedLabel,
                inheritedLanguage = inheritedLanguage,
            )
            is JsonPrimitive -> collectPrimitiveTrack(
                baseUrl = baseUrl,
                subtitleContext = subtitleContext,
                inheritedLabel = inheritedLabel,
                inheritedLanguage = inheritedLanguage,
            )
        }
    }

    private fun JsonPrimitive.collectPrimitiveTrack(
        baseUrl: String,
        subtitleContext: Boolean,
        inheritedLabel: String,
        inheritedLanguage: String?,
    ): List<ResolvedSubtitleTrack> {
        val value = contentOrNull?.trim().orEmpty()
        if (subtitleContext && trackClassifier.looksLikeJsonPayload(value)) {
            val nestedTracks = runCatching { json.parseToJsonElement(value) }
                .getOrNull()
                ?.collectTracks(
                    baseUrl = baseUrl,
                    subtitleContext = true,
                    inheritedLabel = inheritedLabel,
                    inheritedLanguage = inheritedLanguage,
                )
                .orEmpty()
            if (nestedTracks.isNotEmpty()) return nestedTracks
        }
        if (!subtitleContext || !trackClassifier.isResolvableCandidate(value)) return emptyList()
        val uri = trackClassifier.normalizeAgainst(value, baseUrl)
        return listOfNotNull(trackClassifier.potentialTrack(uri, inheritedLabel, inheritedLanguage))
    }

    private fun JsonObject.collectObjectTracks(
        baseUrl: String,
        subtitleContext: Boolean,
        inheritedLabel: String,
        inheritedLanguage: String?,
    ): List<ResolvedSubtitleTrack> {
        val objectContext = subtitleContext ||
            keys.any(trackClassifier::isMetadataKey) ||
            trackClassifier.isDescriptor(firstJsonString("kind", "type", "role").orEmpty())
        val label = firstJsonString("label", "title", "name", "displayName") ?: inheritedLabel
        val language = firstJsonString("language", "lang", "srclang") ?: inheritedLanguage
        val directTracks = entries.mapNotNull { (key, element) ->
            directObjectTrack(key, element, objectContext, baseUrl, label, language)
        }
        val nestedTracks = entries.flatMap { (key, element) ->
            element.collectTracks(
                baseUrl = baseUrl,
                subtitleContext = objectContext || trackClassifier.isMetadataKey(key),
                inheritedLabel = label,
                inheritedLanguage = language,
            )
        }
        return directTracks + nestedTracks
    }

    private fun directObjectTrack(
        key: String,
        element: JsonElement,
        objectContext: Boolean,
        baseUrl: String,
        label: String,
        language: String?,
    ): ResolvedSubtitleTrack? {
        val value = (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (value.isBlank()) return null
        val keySuggestsSubtitle = trackClassifier.isMetadataKey(key) || trackClassifier.isUrlKey(key)
        if (!keySuggestsSubtitle && !objectContext && !trackClassifier.isPotentialRequestUrl(value)) return null
        if (!trackClassifier.isResolvableCandidate(value)) return null
        val uri = trackClassifier.normalizeAgainst(value, baseUrl)
        return trackClassifier.potentialTrack(uri, label, language)
    }

    private fun JsonObject.firstJsonString(vararg names: String): String? {
        val normalizedNames = names.map(String::lowercase).toSet()
        return entries.firstNotNullOfOrNull { (key, value) ->
            key.lowercase()
                .takeIf(normalizedNames::contains)
                ?.let { (value as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.takeIf(String::isNotBlank)
        }
    }
}
