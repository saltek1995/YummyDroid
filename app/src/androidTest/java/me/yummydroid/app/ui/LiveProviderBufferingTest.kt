package me.yummydroid.app.ui

import android.os.SystemClock
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AllohaSessionDescriptor
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoStreamResolver
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.defaultVideoPlaybackClient
import me.yummydroid.app.data.defaultVideoResolveClient
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Opt-in live-source smoke test. It resolves exactly one supplied cached video and makes no
 * attempt to replace or re-resolve it. `liveVideo` is base64 JSON with no persisted credentials.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveProviderBufferingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun gatedSingleLiveSourceUsesMaximumBufferWithoutRequestBurst() {
        val arguments = InstrumentationRegistry.getArguments()
        val encoded = arguments.getString("liveVideo").orEmpty()
        assumeTrue("Provide one base64 liveVideo JSON argument to enable this test", encoded.isNotBlank())
        val video = decodeVideo(encoded)
        val rawJournal = if (arguments.getString("liveRawHttpCapture") == "true")
            LiveAllohaHttpJournal(InstrumentationRegistry.getInstrumentation().targetContext) else null
        val observedBrowserLanguage = arguments.getString("liveAcceptLanguage")?.takeIf { it.isNotBlank() }
        val quality = arguments.getString("preferredQuality")?.let(::preferredQuality) ?: PreferredQuality.P1080
        val websiteBuffer = arguments.getString("liveBuffer") == "allohaWebsite"
        val terminal = AtomicBoolean(false)
        val terminalReason = arrayOfNulls<String>(1)
        val requestCount = AtomicInteger()
        val mediaHost = AtomicReference<String?>()
        val requests = Collections.synchronizedList(mutableListOf<RequestObservation>())
        val observationStarted = SystemClock.elapsedRealtime()
        val observedTokens = mutableMapOf<String, Int>()
        val observedResources = mutableMapOf<String, Int>()
        val observedIdentityHosts = Collections.synchronizedSet(mutableSetOf<String>())
        // OkHttp does not run network interceptors for WebSocket upgrades.
        // Observe the handshake separately, without logging SID, cookies or tokens.
        val socketInterceptor = Interceptor { chain ->
            val request = chain.request().let { original ->
                if (observedBrowserLanguage == null) original else original.newBuilder()
                    .header("Accept-Language", observedBrowserLanguage).build()
            }
            if (!request.header("Upgrade").equals("websocket", ignoreCase = true)) {
                chain.proceed(request)
            } else {
                try {
                    rawJournal?.request(request)
                    val response = chain.proceed(request)
                    rawJournal?.response(response)
                    android.util.Log.i(LOG_TAG, "socketHandshake host=${request.url.host} " +
                        "status=${response.code} headers=${request.headers.names()} " +
                        "origin=${request.header("Origin")} server=${response.header("Server")}")
                    if (response.code != 101) {
                        terminalReason[0] = "socket-HTTP${response.code}"
                        terminal.set(true)
                    }
                    response
                } catch (error: IOException) {
                    android.util.Log.i(LOG_TAG, "socketHandshake failure=${error.javaClass.simpleName}")
                    terminalReason[0] = "socket-${error.javaClass.simpleName}"
                    terminal.set(true)
                    throw error
                }
            }
        }
        val interceptor = Interceptor { chain ->
            if (terminal.get()) throw IOException("Live test transport already stopped")
            val count = requestCount.incrementAndGet()
            if (count > MAX_REQUESTS) {
                terminal.compareAndSet(false, true)
                terminalReason[0] = "request-cap"
                throw IOException("Live test request cap reached")
            }
            val started = SystemClock.elapsedRealtime()
            val originalRequest = chain.request().let { original ->
                if (observedBrowserLanguage == null) original else original.newBuilder()
                    .header("Accept-Language", observedBrowserLanguage).build()
            }
            val request = if (arguments.getString("forceInitialRange") == "true" &&
                originalRequest.url.host == mediaHost.get() && originalRequest.header("Range") == null) {
                originalRequest.newBuilder().header("Range", "bytes=0-").build()
            } else originalRequest
            // Compare repeated loads without writing signed URLs or path credentials.
            val resourceId = synchronized(observedResources) {
                observedResources.getOrPut(request.url.toString()) { observedResources.size + 1 }
            }
            if (observedIdentityHosts.add(request.url.host)) {
                val identity = listOf("User-Agent", "Accept-Language", "sec-ch-ua", "sec-ch-ua-mobile",
                    "sec-ch-ua-platform", "Sec-Fetch-Dest", "Sec-Fetch-Mode", "Sec-Fetch-Site", "Origin")
                    .mapNotNull { name -> request.header(name)?.let { name to it } }.toMap()
                android.util.Log.i(LOG_TAG, "clientIdentity host=${request.url.host} headers=$identity")
            }
            try {
                rawJournal?.request(request)
                val response = chain.proceed(request)
                rawJournal?.response(response)
                val status = response.code
                requests += RequestObservation(
                    host = request.url.host,
                    method = request.method,
                    range = request.header("Range")?.take(RANGE_LOG_LIMIT),
                    status = status,
                    elapsedMs = SystemClock.elapsedRealtime() - started,
                    contentRange = response.header("Content-Range"),
                    contentLength = response.header("Content-Length"),
                    contentType = response.header("Content-Type"),
                    controlsVersion = request.header("Accepts-Controls")?.let { token ->
                        synchronized(observedTokens) { observedTokens.getOrPut(token) { observedTokens.size + 1 } }
                    },
                    extension = request.url.pathSegments.lastOrNull()?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) },
                    retryAfter = response.header("Retry-After"),
                    startedMs = started - observationStarted,
                    resourceId = resourceId,
                    protocol = response.protocol.toString(),
                )
                if (status == 403 || status == 429) {
                    val body = runCatching { response.peekBody(1_024).string().lowercase() }.getOrDefault("")
                    val keywords = listOf("token", "expired", "forbidden", "denied", "rate", "limit", "signature", "session")
                        .filter(body::contains)
                    val denialCode = response.header("X-VD")?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,48}")) }
                    android.util.Log.i(LOG_TAG, "rejection status=$status bodyKeywords=$keywords " +
                        "denialCode=$denialCode requestHeaders=${request.headers.names()} responseHeaders=${response.headers.names()}")
                    terminal.compareAndSet(false, true)
                    terminalReason[0] = "HTTP$status"
                }
                response
            } catch (error: IOException) {
                rawJournal?.failure(request, error)
                requests += RequestObservation(request.url.host, request.method,
                    request.header("Range")?.take(RANGE_LOG_LIMIT), null,
                    SystemClock.elapsedRealtime() - started, resourceId = resourceId,
                    failure = error.javaClass.simpleName, startedMs = started - observationStarted)
                throw error
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = VideoStreamResolver(
            context = context,
            client = defaultVideoResolveClient().newBuilder().addInterceptor { chain ->
                if (arguments.getString("liveIgnoreSubtitles") == "true" &&
                    chain.request().url.encodedPath.endsWith(".vtt", ignoreCase = true)) {
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                        .code(200).message("Local optional-caption fixture")
                        .body("WEBVTT\n\n".toResponseBody("text/vtt".toMediaType())).build()
                } else chain.proceed(chain.request())
            }.addNetworkInterceptor(interceptor).build(),
        )
        val resolveStarted = SystemClock.elapsedRealtime()
        val resolvedStream = try {
            runBlocking {
                withTimeout(90_000L) {
                    resolver.resolve(video, quality, waitForRuntimeSubtitles = true)
                }
            }
        } catch (error: Exception) {
            rawJournal?.resolutionFailure(error)
            val status = (error as? me.yummydroid.app.data.PlaybackHttpException)?.statusCode
            val detail = error.message?.takeIf { it.startsWith("Alloha: ") && it.matches(Regex("[A-Za-z0-9: =,._()-]{1,500}")) }
            android.util.Log.i(LOG_TAG, "stage=resolve error=${error.javaClass.simpleName} detail=$detail status=$status terminal=${terminalReason[0]} observations=$requests")
            throw AssertionError("Live resolution stopped: ${error.javaClass.simpleName}; status=$status; terminal=${terminalReason[0]}")
        }
        val baselineCvh = arguments.getString("baselineCvh") == "true"
        val stream = if (baselineCvh || arguments.getString("disableCvhRecovery") == "true") {
            resolvedStream.copy(cvhRequestRecovery = null)
        } else resolvedStream
        mediaHost.set(android.net.Uri.parse(stream.url).host)
        (stream.sessionDescriptor as? AllohaSessionDescriptor)?.let { descriptor ->
            android.util.Log.i(LOG_TAG, "metadataRemainingMs=${descriptor.expiresAtEpochMs?.minus(System.currentTimeMillis())}")
            android.util.Log.i(LOG_TAG, "discoveryStartupEvents=${descriptor.observedStartupEvents}")
            val template = JSONObject(descriptor.playbackStartTemplate)
            fun safeProviderId(value: String?) = value?.takeIf { it.matches(Regex("[A-Za-z0-9_.-]{0,32}")) }
            android.util.Log.i(LOG_TAG, "providerPair capturedHeight=${safeProviderId(template.optString("resolution"))} " +
                "capturedAudio=${safeProviderId(template.optString("track_id"))} selectedHeight=${stream.selectedVideoHeight} " +
                "selectedAudio=${safeProviderId(stream.providerAudioId)}")
            android.util.Log.i(LOG_TAG, "socketTemplateTypes=" + listOf("current_time", "resolution", "track_id", "speed", "subtitle", "ts")
                .associateWith { template.opt(it)?.javaClass?.simpleName })
        }
        android.util.Log.i(LOG_TAG, "stage=resolved provider=${stream.provider} mime=${stream.mimeType} session=${stream.sessionDescriptor?.javaClass?.simpleName} elapsed=${SystemClock.elapsedRealtime()-resolveStarted} observations=$requests")
        assertTrue("resolution hit terminal transport: ${terminalReason[0]}", !terminal.get())

        val reusable = onMain {
            createVideoPlayer(
                context = context,
                stream = stream,
                httpClient = (if (baselineCvh) defaultVideoResolveClient() else defaultVideoPlaybackClient())
                    .newBuilder().addInterceptor(socketInterceptor).addNetworkInterceptor(interceptor).build(),
                renderersFactory = YummyRenderersFactory(context),
                loadControl = if (websiteBuffer) DefaultLoadControl.Builder()
                    .setBufferDurationsMs(90_000, 90_000, 2_500, 5_000)
                    .setPrioritizeTimeOverSizeThresholds(true).build()
                else PlayerBufferPreset.Maximum.toLoadControl(),
            )
        }
        val player = reusable.player
        val visible = mutableStateOf(true)
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val loadingStarts = AtomicInteger()
        val loadStopped = AtomicBoolean(false)
        val peakBufferedMs = AtomicInteger()
        val reachedReady = AtomicBoolean(false)
        val rebuffers = AtomicInteger()
        val audioUnderruns = AtomicInteger()
        onMain {
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int,
                    bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
                    audioUnderruns.incrementAndGet()
                }

                override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
                    val detail = error.stackTraceToString().replace(Regex("https?://[^\\s]+"), "<url>")
                    android.util.Log.i(LOG_TAG, "loadError baseline=$baselineCvh bytes=${loadEventInfo.bytesLoaded} " +
                        "offset=${loadEventInfo.dataSpec.position} canceled=$wasCanceled detail=$detail")
                    if (error is androidx.media3.common.ParserException) {
                        failures += "parser:${error.message?.replace(Regex("https?://[^\\s]+"), "<url>")}"
                        terminal.set(true)
                    }
                }
            })
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) reachedReady.set(true)
                    if (playbackState == Player.STATE_BUFFERING && reachedReady.get()) rebuffers.incrementAndGet()
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    if (isLoading) loadingStarts.incrementAndGet() else loadStopped.set(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    failures += "player:${error.errorCodeName}"
                }
            })
        }
        try {
            compose.setContent {
                if (visible.value) {
                    AndroidView(factory = { viewContext -> PlayerView(viewContext).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    } })
                    NativePlayerLifecycle(NativePlayerLifecycleBinding(
                        player = player,
                        localPlayer = player,
                        stream = stream,
                        videoId = video.id,
                        pipPlayerHandle = object : PipPlayerHandle {
                            override val isPlaying get() = player.isPlaying
                            override fun play() = player.play()
                            override fun pause() = player.pause()
                        },
                        metadataDurationSeconds = video.durationSeconds,
                        state = NativePlayerEventState(
                            playerView = { null },
                            settings = { AppSettings(playerBufferPreset = PlayerBufferPreset.Maximum) },
                            qualityOptions = { emptyList() }, selectedQualityKey = { null },
                            playbackPreferredQuality = { quality }, streamSelectedQualityKey = { null },
                            fallbackSuppressedUntilMs = { 0L }, onFallbackSuppressedUntilChanged = {},
                            skipControlsTimelineReady = { true }, onSkipControlsTimelineReady = {},
                            onPlaybackReady = {}, onTracksChanged = {},
                            onSelectedSubtitleKeyChanged = {}, onSelectedQualityKeyChanged = {},
                        ),
                        callbacks = NativePlayerEventCallbacks(
                            onPlaybackStarted = {}, onPlaybackEnded = {},
                            onBufferingTimeout = { failures += "watchdog@$it"; terminal.set(true) },
                            onAutoAdvance = {},
                            onPlaybackError = { position, error ->
                                failures += "lifecycle:${error.errorCodeName}@$position"; terminal.set(true)
                            },
                            onProgressSnapshot = { _, _ -> }, onDisplayModeUpdate = {}, onDispose = {},
                        ),
                        receivedNetworkBytes = { reusable.networkProgress.receivedBytes },
                    ))
                }
            }
            onMain { reusable.load(player, stream, MediaMetadata.EMPTY, "live:${video.id}", 0L, true) }
            pollPlayback(player, reusable, terminal, terminalReason, failures, loadingStarts, loadStopped, peakBufferedMs,
                if (websiteBuffer) 60_000L else 120_000L)
            if (InstrumentationRegistry.getArguments().getString("liveFullEpisode") == "true" && !terminal.get()) Thread.sleep(5_000L)
            assertTrue("terminal=${terminalReason[0]} requests=$requests", !terminal.get())
            assertTrue("playback failures=$failures requests=$requests", failures.isEmpty())
            assertTrue("request cap exceeded: ${requestCount.get()}", requestCount.get() <= MAX_REQUESTS)
            assertTrue("Playback rebuffered ${rebuffers.get()} times", rebuffers.get() == 0)
            assertTrue("Audio underruns: ${audioUnderruns.get()}", audioUnderruns.get() == 0)
            assertTrue("MediaItem was reloaded", loadRequests(player) == 1L)
        } finally {
            onMain {
                player.stop()
                reusable.updateProviderState()
            }
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            onMain {
                android.util.Log.i(LOG_TAG, "requests=${requestCount.get()} loads=${loadRequests(player)} " +
                    "loadingStarts=${loadingStarts.get()} stopped=${loadStopped.get()} peakBuffer=${peakBufferedMs.get()} " +
                    "rebuffers=${rebuffers.get()} audioUnderruns=${audioUnderruns.get()} " +
                    "terminal=${terminalReason[0]} failures=$failures " +
                    player.playbackLoadDiagnosticsText())
                reusable.providerSessionDiagnostics()?.let { android.util.Log.i(LOG_TAG, it) }
                // Android log entries have a size limit; HLS/DASH request lists exceed it.
                val observations = synchronized(requests) { requests.toList() }
                observations.chunked(8).forEachIndexed { index, batch ->
                    android.util.Log.i(LOG_TAG, "requestBatch[$index]=$batch")
                }
                reusable.closeProviderSession()
                player.release()
            }
        }
    }

    private fun pollPlayback(
        player: Player,
        reusable: ReusableVideoPlayer,
        terminal: AtomicBoolean,
        terminalReason: Array<String?>,
        failures: List<String>,
        loadingStarts: AtomicInteger,
        loadStopped: AtomicBoolean,
        peakBufferedMs: AtomicInteger,
        requiredFinalBufferMs: Long,
    ) {
        val started = SystemClock.elapsedRealtime()
        val requestedMinutes = InstrumentationRegistry.getArguments().getString("liveMinutes")
            ?.toLongOrNull()?.coerceIn(6L, 30L)
        val minimumPlaybackMs = requestedMinutes?.times(60_000L) ?: MIN_PLAYBACK_MS
        val fullEpisode = InstrumentationRegistry.getArguments().getString("liveFullEpisode") == "true"
        val deadline = started + if (fullEpisode) 40 * 60_000L else
            (requestedMinutes?.plus(3L)?.times(60_000L) ?: MAX_PLAYBACK_MS)
        var previousPosition = 0L
        var previousObservation = started
        var playedMs = 0L
        var nextProviderUpdate = 0L
        var nextLog = 0L
        var completedRefills = false
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            val snapshot = onMain {
                if (now >= nextProviderUpdate) {
                    reusable.updateProviderState()
                    nextProviderUpdate = now + reusable.providerStateUpdateIntervalMs
                }
                PlaybackSnapshot(player.currentPosition, player.totalBufferedDuration, player.isLoading,
                    player.playbackState, reusable.networkProgress.receivedBytes)
            }
            val advanced = snapshot.positionMs - previousPosition
            if (advanced in 1..(now - previousObservation + 1_500L)) playedMs += advanced
            previousPosition = snapshot.positionMs
            previousObservation = now
            peakBufferedMs.updateAndGet { max(it, snapshot.bufferMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            if (now >= nextLog) {
                android.util.Log.i(LOG_TAG, "pos=${snapshot.positionMs} buffer=${snapshot.bufferMs} loading=${snapshot.loading} " +
                    "state=${snapshot.state} bytes=${snapshot.bytes} loads=${loadRequests(player)} " +
                    player.playbackLoadDiagnosticsText())
                reusable.providerSessionDiagnostics()?.let { android.util.Log.i(LOG_TAG, it) }
                nextLog = now + 15_000L
            }
            if (terminal.get() || failures.isNotEmpty()) {
                onMain { player.stop() }
                return
            }
            if (snapshot.state == Player.STATE_ENDED) {
                onMain {
                    assertTrue("Premature end: position=${player.currentPosition}, duration=${player.duration}",
                        player.duration > 0L && player.currentPosition + 5_000L >= player.duration)
                    assertTrue("Episode ended before the requested long-playback interval",
                        player.currentPosition >= minimumPlaybackMs - 10_000L)
                    assertTrue("Episode ended without completing refill cycles",
                        loadStopped.get() && loadingStarts.get() >= 3)
                    if (fullEpisode) {
                        assertTrue("Episode skipped playback: played=$playedMs duration=${player.duration}",
                            playedMs >= player.duration - 10_000L)
                        android.util.Log.i(LOG_TAG, "episodeCompleted=true playedMs=$playedMs durationMs=${player.duration}")
                    }
                }
                return
            }
            if (!fullEpisode && now - started >= minimumPlaybackMs && loadStopped.get() && loadingStarts.get() >= 3 &&
                !snapshot.loading && snapshot.bufferMs >= requiredFinalBufferMs) {
                completedRefills = true
                break
            }
            Thread.sleep(250L)
        }
        onMain {
            assertTrue("Insufficient playback progress: ${player.currentPosition} ms; expected $minimumPlaybackMs ms",
                player.currentPosition >= minimumPlaybackMs - 10_000L)
            assertTrue("Two stop/refill cycles did not complete within the live-test window", completedRefills)
            assertTrue("Loader stopped after an unrecovered error",
                !player.playbackLoadDiagnosticsText().contains("loadStopped=true"))
        }
    }

    private fun decodeVideo(encoded: String): VideoVariant {
        val json = JSONObject(String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8))
        val sourceName = json.getString("sourceName")
        return VideoVariant(
            id = json.getLong("id"), animeId = json.getLong("animeId"), player = sourceName,
            dubbing = "live", episode = "live", url = json.getString("url"), index = 0,
            durationSeconds = json.optInt("durationSeconds", 0).takeIf { it > 0 }, views = 0L,
        )
    }

    private fun preferredQuality(value: String): PreferredQuality {
        return PreferredQuality.fromName(value)
            ?: PreferredQuality.fromHeight(value.removeSuffix("p").toIntOrNull())
            ?: PreferredQuality.P1080
    }

    private fun loadRequests(player: Player): Long = Regex("loadRequests=(\\d+)")
        .find(player.playbackLoadDiagnosticsText())?.groupValues?.get(1)?.toLongOrNull() ?: -1L

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private data class RequestObservation(
        val host: String,
        val method: String,
        val range: String?,
        val status: Int?,
        val elapsedMs: Long,
        val contentRange: String? = null,
        val contentLength: String? = null,
        val contentType: String? = null,
        val controlsVersion: Int? = null,
        val extension: String? = null,
        val retryAfter: String? = null,
        val startedMs: Long? = null,
        val resourceId: Int? = null,
        val protocol: String? = null,
        val failure: String? = null,
    )

    private data class PlaybackSnapshot(
        val positionMs: Long,
        val bufferMs: Long,
        val loading: Boolean,
        val state: Int,
        val bytes: Long,
    )

    private companion object {
        const val LOG_TAG = "LiveProviderBuffering"
        const val MAX_REQUESTS = 1_200
        const val MIN_PLAYBACK_MS = 6 * 60 * 1_000L
        const val MAX_PLAYBACK_MS = 9 * 60 * 1_000L
        const val RANGE_LOG_LIMIT = 96
    }
}
