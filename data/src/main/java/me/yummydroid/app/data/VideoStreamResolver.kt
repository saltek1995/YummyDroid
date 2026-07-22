package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class VideoStreamResolver(
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val appContext = context?.applicationContext

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ): ResolvedVideoStream {
        val stream = resolveInternal(video, preferredQuality)
        return withContext(Dispatchers.IO) {
            validatePlayableStream(stream)
            stream.withDetectedSourceMetadata()
        }
    }

    private suspend fun resolveInternal(
        video: VideoVariant,
        preferredQuality: PreferredQuality,
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
                )
            }.onSuccess { stream ->
                siteDomainResolver.markAvailable(siteBaseUrl)
                return@withContext stream
            }.onFailure { throwable ->
                lastFailure = throwable
                siteDomainResolver.markUnavailable(siteBaseUrl)
            }
        }
        throw lastFailure ?: IOException("Не удалось выбрать рабочий домен сайта")
    }

    private suspend fun resolveInternalForBaseUrl(
        video: VideoVariant,
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
    ): ResolvedVideoStream {
        val headers = iframeHeaders(sourceUrl, siteBaseUrl)

        if (sourceUrl.isCvhIframeUrl()) {
            return resolveCvh(sourceUrl, video, siteBaseUrl, preferredQuality)
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
                throw IOException("Плеер вернул HTTP ${response.code}")
            }

            if (body.trimStart().startsWith("#EXTM3U")) {
                val playbackHeaders = playbackHeaders(sourceUrl, sourceUrl, siteBaseUrl)
                val subtitles = body.extractHlsSubtitleTracks(sourceUrl)
                    .materializedSubtitleDetection(playbackHeaders)
                return ResolvedVideoStream(
                    url = sourceUrl,
                    mimeType = "application/x-mpegURL",
                    headers = playbackHeaders,
                    maxVideoHeight = body.detectVideoHeight(),
                    availableQualities = body.detectSourceQualities(),
                    subtitles = subtitles.tracks,
                    hasEmbeddedSubtitles = subtitles.hasEmbeddedSubtitles,
                )
            }

            body.extractDirectStreamUrl(sourceUrl)?.let { streamUrl ->
                val detectedQualities = (body.detectSourceQualities() + streamUrl.detectSourceQualities())
                    .normalizedSourceQualities()
                return ResolvedVideoStream(
                    url = streamUrl,
                    mimeType = streamUrl.mimeTypeFromUrl(),
                    headers = playbackHeaders(streamUrl, sourceUrl, siteBaseUrl),
                    maxVideoHeight = maxOfOrNull(body.detectVideoHeight(), streamUrl.detectVideoHeight()),
                    availableQualities = detectedQualities,
                    subtitles = body.extractSubtitleTracks(sourceUrl),
                )
            }
        }

        return resolveViaWebView(sourceUrl, siteBaseUrl)
    }

    private fun validatePlayableStream(stream: ResolvedVideoStream) {
        val url = stream.url.takeIf { it.isNotBlank() }
            ?: throw IOException("Плеер не вернул ссылку на видео")
        if (url.startsWith("blob:", ignoreCase = true)) {
            throw IOException("Плеер вернул blob-поток, недоступный для нативного воспроизведения")
        }

        val request = Request.Builder()
            .url(url)
            .headers(stream.headers.toOkHttpHeaders())
            .header("Range", "bytes=0-4095")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code !in listOf(200, 206)) {
                throw IOException("источник вернул HTTP ${response.code}")
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
                throw IOException("источник не похож на HLS/DASH/MP4 поток")
            }
        }
    }

    private fun ResolvedVideoStream.withDetectedSourceMetadata(): ResolvedVideoStream {
        val manifestText = loadAdaptiveManifestTextOrNull()
        val detectedQualities = detectSourceQualities(manifestText)
        val detectedHeight = detectedQualities.mapNotNull { it.height }.maxOrNull()
        val resolvedHeight = maxOfOrNull(maxVideoHeight, detectedHeight, url.detectVideoHeight())
        val resolvedQualities = (availableQualities + detectedQualities + listOfNotNull(resolvedHeight?.let { SourceQuality(height = it) }))
            .normalizedSourceQualities()
        val detectedSubtitles = detectSubtitleTracks(manifestText)
        return copy(
            maxVideoHeight = resolvedHeight,
            availableQualities = resolvedQualities,
            subtitles = (subtitles.materializedSubtitleTracks(headers) + detectedSubtitles.tracks)
                .normalizedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles || detectedSubtitles.hasEmbeddedSubtitles,
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
                hasEmbeddedSubtitles = false,
            )
        }

        val body = manifestText ?: return SubtitleDetection(
            tracks = listOfNotNull(directTrack).normalizedSubtitleTracks(),
            hasEmbeddedSubtitles = false,
        )
        val hlsSubtitles = body.extractHlsSubtitleTracks(url)
            .materializedSubtitleDetection(headers)
        return SubtitleDetection(
            tracks = (listOfNotNull(directTrack) + hlsSubtitles.tracks + body.extractSubtitleTracks(url))
                .normalizedSubtitleTracks(),
            hasEmbeddedSubtitles = hlsSubtitles.hasEmbeddedSubtitles,
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
                throw IOException("Kodik API вернул HTTP ${response.code}")
            }
            text
        }
        val dto = json.decodeFromString<KodikFtorDto>(body)
        val stream = dto.bestStream(preferredQuality)
            ?: throw IOException("Kodik: не найден HLS/MP4/DASH поток")

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
            ?: throw IOException("Sibnet: не найден HLS/MP4/DASH поток")

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
            ?: throw IOException("CVH: не найден anime_id в iframe")
        val episode = iframeUri.getQueryParameter("episode")?.toIntOrNull()
            ?: video.episode.toIntOrNull()
            ?: 1
        val season = iframeUri.getQueryParameter("season")?.toIntOrNull() ?: 1
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
            ?: throw IOException("CVH: у серии нет vkId")
        val videoUrl = "$CVH_VIDEO_URL/$vkId"
        val cvhVideo = getJson<CvhVideoDto>(videoUrl, cvhApiHeaders(sourceUrl))
        val source = cvhVideo.sources?.bestStream(preferredQuality)
            ?: throw IOException("CVH: не найден HLS/DASH/MP4 поток")

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
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException("Плеер вернул HTTP ${response.code}")
            }
            return body
        }
    }

    private inline fun <reified T> getJson(url: String, headers: Map<String, String>): T {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IOException("CVH API вернул HTTP ${response.code}")
            }
            return json.decodeFromString(body)
        }
    }

    private fun SubtitleDetection.materializedSubtitleDetection(headers: Map<String, String>): SubtitleDetection {
        return copy(tracks = tracks.materializedSubtitleTracks(headers))
    }

    private fun List<ResolvedSubtitleTrack>.materializedSubtitleTracks(
        headers: Map<String, String>,
    ): List<ResolvedSubtitleTrack> {
        return map { track -> track.materializedSubtitleTrack(headers) }
            .normalizedSubtitleTracks()
    }

    private fun ResolvedSubtitleTrack.materializedSubtitleTrack(
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack {
        if (!uri.isHlsPlaylistUrl() && mimeType?.contains("mpegurl", ignoreCase = true) != true) return this
        return runCatching { materializeHlsSubtitlePlaylist(this, headers) }
            .getOrDefault(this)
    }

    private fun materializeHlsSubtitlePlaylist(
        track: ResolvedSubtitleTrack,
        headers: Map<String, String>,
    ): ResolvedSubtitleTrack {
        val context = appContext ?: return track
        val outputFile = subtitleCacheFile(context.cacheDir, track.uri)
        if (outputFile.isFreshSubtitleCacheFile()) {
            return track.copy(uri = Uri.fromFile(outputFile).toString(), mimeType = "text/vtt")
        }

        val playlist = getText(track.uri, headers)
        val segments = playlist.hlsSubtitleSegments(track.uri)
        val cueSegments = if (segments.isNotEmpty()) {
            segments.map { segment ->
                MaterializedSubtitleSegment(
                    body = getText(segment.url, headers).webVttCueBody(),
                    offsetMs = segment.offsetMs,
                    durationMs = segment.durationMs,
                )
            }
        } else if (playlist.trimStart().startsWith("WEBVTT", ignoreCase = true)) {
            listOf(
                MaterializedSubtitleSegment(
                    body = playlist.webVttCueBody(),
                    offsetMs = 0L,
                    durationMs = 0L,
                ),
            )
        } else {
            emptyList()
        }

        val nonBlankSegments = cueSegments.filter { it.body.isNotBlank() }
        if (nonBlankSegments.isEmpty()) return track

        val shouldShiftCueTimes = nonBlankSegments.shouldShiftWebVttCueTimes()
        val cues = nonBlankSegments.map { segment ->
            segment.body
                .let { body -> if (shouldShiftCueTimes) body.shiftWebVttCueTimes(segment.offsetMs) else body }
                .trim()
        }.filter { it.isNotBlank() }

        if (cues.isEmpty()) return track

        outputFile.parentFile?.mkdirs()
        cleanupOldSubtitleFiles(outputFile.parentFile)
        outputFile.writeText(
            buildString {
                append("WEBVTT\n\n")
                append(cues.joinToString("\n\n"))
                append('\n')
            },
            Charsets.UTF_8,
        )
        return track.copy(uri = Uri.fromFile(outputFile).toString(), mimeType = "text/vtt")
    }

    private fun subtitleCacheFile(cacheDir: File, sourceUri: String): File {
        return File(File(cacheDir, SUBTITLE_CACHE_DIR), "$SUBTITLE_CACHE_FILE_PREFIX${sourceUri.sha256Hex()}.vtt")
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
                    file.name.startsWith(SUBTITLE_CACHE_FILE_PREFIX) &&
                    file.name.endsWith(".vtt")
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
    ): ResolvedVideoStream = withContext(Dispatchers.Main) {
        val context = appContext ?: throw IOException("Нужен Context для JS-перехвата потока")

        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context)
            var completed = false
            val capturedSubtitleTracks = linkedSetOf<ResolvedSubtitleTrack>()

            fun cleanup() {
                runCatching {
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

            val timeout = Runnable {
                finish(Result.failure(IOException("Не удалось перехватить HLS/MP4/DASH поток плеера за 35 секунд. Iframe: $sourceUrl")))
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

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString().orEmpty()
                    val method = request?.method.orEmpty()
                    if (method.equals("GET", ignoreCase = true)) {
                        url.toDirectSubtitleTrack()?.let(capturedSubtitleTracks::add)
                    }
                    if (method.equals("GET", ignoreCase = true) && url.isCapturedPlaybackUrl()) {
                        val requestHeaders = request?.requestHeaders.orEmpty()
                        handler.post {
                            finish(
                                Result.success(
                                    ResolvedVideoStream(
                                        url = url,
                                        mimeType = url.mimeTypeFromUrl(),
                                        headers = requestHeaders.toPlaybackHeaders(url, sourceUrl, siteBaseUrl),
                                        maxVideoHeight = url.detectVideoHeight(),
                                        subtitles = capturedSubtitleTracks.toList().normalizedSubtitleTracks(),
                                    ),
                                ),
                            )
                        }
                        return WebResourceResponse(
                            url.mimeTypeFromUrl() ?: "text/plain",
                            "UTF-8",
                            ByteArrayInputStream(ByteArray(0)),
                        )
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
        val cookieManager = CookieManager.getInstance()
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

    private fun String.extractDirectStreamUrl(baseUrl: String): String? {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        return streamUrlRegex
            .findAll(normalized)
            .map { it.value.trim('"', '\'', ' ', '\\') }
            .map { it.normalizeVideoUrlAgainst(baseUrl) }
            .firstOrNull { it.isCapturedPlaybackUrl() }
    }

    private fun String.extractSubtitleTracks(baseUrl: String): List<ResolvedSubtitleTrack> {
        val normalized = this
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

        return subtitleUrlRegex
            .findAll(normalized)
            .mapNotNull { match ->
                match.value
                    .trim('"', '\'', ' ', '\\')
                    .normalizeVideoUrlAgainst(baseUrl)
                    .toDirectSubtitleTrack()
            }
            .toList()
            .normalizedSubtitleTracks()
    }

    private fun String.extractHlsSubtitleTracks(baseUrl: String): SubtitleDetection {
        val tracks = mutableListOf<ResolvedSubtitleTrack>()
        var hasEmbeddedSubtitles = false

        lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) return@forEach
            val type = line.hlsAttribute("TYPE").orEmpty()
            when {
                type.equals("SUBTITLES", ignoreCase = true) -> {
                    hasEmbeddedSubtitles = true
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
                }
            }
        }

        return SubtitleDetection(
            tracks = tracks.normalizedSubtitleTracks(),
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
        val value = trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "${baseUrl.urlOrigin() ?: siteDomainResolver.cachedOrDefaultBaseUrl().trimEnd('/')}$value"
            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("blob:", ignoreCase = true) -> value
            else -> baseUrl.toHttpUrlOrNull()?.resolve(value)?.toString() ?: value
        }
    }

    private fun String.isCapturedPlaybackUrl(): Boolean {
        val lower = lowercase()
        return isDirectStreamUrl() &&
            "blank.mp4" !in lower &&
            "cdn.plyr.io" !in lower
    }

    private fun String.isDirectStreamUrl(): Boolean {
        val lower = lowercase()
        return ".m3u8" in lower || ".mp4" in lower || ".mpd" in lower || lower.startsWith("blob:").not() && "#EXTM3U" in this
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

    private fun String.mimeTypeFromUrl(): String? {
        val lower = lowercase()
        return when {
            ".m3u8" in lower -> "application/x-mpegURL"
            ".mpd" in lower -> "application/dash+xml"
            ".mp4" in lower -> "video/mp4"
            else -> null
        }
    }

    private fun String.subtitleMimeTypeFromUrl(): String? {
        val lower = substringBefore('?').substringBefore('#').lowercase()
        return when {
            lower.endsWith(".vtt") -> "text/vtt"
            lower.endsWith(".srt") -> "application/x-subrip"
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> "text/x-ssa"
            lower.endsWith(".ttml") || lower.endsWith(".dfxp") -> "application/ttml+xml"
            lower.endsWith(".m3u8") -> "application/x-mpegURL"
            else -> null
        }
    }

    private fun String.subtitleLabelFromUrl(): String {
        val path = runCatching { toUri().lastPathSegment }.getOrNull()
            ?: substringBefore('?').substringBefore('#').substringAfterLast('/')
        return path
            .substringBeforeLast('.', path)
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Subtitles"
    }

    private fun String.rewriteKnownSiteHost(siteBaseUrl: String): String {
        val targetOrigin = siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/')
        return runCatching {
            val uri = toUri()
            val path = uri.encodedPath.orEmpty()
            val query = uri.encodedQuery?.let { "?$it" }.orEmpty()
            val fragment = uri.encodedFragment?.let { "#$it" }.orEmpty()
            "$targetOrigin$path$query$fragment"
        }.getOrDefault(this)
    }

    private fun String.isCvhIframeUrl(): Boolean {
        val uri = runCatching { toUri() }.getOrNull() ?: return false
        return siteDomainResolver.isKnownSiteHost(uri.host) &&
            uri.path.orEmpty().contains("iframeCVH", ignoreCase = true)
    }

    private fun String.isKodikIframeUrl(): Boolean {
        val host = runCatching { toUri().host.orEmpty() }.getOrDefault("")
        return host.equals("kodikplayer.com", ignoreCase = true) ||
            host.endsWith(".kodikplayer.com", ignoreCase = true)
    }

    private fun String.isAksorIframeUrl(): Boolean {
        val uri = runCatching { toUri() }.getOrNull() ?: return false
        return uri.host.equals("player.aksor.tv", ignoreCase = true) &&
            uri.path.orEmpty().startsWith("/video/", ignoreCase = true)
    }

    private fun String.isSibnetIframeUrl(): Boolean {
        val uri = runCatching { toUri() }.getOrNull() ?: return false
        return uri.host.equals("video.sibnet.ru", ignoreCase = true) &&
            uri.path.orEmpty().contains("shell.php", ignoreCase = true)
    }

    private fun String.kodikParams(): KodikParams {
        val type = extractKodikValue("type")
            ?: extractKodikVInfoValue("type")
            ?: throw IOException("Kodik: не найден type")
        val id = extractKodikVInfoValue("id")
            ?: extractKodikValue("videoId")
            ?: throw IOException("Kodik: не найден id")
        val hash = extractKodikVInfoValue("hash")
            ?: throw IOException("Kodik: не найден hash")

        return KodikParams(
            type = type,
            id = id,
            hash = hash,
            domain = extractKodikValue("domain") ?: throw IOException("Kodik: не найден domain"),
            domainSign = extractKodikValue("d_sign") ?: throw IOException("Kodik: не найден d_sign"),
            playerDomain = extractKodikValue("pd") ?: "kodikplayer.com",
            playerDomainSign = extractKodikValue("pd_sign") ?: throw IOException("Kodik: не найден pd_sign"),
            referer = extractKodikValue("ref") ?: DEFAULT_SITE_BASE_URL,
            refererSign = extractKodikValue("ref_sign") ?: throw IOException("Kodik: не найден ref_sign"),
        )
    }

    private fun String.extractKodikValue(name: String): String? {
        val doubleQuoted = Regex("""var\s+$name\s*=\s*"([^"]*)"""").find(this)?.groupValues?.getOrNull(1)
        if (!doubleQuoted.isNullOrBlank()) return doubleQuoted
        val singleQuoted = Regex("""var\s+$name\s*=\s*'([^']*)'""").find(this)?.groupValues?.getOrNull(1)
        return singleQuoted?.takeIf { it.isNotBlank() }
    }

    private fun String.extractKodikVInfoValue(name: String): String? {
        return Regex("""vInfo\.$name\s*=\s*['"]([^'"]+)['"]""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildCvhVoiceCandidates(
        iframeUri: Uri,
        video: VideoVariant,
    ): List<String> {
        val iframeVoices = listOf(
            "dubbing_code",
            "priority-voice",
            "translation",
            "voice",
            "voiceStudio",
            "voice_studio",
            "dubbing",
        ).mapNotNull { name -> iframeUri.getQueryParameter(name) }

        return (iframeVoices + video.dubbing + video.groupTitle)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.cvhVoiceAliases().isNotEmpty() }
            .distinctBy { it.cvhVoiceIdentity() }
    }

    private fun List<CvhItemDto>.selectCvhItem(
        season: Int,
        episode: Int,
        priorityVoices: List<String>,
    ): CvhItemDto? {
        val episodeItems = filter { item ->
            (item.season ?: 1) == season && (item.episode ?: 1) == episode
        }
        if (episodeItems.isEmpty()) return null

        val requestedAliases = priorityVoices
            .flatMap { it.cvhVoiceAliases() }
            .toSet()
        if (requestedAliases.isNotEmpty()) {
            episodeItems.firstOrNull { item ->
                item.cvhVoiceAliases().any { it in requestedAliases }
            }?.let { return it }

            episodeItems.firstOrNull { item ->
                item.cvhVoiceAliases().any { itemAlias ->
                    requestedAliases.any { requestedAlias ->
                        itemAlias.isMeaningfulCvhAliasMatch(requestedAlias)
                    }
                }
            }?.let { return it }

            if (priorityVoices.any { it.isSubtitleCvhVoice() }) {
                episodeItems.firstOrNull { item ->
                    item.voiceType.orEmpty().isSubtitleCvhVoice() ||
                        item.voiceStudio.orEmpty().isSubtitleCvhVoice()
                }?.let { return it }
            }

            return null
        }

        return episodeItems.firstOrNull { !it.voiceStudio.isNullOrBlank() }
            ?: episodeItems.firstOrNull()
    }

    private fun CvhItemDto.cvhVoiceAliases(): Set<String> {
        return buildSet {
            voiceStudio?.cvhVoiceAliases()?.let(::addAll)
            voiceType?.cvhVoiceAliases()?.let(::addAll)
            if (!voiceStudio.isNullOrBlank() && !voiceType.isNullOrBlank()) {
                addAll("${voiceStudio.orEmpty()} ${voiceType.orEmpty()}".cvhVoiceAliases())
            }
        }
    }

    private fun String.cvhVoiceAliases(): Set<String> {
        val identity = cvhVoiceIdentity()
        if (identity.isBlank()) return emptySet()
        return buildSet {
            add(identity)
            if (identity.endsWith("tv") && identity.length > 4) {
                add(identity.removeSuffix("tv"))
            }
        }
    }

    private fun String.cvhVoiceIdentity(): String {
        return trim()
            .lowercase()
            .replace('ё', 'е')
            .replace("озвучка", "")
            .replace("плеер", "")
            .replace("субтитры", "")
            .replace("subtitle", "")
            .replace("subtitles", "")
            .replace("subs", "")
            .replace("voice", "")
            .replace("dubbing", "")
            .replace("dub", "")
            .replace(Regex("""[\s./|•:_+&\-]+"""), "")
            .trim()
    }

    private fun String.isSubtitleCvhVoice(): Boolean {
        val value = lowercase().replace('ё', 'е')
        return "субтитр" in value || "subtitle" in value
    }

    private fun String.isMeaningfulCvhAliasMatch(other: String): Boolean {
        if (length < 4 || other.length < 4) return false
        return startsWith(other) || other.startsWith(this)
    }

    private fun String.detectVideoHeight(): Int? {
        val heights = buildList {
            this@detectVideoHeight.hlsSourceQualities().mapNotNull { it.height }.forEach(::add)
            dashHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
            }
            qualityHeightRegex.findAll(this@detectVideoHeight).forEach { match ->
                match.groupValues.getOrNull(1)?.toIntOrNull()?.let(::add)
            }
        }
        return heights.filter { it in 100..4320 }.maxOrNull()
    }

    private fun maxOfOrNull(vararg values: Int?): Int? {
        return values.filterNotNull().maxOrNull()
    }

    companion object {
        const val WEBVIEW_RESOLVE_TIMEOUT_MS = 20_000L
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

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val streamUrlRegex = Regex(
            """(?:(?:https?:)?//|/)[^"'\s<>\\]+?(?:\.m3u8|\.mp4|\.mpd)(?:\?[^"'\s<>\\]*)?""",
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
    }
}

private data class SubtitleDetection(
    val tracks: List<ResolvedSubtitleTrack>,
    val hasEmbeddedSubtitles: Boolean,
)

private data class HlsSubtitleSegment(
    val url: String,
    val offsetMs: Long,
    val durationMs: Long,
)

private data class MaterializedSubtitleSegment(
    val body: String,
    val offsetMs: Long,
    val durationMs: Long,
)

private fun String.isHlsPlaylistUrl(): Boolean {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return lower.endsWith(".m3u8") || "mpegurl" in lower
}

private fun String.hlsSubtitleSegments(baseUrl: String): List<HlsSubtitleSegment> {
    val segments = mutableListOf<HlsSubtitleSegment>()
    var offsetMs = 0L
    var pendingDurationMs = 0L

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingDurationMs = line.substringAfter(':')
                    .substringBefore(',')
                    .toDoubleOrNull()
                    ?.let { (it * 1000.0).toLong() }
                    ?: 0L
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                segments += HlsSubtitleSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    offsetMs = offsetMs,
                    durationMs = pendingDurationMs,
                )
                offsetMs += pendingDurationMs
                pendingDurationMs = 0L
            }
        }
    }

    return segments
}

private fun String.webVttCueBody(): String {
    val lines = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
    if (lines.isEmpty()) return ""

    var index = 0
    if (lines[index].trim().startsWith("WEBVTT", ignoreCase = true)) {
        index++
        while (index < lines.size && lines[index].isNotBlank()) {
            index++
        }
        while (index < lines.size && lines[index].isBlank()) {
            index++
        }
    }

    return lines
        .drop(index)
        .filterNot { line -> line.trim().startsWith("X-TIMESTAMP-MAP", ignoreCase = true) }
        .joinToString("\n")
        .trim()
}

private fun List<MaterializedSubtitleSegment>.shouldShiftWebVttCueTimes(): Boolean {
    val samples = filter { it.offsetMs > 0L }
        .mapNotNull { segment ->
            val firstCueStartMs = segment.body.firstWebVttCueStartMs() ?: return@mapNotNull null
            segment to firstCueStartMs
        }
    if (samples.isEmpty()) return false

    val localCueCount = samples.count { (segment, firstCueStartMs) ->
        val localWindowMs = maxOf(segment.durationMs + 5_000L, 60_000L)
        firstCueStartMs < localWindowMs && firstCueStartMs + 10_000L < segment.offsetMs
    }
    val absoluteCueCount = samples.count { (segment, firstCueStartMs) ->
        firstCueStartMs + 10_000L >= segment.offsetMs ||
            kotlin.math.abs(firstCueStartMs - segment.offsetMs) <= segment.durationMs + 10_000L
    }

    return localCueCount > absoluteCueCount
}

private fun String.firstWebVttCueStartMs(): Long? {
    return lineSequence()
        .mapNotNull { line ->
            VideoStreamResolver.webVttTimingRegex
                .find(line.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.webVttTimestampMs()
        }
        .firstOrNull()
}

private fun String.shiftWebVttCueTimes(offsetMs: Long): String {
    if (offsetMs <= 0L) return this
    return lineSequence().joinToString("\n") { line ->
        val match = VideoStreamResolver.webVttTimingRegex.find(line.trim()) ?: return@joinToString line
        val startMs = match.groupValues.getOrNull(1)?.webVttTimestampMs() ?: return@joinToString line
        val endMs = match.groupValues.getOrNull(2)?.webVttTimestampMs() ?: return@joinToString line
        val settings = match.groupValues.getOrNull(3).orEmpty()
        "${(startMs + offsetMs).toWebVttTimestamp()} --> ${(endMs + offsetMs).toWebVttTimestamp()}$settings"
    }
}

private fun String.webVttTimestampMs(): Long? {
    val pieces = split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size != 2) return null

    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val milliseconds = secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null

    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
}

private fun Long.toWebVttTimestamp(): String {
    val safeMs = coerceAtLeast(0L)
    val hours = safeMs / 3_600_000L
    val minutes = (safeMs % 3_600_000L) / 60_000L
    val seconds = (safeMs % 60_000L) / 1_000L
    val milliseconds = safeMs % 1_000L
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
}

private fun String.sha256Hex(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun String.detectSourceQualities(): List<SourceQuality> {
    val qualities = mutableListOf<SourceQuality>()
    qualities += hlsSourceQualities()
    VideoStreamResolver.dashHeightRegex.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    VideoStreamResolver.qualityHeightRegex.findAll(this).forEach { match ->
        match.groupValues.getOrNull(1)?.toIntOrNull()?.let { height ->
            qualities += SourceQuality(height = height)
        }
    }
    return qualities.normalizedSourceQualities()
}

private data class KodikParams(
    val type: String,
    val id: String,
    val hash: String,
    val domain: String,
    val domainSign: String,
    val playerDomain: String,
    val playerDomainSign: String,
    val referer: String,
    val refererSign: String,
)

@Serializable
private data class KodikFtorDto(
    val link: String = "",
    val links: Map<String, List<KodikLinkDto>> = emptyMap(),
) {
    fun availableQualities(): List<SourceQuality> {
        val qualities = links.keys.mapNotNull { key ->
            key.toIntOrNull()?.takeIf { it in 100..4320 }?.let { SourceQuality(height = it) }
        }
        return (qualities + link.detectSourceQualities()).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): KodikStream? {
        linkStreams()
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
            ?.let { return it }

        return directLinkStream()
    }

    private fun linkStreams(): List<KodikStream> {
        return links.entries.flatMap { (quality, links) ->
            val height = quality.toIntOrNull()
            links.mapNotNull { link ->
                link.src
                    .takeIf { it.isNotBlank() }
                    ?.let { src ->
                        KodikStream(
                            url = src.decodeKodikUrl().normalizeKodikUrl(),
                            mimeType = link.type.takeIf { it.isNotBlank() },
                            height = height,
                        )
                    }
            }
        }
    }

    private fun directLinkStream(): KodikStream? {
        return link.takeIf { it.isNotBlank() }?.let {
            KodikStream(
                url = it.normalizeKodikUrl(),
                mimeType = it.mimeTypeFromKodikUrl(),
                height = it.detectKodikHeight(),
            )
        }
    }
}

@Serializable
private data class KodikLinkDto(
    val src: String = "",
    val type: String = "",
)

private data class KodikStream(
    val url: String,
    val mimeType: String?,
    val height: Int?,
)

@Serializable
private data class AksorVideoDto(
    val qualities: AksorQualitiesDto = AksorQualitiesDto(),
) {
    fun bestStream(preferredQuality: PreferredQuality): AksorStream? = qualities.bestStream(preferredQuality)
}

@Serializable
private data class AksorQualitiesDto(
    val q4k: String? = null,
    val q2k: String? = null,
    val q1080: String? = null,
    val q720: String? = null,
    val q480: String? = null,
    val q360: String? = null,
) {
    fun availableQualities(): List<SourceQuality> {
        return streams().availableSourceQualities(
            url = { it.url },
            height = { it.height },
        )
    }

    fun bestStream(preferredQuality: PreferredQuality): AksorStream? {
        return streams()
            .filter { it.url.isNotBlank() }
            .selectForPreferredQuality(
                preferredQuality = preferredQuality,
                height = { it.height },
            )
    }

    private fun streams(): List<AksorStream> {
        return listOf(
            AksorStream(q4k.orEmpty(), 2160),
            AksorStream(q2k.orEmpty(), 1440),
            AksorStream(q1080.orEmpty(), 1080),
            AksorStream(q720.orEmpty(), 720),
            AksorStream(q480.orEmpty(), 480),
            AksorStream(q360.orEmpty(), 360),
        )
    }
}

private data class AksorStream(
    val url: String,
    val height: Int,
)

private fun String.decodeKodikUrl(): String {
    val rotated = map { char ->
        when (char) {
            in 'A'..'Z' -> {
                val shifted = char.code + 18
                if (shifted <= 'Z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            in 'a'..'z' -> {
                val shifted = char.code + 18
                if (shifted <= 'z'.code) shifted.toChar() else (shifted - 26).toChar()
            }
            else -> char
        }
    }.joinToString("")
    val padded = rotated.padEnd(rotated.length + ((4 - rotated.length % 4) % 4), '=')
    return runCatching {
        String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
    }.getOrDefault(this)
}

private fun String.normalizeKodikUrl(): String {
    return when {
        startsWith("//") -> "https:$this"
        startsWith("/") -> "https://kodikplayer.com$this"
        else -> this
    }
}

private fun String.mimeTypeFromKodikUrl(): String? {
    val lower = lowercase()
    return when {
        ".m3u8" in lower -> "application/x-mpegURL"
        ".mpd" in lower -> "application/dash+xml"
        ".mp4" in lower -> "video/mp4"
        else -> null
    }
}

private fun String.detectKodikHeight(): Int? {
    return Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

@Serializable
private data class CvhPlaylistDto(
    val items: List<CvhItemDto> = emptyList(),
)

@Serializable
private data class CvhItemDto(
    @SerialName("vkId") val vkId: String = "",
    @SerialName("voiceStudio") val voiceStudio: String? = null,
    @SerialName("voiceType") val voiceType: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

@Serializable
private data class CvhVideoDto(
    val sources: CvhSourcesDto? = null,
)

@Serializable
private data class CvhSourcesDto(
    @SerialName("hlsUrl") val hlsUrl: String = "",
    @SerialName("dashUrl") val dashUrl: String = "",
    @SerialName("mpeg4kUrl") val mpeg4kUrl: String = "",
    @SerialName("mpeg2kUrl") val mpeg2kUrl: String = "",
    @SerialName("mpegQhdUrl") val mpegQhdUrl: String = "",
    @SerialName("mpegFullHdUrl") val mpegFullHdUrl: String = "",
    @SerialName("mpegHighUrl") val mpegHighUrl: String = "",
    @SerialName("mpegMediumUrl") val mpegMediumUrl: String = "",
    @SerialName("mpegLowUrl") val mpegLowUrl: String = "",
    @SerialName("mpegLowestUrl") val mpegLowestUrl: String = "",
    @SerialName("mpegTinyUrl") val mpegTinyUrl: String = "",
) {
    fun availableQualities(): List<SourceQuality> {
        return (
            mpegStreams().availableSourceQualities(
                url = { it.url },
                height = { it.height },
            ) +
                hlsUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty() +
                dashUrl.takeIf { it.isNotBlank() }?.detectSourceQualities().orEmpty()
            ).normalizedSourceQualities()
    }

    fun bestStream(preferredQuality: PreferredQuality): CvhStream? {
        val mpegStreams = mpegStreams()
        val highestKnownHeight = mpegStreams
            .asSequence()
            .filter { it.url.isNotBlank() }
            .mapNotNull { it.height }
            .maxOrNull()

        return (
            listOf(
                CvhStream(hlsUrl, "application/x-mpegURL", highestKnownHeight),
                CvhStream(dashUrl, "application/dash+xml", highestKnownHeight),
            ) + mpegStreams
            )
                .filter { it.url.isNotBlank() }
                .selectForPreferredQuality(
                    preferredQuality = preferredQuality,
                    height = { it.height },
                    priority = { if (it.isAdaptiveStream()) 1 else 0 },
                )
    }

    private fun mpegStreams(): List<CvhStream> {
        return listOf(
            CvhStream(mpeg4kUrl, "video/mp4", 2160),
            CvhStream(mpeg2kUrl, "video/mp4", 1440),
            CvhStream(mpegQhdUrl, "video/mp4", 1440),
            CvhStream(mpegFullHdUrl, "video/mp4", 1080),
            CvhStream(mpegHighUrl, "video/mp4", 720),
            CvhStream(mpegMediumUrl, "video/mp4", 480),
            CvhStream(mpegLowUrl, "video/mp4", 360),
            CvhStream(mpegLowestUrl, "video/mp4", 240),
            CvhStream(mpegTinyUrl, "video/mp4", 144),
        )
    }
}

private data class CvhStream(
    val url: String,
    val mimeType: String,
    val height: Int?,
)

private fun <T> Iterable<T>.availableSourceQualities(
    url: (T) -> String,
    height: (T) -> Int?,
): List<SourceQuality> {
    return mapNotNull { stream ->
        height(stream)
            ?.takeIf { url(stream).isNotBlank() }
            ?.let { SourceQuality(height = it) }
    }.normalizedSourceQualities()
}

private fun CvhStream.isAdaptiveStream(): Boolean {
    return mimeType.contains("mpegURL") || mimeType.contains("dash")
}
