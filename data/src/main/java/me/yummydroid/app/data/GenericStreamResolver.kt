package me.yummydroid.app.data

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class GenericStreamResolver(
    private val client: OkHttpClient,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val runtimeStreamResolver: RuntimeStreamResolver,
) {
    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream {
        if (sourceUrl.isDirectStreamUrl()) {
            return directStream(sourceUrl, siteBaseUrl)
        }
        if (sourceUrl.requiresRuntimePlayerDiscovery()) {
            return runtimeStreamResolver.resolve(
                sourceUrl,
                siteBaseUrl,
                preferredQuality,
                waitForRuntimeSubtitles,
            )
        }

        val request = Request.Builder()
            .url(sourceUrl)
            .headers(playbackRequestHeaders.iframe(sourceUrl, siteBaseUrl).toOkHttpHeaders())
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Player returned HTTP ${response.code}")
            }
            if (body.isHlsManifestBody()) {
                return hlsManifestStream(sourceUrl, siteBaseUrl, body)
            }
            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                return staticStream(sourceUrl, siteBaseUrl, streamUrl, body)
            }
        }
        return runtimeStreamResolver.resolve(
            sourceUrl,
            siteBaseUrl,
            preferredQuality,
            waitForRuntimeSubtitles,
        )
    }

    private fun directStream(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = sourceUrl,
            mimeType = sourceUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(sourceUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = sourceUrl.detectVideoHeight(),
            availableQualities = sourceUrl.detectSourceQualities(),
            subtitles = listOfNotNull(subtitleMetadataParser.directTrack(sourceUrl)).normalizedSubtitleTracks(),
        )
    }

    private fun hlsManifestStream(
        sourceUrl: String,
        siteBaseUrl: String,
        body: String,
    ): ResolvedVideoStream {
        val subtitles = subtitleMetadataParser.extractHlsTracks(body, sourceUrl)
        return ResolvedVideoStream(
            url = sourceUrl,
            mimeType = "application/x-mpegURL",
            headers = playbackRequestHeaders.playback(sourceUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = body.detectVideoHeight(),
            availableQualities = body.detectSourceQualities(),
            subtitles = subtitles.tracks,
            embeddedSubtitles = subtitles.embeddedSubtitles,
            hasEmbeddedSubtitles = subtitles.hasEmbeddedSubtitles,
        )
    }

    private fun staticStream(
        sourceUrl: String,
        siteBaseUrl: String,
        streamUrl: String,
        body: String,
    ): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackRequestHeaders.playback(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), streamUrl.detectVideoHeight()),
            availableQualities = (body.detectSourceQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
        )
    }

}
