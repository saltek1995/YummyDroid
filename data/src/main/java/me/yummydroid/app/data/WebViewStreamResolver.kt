package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// WebViewStreamResolver
internal class WebViewStreamResolver(
    context: Context?,
    private val providerStreamResolver: ProviderStreamResolver,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val playerMetadataInspector: PlayerMetadataInspector,
    private val fallbackSiteBaseUrl: () -> String,
) {
    private val appContext = context?.applicationContext

    suspend fun resolve(
        sourceUrl: String,
        siteBaseUrl: String,
        preferredQuality: PreferredQuality,
        waitForRuntimeSubtitles: Boolean,
    ): ResolvedVideoStream = withContext(Dispatchers.Main) {
        val context = appContext ?: throw IOException("Context is required for JS stream capture")
        suspendCancellableCoroutine { continuation ->
            WebViewCaptureSession(
                context = context,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
                preferredQuality = preferredQuality,
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                providerStreamResolver = providerStreamResolver,
                subtitleMetadataParser = subtitleMetadataParser,
                subtitleTrackMaterializer = subtitleTrackMaterializer,
                playbackRequestHeaders = playbackRequestHeaders,
                playerMetadataInspector = playerMetadataInspector,
                fallbackSiteBaseUrl = fallbackSiteBaseUrl,
                continuation = continuation,
            ).start()
        }
    }
}

private class WebViewCaptureSession(
    context: Context,
    private val sourceUrl: String,
    private val siteBaseUrl: String,
    private val preferredQuality: PreferredQuality,
    private val waitForRuntimeSubtitles: Boolean,
    private val providerStreamResolver: ProviderStreamResolver,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val playerMetadataInspector: PlayerMetadataInspector,
    private val fallbackSiteBaseUrl: () -> String,
    private val continuation: CancellableContinuation<ResolvedVideoStream>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val webView = WebView(context)
    private val capturedRequestHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val capturedSubtitleTracks = linkedSetOf<ResolvedSubtitleTrack>()
    private val capturedEmbeddedSubtitleTracks = linkedSetOf<ResolvedEmbeddedSubtitleTrack>()
    private val isAllohaIframe = sourceUrl.isAllohaIframeUrl()
    private val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val termination = WebViewSessionTermination()
    private var capturedPlayback: CapturedPlayback? = null
    private var capturedHasEmbeddedSubtitles = false
    private var discoveryVersion = 0
    private var playerStateScriptHandler: ScriptHandler? = null
    private var preferredQualityScriptHandler: ScriptHandler? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        continuation.invokeOnCancellation {
            if (termination.tryTerminate()) {
                handler.post(::cleanupAfterTermination)
            }
        }
        if (!continuation.isActive || termination.isTerminated) return

        if (isAllohaIframe && !supportsDocumentStartScript) {
            finish(
                Result.failure(
                    IOException(
                        "WebView document-start script is not supported; " +
                            "runtime player discovery cannot run. Iframe: $sourceUrl",
                    ),
                ),
            )
            return
        }

        runCatching {
            installJavascriptBridge()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = BROWSER_USER_AGENT
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }

            installDocumentStartScript()
            installRequestInterceptor()
            handler.postDelayed(::finishWithCapturedPlaybackOrFailure, STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS)
            loadPlayerFrame()
        }.onFailure { failure ->
            finish(Result.failure(failure))
        }
    }

    private fun installJavascriptBridge() {
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun captureResponse(rawUrl: String?, contentType: String?, rawBody: String?) {
                    captureJavascriptResponse(rawUrl, contentType, rawBody)
                }
            },
            STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME,
        )
    }

    private fun captureJavascriptResponse(
        rawUrl: String?,
        contentType: String?,
        rawBody: String?,
    ) {
        if (termination.isTerminated) return
        val url = rawUrl
            ?.takeIf { it.isNotBlank() }
            ?.normalizeAgainst(sourceUrl)
            ?: return
        val body = rawBody?.takeIf { it.isNotBlank() } ?: return
        if (
            !playerMetadataInspector.isInspectableUrl(url) &&
            !subtitleMetadataParser.isResolvableCandidate(url) &&
            !body.looksLikePlayerMetadataBody()
        ) {
            return
        }

        val playbackHeaders = playbackRequestHeaders.forwardedPlayback(
            sourceHeaders = capturedRequestHeaders[url].orEmpty(),
            streamUrl = url,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
        )
        val metadataCapture = runCatching {
            playerMetadataInspector.inspect(
                url = url,
                body = body,
                requestHeaders = playbackHeaders,
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

    private fun installDocumentStartScript() {
        if (!isAllohaIframe || !supportsDocumentStartScript) return
        playerStateScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT,
            setOf(runtimeDocumentStartOriginRule(sourceUrl)),
        )
        preferredQuality.height?.let { height ->
            preferredQualityScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                webView,
                allohaPreferredQualityScript(height),
                setOf(runtimeDocumentStartOriginRule(sourceUrl)),
            )
        }
    }

    private fun installRequestInterceptor() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
            ): WebResourceResponse? {
                return interceptRequest(request)
            }
        }
    }

    private fun interceptRequest(request: WebResourceRequest?): WebResourceResponse? {
        if (termination.isTerminated) return null
        val url = request?.url?.toString().orEmpty()
        val method = request?.method.orEmpty()
        val requestHeaders = request?.requestHeaders.orEmpty()
        return if (method.equals("GET", ignoreCase = true)) {
            interceptGetRequest(url, requestHeaders)
        } else {
            captureForwardedHeadersIfNeeded(url, requestHeaders)
            null
        }
    }

    private fun interceptGetRequest(
        url: String,
        requestHeaders: Map<String, String>,
    ): WebResourceResponse? {
        val playbackHeaders = forwardedPlaybackHeaders(url, requestHeaders)
        capturedRequestHeaders[url] = playbackHeaders
        captureAllohaPlaybackHeaders(url, playbackHeaders)
        val potentialSubtitle = subtitleMetadataParser.potentialTrack(url)
        if (potentialSubtitle != null && !potentialSubtitle.uri.isHlsPlaylistUrl()) {
            return interceptDirectSubtitle(url, playbackHeaders)
        }
        potentialSubtitle?.let { track -> capturePotentialSubtitleTrack(track, playbackHeaders) }
        return interceptInspectablePlayerMetadata(url, playbackHeaders)
            ?: capturePlaybackRequest(url, playbackHeaders)
    }

    private fun captureAllohaPlaybackHeaders(
        url: String,
        playbackHeaders: Map<String, String>,
    ) {
        if (!isAllohaIframe || !url.isCapturedPlaybackUrl()) return
        handler.post {
            val playback = capturedPlayback ?: return@post
            if (playback.url != url && url !in playback.fallbackUrls) return@post
            capturePlayback(playback.withHeadersFor(url, playbackHeaders))
        }
    }

    private fun forwardedPlaybackHeaders(
        url: String,
        requestHeaders: Map<String, String>,
    ): Map<String, String> {
        return playbackRequestHeaders.forwardedPlayback(
            sourceHeaders = requestHeaders,
            streamUrl = url,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
        )
    }

    private fun capturePotentialSubtitleTrack(
        track: ResolvedSubtitleTrack,
        playbackHeaders: Map<String, String>,
    ) {
        val trackWithHeaders = track.copy(headers = playbackHeaders)
        runCatching { subtitleTrackMaterializer.validateTracks(listOf(trackWithHeaders), playbackHeaders) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { tracks -> handler.post { captureSubtitleTracks(tracks) } }
    }

    private fun interceptDirectSubtitle(
        url: String,
        playbackHeaders: Map<String, String>,
    ): WebResourceResponse? {
        val response = runCatching {
            providerStreamResolver.getResponseBlocking(url, playbackHeaders)
        }.getOrNull() ?: return null
        if (response.isSuccessful && response.body.isNotEmpty()) {
            runCatching {
                subtitleTrackMaterializer.materializeCapturedBody(
                    url = url,
                    contentType = response.mimeType,
                    body = response.bodyString(),
                )
            }.getOrNull()?.let { track -> handler.post { captureSubtitleTracks(listOf(track)) } }
        }
        return response.toWebResourceResponse()
    }

    private fun interceptInspectablePlayerMetadata(
        url: String,
        playbackHeaders: Map<String, String>,
    ): WebResourceResponse? {
        if (!playerMetadataInspector.isInspectableUrl(url)) return null
        val response = runCatching {
            providerStreamResolver.getResponseBlocking(url, playbackHeaders)
        }.getOrNull() ?: return null
        if (response.isSuccessful && response.body.isNotEmpty()) {
            runCatching {
                inspectPlayerMetadataResponse(url, playbackHeaders, response.bodyString())
            }.onSuccess { capture -> handler.post { capturePlayerMetadata(capture) } }
        }
        return response.toWebResourceResponse()
    }

    private fun capturePlaybackRequest(
        url: String,
        playbackHeaders: Map<String, String>,
    ): WebResourceResponse? {
        if (isAllohaIframe) return null
        if (!url.isCapturedPlaybackUrl()) return null
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
        return if (url.isProgressivePlaybackUrl()) emptyProgressivePlaybackResponse(url) else null
    }

    private fun captureForwardedHeadersIfNeeded(
        url: String,
        requestHeaders: Map<String, String>,
    ) {
        if (url.isBlank()) return
        capturedRequestHeaders[url] = forwardedPlaybackHeaders(url, requestHeaders)
    }

    private fun capturePlayerMetadata(capture: PlayerMetadataCapture) {
        capture.playback?.let(::capturePlayback)
        captureSubtitleTracks(capture.subtitles)
        captureEmbeddedSubtitleTracks(
            tracks = capture.embeddedSubtitles,
            hasEmbeddedSubtitles = capture.hasEmbeddedSubtitles,
        )
    }

    private fun emptyProgressivePlaybackResponse(url: String): WebResourceResponse {
        return WebResourceResponse(
            url.mimeTypeFromUrl() ?: "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun inspectPlayerMetadataResponse(
        url: String,
        requestHeaders: Map<String, String>,
        body: String,
    ): PlayerMetadataCapture {
        return playerMetadataInspector.inspect(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            preferredQuality = preferredQuality,
        )
    }

    private fun HttpResponseSnapshot.toWebResourceResponse(): WebResourceResponse {
        return WebResourceResponse(
            mimeType ?: urlMimeTypeFallback(),
            encoding,
            code,
            message,
            headers,
            ByteArrayInputStream(body),
        )
    }

    private fun urlMimeTypeFallback(): String = "application/json"

    private fun capturePlayback(playback: CapturedPlayback) {
        if (termination.isTerminated) return
        val enrichedPlayback = if (isAllohaIframe) playback.withCapturedAllohaPlaybackHeaders() else playback
        val mergedPlayback = capturedPlayback?.mergeWith(enrichedPlayback) ?: enrichedPlayback
        if (capturedPlayback == mergedPlayback) return
        capturedPlayback = mergedPlayback
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun CapturedPlayback.withCapturedAllohaPlaybackHeaders(): CapturedPlayback {
        return (listOf(url) + fallbackUrls)
            .firstNotNullOfOrNull { playbackUrl ->
                capturedRequestHeaders[playbackUrl]
                    ?.let { headers -> withHeadersFor(playbackUrl, headers) }
            }
            ?: this
    }

    private fun captureSubtitleTracks(tracks: List<ResolvedSubtitleTrack>) {
        if (termination.isTerminated || tracks.isEmpty()) return
        val changed = tracks.fold(false) { hasChanged, track ->
            capturedSubtitleTracks.add(track) || hasChanged
        }
        if (!changed) return
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun captureEmbeddedSubtitleTracks(
        tracks: List<ResolvedEmbeddedSubtitleTrack>,
        hasEmbeddedSubtitles: Boolean,
    ) {
        if (termination.isTerminated) return
        val hadEmbeddedSubtitles = capturedHasEmbeddedSubtitles
        if (hasEmbeddedSubtitles) {
            capturedHasEmbeddedSubtitles = true
        }
        var changed = capturedHasEmbeddedSubtitles != hadEmbeddedSubtitles
        if (tracks.isNotEmpty()) {
            tracks.forEach { track ->
                if (capturedEmbeddedSubtitleTracks.add(track)) {
                    changed = true
                }
            }
        }
        if (changed) {
            scheduleFinishAfterDiscoveryIdle()
        }
    }

    private fun scheduleFinishAfterDiscoveryIdle() {
        if (termination.isTerminated || capturedPlayback == null) return
        discoveryVersion += 1
        val scheduledVersion = discoveryVersion
        handler.postDelayed(
            {
                if (!termination.isTerminated && scheduledVersion == discoveryVersion) {
                    finishWithCapturedPlaybackOrFailure()
                }
            },
            webViewDiscoveryIdleMs(
                waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                hasCapturedSubtitles = capturedSubtitleTracks.isNotEmpty(),
                isAllohaIframe = isAllohaIframe,
            ),
        )
    }

    private fun finishWithCapturedPlaybackOrFailure() {
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
            val timeoutSeconds = STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS / 1_000L
            finish(
                Result.failure(
                    IOException(
                        "Could not capture an HLS/MP4/DASH player stream in $timeoutSeconds seconds. " +
                            "Iframe: $sourceUrl",
                    ),
                ),
            )
        }
    }

    private fun finish(result: Result<ResolvedVideoStream>) {
        if (!termination.tryTerminate()) return
        handler.removeCallbacksAndMessages(null)
        if (continuation.isActive) {
            result
                .onSuccess { continuation.resume(it) }
                .onFailure { continuation.resumeWithException(it) }
        }
        handler.post(::cleanup)
    }

    private fun cleanupAfterTermination() {
        handler.removeCallbacksAndMessages(null)
        cleanup()
    }

    private fun cleanup() {
        runCatching { playerStateScriptHandler?.remove() }
        playerStateScriptHandler = null
        runCatching { preferredQualityScriptHandler?.remove() }
        preferredQualityScriptHandler = null
        runCatching { webView.stopLoading() }
        runCatching { webView.removeJavascriptInterface(STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME) }
        runCatching { webView.removeAllViews() }
        runCatching { webView.destroy() }
    }

    private fun loadPlayerFrame() {
        val html = """
            <!doctype html>
            <html>
                <head><meta name="referrer" content="no-referrer-when-downgrade"></head>
                <body style="margin:0;background:#000">
                    <iframe
                        src="$sourceUrl"
                        width="1280"
                        height="720"
                        allow="accelerometer *; autoplay *; clipboard-write *; encrypted-media *; gyroscope *; picture-in-picture *; fullscreen *"
                        allowfullscreen>
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

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
    }
}

internal fun CapturedPlayback.mergeWith(newer: CapturedPlayback): CapturedPlayback {
    if (url != newer.url) {
        return when {
            skipPlaybackProbe && !newer.skipPlaybackProbe -> this
            else -> newer
        }
    }
    return newer.copy(
        mimeType = newer.mimeType ?: mimeType,
        headers = newer.headers.ifEmpty { headers },
        maxVideoHeight = maxOfOrNull(maxVideoHeight, newer.maxVideoHeight),
        availableQualities = (availableQualities + newer.availableQualities).normalizedSourceQualities(),
        selectedVideoHeight = newer.selectedVideoHeight ?: selectedVideoHeight,
        fallbackUrls = (newer.fallbackUrls + fallbackUrls).distinct(),
        fallbackUrlHeights = fallbackUrlHeights + newer.fallbackUrlHeights,
        skipPlaybackProbe = skipPlaybackProbe || newer.skipPlaybackProbe,
    )
}

internal fun CapturedPlayback.withHeadersFor(
    playbackUrl: String,
    playbackHeaders: Map<String, String>,
): CapturedPlayback {
    return when {
        url == playbackUrl -> copy(headers = playbackHeaders)
        playbackUrl in fallbackUrls -> copy(
            url = playbackUrl,
            headers = playbackHeaders,
            selectedVideoHeight = fallbackUrlHeights[playbackUrl]
                ?: playbackUrl.detectVideoHeight()
                ?: selectedVideoHeight,
            fallbackUrls = listOf(url) + fallbackUrls.filterNot { it == playbackUrl },
            fallbackUrlHeights = (fallbackUrlHeights - playbackUrl) + listOfNotNull(
                selectedVideoHeight?.let { height -> url to height },
            ),
        )
        else -> this
    }
}

internal class WebViewSessionTermination {
    private val terminated = AtomicBoolean(false)

    val isTerminated: Boolean
        get() = terminated.get()

    fun tryTerminate(): Boolean = terminated.compareAndSet(false, true)
}

internal fun webViewDiscoveryIdleMs(
    waitForRuntimeSubtitles: Boolean,
    hasCapturedSubtitles: Boolean,
    isAllohaIframe: Boolean,
): Long {
    return when {
        !waitForRuntimeSubtitles -> STREAM_WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS
        !hasCapturedSubtitles && isAllohaIframe -> STREAM_WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS
        else -> STREAM_WEBVIEW_DISCOVERY_IDLE_MS
    }
}

internal fun runtimeDocumentStartOriginRule(sourceUrl: String): String {
    return sourceUrl.urlOrigin()
        ?: throw IOException("Runtime player URL has no valid origin: $sourceUrl")
}

internal fun allohaPreferredQualityScript(height: Int): String {
    return """
        (function() {
            if (window.__yummyPreferredQualityHeight === $height) return;
            window.__yummyPreferredQualityHeight = $height;
            var attemptsLeft = 80;

            function matchesTarget(value) {
                return String(value || '').replace(/[^\d]/g, '') === '$height';
            }

            function applyPreferredQuality() {
                try {
                    var player = window.player || window.allplay || window.videoPlayer;
                    if (!player) return false;
                    if (matchesTarget(player.quality)) return true;
                    if (typeof player.setQuality === 'function') {
                        player.setQuality($height);
                    } else {
                        player.quality = $height;
                    }
                    return true;
                } catch (error) {
                    return false;
                }
            }

            var timer = window.setInterval(function() {
                if (applyPreferredQuality()) {
                    window.clearInterval(timer);
                    return;
                }
                attemptsLeft -= 1;
                if (attemptsLeft <= 0) window.clearInterval(timer);
            }, 250);
        })();
    """.trimIndent()
}

internal const val STREAM_WEBVIEW_RESOLVE_TIMEOUT_MS = 30_000L
private const val STREAM_WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS = 250L
private const val STREAM_WEBVIEW_DISCOVERY_IDLE_MS = 1_200L
private const val STREAM_WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS = 4_000L
private const val STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME = "YummyResolverBridge"

internal val STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT = """
    (function() {
        if (window.__yummyResolverBridgeInstalled) return;
        window.__yummyResolverBridgeInstalled = true;
        var attemptsLeft = 80;
        var lastCapturedBody = '';

        function pushCandidate(target, value) {
            if (!value) return;
            if (Array.isArray(value)) {
                value.forEach(function(item) { pushCandidate(target, item); });
                return;
            }
            target.push(value);
        }

        function isEmptyObject(value) {
            return value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0;
        }

        function safeClone(value, depth, seen) {
            if (value == null || depth > 5) return null;
            var valueType = typeof value;
            if (valueType === 'string' || valueType === 'number' || valueType === 'boolean') return value;
            if (valueType !== 'object') return null;
            if (value === window || value === document || value.nodeType) return null;
            if (seen.indexOf(value) >= 0) return null;
            seen.push(value);
            try {
                if (Array.isArray(value)) {
                    return value.map(function(item) {
                        return safeClone(item, depth + 1, seen);
                    }).filter(function(item) {
                        return item != null && !isEmptyObject(item);
                    });
                }
                var clone = {};
                Object.keys(value).forEach(function(key) {
                    try {
                        var cloned = safeClone(value[key], depth + 1, seen);
                        if (cloned != null && !isEmptyObject(cloned)) clone[key] = cloned;
                    } catch (error) {}
                });
                return clone;
            } finally {
                seen.pop();
            }
        }

        function collectDomTracks() {
            var tracks = [];
            try {
                Array.prototype.forEach.call(document.querySelectorAll('track'), function(track) {
                    tracks.push({
                        src: track.src || track.getAttribute('src') || '',
                        label: track.label || track.getAttribute('label') || '',
                        language: track.srclang || track.getAttribute('srclang') || '',
                        kind: track.kind || track.getAttribute('kind') || 'subtitles'
                    });
                });
            } catch (error) {}
            return tracks;
        }

        function collectTextTrackList(value) {
            var tracks = [];
            if (!value || typeof value.length !== 'number') return tracks;
            try {
                for (var index = 0; index < value.length; index += 1) {
                    var track = value[index];
                    tracks.push({
                        id: track.id || '',
                        label: track.label || '',
                        language: track.language || track.srclang || '',
                        kind: track.kind || 'subtitles',
                        mode: track.mode || ''
                    });
                }
            } catch (error) {}
            return tracks;
        }

        function payloadHasPattern(payload, pattern) {
            try {
                return pattern.test(JSON.stringify(payload));
            } catch (error) {
                return false;
            }
        }

        function callPlayerGetter(player, name) {
            try {
                if (!player || typeof player[name] !== 'function') return null;
                return player[name]();
            } catch (error) {
                return null;
            }
        }

        var timer = window.setInterval(function() {
            try {
                var player = window.player || window.allplay || window.videoPlayer;
                var candidates = [];
                pushCandidate(candidates, player && player.currentSource);
                pushCandidate(candidates, player && player.source);
                pushCandidate(candidates, player && player.sources);
                pushCandidate(candidates, player && player.hlsSource);
                pushCandidate(candidates, player && player.config && player.config.source);
                pushCandidate(candidates, player && player.config && player.config.sources);
                pushCandidate(candidates, player && player.config && player.config.hlsSource);
                pushCandidate(candidates, player && player.options && player.options.source);
                pushCandidate(candidates, player && player.options && player.options.sources);
                pushCandidate(candidates, player && player.options && player.options.hlsSource);
                pushCandidate(candidates, callPlayerGetter(player, 'getSources'));
                pushCandidate(candidates, callPlayerGetter(player, 'getQualityOptions'));

                var textTracks = [];
                pushCandidate(textTracks, player && player.textTracks);
                pushCandidate(textTracks, player && player.captions);
                pushCandidate(textTracks, player && player.config && player.config.textTracks);
                pushCandidate(textTracks, player && player.config && player.config.captions);
                pushCandidate(textTracks, player && player.config && player.config.tracks);
                pushCandidate(textTracks, player && player.options && player.options.textTracks);
                pushCandidate(textTracks, player && player.options && player.options.captions);
                pushCandidate(textTracks, player && player.options && player.options.tracks);
                pushCandidate(textTracks, callPlayerGetter(player, 'getTracks'));
                var video = document.querySelector('video');
                pushCandidate(textTracks, collectTextTrackList(video && video.textTracks));
                pushCandidate(textTracks, collectDomTracks());

                var payload = {
                    hlsSource: safeClone(candidates, 0, []),
                    source: safeClone(player && player.source, 0, []),
                    sources: safeClone(player && player.sources, 0, []),
                    currentSource: safeClone(player && player.currentSource, 0, []),
                    textTracks: safeClone(textTracks, 0, []),
                    captions: safeClone(player && player.captions, 0, [])
                };
                var hasPlayback = payloadHasPattern(payload, /\.(?:m3u8|mp4|mpd)(?:[?#]|${'$'})/i);
                var hasSubtitle = payloadHasPattern(payload, /\.(?:vtt|srt|ass|ssa|ttml|dfxp)(?:[?#]|${'$'})/i) ||
                    (payload.textTracks && payload.textTracks.length > 0);
                if (!hasPlayback && !hasSubtitle) {
                    attemptsLeft -= 1;
                    if (attemptsLeft <= 0) window.clearInterval(timer);
                    return;
                }
                var bridge = window['$STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME'];
                if (bridge && bridge.captureResponse) {
                    var body = JSON.stringify(payload);
                    if (body === lastCapturedBody) {
                        attemptsLeft -= 1;
                        if (attemptsLeft <= 0) window.clearInterval(timer);
                        return;
                    }
                    lastCapturedBody = body;
                    bridge.captureResponse(
                        String(location.href),
                        'application/json',
                        body
                    );
                    if (hasPlayback && hasSubtitle) window.clearInterval(timer);
                }
            } catch (error) {}
            attemptsLeft -= 1;
            if (attemptsLeft <= 0) window.clearInterval(timer);
        }, 250);
    })();
""".trimIndent()
