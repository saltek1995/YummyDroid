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
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoStreamResolver
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.defaultVideoPlaybackClient
import me.yummydroid.app.data.defaultVideoResolveClient
import okhttp3.Interceptor
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
        val quality = arguments.getString("preferredQuality")?.let(::preferredQuality) ?: PreferredQuality.P1080
        val terminal = AtomicBoolean(false)
        val terminalReason = arrayOfNulls<String>(1)
        val requestCount = AtomicInteger()
        val mediaHost = AtomicReference<String?>()
        val requests = Collections.synchronizedList(mutableListOf<RequestObservation>())
        val interceptor = Interceptor { chain ->
            if (terminal.get()) throw IOException("Live test transport already stopped")
            val count = requestCount.incrementAndGet()
            if (count > MAX_REQUESTS) {
                terminal.compareAndSet(false, true)
                terminalReason[0] = "request-cap"
                throw IOException("Live test request cap reached")
            }
            val started = SystemClock.elapsedRealtime()
            val originalRequest = chain.request()
            val request = if (arguments.getString("forceInitialRange") == "true" &&
                originalRequest.url.host == mediaHost.get() && originalRequest.header("Range") == null) {
                originalRequest.newBuilder().header("Range", "bytes=0-").build()
            } else originalRequest
            try {
                val response = chain.proceed(request)
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
                )
                if (status == 403 || status == 429) {
                    terminal.compareAndSet(false, true)
                    terminalReason[0] = "HTTP$status"
                }
                response
            } catch (error: IOException) {
                requests += RequestObservation(request.url.host, request.method,
                    request.header("Range")?.take(RANGE_LOG_LIMIT), null,
                    SystemClock.elapsedRealtime() - started)
                throw error
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = VideoStreamResolver(
            context = context,
            client = defaultVideoResolveClient().newBuilder().addNetworkInterceptor(interceptor).build(),
        )
        val resolveStarted = SystemClock.elapsedRealtime()
        val resolvedStream = try {
            runBlocking {
                withTimeout(90_000L) {
                    resolver.resolve(video, quality, waitForRuntimeSubtitles = true)
                }
            }
        } catch (error: Exception) {
            android.util.Log.i(LOG_TAG, "stage=resolve error=${error.javaClass.simpleName} terminal=${terminalReason[0]} observations=$requests")
            throw AssertionError("Live resolution stopped: ${error.javaClass.simpleName}; terminal=${terminalReason[0]}")
        }
        val baselineCvh = arguments.getString("baselineCvh") == "true"
        val stream = if (baselineCvh || arguments.getString("disableCvhRecovery") == "true") {
            resolvedStream.copy(cvhRequestRecovery = null)
        } else resolvedStream
        mediaHost.set(android.net.Uri.parse(stream.url).host)
        android.util.Log.i(LOG_TAG, "stage=resolved provider=${stream.provider} mime=${stream.mimeType} elapsed=${SystemClock.elapsedRealtime()-resolveStarted} observations=$requests")
        assertTrue("resolution hit terminal transport: ${terminalReason[0]}", !terminal.get())

        val reusable = onMain {
            createVideoPlayer(
                context = context,
                stream = stream,
                httpClient = (if (baselineCvh) defaultVideoResolveClient() else defaultVideoPlaybackClient())
                    .newBuilder().addNetworkInterceptor(interceptor).build(),
                renderersFactory = YummyRenderersFactory(context),
                loadControl = PlayerBufferPreset.Maximum.toLoadControl(),
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
            pollPlayback(player, reusable, terminal, terminalReason, failures, loadingStarts, loadStopped, peakBufferedMs)
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
    ) {
        val started = SystemClock.elapsedRealtime()
        val deadline = started + MAX_PLAYBACK_MS
        var nextProviderUpdate = 0L
        var nextLog = 0L
        var completedRefills = false
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            val snapshot = onMain {
                if (now >= nextProviderUpdate) {
                    reusable.updateProviderState()
                    nextProviderUpdate = now + 1_000L
                }
                PlaybackSnapshot(player.currentPosition, player.totalBufferedDuration, player.isLoading,
                    player.playbackState, reusable.networkProgress.receivedBytes)
            }
            peakBufferedMs.updateAndGet { max(it, snapshot.bufferMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
            if (now >= nextLog) {
                android.util.Log.i(LOG_TAG, "pos=${snapshot.positionMs} buffer=${snapshot.bufferMs} loading=${snapshot.loading} " +
                    "state=${snapshot.state} bytes=${snapshot.bytes} loads=${loadRequests(player)} " +
                    player.playbackLoadDiagnosticsText())
                nextLog = now + 15_000L
            }
            if (terminal.get() || failures.isNotEmpty()) {
                onMain { player.stop() }
                return
            }
            if (snapshot.state == Player.STATE_ENDED) {
                onMain { assertTrue("Premature end: position=${player.currentPosition}, duration=${player.duration}",
                    player.duration <= 0L || player.currentPosition + 5_000L >= player.duration) }
                return
            }
            if (now - started >= MIN_PLAYBACK_MS && loadStopped.get() && loadingStarts.get() >= 3 &&
                !snapshot.loading && snapshot.bufferMs >= 120_000L) {
                completedRefills = true
                break
            }
            Thread.sleep(250L)
        }
        onMain {
            assertTrue("Insufficient playback progress", player.currentPosition >= 300_000L)
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
