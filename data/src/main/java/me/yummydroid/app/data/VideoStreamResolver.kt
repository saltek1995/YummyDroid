package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource

class VideoStreamResolver(
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val appContext = context?.applicationContext

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        val stream = resolveInternal(video, preferredQuality, waitForRuntimeSubtitles)
        return withContext(Dispatchers.IO) {
            stream.withFirstPlayableUrl().withDetectedSourceMetadata()
        }
    }

    private suspend fun resolveInternal(
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream = withContext(Dispatchers.IO) {
        var lastFailure: Throwable? = null
        siteDomainResolver.orderedBaseUrlsFor(video.url).forEach { siteBaseUrl ->
            val sourceUrl = video.url.normalizeVideoUrl(siteBaseUrl)
            runCatching {
                resolveInternalForBaseUrl(
                    video = video,
                    sourceUrl = sourceUrl,
                    siteBaseUrl = siteBaseUrl,
                    preferredQuality = preferredQuality,
                    waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                )
            }.onSuccess { stream ->
                siteDomainResolver.markAvailable(siteBaseUrl)
                return@withContext stream
            }.onFailure { throwable ->
                lastFailure = throwable
                siteDomainResolver.markUnavailable(siteBaseUrl)
            }
        }
        throw lastFailure ?: IOException("Could not select a working site domain")
    }

    private suspend fun resolveInternalForBaseUrl(
        video: VideoVariant,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream {
        val headers = iframeHeaders(sourceUrl, siteBaseUrl)

        if (sourceUrl.isCvhIframeUrl()) {
            val cvhFailure = runCatching {
                resolveCvh(sourceUrl, video, siteBaseUrl, preferredQuality)
            }.onSuccess { stream ->
                return stream
            }.exceptionOrNull()

            return runCatching {
                resolveViaWebView(sourceUrl, siteBaseUrl, preferredQuality, waitForRuntimeSubtitles)
            }.getOrElse { runtimeFailure ->
                cvhFailure?.addSuppressed(runtimeFailure)
                throw cvhFailure ?: runtimeFailure
            }
        }

        if (sourceUrl.isKodikIframeUrl()) {
            return resolveKodik(sourceUrl, siteBaseUrl, preferredQuality)
        }

        if (sourceUrl.isAksorIframeUrl()) {
            return resolveAksor(sourceUrl, siteBaseUrl, preferredQuality)
        }

        if (sourceUrl.isSibnetIframeUrl()) {
            return resolveSibnet(sourceUrl, siteBaseUrl)
        }

        if (sourceUrl.isDirectStreamUrl()) {
            return ResolvedVideoStream(
                url = sourceUrl,
                mimeType = sourceUrl.mimeTypeFromUrl(),
                headers = playbackHeaders(sourceUrl, sourceUrl, siteBaseUrl),
                maxVideoHeight = sourceUrl.detectVideoHeight(),
                availableQualities = sourceUrl.detectSourceQualities(),
                subtitles = listOfNotNull(sourceUrl.toDirectSubtitleTrack()).normalizedSubtitleTracks(),
            )
        }

        val request = Request.Builder()
            .url(sourceUrl)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Player returned HTTP ${response.code}")
            }

            if (body.trimStart().startsWith("#EXTM3U")) {
                val playbackHeaders = playbackHeaders(sourceUrl, sourceUrl, siteBaseUrl)
                val subtitles = body.extractHlsSubtitleTracks(sourceUrl)
                return ResolvedVideoStream(
                    url = sourceUrl,
                    mimeType = "application/x-mpegURL",
                    headers = playbackHeaders,
                    maxVideoHeight = body.detectVideoHeight(),
                    availableQualities = body.detectSourceQualities(),
                    subtitles = subtitles.tracks,
                    embeddedSubtitles = subtitles.embeddedSubtitles,
                    hasEmbeddedSubtitles = subtitles.hasEmbeddedSubtitles,
                )
            }

            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                val detectedQualities = (body.detectSourceQualities() + streamUrl.detectSourceQualities())
                    .normalizedSourceQualities()
                val staticStream = ResolvedVideoStream(
                    url = streamUrl,
                    mimeType = streamUrl.mimeTypeFromUrl(),
                    headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
                    maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), streamUrl.detectVideoHeight()),
                    availableQualities = detectedQualities,
                    subtitles = body.extractSubtitleTracks(sourceUrl),
                )
                if (sourceUrl.requiresRuntimePlayerDiscovery()) {
                    runCatching {
                        resolveViaWebView(
                            sourceUrl,
                            siteBaseUrl,
                            preferredQuality,
                            waitForRuntimeSubtitles,
                        )
                    }
                        .getOrNull()
                        ?.let { runtimeStream -> return runtimeStream.withMergedStaticPlayerMetadata(staticStream) }
                }
                return staticStream
            }
        }

        return resolveViaWebView(sourceUrl, siteBaseUrl, preferredQuality, waitForRuntimeSubtitles)
    }

    private fun ResolvedVideoStream.withFirstPlayableUrl(): ResolvedVideoStream {
        if (skipPlaybackProbe) {
            return copy(
                mimeType = url.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, url.detectVideoHeight()),
            )
        }

        val candidates = (listOf(url) + fallbackUrls)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        var lastFailure: Throwable? = null

        candidates.forEach { candidateUrl ->
            val candidate = copy(
                url = candidateUrl,
                mimeType = candidateUrl.mimeTypeFromUrl() ?: mimeType,
                maxVideoHeight = maxOfOrNull(maxVideoHeight, candidateUrl.detectVideoHeight()),
                fallbackUrls = candidates.filterNot { it == candidateUrl },
            )
            runCatching {
                validatePlayableStream(candidate)
            }.onSuccess {
                return candidate
            }.onFailure { throwable ->
                lastFailure = throwable
            }
        }

        throw lastFailure ?: IOException("Player did not return a video URL")
    }

    private fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf { it.isNotBlank() }
            ?: throw IOException("Player did not return a video URL")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Player returned a blob stream that native playback cannot use")
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
        if (!stream.looksLikeAdaptiveManifest()) {
            requestBuilder.header("Range", "bytes=0-4095")
        }
        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 206)) {
                throw IOException("Source returned HTTP ${response.code}")
            }

            val contentType = response.header("Content-Type").orEmpty()
            val bodyPrefix = response.body?.source()?.use { source ->
                source.request(512)
                source.buffer.clone().readUtf8().take(512)
            }.orEmpty()
            val isExpectedStream = stream.mimeType?.contains("mpegURL", ignoreCase = true) == true ||
                stream.mimeType?.contains("dash", ignoreCase = true) == true ||
                stream.mimeType?.contains("video", ignoreCase = true) == true ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("dash", ignoreCase = true) ||
                contentType.contains("video", ignoreCase = true) ||
                bodyPrefix.trimStart().startsWith("#EXTM3U")

            if (!isExpectedStream) {
                throw IOException("Source does not look like an HLS/DASH/MP4 stream")
            }
        }
    }

    private fun ResolvedVideoStream.withDetectedSourceMetadata(): ResolvedVideoStream {
        val manifestText = if (skipPlaybackProbe) null else loadAdaptiveManifestTextOrNull()
        val detectedQualities = detectSourceQualities(manifestText)
        val detectedHeight = detectedQualities.mapNotNull { it.height }.maxOrNull()
        val resolvedHeight = maxOfOrNull(maxVideoHeight, detectedHeight, url.detectVideoHeight())
        val resolvedQualities = (availableQualities + detectedQualities + listOfNotNull(resolvedHeight?.let { SourceQuality(height = it) }))
            .normalizedSourceQualities()
        val detectedSubtitles = detectSubtitleTracks(manifestText)
        return copy(
            maxVideoHeight = resolvedHeight,
            availableQualities = resolvedQualities,
            subtitles = (subtitles + detectedSubtitles.tracks).validatedSubtitleTracks(headers),
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
        val directTrack = url.toDirectSubtitleTrack()
        if (!looksLikeAdaptiveManifest()) {
            return SubtitleDetection(
                tracks = listOfNotNull(directTrack).normalizedSubtitleTracks(),
                embeddedSubtitles = emptyList(),
                hasEmbeddedSubtitles = false,
            )
        }

        val body = manifestText ?: return SubtitleDetection(
            tracks = listOfNotNull(directTrack).normalizedSubtitleTracks(),
            embeddedSubtitles = emptyList(),
            hasEmbeddedSubtitles = false,
        )
        val hlsSubtitles = body.extractHlsSubtitleTracks(url)
        val dashEmbeddedSubtitles = if (body.isDashManifestBody()) {
            body.extractDashEmbeddedSubtitleTracks()
        } else {
            emptyList()
        }
        return SubtitleDetection(
            tracks = (listOfNotNull(directTrack) + hlsSubtitles.tracks + body.extractSubtitleTracks(url))
                .normalizedSubtitleTracks(),
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
        val lowerUrl = url.lowercase()
        val lowerMimeType = mimeType.orEmpty().lowercase()
        return ".m3u8" in lowerUrl ||
            ".mpd" in lowerUrl ||
            "mpegurl" in lowerMimeType ||
            "dash" in lowerMimeType
    }

    private fun ResolvedVideoStream.withMergedStaticPlayerMetadata(
        staticStream: ResolvedVideoStream,
    ): ResolvedVideoStream {
        return copy(
            maxVideoHeight = maxOfOrNull(maxVideoHeight, staticStream.maxVideoHeight),
            availableQualities = (availableQualities + staticStream.availableQualities)
                .normalizedSourceQualities(),
            subtitles = (subtitles + staticStream.subtitles).normalizedSubtitleTracks(),
            embeddedSubtitles = (embeddedSubtitles + staticStream.embeddedSubtitles)
                .normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles ||
                staticStream.hasEmbeddedSubtitles ||
                staticStream.embeddedSubtitles.isNotEmpty(),
        )
    }

    private fun resolveKodik(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val html = getText(sourceUrl, iframeHeaders(sourceUrl, siteBaseUrl))
        val params = html.kodikParams()
        val form = FormBody.Builder()
            .add("d", params.domain)
            .add("d_sign", params.domainSign)
            .add("pd", params.playerDomain)
            .add("pd_sign", params.playerDomainSign)
            .add("ref", params.referer)
            .add("ref_sign", params.refererSign)
            .add("bad_user", "false")
            .add("cdn_is_working", "true")
            .add("type", params.type)
            .add("hash", params.hash)
            .add("id", params.id)
            .build()
        val request = Request.Builder()
            .url(KODIK_FTOR_URL)
            .headers(kodikApiHeaders(sourceUrl).toOkHttpHeaders())
            .post(form)
            .build()

        val body = client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) {
                throw IOException("Kodik API returned HTTP ${response.code}")
            }
            text
        }
        val dto = json.decodeFromString<KodikFtorDto>(body)
        val stream = dto.bestStream(preferredQuality)
            ?: throw IOException("Kodik: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = stream.url,
            mimeType = stream.mimeType ?: stream.url.mimeTypeFromKodikUrl(),
            headers = kodikPlaybackHeaders(stream.url),
            maxVideoHeight = maxOfOrNull(stream.height, stream.url.detectVideoHeight()),
            availableQualities = (dto.availableQualities() + stream.url.detectSourceQualities())
                .normalizedSourceQualities(),
            subtitles = body.extractSubtitleTracks(sourceUrl),
        )
    }

    private fun resolveAksor(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val videoId = sourceUrl.toUri().lastPathSegment?.takeIf { it.isNotBlank() }
            ?: throw IOException("Aksor: missing video id")
        val origin = sourceUrl.urlOrigin() ?: AKSOR_ORIGIN
        val video = getJson<AksorVideoDto>(
            url = "$origin/api/video/$videoId",
            headers = aksorApiHeaders(sourceUrl),
        )
        val stream = video.bestStream(preferredQuality)
            ?: throw IOException("Aksor: stream is unavailable")
        val streamUrl = stream.url.normalizeVideoUrlAgainst(sourceUrl)

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = maxOfOrNull(stream.height, streamUrl.detectVideoHeight()),
            availableQualities = (video.qualities.availableQualities() + streamUrl.detectSourceQualities())
                .normalizedSourceQualities(),
        )
    }

    private fun resolveSibnet(sourceUrl: String, siteBaseUrl: String): ResolvedVideoStream {
        val html = getText(sourceUrl, iframeHeaders(sourceUrl, siteBaseUrl))
        val streamUrl = html.extractSibnetStreamUrl(sourceUrl)
            ?: html.extractDirectStreamUrl(sourceUrl)
            ?: throw IOException("Sibnet: HLS/MP4/DASH stream was not found")

        return ResolvedVideoStream(
            url = streamUrl,
            mimeType = streamUrl.mimeTypeFromUrl(),
            headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
            maxVideoHeight = streamUrl.detectVideoHeight(),
        )
    }

    private fun resolveCvh(
        sourceUrl: String,
        video: VideoVariant,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val iframeUri = sourceUrl.toUri()
        val titleId = iframeUri.getQueryParameter("anime_id")?.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: anime_id was not found in iframe")
        val episode = iframeUri.getQueryParameter("episode")?.toIntOrNull()
            ?: video.episode.toIntOrNull()
            ?: 1
        val season = iframeUri.getQueryParameter("season")?.toIntOrNull()
        val priorityVoices = buildCvhVoiceCandidates(iframeUri, video)

        val playlistUrl = CVH_PLAYLIST_URL.newBuilder()
            .addQueryParameter("pub", CVH_PUBLISHER_ID)
            .addQueryParameter("id", titleId)
            .addQueryParameter("aggr", CVH_AGGREGATOR)
            .build()
            .toString()
        val playlist = getJson<CvhPlaylistDto>(playlistUrl, cvhApiHeaders(sourceUrl))
        val selectedVideo = playlist.items.selectCvhItem(
            season = season,
            episode = episode,
            priorityVoices = priorityVoices,
        ) ?: throw IOException("CVH: voice is unavailable for episode $episode: ${priorityVoices.firstOrNull().orEmpty()}")

        val vkId = selectedVideo.vkId.takeIf { it.isNotBlank() }
            ?: throw IOException("CVH: episode has no vkId")
        val videoUrl = "$CVH_VIDEO_URL/$vkId"
        val cvhVideo = getJson<CvhVideoDto>(videoUrl, cvhApiHeaders(sourceUrl))
        val source = cvhVideo.sources?.bestStream(preferredQuality)
            ?: throw IOException("CVH: HLS/DASH/MP4 stream was not found")

        val selectedHeight = maxOfOrNull(source.height, source.url.detectVideoHeight())
        return ResolvedVideoStream(
            url = source.url,
            mimeType = source.mimeType,
            headers = cvhPlaybackHeaders(source.url, sourceUrl, siteBaseUrl),
            maxVideoHeight = selectedHeight,
            availableQualities = (cvhVideo.sources?.availableQualities().orEmpty() + source.url.detectSourceQualities())
                .normalizedSourceQualities(),
            selectedVideoHeight = selectedHeight,
        )
    }

    private fun getText(url: String, headers: Map<String, String>): String {
        return readRequiredResponseBody(url, headers) { code -> "Player returned HTTP $code" }
    }

    private inline fun <reified T> getJson(url: String, headers: Map<String, String>): T {
        val body = readRequiredResponseBody(url, headers) { code -> "CVH API returned HTTP $code" }
        return json.decodeFromString(body)
    }

    private fun readRequiredResponseBody(
        url: String,
        headers: Map<String, String>,
        errorMessage: (Int) -> String,
    ): String {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException(errorMessage(response.code))
            }
            return body
        }
    }

    private fun List<ResolvedSubtitleTrack>.validatedSubtitleTracks(
        headers: Map<String, String>,
    ): List<ResolvedSubtitleTrack> {
        return mapNotNull { track -> track.validatedSubtitleTrack(headers) }
            .normalizedSubtitleTracks()
    }

    private fun ResolvedSubtitleTrack.validatedSubtitleTrack(
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val subtitleHeaders = this.headers.ifEmpty { headers }
        if (!uri.isHlsPlaylistUrl() && mimeType?.contains("mpegurl", ignoreCase = true) != true) {
            return runCatching { materializeDirectSubtitleTrack(this, subtitleHeaders) }
                .getOrNull()
        }
        return runCatching { materializeHlsSubtitlePlaylist(this, subtitleHeaders) }
            .getOrNull()
    }

    private fun materializeDirectSubtitleTrack(
        track: ResolvedSubtitleTrack,
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val body = when {
            track.uri.startsWith("file:", ignoreCase = true) -> {
                val path = runCatching { Uri.parse(track.uri).path }.getOrNull() ?: return null
                File(path).subtitleTextOrNull() ?: return null
            }
            track.uri.startsWith("content:", ignoreCase = true) -> return track
            else -> getText(track.uri, headers)
        }
        return materializeSubtitleBody(track, body)
    }

    private fun materializeCapturedSubtitleBody(
        url: String,
        contentType: String?,
        body: String,
    ): ResolvedSubtitleTrack? {
        val mimeType = contentType.subtitleMimeTypeFromContentType() ?: url.subtitleMimeTypeFromUrl()
        val track = ResolvedSubtitleTrack(
            uri = url,
            label = url.subtitleLabelFromUrl(),
            mimeType = mimeType,
        )
        return materializeSubtitleBody(track, body)
    }

    private fun materializeSubtitleBody(
        track: ResolvedSubtitleTrack,
        body: String,
    ): ResolvedSubtitleTrack? {
        if (body.looksLikeStandaloneHlsWebVttSegment()) return null
        val playable = body.toPlayableSubtitleBody(mimeType = track.mimeType, uri = track.uri) ?: return null
        val outputFile = appContext?.let { context ->
            subtitleCacheFile(context.cacheDir, track.uri, playable.fileExtension)
        }
        if (outputFile?.isFreshSubtitleCacheFile() == true) {
            if (outputFile.hasSubtitleCues(mimeType = playable.mimeType)) {
                return track.withSubtitleCacheFile(outputFile, playable.mimeType)
            }
            runCatching { outputFile.delete() }
        }
        if (outputFile == null) {
            return track.copy(mimeType = playable.mimeType)
        }

        outputFile.parentFile?.mkdirs()
        cleanupOldSubtitleFiles(outputFile.parentFile)
        if (!outputFile.writeVerifiedSubtitleCacheFile(playable.text, playable.mimeType)) return null
        return track.withSubtitleCacheFile(outputFile, playable.mimeType)
    }

    private fun materializeHlsSubtitlePlaylist(
        track: ResolvedSubtitleTrack,
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack? {
        val outputFile = appContext?.let { context -> subtitleCacheFile(context.cacheDir, track.uri, "vtt") }
        if (outputFile?.isFreshSubtitleCacheFile() == true) {
            if (outputFile.hasSubtitleCues(mimeType = "text/vtt")) {
                return track.withSubtitleCacheFile(outputFile, "text/vtt")
            }
            runCatching { outputFile.delete() }
        }

        val playlist = getText(track.uri, headers)
        val segments = playlist.hlsSubtitleSegments(track.uri)
        val cueSegments = if (segments.isNotEmpty()) {
            segments.map { segment ->
                val body = getText(segment.url, headers).webVttCueBody()
                MaterializedSubtitleSegment(
                    body = body.text,
                    offsetMs = segment.offsetMs,
                    durationMs = segment.durationMs,
                    localMapMs = body.localMapMs,
                    topLevelBlocks = body.topLevelBlocks,
                )
            }
        } else if (playlist.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
            val body = playlist.webVttCueBody()
            listOf(
                MaterializedSubtitleSegment(
                    body = body.text,
                    offsetMs = 0L,
                    durationMs = 0L,
                    localMapMs = body.localMapMs,
                    topLevelBlocks = body.topLevelBlocks,
                ),
            )
        } else {
            emptyList()
        }

        val nonBlankSegments = cueSegments.filter { it.body.isNotBlank() }
        if (nonBlankSegments.isEmpty()) return null
        val topLevelBlocks = cueSegments
            .flatMap { it.topLevelBlocks }
            .distinct()

        val shouldShiftCueTimes = nonBlankSegments.shouldShiftWebVttCueTimes()
        val cues = nonBlankSegments.map { segment ->
            segment.normalizedWebVttCueBody(shouldShiftCueTimes)
                .trim()
        }.filter { it.isNotBlank() }

        val materializedBody = cues.joinToString("\n\n")
        val playable = buildString {
            append("WEBVTT\n\n")
            if (topLevelBlocks.isNotEmpty()) {
                append(topLevelBlocks.joinToString("\n\n"))
                append("\n\n")
            }
            append(materializedBody)
            append('\n')
        }.toPlayableSubtitleBody(mimeType = "text/vtt", uri = track.uri) ?: return null
        if (outputFile == null) return track.copy(mimeType = playable.mimeType)

        outputFile.parentFile?.mkdirs()
        cleanupOldSubtitleFiles(outputFile.parentFile)
        if (!outputFile.writeVerifiedSubtitleCacheFile(playable.text, playable.mimeType)) return null
        return track.withSubtitleCacheFile(outputFile, playable.mimeType)
    }

    private fun ResolvedSubtitleTrack.withSubtitleCacheFile(
        file: File,
        mimeType: String,
    ): ResolvedSubtitleTrack {
        return copy(
            uri = Uri.fromFile(file).toString(),
            label = label.ifBlank { file.nameWithoutExtension.takeUnless { it.startsWith(SUBTITLE_CACHE_FILE_PREFIX) }.orEmpty() },
            mimeType = mimeType,
            headers = emptyMap(),
        )
    }

    private fun ResolvedSubtitleTrack.hasSubtitleCues(headers: Map<String, String>): Boolean {
        val body = when {
            uri.startsWith("file:", ignoreCase = true) -> {
                val path = runCatching { Uri.parse(uri).path }.getOrNull() ?: return false
                File(path).subtitleTextOrNull() ?: return false
            }
            uri.startsWith("content:", ignoreCase = true) -> return true
            else -> getText(uri, headers)
        }
        return body.hasSubtitleCues(mimeType = mimeType, uri = uri)
    }

    private fun subtitleCacheFile(cacheDir: File, sourceUri: String, extension: String): File {
        val safeExtension = extension
            .trim()
            .trimStart('.')
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?: "vtt"
        return File(File(cacheDir, SUBTITLE_CACHE_DIR), "$SUBTITLE_CACHE_FILE_PREFIX${sourceUri.sha256Hex()}.$safeExtension")
    }

    private fun File.isFreshSubtitleCacheFile(): Boolean {
        if (!isFile || length() <= WEBVTT_HEADER_MIN_BYTES) return false
        return System.currentTimeMillis() - lastModified() <= SUBTITLE_CACHE_TTL_MS
    }

    private fun cleanupOldSubtitleFiles(directory: File?) {
        val now = System.currentTimeMillis()
        directory
            ?.listFiles { file ->
                file.isFile &&
                    file.name.startsWith(SUBTITLE_CACHE_FILE_PREFIX)
            }
            ?.forEach { file ->
                if (now - file.lastModified() > SUBTITLE_CACHE_TTL_MS) {
                    runCatching { file.delete() }
                }
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaWebView(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream = withContext(Dispatchers.Main) {
        val context = appContext ?: throw IOException("Context is required for JS stream capture")

        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            var completed = false
            var capturedPlayback: CapturedPlayback? = null
            var discoveryVersion = 0
            val capturedRequestHeaders = ConcurrentHashMap<String, Map<String, String>>()
            val capturedSubtitleTracks = linkedSetOf<ResolvedSubtitleTrack>()
            val capturedEmbeddedSubtitleTracks = linkedSetOf<ResolvedEmbeddedSubtitleTrack>()
            var capturedHasEmbeddedSubtitles = false
            var playerStateScriptHandler: ScriptHandler? = null
            val requiresRuntimePlayerDiscovery = sourceUrl.requiresRuntimePlayerDiscovery()
            val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

            fun cleanup() {
                runCatching {
                    playerStateScriptHandler?.remove()
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeAllViews()
                    webView.destroy()
                }
            }

            fun finish(result: Result<ResolvedVideoStream>) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(null)
                cleanup()

                if (!continuation.isActive) return
                result
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            }

            fun finishWithCapturedPlaybackOrFailure() {
                val playback = capturedPlayback
                if (playback != null) {
                    finish(
                        Result.success(
                            playback.toStream(
                                subtitles = capturedSubtitleTracks.toList(),
                                embeddedSubtitles = capturedEmbeddedSubtitleTracks.toList(),
                                hasEmbeddedSubtitles = capturedHasEmbeddedSubtitles,
                            ),
                        ),
                    )
                } else {
                    val timeoutSeconds = WEBVIEW_RESOLVE_TIMEOUT_MS / 1_000L
                    finish(
                        Result.failure(
                            IOException(
                                "Could not capture an HLS/MP4/DASH player stream in $timeoutSeconds seconds. Iframe: $sourceUrl",
                            ),
                        ),
                    )
                }
            }

            fun scheduleFinishAfterDiscoveryIdle() {
                if (completed || capturedPlayback == null) return
                discoveryVersion += 1
                val scheduledVersion = discoveryVersion
                val discoveryIdleMs = if (
                    !waitForRuntimeSubtitles
                ) {
                    WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS
                } else if (
                    capturedSubtitleTracks.isEmpty() &&
                    sourceUrl.requiresRuntimePlayerDiscovery()
                ) {
                    WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS
                } else {
                    WEBVIEW_DISCOVERY_IDLE_MS
                }
                handler.postDelayed(
                    {
                        if (!completed && scheduledVersion == discoveryVersion) {
                            finishWithCapturedPlaybackOrFailure()
                        }
                    },
                    discoveryIdleMs,
                )
            }

            fun captureSubtitleTracks(tracks: List<ResolvedSubtitleTrack>) {
                if (completed || tracks.isEmpty()) return
                tracks.forEach(capturedSubtitleTracks::add)
                scheduleFinishAfterDiscoveryIdle()
            }

            fun captureEmbeddedSubtitleTracks(tracks: List<ResolvedEmbeddedSubtitleTrack>, hasEmbeddedSubtitles: Boolean) {
                if (completed) return
                if (hasEmbeddedSubtitles) {
                    capturedHasEmbeddedSubtitles = true
                }
                if (tracks.isNotEmpty()) {
                    tracks.forEach(capturedEmbeddedSubtitleTracks::add)
                }
                if (tracks.isNotEmpty() || hasEmbeddedSubtitles) {
                    scheduleFinishAfterDiscoveryIdle()
                }
            }

            fun capturePlayback(playback: CapturedPlayback) {
                if (completed) return
                if (capturedPlayback?.url != playback.url) {
                    capturedPlayback = playback
                }
                scheduleFinishAfterDiscoveryIdle()
            }

            webView.addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun captureResponse(rawUrl: String?, contentType: String?, rawBody: String?) {
                        val url = rawUrl
                            ?.takeIf { it.isNotBlank() }
                            ?.normalizeVideoUrlAgainst(sourceUrl)
                            ?: return
                        val body = rawBody?.takeIf { it.isNotBlank() } ?: return
                        if (
                            !url.isInspectablePlayerMetadataUrl() &&
                            !url.isResolvableSubtitleCandidate() &&
                            !body.looksLikePlayerMetadataBody()
                        ) {
                            return
                        }

                        val requestHeaders = capturedRequestHeaders[url].orEmpty()
                            .toPlaybackHeaders(url, sourceUrl, siteBaseUrl)
                        val metadataCapture = runCatching {
                            inspectPlayerMetadataBody(
                                url = url,
                                body = body,
                                requestHeaders = requestHeaders,
                                sourceUrl = sourceUrl,
                                siteBaseUrl = siteBaseUrl,
                                preferredQuality = preferredQuality,
                            )
                        }.getOrNull()
                        val capturedSubtitle = runCatching {
                            materializeCapturedSubtitleBody(
                                url = url,
                                contentType = contentType,
                                body = body,
                            )
                        }.getOrNull()

                        handler.post {
                            metadataCapture?.playback?.let(::capturePlayback)
                            captureSubtitleTracks(metadataCapture?.subtitles.orEmpty() + listOfNotNull(capturedSubtitle))
                            metadataCapture?.let { capture ->
                                captureEmbeddedSubtitleTracks(
                                    tracks = capture.embeddedSubtitles,
                                    hasEmbeddedSubtitles = capture.hasEmbeddedSubtitles,
                                )
                            }
                        }
                    }
                },
                WEBVIEW_DISCOVERY_BRIDGE_NAME,
            )

            if (requiresRuntimePlayerDiscovery && !supportsDocumentStartScript) {
                finish(
                    Result.failure(
                        IOException(
                            "WebView document-start script is not supported; runtime player discovery cannot run. Iframe: $sourceUrl",
                        ),
                    ),
                )
                return@suspendCancellableCoroutine
            }

            val timeout = Runnable {
                finishWithCapturedPlaybackOrFailure()
            }
            handler.postDelayed(timeout, WEBVIEW_RESOLVE_TIMEOUT_MS)

            continuation.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacksAndMessages(null)
                    cleanup()
                }
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = USER_AGENT
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }

            if (
                requiresRuntimePlayerDiscovery &&
                supportsDocumentStartScript
            ) {
                playerStateScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    playerDiscoveryBridgeScript,
                    setOf(ALLOHA_ORIGIN_RULE),
                )
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    val method = request?.method.orEmpty()
                    if (method.equals("GET", ignoreCase = true)) {
                        val requestHeaders = request?.requestHeaders.orEmpty()
                        val playbackHeaders = requestHeaders.toPlaybackHeaders(url, sourceUrl, siteBaseUrl)
                        capturedRequestHeaders[url] = playbackHeaders

                        url.toPotentialSubtitleTrack()
                            ?.copy(headers = playbackHeaders)
                            ?.let { track -> handler.post { captureSubtitleTracks(listOf(track)) } }

                        if (url.isInspectablePlayerMetadataUrl()) {
                            runCatching {
                                inspectPlayerMetadataResponse(
                                    url = url,
                                    requestHeaders = playbackHeaders,
                                    sourceUrl = sourceUrl,
                                    siteBaseUrl = siteBaseUrl,
                                    preferredQuality = preferredQuality,
                                )
                            }.onSuccess { capture ->
                                handler.post {
                                    capture.playback?.let(::capturePlayback)
                                    captureSubtitleTracks(capture.subtitles)
                                    captureEmbeddedSubtitleTracks(
                                        tracks = capture.embeddedSubtitles,
                                        hasEmbeddedSubtitles = capture.hasEmbeddedSubtitles,
                                    )
                                }
                            }
                        }

                        if (url.isCapturedPlaybackUrl()) {
                            handler.post {
                                capturePlayback(
                                    CapturedPlayback(
                                        url = url,
                                        mimeType = url.mimeTypeFromUrl(),
                                        headers = playbackHeaders,
                                        maxVideoHeight = url.detectVideoHeight(),
                                    ),
                                )
                            }
                            if (url.isProgressivePlaybackUrl()) {
                                return WebResourceResponse(
                                    url.mimeTypeFromUrl() ?: "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                            }
                        }
                    }
                    if (!method.equals("GET", ignoreCase = true) && url.isNotBlank()) {
                        capturedRequestHeaders[url] = request?.requestHeaders.orEmpty()
                            .toPlaybackHeaders(url, sourceUrl, siteBaseUrl)
                    }
                    return null
                }
            }

            val html = """
                <!doctype html>
                <html>
                    <head><meta name="referrer" content="no-referrer-when-downgrade"></head>
                    <body style="margin:0;background:#000">
                        <iframe
                            src="$sourceUrl"
                            width="1280"
                            height="720"
                            allow="autoplay; fullscreen"
                            referrerpolicy="origin">
                        </iframe>
                    </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(
                siteBaseUrl,
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    private fun inspectPlayerMetadataResponse(
        url: String,
        requestHeaders: Map<String, String>,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): PlayerMetadataCapture {
        val body = getText(url, requestHeaders)
        return inspectPlayerMetadataBody(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            preferredQuality = preferredQuality,
        )
    }

    private fun inspectPlayerMetadataBody(
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
                !url.isResolvableSubtitleCandidate() &&
                    (bodyIsHlsManifest || bodyIsDashManifest)
            }
        val playback = streamUrl?.let { capturedUrl ->
            val playbackUrl = capturedUrl.normalizeVideoUrlAgainst(sourceUrl)
            val capturedMetadataUrl = capturedUrl == url
            CapturedPlayback(
                url = playbackUrl,
                mimeType = when {
                    capturedMetadataUrl && bodyIsHlsManifest -> "application/x-mpegURL"
                    capturedMetadataUrl && bodyIsDashManifest -> "application/dash+xml"
                    else -> playbackUrl.mimeTypeFromUrl()
                },
                headers = requestHeaders.toPlaybackHeaders(playbackUrl, sourceUrl, siteBaseUrl),
                maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), runtimeStream?.height, playbackUrl.detectVideoHeight()),
                fallbackUrls = runtimeStreams
                    .drop(1)
                    .map { it.url.normalizeVideoUrlAgainst(sourceUrl) },
                skipPlaybackProbe = false,
            )
        }
        val hlsSubtitles = if (bodyIsHlsManifest) {
            body.extractHlsSubtitleTracks(url)
        } else {
            SubtitleDetection(tracks = emptyList(), embeddedSubtitles = emptyList(), hasEmbeddedSubtitles = false)
        }
        val dashEmbeddedSubtitles = if (bodyIsDashManifest) {
            body.extractDashEmbeddedSubtitleTracks()
        } else {
            emptyList()
        }
        val subtitleHeaders = requestHeaders.toPlaybackHeaders(url, sourceUrl, siteBaseUrl)
        val subtitles = (body.extractSubtitleTracks(url) + hlsSubtitles.tracks)
            .map { track ->
                if (track.uri.startsWith("file:", ignoreCase = true) || track.uri.startsWith("content:", ignoreCase = true)) {
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

    private fun iframeHeaders(
        url: String,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            put("Accept", "*/*")
            put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
            put("Referer", siteBaseUrl.withTrailingSlash())
            put("User-Agent", USER_AGENT)
            if (url.contains("alloha.yani.tv", ignoreCase = true)) {
                put("Sec-Fetch-Dest", "iframe")
                put("Sec-Fetch-Mode", "navigate")
            }
        }
    }

    private fun aksorApiHeaders(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: AKSOR_ORIGIN
        return buildMap {
            put("Accept", "application/json")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
        }
    }

    private fun kodikApiHeaders(sourceUrl: String): Map<String, String> {
        return buildMap {
            put("Accept", "application/json, text/javascript, */*; q=0.01")
            put("Origin", sourceUrl.urlOrigin() ?: "https://kodikplayer.com")
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
            put("X-Requested-With", "XMLHttpRequest")
        }
    }

    private fun kodikPlaybackHeaders(url: String): Map<String, String> {
        return buildMap {
            putAll(playbackHeaders(url, "https://kodikplayer.com/"))
            put("Accept", "*/*")
            put("Origin", "https://kodikplayer.com")
            put("Referer", "https://kodikplayer.com/")
            put("User-Agent", USER_AGENT)
        }
    }

    private fun playbackHeaders(
        url: String,
        refererUrl: String? = null,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        val referer = refererUrl?.takeIf { it.isNotBlank() }
        val origin = referer?.urlOrigin()

        return buildMap {
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            put("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            put("User-Agent", USER_AGENT)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
            if (url.contains("vkvideo.cloud", ignoreCase = true)) {
                put("Origin", origin ?: "https://alloha.yani.tv")
                put("Referer", referer ?: "https://alloha.yani.tv/")
            } else if (referer != null && origin != null) {
                put("Origin", origin)
                put("Referer", referer)
            } else {
                put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
                put("Referer", siteBaseUrl.withTrailingSlash())
            }
        }
    }

    private fun cvhApiHeaders(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: "https://ru.yummyani.me"
        return buildMap {
            put("Accept", "application/json, text/plain, */*")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", USER_AGENT)
        }
    }

    private fun cvhPlaybackHeaders(url: String, sourceUrl: String, siteBaseUrl: String): Map<String, String> {
        return buildMap {
            putAll(playbackHeaders(url, sourceUrl, siteBaseUrl))
            put("Accept", "*/*")
            put("Origin", "https://player.cdnvideohub.com")
            put("Referer", "https://player.cdnvideohub.com/")
            put("User-Agent", USER_AGENT)
        }
    }

    private fun Map<String, String>.toPlaybackHeaders(
        streamUrl: String,
        sourceUrl: String,
        siteBaseUrl: String = siteDomainResolver.cachedOrDefaultBaseUrl(),
    ): Map<String, String> {
        val sourceHeaders = this
        return buildMap {
            putAll(playbackHeaders(streamUrl, sourceUrl, siteBaseUrl))
            sourceHeaders.forEach { (name, value) ->
                if (name.isForwardablePlaybackHeader() && value.isNotBlank()) {
                    put(name, value)
                }
            }
            putIfAbsent("Referer", sourceUrl)
            putIfAbsent("Origin", sourceUrl.urlOrigin() ?: siteBaseUrl.urlOrigin().orEmpty())
            putIfAbsent("User-Agent", USER_AGENT)
            putIfAbsent("Accept-Encoding", "identity")
            putIfAbsent("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            putIfAbsent("Sec-Fetch-Dest", "empty")
            putIfAbsent("Sec-Fetch-Mode", "cors")
            putIfAbsent("Sec-Fetch-Site", "cross-site")
            playbackCookies(streamUrl, sourceUrl)?.let { put("Cookie", it) }
        }
    }

    private fun String.isForwardablePlaybackHeader(): Boolean {
        return when (lowercase()) {
            "accept-encoding",
            "access-control-request-headers",
            "access-control-request-method",
            "connection",
            "host",
            "range" -> false
            else -> true
        }
    }

    private fun playbackCookies(streamUrl: String, sourceUrl: String): String? {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return null
        val streamOrigin = streamUrl.urlOrigin()
        val sourceOrigin = sourceUrl.urlOrigin()
        val cookieUrls = buildList {
            add(streamUrl)
            add(streamOrigin)
            if (streamOrigin != null && streamOrigin == sourceOrigin) {
                add(sourceUrl)
                add(sourceOrigin)
            }
        }
        return cookieUrls
            .asSequence()
            .filterNotNull()
            .mapNotNull { url -> runCatching { cookieManager.getCookie(url) }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun String.extractSubtitleTracks(baseUrl: String): List<ResolvedSubtitleTrack> {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        val urlTracks = subtitleUrlRegex
            .findAll(normalized)
            .mapNotNull { match ->
                match.value
                    .trim('"', '\'', ' ', '\\')
                    .normalizeVideoUrlAgainst(baseUrl)
                    .toDirectSubtitleTrack()
            }
            .toList()

        return (urlTracks + normalized.extractStructuredSubtitleTracks(baseUrl))
            .normalizedSubtitleTracks()
    }

    private fun String.extractStructuredSubtitleTracks(baseUrl: String): List<ResolvedSubtitleTrack> {
        val element = runCatching { json.parseToJsonElement(this) }.getOrNull() ?: return emptyList()
        return element.collectStructuredSubtitleTracks(baseUrl).normalizedSubtitleTracks()
    }

    private fun JsonElement.collectStructuredSubtitleTracks(
        baseUrl: String,
        subtitleContext: Boolean = false,
        inheritedLabel: String = "",
        inheritedLanguage: String? = null,
    ): List<ResolvedSubtitleTrack> {
        return when (this) {
            is JsonArray -> flatMap { item ->
                item.collectStructuredSubtitleTracks(
                    baseUrl = baseUrl,
                    subtitleContext = subtitleContext,
                    inheritedLabel = inheritedLabel,
                    inheritedLanguage = inheritedLanguage,
                )
            }
            is JsonObject -> collectStructuredSubtitleTracksFromObject(
                baseUrl = baseUrl,
                subtitleContext = subtitleContext,
                inheritedLabel = inheritedLabel,
                inheritedLanguage = inheritedLanguage,
            )
            is JsonPrimitive -> {
                val value = contentOrNull?.trim().orEmpty()
                if (subtitleContext && value.looksLikeJsonPayload()) {
                    runCatching { json.parseToJsonElement(value) }
                        .getOrNull()
                        ?.collectStructuredSubtitleTracks(
                            baseUrl = baseUrl,
                            subtitleContext = true,
                            inheritedLabel = inheritedLabel,
                            inheritedLanguage = inheritedLanguage,
                        )
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { return it }
                }
                if (subtitleContext && value.isResolvableSubtitleCandidate()) {
                    val uri = value.normalizeVideoUrlAgainst(baseUrl)
                    listOfNotNull(uri.toPotentialSubtitleTrack(inheritedLabel, inheritedLanguage))
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun JsonObject.collectStructuredSubtitleTracksFromObject(
        baseUrl: String,
        subtitleContext: Boolean,
        inheritedLabel: String,
        inheritedLanguage: String?,
    ): List<ResolvedSubtitleTrack> {
        val objectContext = subtitleContext ||
            keys.any { it.isSubtitleMetadataKey() } ||
            firstJsonString("kind", "type", "role").orEmpty().isSubtitleDescriptor()
        val label = firstJsonString("label", "title", "name", "displayName")
            ?: inheritedLabel
        val language = firstJsonString("language", "lang", "srclang")
            ?: inheritedLanguage

        val directTracks = entries.mapNotNull { (key, element) ->
            val value = (element as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (value.isBlank()) return@mapNotNull null
            val keySuggestsSubtitle = key.isSubtitleMetadataKey() || key.isSubtitleUrlKey()
            if (!keySuggestsSubtitle && !objectContext && !value.isPotentialSubtitleRequestUrl()) {
                return@mapNotNull null
            }
            if (!value.isResolvableSubtitleCandidate()) return@mapNotNull null
            val uri = value.normalizeVideoUrlAgainst(baseUrl)
            uri.toPotentialSubtitleTrack(label, language)
        }

        val nestedTracks = entries.flatMap { (key, element) ->
            element.collectStructuredSubtitleTracks(
                baseUrl = baseUrl,
                subtitleContext = objectContext || key.isSubtitleMetadataKey(),
                inheritedLabel = label,
                inheritedLanguage = language,
            )
        }

        return directTracks + nestedTracks
    }

    private fun JsonObject.firstJsonString(vararg names: String): String? {
        val normalizedNames = names.map { it.lowercase() }.toSet()
        return entries.firstNotNullOfOrNull { (key, value) ->
            key.lowercase()
                .takeIf(normalizedNames::contains)
                ?.let { (value as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun String.looksLikeJsonPayload(): Boolean {
        val value = trim()
        return (value.startsWith("{") && value.endsWith("}")) ||
            (value.startsWith("[") && value.endsWith("]"))
    }

    private fun String.extractDashEmbeddedSubtitleTracks(): List<ResolvedEmbeddedSubtitleTrack> {
        val document = runCatching {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(this)))
        }.getOrNull() ?: return emptyList()

        val adaptationSets = document.getElementsByTagNameNS("*", "AdaptationSet")
        return (0 until adaptationSets.length)
            .asSequence()
            .mapNotNull { index -> adaptationSets.item(index) as? Element }
            .filter { adaptationSet -> adaptationSet.isDashSubtitleAdaptationSet() }
            .map { adaptationSet -> adaptationSet.dashEmbeddedSubtitleTrack() }
            .toList()
            .normalizedEmbeddedSubtitleTracks()
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
            .ifBlank { representations.firstNotNullOfOrNull { it.childText("Label").takeIf(String::isNotBlank) }.orEmpty() }
            .ifBlank { representations.firstNotNullOfOrNull { it.attributeOrBlank("label").takeIf(String::isNotBlank) }.orEmpty() }
        val id = attributeOrBlank("id")
            .ifBlank { representations.firstNotNullOfOrNull { it.attributeOrBlank("id").takeIf(String::isNotBlank) }.orEmpty() }
            .ifBlank { label }
        return ResolvedEmbeddedSubtitleTrack(
            id = id,
            label = label,
            language = language,
        )
    }

    private fun Element.childElements(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return (0 until nodes.length)
            .mapNotNull { index -> nodes.item(index) as? Element }
    }

    private fun Element.childText(name: String): String {
        return childElements(name)
            .firstOrNull()
            ?.textContent
            ?.trim()
            .orEmpty()
    }

    private fun Element.attributeOrBlank(name: String): String {
        return getAttribute(name).trim()
    }

    private fun String.extractHlsSubtitleTracks(baseUrl: String): SubtitleDetection {
        val tracks = mutableListOf<ResolvedSubtitleTrack>()
        val embeddedTracks = mutableListOf<ResolvedEmbeddedSubtitleTrack>()
        var hasEmbeddedSubtitles = false

        lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) return@forEach
            val type = line.hlsAttribute("TYPE").orEmpty()
            when {
                type.equals("SUBTITLES", ignoreCase = true) -> {
                    val uri = line.hlsAttribute("URI")
                    if (uri.isNullOrBlank()) {
                        return@forEach
                    } else {
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
                }
                type.equals("CLOSED-CAPTIONS", ignoreCase = true) -> {
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

    private fun String.extractSibnetStreamUrl(baseUrl: String): String? {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        return sibnetPlayerSourceRegex
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"', '\'', ' ', '\\')
            ?.normalizeVideoUrlAgainst(baseUrl)
            ?.takeIf { it.isCapturedPlaybackUrl() }
    }

    private fun String.normalizeVideoUrl(siteBaseUrl: String): String {
        val value = trim()
        return when {
            value.startsWith("//") -> {
                val absoluteUrl = "https:$value"
                if (siteDomainResolver.isKnownSiteHost(runCatching { absoluteUrl.toUri().host }.getOrNull())) {
                    absoluteUrl.rewriteKnownSiteHost(siteBaseUrl)
                } else {
                    absoluteUrl
                }
            }
            value.startsWith("/") -> "${siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/')}$value"
            siteDomainResolver.isKnownSiteHost(runCatching { value.toUri().host }.getOrNull()) ->
                value.rewriteKnownSiteHost(siteBaseUrl)
            else -> value
        }
    }

    private fun String.normalizeVideoUrlAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, siteDomainResolver.cachedOrDefaultBaseUrl())
    }

    private fun String.isInspectablePlayerMetadataUrl(): Boolean {
        val uri = runCatching { toUri() }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        if ("alloha" !in host && "alloh" !in host) return false
        val path = uri.path.orEmpty().lowercase()
        return path.startsWith("/movies/") ||
            path.startsWith("/serials/") ||
            path.startsWith("/trailers/") ||
            path.startsWith("/player/") ||
            path.startsWith("/video/")
    }

    private fun String.isSubtitleUrl(): Boolean {
        val lower = substringBefore('?').substringBefore('#').lowercase()
        return lower.endsWith(".vtt") ||
            lower.endsWith(".srt") ||
            lower.endsWith(".ass") ||
            lower.endsWith(".ssa") ||
            lower.endsWith(".ttml") ||
            lower.endsWith(".dfxp")
    }

    private fun String.toDirectSubtitleTrack(): ResolvedSubtitleTrack? {
        if (!isSubtitleUrl()) return null
        return ResolvedSubtitleTrack(
            uri = this,
            label = subtitleLabelFromUrl(),
            mimeType = subtitleMimeTypeFromUrl(),
        )
    }

    private fun String.toPotentialSubtitleTrack(
        label: String = "",
        language: String? = null,
    ): ResolvedSubtitleTrack? {
        if (!isResolvableSubtitleCandidate()) return null
        return ResolvedSubtitleTrack(
            uri = this,
            label = label.takeIf { it.isNotBlank() } ?: subtitleLabelFromUrl(),
            language = language?.takeIf { it.isNotBlank() },
            mimeType = subtitleMimeTypeFromUrl(),
        )
    }

    private fun String.isResolvableSubtitleCandidate(): Boolean {
        val value = trim()
        if (value.isBlank()) return false
        if (value.isSubtitleUrl()) return true
        if (!value.isUrlLike()) return false
        return value.isPotentialSubtitleRequestUrl()
    }

    private fun String.isPotentialSubtitleRequestUrl(): Boolean {
        val lower = runCatching { java.net.URLDecoder.decode(this, Charsets.UTF_8.name()) }
            .getOrDefault(this)
            .lowercase()
        return "subtitle" in lower ||
            "subtitles" in lower ||
            "caption" in lower ||
            "captions" in lower ||
            "texttrack" in lower ||
            "texttracks" in lower ||
            "/track" in lower ||
            "track=" in lower ||
            ".vtt" in lower ||
            ".srt" in lower ||
            ".ass" in lower ||
            ".ssa" in lower ||
            ".ttml" in lower ||
            ".dfxp" in lower
    }

    private fun String.isUrlLike(): Boolean {
        val value = trim()
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return true
        }
        if (value.startsWith("//") || value.startsWith("/")) return true
        return Regex("""^[\w.-]+\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
            .matches(value)
    }

    private fun String.isSubtitleMetadataKey(): Boolean {
        val lower = lowercase()
        return "subtitle" in lower ||
            "caption" in lower ||
            lower == "texttrack" ||
            lower == "texttracks"
    }

    private fun String.isSubtitleUrlKey(): Boolean {
        return when (lowercase()) {
            "src",
            "url",
            "file",
            "href",
            "path",
            "link",
            "track",
            "tracks" -> true
            else -> false
        }
    }

    private fun String.isSubtitleDescriptor(): Boolean {
        val lower = trim().lowercase()
        return lower == "subtitle" ||
            lower == "subtitles" ||
            lower == "caption" ||
            lower == "captions" ||
            lower == "sub" ||
            lower == "subs" ||
            lower == "texttrack"
    }

    private fun String.isCvhIframeUrl(): Boolean {
        val uri = runCatching { toUri() }.getOrNull() ?: return false
        return siteDomainResolver.isKnownSiteHost(uri.host) &&
            uri.path.orEmpty().contains("iframeCVH", ignoreCase = true)
    }

    companion object {
        const val WEBVIEW_RESOLVE_TIMEOUT_MS = 30_000L
        const val USER_AGENT = BROWSER_USER_AGENT
        const val CVH_PUBLISHER_ID = "745"
        const val CVH_AGGREGATOR = "mali"
        const val CVH_VIDEO_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/video"
        val CVH_PLAYLIST_URL = "https://plapi.cdnvideohub.com/api/v1/player/sv/playlist".toHttpUrl()
        const val KODIK_FTOR_URL = "https://kodikplayer.com/ftor"
        const val AKSOR_ORIGIN = "https://player.aksor.tv"
        const val SUBTITLE_CACHE_DIR = "subtitle_streams"
        const val SUBTITLE_CACHE_FILE_PREFIX = "subtitle_"
        const val SUBTITLE_CACHE_TTL_MS = 6L * 60L * 60L * 1000L
        const val WEBVTT_HEADER_MIN_BYTES = 8L
        const val WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS = 250L
        const val WEBVIEW_DISCOVERY_IDLE_MS = 1_200L
        const val WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS = 4_000L
        const val WEBVIEW_DISCOVERY_BRIDGE_NAME = "YummyResolverBridge"
        const val ALLOHA_ORIGIN_RULE = "https://alloha.yani.tv"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val playerDiscoveryBridgeScript = """
            (function() {
                if (window.__yummyResolverBridgeInstalled) return;
                window.__yummyResolverBridgeInstalled = true;
                var attemptsLeft = 80;
                var timer = window.setInterval(function() {
                    try {
                        var source = window.player && window.player.currentSource;
                        var quality = source && source.quality;
                        if (!quality || typeof quality !== 'object') {
                            if (--attemptsLeft <= 0) window.clearInterval(timer);
                            return;
                        }
                        var hasHls = Object.keys(quality).some(function(key) {
                            return /\.m3u8(?:[?#]|${'$'})/i.test(String(quality[key] || ''));
                        });
                        if (!hasHls) {
                            if (--attemptsLeft <= 0) window.clearInterval(timer);
                            return;
                        }
                        var bridge = window['$WEBVIEW_DISCOVERY_BRIDGE_NAME'];
                        if (bridge && bridge.captureResponse) {
                            bridge.captureResponse(
                                String(location.href),
                                'application/json',
                                JSON.stringify({ hlsSource: [source] })
                            );
                            window.clearInterval(timer);
                        }
                    } catch (error) {}
                }, 250);
            })();
        """.trimIndent()

        val streamUrlRegex = Regex(
            """(?:(?:https?:)?//|/)[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val embeddedAbsoluteStreamUrlRegex = Regex(
            """https?://[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val subtitleUrlRegex = Regex(
            """(?:(?:https?:)?//|/)?[^"'\s<>\\]+?\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:\?[^"'\s<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        val sibnetPlayerSourceRegex = Regex(
            """src\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd)(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE,
        )
        val dashHeightRegex = Regex("""(?i)\b(?:height|maxHeight)\s*=\s*["'](\d+)["']""")
        val qualityHeightRegex = Regex("""(?i)(?:^|[^\d])(2160|1440|1080|720|576|540|480|360|240|144)p(?:[^\d]|$)""")
        val webVttTimingRegex = Regex(
            """^(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2,}:\d{2}:\d{2}\.\d{3}|\d{2}:\d{2}\.\d{3})(.*)$""",
        )
        val webVttTimestampMapLocalRegex = Regex("""(?i)\bLOCAL:([^,\s]+)""")
        val subtitleTimingRegex = Regex(
            """^\s*(?:\d+\s+)?(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}\s*-->\s*(?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3}(?:\s+.*)?$""",
        )
        val subtitleTimingLineRegex = Regex(
            """^\s*(?:\d+\s+)?((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[,.]\d{1,3})(.*)$""",
        )
        val ttmlParagraphRegex = Regex(
            """<p\b[^>]*>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val ttmlParagraphWithAttributesRegex = Regex(
            """<p\b([^>]*)>(.*?)</p>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val subtitleHtmlTagRegex = Regex("""<[^>]+>""")
        val subtitleHtmlSpaceEntityRegex = Regex("""&(?:nbsp|#160|#xA0);""", RegexOption.IGNORE_CASE)
        val assBlankEscapeRegex = Regex("""\\[Nnh]""")
        val xmlTimeAttributeRegex = Regex("""\b([A-Za-z_:][\w:.-]*)\s*=\s*["']([^"']+)["']""")
    }
}
