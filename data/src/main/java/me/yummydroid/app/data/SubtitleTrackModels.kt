package me.yummydroid.app.data

private val GENERIC_SUBTITLE_TRACK_PATTERN =
    Regex("""(?:sub|subs|subtitle|subtitles|caption|captions)\s*[a-z]{2,3}\s*\d*""")
private val GENERIC_SUBTITLE_LABELS = setOf("subtitles", "subtitle", "captions", "caption")

data class ResolvedSubtitleTrack(
    val uri: String,
    val label: String = "",
    val language: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class ResolvedEmbeddedSubtitleTrack(
    val id: String = "",
    val label: String = "",
    val language: String? = null,
)

fun List<ResolvedEmbeddedSubtitleTrack>.normalizedEmbeddedSubtitleTracks(): List<ResolvedEmbeddedSubtitleTrack> {
    return asSequence()
        .map(ResolvedEmbeddedSubtitleTrack::trimmed)
        .filter(ResolvedEmbeddedSubtitleTrack::hasIdentity)
        .groupBy(ResolvedEmbeddedSubtitleTrack::identityKey)
        .values
        .map(::mergeEmbeddedSubtitleTracks)
        .toList()
}

fun List<ResolvedSubtitleTrack>.normalizedSubtitleTracks(): List<ResolvedSubtitleTrack> {
    return asSequence()
        .filter { it.uri.isNotBlank() }
        .groupBy { it.uri.trim().lowercase() }
        .values
        .map(::mergeSubtitleTracks)
        .toList()
}

private fun ResolvedEmbeddedSubtitleTrack.trimmed(): ResolvedEmbeddedSubtitleTrack {
    return copy(
        id = id.trim(),
        label = label.trim(),
        language = language?.trim()?.takeIf { it.isNotBlank() },
    )
}

private fun ResolvedEmbeddedSubtitleTrack.hasIdentity(): Boolean {
    return id.isNotBlank() || label.isNotBlank() || language.orEmpty().isNotBlank()
}

private fun ResolvedEmbeddedSubtitleTrack.identityKey(): String {
    return id.takeIf { it.isNotBlank() }?.lowercase()
        ?: listOf(label.lowercase(), language.orEmpty().lowercase()).joinToString(":")
}

private fun mergeEmbeddedSubtitleTracks(
    tracks: List<ResolvedEmbeddedSubtitleTrack>,
): ResolvedEmbeddedSubtitleTrack {
    val metadata = tracks.maxWithOrNull(
        compareBy<ResolvedEmbeddedSubtitleTrack> { it.label.subtitleLabelScore() }
            .thenBy { if (it.language.orEmpty().isNotBlank()) 1 else 0 }
            .thenBy { if (it.id.isNotBlank()) 1 else 0 },
    ) ?: tracks.first()
    return metadata.copy(
        id = tracks.firstOrNull { it.id.isNotBlank() }?.id.orEmpty(),
        label = metadata.label.takeIf { it.isNotBlank() }.orEmpty(),
        language = metadata.language?.takeIf { it.isNotBlank() }
            ?: tracks.firstOrNull { it.language.orEmpty().isNotBlank() }?.language,
    )
}

private fun mergeSubtitleTracks(tracks: List<ResolvedSubtitleTrack>): ResolvedSubtitleTrack {
    val source = tracks.firstOrNull { it.uri.startsWith("file:", ignoreCase = true) }
        ?: tracks.firstOrNull { it.headers.isNotEmpty() }
        ?: tracks.first()
    val metadata = tracks.maxWithOrNull(
        compareBy<ResolvedSubtitleTrack> { it.label.subtitleLabelScore() }
            .thenBy { if (it.language.orEmpty().isNotBlank()) 1 else 0 }
            .thenBy { if (it.mimeType.orEmpty().isNotBlank()) 1 else 0 },
    )
    return source.copy(
        label = metadata?.label?.takeIf { it.isNotBlank() }.orEmpty(),
        language = metadata?.language?.takeIf { it.isNotBlank() } ?: source.language,
        mimeType = source.mimeType ?: metadata?.mimeType,
    )
}

private fun String.subtitleLabelScore(): Int {
    val normalized = trim().lowercase()
    return when {
        normalized.isOpaqueSubtitleLabel() -> 0
        GENERIC_SUBTITLE_TRACK_PATTERN.matches(normalized) -> 1
        normalized in GENERIC_SUBTITLE_LABELS -> 2
        else -> 3
    }
}

private fun String.isOpaqueSubtitleLabel(): Boolean {
    return isBlank() || all(Char::isDigit) || isShortHexToken() || isGeneratedSubtitleToken()
}

private fun String.isShortHexToken(): Boolean {
    if (length !in 4..16) return false
    if (!all { it in '0'..'9' || it in 'a'..'f' }) return false
    return any(Char::isDigit) && any { it in 'a'..'f' }
}

private fun String.isGeneratedSubtitleToken(): Boolean {
    if (!startsWith("subtitle_") || length < 24) return false
    return removePrefix("subtitle_").all { it in '0'..'9' || it in 'a'..'f' }
}
