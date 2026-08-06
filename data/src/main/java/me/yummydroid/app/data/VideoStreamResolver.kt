package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VideoStreamResolver(
    context: Context? = null,
    private val siteDomainResolver: SiteDomainResolver = SiteDomainResolver(),
    private val client: OkHttpClient = defaultVideoResolveClient(),
) {
    private val appContext = context?.applicationContext
    private val subtitleMetadataParser = SubtitleMetadataParser(
        fallbackSiteBaseUrl = siteDomainResolver::cachedOrDefaultBaseUrl,
        json = json,
    )
    private val subtitleTrackMaterializer = SubtitleTrackMaterializer(appContext, client)
    private val streamPostProcessor = ResolvedStreamPostProcessor(
        client = client,
        subtitleMetadataParser = subtitleMetadataParser,
        subtitleTrackMaterializer = subtitleTrackMaterializer,
    )

    suspend fun resolve(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        waitForRuntimeSubtitles: Boolean = true,
    ): ResolvedVideoStream {
        val stream = resolveInternal(video, preferredQuality, waitForRuntimeSubtitles)
        return withContext(Dispatchers.IO) {
            streamPostProcessor.process(stream)
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
                subtitles = listOfNotNull(subtitleMetadataParser.directTrack(sourceUrl)).normalizedSubtitleTracks(),
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
                val subtitles = subtitleMetadataParser.extractHlsTracks(body, sourceUrl)
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
                    subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
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
                        ?.let { runtimeStream ->
                            return streamPostProcessor.mergeStaticMetadata(runtimeStream, staticStream)
                        }
                }
                return staticStream
            }
        }

        return resolveViaWebView(sourceUrl, siteBaseUrl, preferredQuality, waitForRuntimeSubtitles)
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
            subtitles = subtitleMetadataParser.extractTracks(body, sourceUrl),
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
                            !subtitleMetadataParser.isResolvableCandidate(url) &&
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
                            subtitleTrackMaterializer.materializeCapturedBody(
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

                        subtitleMetadataParser.potentialTrack(url)
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
                !subtitleMetadataParser.isResolvableCandidate(url) &&
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
            subtitleMetadataParser.extractHlsTracks(body, url)
        } else {
            SubtitleDetection(tracks = emptyList(), embeddedSubtitles = emptyList(), hasEmbeddedSubtitles = false)
        }
        val dashEmbeddedSubtitles = if (bodyIsDashManifest) {
            subtitleMetadataParser.extractDashEmbeddedTracks(body)
        } else {
            emptyList()
        }
        val subtitleHeaders = requestHeaders.toPlaybackHeaders(url, sourceUrl, siteBaseUrl)
        val subtitles = (subtitleMetadataParser.extractTracks(body, url) + hlsSubtitles.tracks)
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
