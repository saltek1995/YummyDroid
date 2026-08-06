package me.yummydroid.app.data

internal data class SubtitleDetection(
    val tracks: List<ResolvedSubtitleTrack>,
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack>,
    val hasEmbeddedSubtitles: Boolean,
)

internal data class CapturedPlayback(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val maxVideoHeight: Int?,
    val fallbackUrls: List<String> = emptyList(),
    val skipPlaybackProbe: Boolean = false,
) {
    fun toStream(
        subtitles: List<ResolvedSubtitleTrack>,
        embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack>,
        hasEmbeddedSubtitles: Boolean,
    ): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = url,
            mimeType = mimeType,
            headers = headers,
            maxVideoHeight = maxVideoHeight,
            fallbackUrls = fallbackUrls,
            skipPlaybackProbe = skipPlaybackProbe,
            subtitles = subtitles.normalizedSubtitleTracks(),
            embeddedSubtitles = embeddedSubtitles.normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles || embeddedSubtitles.isNotEmpty(),
        )
    }
}

internal data class PlayerMetadataCapture(
    val playback: CapturedPlayback? = null,
    val subtitles: List<ResolvedSubtitleTrack> = emptyList(),
    val embeddedSubtitles: List<ResolvedEmbeddedSubtitleTrack> = emptyList(),
    val hasEmbeddedSubtitles: Boolean = false,
)
