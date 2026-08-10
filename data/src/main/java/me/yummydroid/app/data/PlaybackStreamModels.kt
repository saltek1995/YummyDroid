package me.yummydroid.app.data

data class ResolvedVideoStream(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val maxVideoHeight: Int? = null,
    val availableQualities: List<SourceQuality> = emptyList(),
    val selectedVideoHeight: Int? = null,
    val fallbackUrls: List<String> = emptyList(),
    val skipPlaybackProbe: Boolean = false,
    val subtitles: List<ResolvedSubtitleTrack> = emptyList(),
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack> = emptyList(),
    val hasEmbeddedSubtitles: Boolean = false,
    val sourceSubtitleSourceKeys: Set<String> = emptySet(),
) {
    val hasResolvedSubtitles: Boolean
        get() = subtitles.isNotEmpty() || embeddedSubtitles.isNotEmpty()

    val hasSubtitles: Boolean
        get() = hasResolvedSubtitles || hasEmbeddedSubtitles
}

fun ResolvedVideoStream.sourceResolutionHeight(): Int {
    return (
        availableQualities.mapNotNull { it.height } +
            listOfNotNull(maxVideoHeight, selectedVideoHeight)
        )
        .mapNotNull { it.validVideoQualityHeight() }
        .maxOrNull()
        ?: 0
}
