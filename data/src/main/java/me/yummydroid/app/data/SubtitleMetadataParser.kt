package me.yummydroid.app.data

import java.io.StringReader
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal class SubtitleMetadataParser(
    private val fallbackSiteBaseUrl: () -> String,
    private val json: Json,
) {
    fun extractTracks(body: String, baseUrl: String): List<ResolvedSubtitleTrack> {
        val normalized = body
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
        val urlTracks = subtitleUrlRegex
            .findAll(normalized)
            .mapNotNull { match ->
                match.value
                    .trim('"', '\'', ' ', '\\')
                    .normalizeAgainst(baseUrl)
                    .toDirectTrack()
            }
            .toList()
        return (urlTracks + normalized.extractStructuredTracks(baseUrl))
            .normalizedSubtitleTracks()
    }

    fun extractDashEmbeddedTracks(body: String): List<ResolvedEmbeddedSubtitleTrack> {
        val document = runCatching {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(body)))
        }.getOrNull() ?: return emptyList()
        val adaptationSets = document.getElementsByTagNameNS("*", "AdaptationSet")
        return (0 until adaptationSets.length)
            .asSequence()
            .mapNotNull { index -> adaptationSets.item(index) as? Element }
            .filter { element -> element.isDashSubtitleAdaptationSet() }
            .map { element -> element.dashEmbeddedSubtitleTrack() }
            .toList()
            .normalizedEmbeddedSubtitleTracks()
    }

    fun extractHlsTracks(body: String, baseUrl: String): SubtitleDetection {
        val tracks = mutableListOf<ResolvedSubtitleTrack>()
        val embeddedTracks = mutableListOf<ResolvedEmbeddedSubtitleTrack>()
        var hasEmbeddedSubtitles = false
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) return@forEach
            when (line.hlsAttribute("TYPE").orEmpty().uppercase()) {
                "SUBTITLES" -> {
                    val uri = line.hlsAttribute("URI") ?: return@forEach
                    if (uri.isBlank()) return@forEach
                    val resolvedUri = uri.resolveUrlAgainst(baseUrl)
                    tracks += ResolvedSubtitleTrack(
                        uri = resolvedUri,
                        label = line.hlsAttribute("NAME").orEmpty()
                            .ifBlank { line.hlsAttribute("GROUP-ID").orEmpty() }
                            .ifBlank { resolvedUri.subtitleLabelFromUrl() },
                        language = line.hlsAttribute("LANGUAGE"),
                        mimeType = resolvedUri.subtitleMimeTypeFromUrl() ?: "application/x-mpegURL",
                    )
                }
                "CLOSED-CAPTIONS" -> {
                    hasEmbeddedSubtitles = true
                    val name = line.hlsAttribute("NAME").orEmpty()
                    val groupId = line.hlsAttribute("GROUP-ID").orEmpty()
                    val instreamId = line.hlsAttribute("INSTREAM-ID").orEmpty()
                    embeddedTracks += ResolvedEmbeddedSubtitleTrack(
                        id = instreamId.ifBlank { groupId }.ifBlank { name },
                        label = name.ifBlank { groupId },
                        language = line.hlsAttribute("LANGUAGE"),
                    )
                }
            }
        }
        return SubtitleDetection(
            tracks = tracks.normalizedSubtitleTracks(),
            embeddedSubtitles = embeddedTracks.normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles,
        )
    }

    fun directTrack(url: String): ResolvedSubtitleTrack? = url.toDirectTrack()

    fun potentialTrack(url: String): ResolvedSubtitleTrack? = url.toPotentialTrack()

    fun isResolvableCandidate(url: String): Boolean = url.isResolvableSubtitleCandidate()

    private fun String.extractStructuredTracks(baseUrl: String): List<ResolvedSubtitleTrack> {
        val element = runCatching { json.parseToJsonElement(this) }.getOrNull() ?: return emptyList()
        return element.collectStructuredTracks(baseUrl).normalizedSubtitleTracks()
    }

    private fun JsonElement.collectStructuredTracks(
        baseUrl: String,
        subtitleContext: Boolean = false,
        inheritedLabel: String = "",
        inheritedLanguage: String? = null,
    ): List<ResolvedSubtitleTrack> {
        return when (this) {
            is JsonArray -> flatMap { item ->
                item.collectStructuredTracks(
                    baseUrl = baseUrl,
                    subtitleContext = subtitleContext,
                    inheritedLabel = inheritedLabel,
                    inheritedLanguage = inheritedLanguage,
                )
            }
            is JsonObject -> collectStructuredTracksFromObject(
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
        if (subtitleContext && value.looksLikeJsonPayload()) {
            val nestedTracks = runCatching { json.parseToJsonElement(value) }
                .getOrNull()
                ?.collectStructuredTracks(
                    baseUrl = baseUrl,
                    subtitleContext = true,
                    inheritedLabel = inheritedLabel,
                    inheritedLanguage = inheritedLanguage,
                )
                .orEmpty()
            if (nestedTracks.isNotEmpty()) return nestedTracks
        }
        if (!subtitleContext || !value.isResolvableSubtitleCandidate()) return emptyList()
        val uri = value.normalizeAgainst(baseUrl)
        return listOfNotNull(uri.toPotentialTrack(inheritedLabel, inheritedLanguage))
    }

    private fun JsonObject.collectStructuredTracksFromObject(
        baseUrl: String,
        subtitleContext: Boolean,
        inheritedLabel: String,
        inheritedLanguage: String?,
    ): List<ResolvedSubtitleTrack> {
        val objectContext = subtitleContext ||
            keys.any { key -> key.isSubtitleMetadataKey() } ||
            firstJsonString("kind", "type", "role").orEmpty().isSubtitleDescriptor()
        val label = firstJsonString("label", "title", "name", "displayName") ?: inheritedLabel
        val language = firstJsonString("language", "lang", "srclang") ?: inheritedLanguage
        val directTracks = entries.mapNotNull { (key, element) ->
            val value = (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (value.isBlank()) return@mapNotNull null
            val keySuggestsSubtitle = key.isSubtitleMetadataKey() || key.isSubtitleUrlKey()
            if (!keySuggestsSubtitle && !objectContext && !value.isPotentialSubtitleRequestUrl()) {
                return@mapNotNull null
            }
            if (!value.isResolvableSubtitleCandidate()) return@mapNotNull null
            value.normalizeAgainst(baseUrl).toPotentialTrack(label, language)
        }
        val nestedTracks = entries.flatMap { (key, element) ->
            element.collectStructuredTracks(
                baseUrl = baseUrl,
                subtitleContext = objectContext || key.isSubtitleMetadataKey(),
                inheritedLabel = label,
                inheritedLanguage = language,
            )
        }
        return directTracks + nestedTracks
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

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setXIncludeAware(false) }
            runCatching { setExpandEntityReferences(false) }
        }
    }

    private fun Element.isDashSubtitleAdaptationSet(): Boolean {
        val contentType = attributeOrBlank("contentType").lowercase()
        val mimeType = attributeOrBlank("mimeType").lowercase()
        val codecs = (
            listOf(attributeOrBlank("codecs")) +
                childElements("Representation").map { it.attributeOrBlank("codecs") }
            )
            .joinToString(",")
            .lowercase()
        val roles = childElements("Role")
            .map { role -> role.attributeOrBlank("value").lowercase() }
        return contentType == "text" ||
            mimeType.startsWith("text/") ||
            "ttml" in mimeType ||
            "vtt" in mimeType ||
            "wvtt" in codecs ||
            "stpp" in codecs ||
            roles.any { role -> role == "subtitle" || role == "caption" }
    }

    private fun Element.dashEmbeddedSubtitleTrack(): ResolvedEmbeddedSubtitleTrack {
        val representations = childElements("Representation")
        val language = attributeOrBlank("lang")
            .ifBlank { getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang") }
            .ifBlank { null }
        val label = childText("Label")
            .ifBlank { attributeOrBlank("label") }
            .ifBlank { attributeOrBlank("name") }
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.childText("Label").takeIf(String::isNotBlank)
                }.orEmpty()
            }
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.attributeOrBlank("label").takeIf(String::isNotBlank)
                }.orEmpty()
            }
        val id = attributeOrBlank("id")
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.attributeOrBlank("id").takeIf(String::isNotBlank)
                }.orEmpty()
            }
            .ifBlank { label }
        return ResolvedEmbeddedSubtitleTrack(id = id, label = label, language = language)
    }

    private fun Element.childElements(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return (0 until nodes.length).mapNotNull { index -> nodes.item(index) as? Element }
    }

    private fun Element.childText(name: String): String {
        return childElements(name).firstOrNull()?.textContent?.trim().orEmpty()
    }

    private fun Element.attributeOrBlank(name: String): String = getAttribute(name).trim()

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }

    private fun String.toDirectTrack(): ResolvedSubtitleTrack? {
        if (!isSubtitleUrl()) return null
        return ResolvedSubtitleTrack(
            uri = this,
            label = subtitleLabelFromUrl(),
            mimeType = subtitleMimeTypeFromUrl(),
        )
    }

    private fun String.toPotentialTrack(
        label: String = "",
        language: String? = null,
    ): ResolvedSubtitleTrack? {
        if (!isResolvableSubtitleCandidate()) return null
        return ResolvedSubtitleTrack(
            uri = this,
            label = label.takeIf(String::isNotBlank) ?: subtitleLabelFromUrl(),
            language = language?.takeIf(String::isNotBlank),
            mimeType = subtitleMimeTypeFromUrl(),
        )
    }

    private fun String.isResolvableSubtitleCandidate(): Boolean {
        val value = trim()
        if (value.isBlank()) return false
        if (value.isSubtitleUrl()) return true
        return value.isUrlLike() && value.isPotentialSubtitleRequestUrl()
    }

    private fun String.isSubtitleUrl(): Boolean {
        val lower = substringBefore('?').substringBefore('#').lowercase()
        return subtitleExtensions.any(lower::endsWith)
    }

    private fun String.isPotentialSubtitleRequestUrl(): Boolean {
        val lower = runCatching { URLDecoder.decode(this, Charsets.UTF_8.name()) }
            .getOrDefault(this)
            .lowercase()
        return subtitleUrlMarkers.any(lower::contains)
    }

    private fun String.isUrlLike(): Boolean {
        val value = trim()
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (value.startsWith("//") || value.startsWith("/")) return true
        return relativeSubtitleUrlRegex.matches(value)
    }

    private fun String.isSubtitleMetadataKey(): Boolean {
        val lower = lowercase()
        return subtitleMetadataKeyMarkers.any(lower::contains) || lower in exactSubtitleMetadataKeys
    }

    private fun String.isSubtitleUrlKey(): Boolean = lowercase() in subtitleUrlKeys

    private fun String.isSubtitleDescriptor(): Boolean = trim().lowercase() in subtitleDescriptors

    private fun String.looksLikeJsonPayload(): Boolean {
        val value = trim()
        return (value.startsWith("{") && value.endsWith("}")) ||
            (value.startsWith("[") && value.endsWith("]"))
    }

    private companion object {
        val subtitleUrlRegex = Regex(
            """(?:(?:https?:)?//|/)?[^"'\s<>\\]+?\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val relativeSubtitleUrlRegex = Regex(
            """^[\w.-]+\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )
        val subtitleExtensions = setOf(".vtt", ".srt", ".ass", ".ssa", ".ttml", ".dfxp")
        val subtitleUrlMarkers = setOf(
            "subtitle",
            "subtitles",
            "caption",
            "captions",
            "texttrack",
            "texttracks",
            "/track",
            "track=",
            ".vtt",
            ".srt",
            ".ass",
            ".ssa",
            ".ttml",
            ".dfxp",
        )
        val subtitleMetadataKeyMarkers = setOf("subtitle", "caption")
        val exactSubtitleMetadataKeys = setOf("texttrack", "texttracks")
        val subtitleUrlKeys = setOf("src", "url", "file", "href", "path", "link", "track", "tracks")
        val subtitleDescriptors = setOf("subtitle", "subtitles", "caption", "captions", "sub", "subs", "texttrack")
    }
}
