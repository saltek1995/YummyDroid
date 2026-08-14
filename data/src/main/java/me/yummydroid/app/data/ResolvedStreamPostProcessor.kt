package me.yummydroid.app.data

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

// ResolvedStreamPostProcessor
internal class ResolvedStreamPostProcessor(
    private val client: OkHttpClient,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
) {
    fun process(stream: ResolvedVideoStream): ResolvedVideoStream {
        return stream.withFirstPlayableUrl().withDetectedSourceMetadata()
    }

    private fun ResolvedVideoStream.withFirstPlayableUrl(): ResolvedVideoStream {
        if (skipPlaybackProbe) {
            return copy(
                mimeType = url.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, url.detectVideoHeight()),
            )
        }
        val candidates = (listOf(url) + fallbackUrls)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
        var lastFailure: Throwable? = null
        val playable = candidates.firstNotNullOfOrNull { candidateUrl ->
            val candidate = copy(
                url = candidateUrl,
                mimeType = candidateUrl.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, candidateUrl.detectVideoHeight()),
            )
            runCatching { validatePlayableStream(candidate) }
                .onFailure { throwable -> lastFailure = throwable }
                .map { candidate }
                .getOrNull()
        }
            ?: throw lastFailure ?: IOException("Player did not return a video URL")
        return playable.copy(fallbackUrls = candidates.filterNot { it == playable.url })
    }

    private fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf(String::isNotBlank)
            ?: throw IOException("Player did not return a video URL")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Player returned a blob stream that native playback cannot use")
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
        if (!stream.looksLikeAdaptiveManifest()) requestBuilder.header("Range", "bytes=0-4095")
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code !in listOf(200, 206)) {
                throw IOException("Source returned HTTP ${response.code}")
            }
            val contentType = response.header("Content-Type").orEmpty()
            val bodyPrefix = response.body?.source()?.use { source ->
                source.request(512)
                source.buffer.clone().readUtf8().take(512)
            }.orEmpty()
            if (!streamResponseLooksPlayable(stream, contentType, bodyPrefix)) {
                throw IOException("Source does not look like an HLS/DASH/MP4 stream")
            }
        }
    }

    private fun streamResponseLooksPlayable(
        stream: ResolvedVideoStream,
        contentType: String,
        bodyPrefix: String,
    ): Boolean {
        val metadata = listOf(stream.mimeType.orEmpty(), contentType)
        return metadata.any { value ->
            value.contains("mpegURL", ignoreCase = true) ||
                value.contains("dash", ignoreCase = true) ||
                value.contains("video", ignoreCase = true)
        } || bodyPrefix.trimStart().startsWith("#EXTM3U")
    }

    private fun ResolvedVideoStream.withDetectedSourceMetadata(): ResolvedVideoStream {
        val manifestText = if (skipPlaybackProbe) null else loadAdaptiveManifestTextOrNull()
        val detectedQualities = detectSourceQualities(manifestText)
        val detectedHeight = detectedQualities.mapNotNull(SourceQuality::height).maxOrNull()
        val resolvedHeight = maxOfOrNull(maxVideoHeight, detectedHeight, url.detectVideoHeight())
        val resolvedQualities = (
            availableQualities +
                detectedQualities +
                listOfNotNull(resolvedHeight?.let { SourceQuality(height = it) })
            ).normalizedSourceQualities()
        val detectedSubtitles = detectSubtitleTracks(manifestText)
        return copy(
            maxVideoHeight = resolvedHeight,
            availableQualities = resolvedQualities,
            subtitles = subtitleTrackMaterializer.validateTracks(subtitles + detectedSubtitles.tracks, headers),
            embeddedSubtitles = (embeddedSubtitles + detectedSubtitles.embeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles ||
                detectedSubtitles.hasEmbeddedSubtitles ||
                detectedSubtitles.embeddedSubtitles.isNotEmpty(),
        )
    }

    private fun ResolvedVideoStream.detectSourceQualities(manifestText: String?): List<SourceQuality> {
        val urlHeight = url.detectVideoHeight()
        if (!looksLikeAdaptiveManifest()) {
            return listOfNotNull(urlHeight?.let { SourceQuality(height = it) })
        }
        val manifestQualities = manifestText?.detectSourceQualities()
        return (manifestQualities.orEmpty() + listOfNotNull(urlHeight?.let { SourceQuality(height = it) }))
            .normalizedSourceQualities()
    }

    private fun ResolvedVideoStream.detectSubtitleTracks(manifestText: String?): SubtitleDetection {
        val directTrack = subtitleMetadataParser.directTrack(url)
        if (!looksLikeAdaptiveManifest() || manifestText == null) {
            return SubtitleDetection(
                tracks = listOfNotNull(directTrack).normalizedSubtitleTracks(),
                embeddedSubtitles = emptyList(),
                hasEmbeddedSubtitles = false,
            )
        }
        val hlsSubtitles = subtitleMetadataParser.extractHlsTracks(manifestText, url)
        val dashEmbeddedSubtitles = if (manifestText.isDashManifestBody()) {
            subtitleMetadataParser.extractDashEmbeddedTracks(manifestText)
        } else {
            emptyList()
        }
        return SubtitleDetection(
            tracks = (
                listOfNotNull(directTrack) +
                    hlsSubtitles.tracks +
                    subtitleMetadataParser.extractTracks(manifestText, url)
                ).normalizedSubtitleTracks(),
            embeddedSubtitles = (hlsSubtitles.embeddedSubtitles + dashEmbeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hlsSubtitles.hasEmbeddedSubtitles || dashEmbeddedSubtitles.isNotEmpty(),
        )
    }

    private fun ResolvedVideoStream.loadAdaptiveManifestTextOrNull(): String? {
        if (!looksLikeAdaptiveManifest()) return null
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code !in listOf(200, 206)) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun ResolvedVideoStream.looksLikeAdaptiveManifest(): Boolean {
        return url.looksLikeAdaptiveStreamUrl(mimeType)
    }
}
