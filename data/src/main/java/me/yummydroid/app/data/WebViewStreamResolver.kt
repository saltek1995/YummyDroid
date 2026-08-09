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

internal class WebViewStreamResolver(
    context: Context?,
    private val providerStreamResolver: ProviderStreamResolver,
    private val subtitleMetadataParser: SubtitleMetadataParser,
    private val subtitleTrackMaterializer: SubtitleTrackMaterializer,
    private val playbackRequestHeaders: PlaybackRequestHeaders,
    private val playerMetadataInspector: PlayerMetadataInspector,
    private val fallbackSiteBaseUrl: () -> String,
) : RuntimeStreamResolver {
    private val appContext = context?.applicationContext

    override suspend fun resolve(
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
    private val requiresRuntimePlayerDiscovery = sourceUrl.requiresRuntimePlayerDiscovery()
    private val supportsDocumentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val termination = WebViewSessionTermination()
    private var capturedPlayback: CapturedPlayback? = null
    private var capturedHasEmbeddedSubtitles = false
    private var discoveryVersion = 0
    private var playerStateScriptHandler: ScriptHandler? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        continuation.invokeOnCancellation {
            if (termination.tryTerminate()) {
                handler.post(::cleanupAfterTermination)
            }
        }
        if (!continuation.isActive || termination.isTerminated) return

        if (requiresRuntimePlayerDiscovery && !supportsDocumentStartScript) {
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
        if (!requiresRuntimePlayerDiscovery || !supportsDocumentStartScript) return
        playerStateScriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView,
            STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT,
            setOf(runtimeDocumentStartOriginRule(sourceUrl)),
        )
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
        if (method.equals("GET", ignoreCase = true)) {
            val requestHeaders = request?.requestHeaders.orEmpty()
            val playbackHeaders = playbackRequestHeaders.forwardedPlayback(
                sourceHeaders = requestHeaders,
                streamUrl = url,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
            )
            capturedRequestHeaders[url] = playbackHeaders

            subtitleMetadataParser.potentialTrack(url)
                ?.copy(headers = playbackHeaders)
                ?.let { track -> handler.post { captureSubtitleTracks(listOf(track)) } }

            if (playerMetadataInspector.isInspectableUrl(url)) {
                runCatching { inspectPlayerMetadataResponse(url, playbackHeaders) }
                    .onSuccess { capture ->
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
            capturedRequestHeaders[url] = playbackRequestHeaders.forwardedPlayback(
                sourceHeaders = request?.requestHeaders.orEmpty(),
                streamUrl = url,
                sourceUrl = sourceUrl,
                siteBaseUrl = siteBaseUrl,
            )
        }
        return null
    }

    private fun inspectPlayerMetadataResponse(
        url: String,
        requestHeaders: Map<String, String>,
    ): PlayerMetadataCapture {
        val body = providerStreamResolver.getText(url, requestHeaders)
        return playerMetadataInspector.inspect(
            url = url,
            body = body,
            requestHeaders = requestHeaders,
            sourceUrl = sourceUrl,
            siteBaseUrl = siteBaseUrl,
            preferredQuality = preferredQuality,
        )
    }

    private fun capturePlayback(playback: CapturedPlayback) {
        if (termination.isTerminated) return
        if (capturedPlayback?.url != playback.url) {
            capturedPlayback = playback
        }
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun captureSubtitleTracks(tracks: List<ResolvedSubtitleTrack>) {
        if (termination.isTerminated || tracks.isEmpty()) return
        tracks.forEach(capturedSubtitleTracks::add)
        scheduleFinishAfterDiscoveryIdle()
    }

    private fun captureEmbeddedSubtitleTracks(
        tracks: List<ResolvedEmbeddedSubtitleTrack>,
        hasEmbeddedSubtitles: Boolean,
    ) {
        if (termination.isTerminated) return
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
                requiresRuntimePlayerDiscovery = requiresRuntimePlayerDiscovery,
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
        cleanupAfterTermination()
        if (!continuation.isActive) return
        result
            .onSuccess { continuation.resume(it) }
            .onFailure { continuation.resumeWithException(it) }
    }

    private fun cleanupAfterTermination() {
        handler.removeCallbacksAndMessages(null)
        cleanup()
    }

    private fun cleanup() {
        runCatching { playerStateScriptHandler?.remove() }
        playerStateScriptHandler = null
        runCatching { webView.stopLoading() }
        runCatching { webView.removeJavascriptInterface(STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME) }
        runCatching { webView.loadUrl("about:blank") }
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

    private fun String.normalizeAgainst(baseUrl: String): String {
        return normalizeVideoUrlAgainstBase(baseUrl, fallbackSiteBaseUrl())
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
    requiresRuntimePlayerDiscovery: Boolean,
): Long {
    return when {
        !waitForRuntimeSubtitles -> STREAM_WEBVIEW_PLAYBACK_DISCOVERY_IDLE_MS
        !hasCapturedSubtitles && requiresRuntimePlayerDiscovery -> STREAM_WEBVIEW_SUBTITLE_DISCOVERY_GRACE_MS
        else -> STREAM_WEBVIEW_DISCOVERY_IDLE_MS
    }
}

internal fun runtimeDocumentStartOriginRule(sourceUrl: String): String {
    return sourceUrl.urlOrigin()
        ?: throw IOException("Runtime player URL has no valid origin: $sourceUrl")
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
                var bridge = window['$STREAM_WEBVIEW_DISCOVERY_BRIDGE_NAME'];
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
