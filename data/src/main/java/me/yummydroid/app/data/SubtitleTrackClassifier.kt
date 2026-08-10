package me.yummydroid.app.data

import java.net.URLDecoder

internal class SubtitleTrackClassifier(
    private val fallbackSiteBaseUrl: () -> String,
) {
    fun extractDirectTracks(body: String, baseUrl: String): List<ResolvedSubtitleTrack> {
        return subtitleUrlRegex
            .findAll(body)
            .mapNotNull { match ->
                match.value
                    .trim('"', '\'', ' ', '\\')
                    .let { value -> normalizeAgainst(value, baseUrl) }
                    .let(::directTrack)
            }
            .toList()
    }

    fun normalizeAgainst(value: String, baseUrl: String): String {
        return value.normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }

    fun directTrack(url: String): ResolvedSubtitleTrack? {
        if (!url.isSubtitleUrl()) return null
        return ResolvedSubtitleTrack(
            uri = url,
            label = url.subtitleLabelFromUrl(),
            mimeType = url.subtitleMimeTypeFromUrl(),
        )
    }

    fun potentialTrack(
        url: String,
        label: String = "",
        language: String? = null,
    ): ResolvedSubtitleTrack? {
        if (!isResolvableCandidate(url)) return null
        return ResolvedSubtitleTrack(
            uri = url,
            label = label.takeIf(String::isNotBlank) ?: url.subtitleLabelFromUrl(),
            language = language?.takeIf(String::isNotBlank),
            mimeType = url.subtitleMimeTypeFromUrl(),
        )
    }

    fun isResolvableCandidate(url: String): Boolean {
        val value = url.trim()
        if (value.isBlank()) return false
        if (value.isSubtitleUrl()) return true
        return value.isUrlLike() && isPotentialRequestUrl(value)
    }

    fun isPotentialRequestUrl(value: String): Boolean {
        val lower = runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
            .lowercase()
        return subtitleUrlMarkers.any(lower::contains)
    }

    fun isMetadataKey(value: String): Boolean {
        val lower = value.lowercase()
        return subtitleMetadataKeyMarkers.any(lower::contains) || lower in exactSubtitleMetadataKeys
    }

    fun isUrlKey(value: String): Boolean = value.lowercase() in subtitleUrlKeys

    fun isDescriptor(value: String): Boolean = value.trim().lowercase() in subtitleDescriptors

    fun looksLikeJsonPayload(value: String): Boolean {
        val trimmed = value.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun String.isSubtitleUrl(): Boolean {
        val lower = substringBefore('?').substringBefore('#').lowercase()
        return subtitleExtensions.any(lower::endsWith)
    }

    private fun String.isUrlLike(): Boolean {
        val value = trim()
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (value.startsWith("//") || value.startsWith("/")) return true
        return relativeSubtitleUrlRegex.matches(value)
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
