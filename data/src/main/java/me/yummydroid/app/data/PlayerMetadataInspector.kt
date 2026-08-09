package me.yummydroid.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class PlayerMetadataInspector(
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val fallbackSiteBaseUrl: () -> String,
) {
    fun isInspectableUrl(url: String): Boolean {
        val parsedUrl = url.toHttpUrlOrNull() ?: return false
        val host = parsedUrl.host.lowercase()
        if ("alloha" !in host && "alloh" !in host) return false
        val path = parsedUrl.encodedPath.lowercase()
        return path.startsWith("/movies/") ||
            path.startsWith("/serials/") ||
            path.startsWith("/trailers/") ||
            path.startsWith("/player/") ||
            path.startsWith("/video/")
    }

    fun inspect(
        url: String,
        body: String,
        requestHeaders: Map<String, String>,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): PlayerMetadataCapture {
        val bodyIsHlsManifest = body.isHlsManifestBody()
        val bodyIsDashManifest = body.isDashManifestBody()
        val runtimeStreams = body.extractAllohaRuntimeStreams(url)
            .sortedForPreferredQuality(preferredQuality)
        val runtimeStream = runtimeStreams.firstOrNull()
        val streamUrl = runtimeStream?.url
            ?: body.extractDirectStreamUrl(url)
            ?: url.takeIf {
                !subtitleMetadataParser.isResolvableCandidate(url) &&
                    (bodyIsHlsManifest || bodyIsDashManifest)
            }
        val playback = streamUrl?.let { capturedUrl ->
            val playbackUrl = capturedUrl.normalizeVideoUrlAgainstBase(sourceUrl, fallbackSiteBaseUrl())
            val capturedMetadataUrl = capturedUrl == url
            CapturedPlayback(
                url = playbackUrl,
                mimeType = when {
                    capturedMetadataUrl && bodyIsHlsManifest -> "application/x-mpegURL"
                    capturedMetadataUrl && bodyIsDashManifest -> "application/dash+xml"
                    else -> playbackUrl.mimeTypeFromUrl()
                },
                headers = playbackRequestHeaders.forwardedPlayback(
                    sourceHeaders = requestHeaders,
                    streamUrl = playbackUrl,
                    sourceUrl = sourceUrl,
                    siteBaseUrl = siteBaseUrl,
                ),
                maxVideoHeight = maxOfOrNull(
                    body.detectVideoHeight(),
                    runtimeStream?.height,
                    playbackUrl.detectVideoHeight(),
                ),
                fallbackUrls = runtimeStreams
                    .drop(1)
                    .map { it.url.normalizeVideoUrlAgainstBase(sourceUrl, fallbackSiteBaseUrl()) },
                skipPlaybackProbe = runtimeStream != null,
            )
        }
        val hlsSubtitles = if (bodyIsHlsManifest) {
            subtitleMetadataParser.extractHlsTracks(body, url)
        } else {
            SubtitleDetection(tracks = emptyList(), embeddedSubtitles = emptyList(), hasEmbeddedSubtitles = false)
        }
        val dashEmbeddedSubtitles = if (bodyIsDashManifest) {
            subtitleMetadataParser.extractDashEmbeddedTracks(body)
        } else {
            emptyList()
        }
        val subtitleHeaders = playbackRequestHeaders.forwardedPlayback(
            sourceHeaders = requestHeaders,
            streamUrl = url,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
        )
        val subtitles = (subtitleMetadataParser.extractTracks(body, url) + hlsSubtitles.tracks)
            .map { track ->
                if (track.uri.startsWith("file:", ignoreCase = true) ||
                    track.uri.startsWith("content:", ignoreCase = true)
                ) {
                    track
                } else {
                    track.copy(headers = track.headers.ifEmpty { subtitleHeaders })
                }
            }
            .normalizedSubtitleTracks()

        return PlayerMetadataCapture(
            playback = playback,
            subtitles = subtitles,
            embeddedSubtitles = (hlsSubtitles.embeddedSubtitles + dashEmbeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hlsSubtitles.hasEmbeddedSubtitles || dashEmbeddedSubtitles.isNotEmpty(),
        )
    }
}
