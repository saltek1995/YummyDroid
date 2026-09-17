package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.CvhMediaRequestRecovery
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.defaultVideoPlaybackClient
import me.yummydroid.app.data.defaultVideoResolveClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Real Android decoders, SurfaceView, AudioTrack, clocks and local HTTPS. No provider traffic.
 * Baseline reconstructs the older HTTP timeout/default transport, not the whole v1.4.50 APK.
 * Run on an Android device/emulator; the virtual audio sink does not prove audible TV output.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RealDecoderBufferingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun currentTransportResumesFullBufferWithRealDecoders() = exercise(false)
    @Test fun earlierTransportTimeoutResumesFullBufferWithRealDecoders() = exercise(true)
    @Test fun maximumBufferResumesAfterLongIdleWithRealDecoders() =
        exercise(false, PlayerBufferPreset.Maximum, 540)

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private fun exercise(
        earlierTransport: Boolean,
        preset: PlayerBufferPreset = PlayerBufferPreset.Standard,
        durationSeconds: Int = 180,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val media = instrumentation.context.assets.open("media/realtime-buffer/av-${durationSeconds}s.mp4").use { it.readBytes() }
        val requests = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")
                        ?.substringBefore('-')?.toLongOrNull() ?: 0L
                    requests += offset to SystemClock.elapsedRealtime()
                    val response = MockResponse().setHeader("Content-Type", "video/mp4")
                        .setHeader("Accept-Ranges", "bytes")
                    if (offset == 0L) {
                        // Server closes a partial response while the player can pause with a full buffer.
                        return response.setBody(Buffer().write(media, 0, media.size * 3 / 4))
                            .setHeader("Content-Length", media.size).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
                    }
                    return response.setResponseCode(206)
                        .setHeader("Content-Range", "bytes $offset-${media.lastIndex}/${media.size}")
                        .setBody(Buffer().write(media, offset.toInt(), media.size - offset.toInt()))
                }
            }
            server.start()
            val base = if (earlierTransport) defaultVideoResolveClient() else defaultVideoPlaybackClient()
            val client = base.newBuilder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val url = server.url("/video.mp4").toString()
            val stream = ResolvedVideoStream(url, "video/mp4", emptyMap(),
                provider = if (earlierTransport) PlaybackProvider.Unknown else PlaybackProvider.Cvh,
                cvhRequestRecovery = if (earlierTransport) null else CvhMediaRequestRecovery(server.hostName, server.hostName))
            val reusable = onMain { createVideoPlayer(instrumentation.targetContext, stream, client,
                YummyRenderersFactory(instrumentation.targetContext), preset.toLoadControl()) }
            val player = reusable.player
            val failures = Collections.synchronizedList(mutableListOf<String>())
            val decoders = Collections.synchronizedList(mutableListOf<String>())
            val loadingStarts = AtomicInteger()
            val transitions = AtomicInteger()
            val rebufferings = AtomicInteger()
            val underruns = AtomicInteger()
            var started = false
            var initialBuffer = 0L
            val startAt = SystemClock.elapsedRealtime()
            onMain {
                player.addListener(object : Player.Listener {
                    override fun onIsLoadingChanged(isLoading: Boolean) {
                        if (isLoading) loadingStarts.incrementAndGet()
                        android.util.Log.i("RealDecoderBufferingTest", "cycle preset=$preset elapsed=${SystemClock.elapsedRealtime()-startAt} " +
                            "loading=$isLoading pos=${player.currentPosition} buffer=${player.totalBufferedDuration}")
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (started && state == Player.STATE_BUFFERING) rebufferings.incrementAndGet()
                    }
                    override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { transitions.incrementAndGet() }
                })
                player.addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, name: String, initializedTimestampMs: Long, initializationDurationMs: Long) { decoders += "video:$name" }
                    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, name: String, initializedTimestampMs: Long, initializationDurationMs: Long) { decoders += "audio:$name" }
                    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) { if (started) underruns.incrementAndGet() }
                })
            }
            try {
                compose.setContent {
                    AndroidView(factory = { context -> PlayerView(context).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                    } })
                    NativePlayerLifecycle(NativePlayerLifecycleBinding(
                        player = player, localPlayer = player, stream = stream, videoId = 1,
                        pipPlayerHandle = object : PipPlayerHandle {
                            override val isPlaying get() = player.isPlaying
                            override fun play() { player.play() }
                            override fun pause() { player.pause() }
                        }, metadataDurationSeconds = durationSeconds,
                        state = NativePlayerEventState(
                            playerView = { null }, settings = { AppSettings(playerBufferPreset = preset) },
                            qualityOptions = { emptyList() }, selectedQualityKey = { null },
                            playbackPreferredQuality = { PreferredQuality.Auto }, streamSelectedQualityKey = { null },
                            fallbackSuppressedUntilMs = { 0L }, onFallbackSuppressedUntilChanged = {},
                            skipControlsTimelineReady = { true }, onSkipControlsTimelineReady = {},
                            onPlaybackReady = {}, onTracksChanged = {}, onSelectedSubtitleKeyChanged = {}, onSelectedQualityKeyChanged = {}),
                        callbacks = NativePlayerEventCallbacks(
                            onPlaybackStarted = {}, onPlaybackEnded = {}, onBufferingTimeout = { failures += "watchdog@$it" },
                            onAutoAdvance = {}, onPlaybackError = { position, error -> failures += "${error.errorCodeName}@$position" },
                            onProgressSnapshot = { _, _ -> }, onDisplayModeUpdate = {}, onDispose = {}),
                        receivedNetworkBytes = { reusable.networkProgress.receivedBytes }))
                }
                onMain { reusable.load(player, stream, MediaMetadata.EMPTY, "real-buffer-fixture", 0, false) }
                compose.waitUntil(30_000) { onMain {
                    failures.isNotEmpty() || (player.totalBufferedDuration >= preset.maxBufferMs && !player.isLoading)
                } }
                onMain {
                    assertTrue(failures.toString(), failures.isEmpty())
                    initialBuffer = player.totalBufferedDuration
                    started = true
                    player.play()
                }
                compose.waitUntil(durationSeconds * 1_000L + 50_000L) { onMain { player.playbackState == Player.STATE_ENDED || failures.isNotEmpty() } }
                onMain {
                    assertTrue(failures.toString(), failures.isEmpty())
                    assertEquals(Player.STATE_ENDED, player.playbackState)
                    assertEquals(1, transitions.get())
                    assertTrue("Loading never resumed", loadingStarts.get() >= 2)
                    if (preset == PlayerBufferPreset.Maximum) {
                        assertTrue("Need repeated refill cycles", loadingStarts.get() >= 3)
                        assertEquals("Playback must stay uninterrupted", 0, rebufferings.get())
                    }
                    assertTrue("No actual video decoder: $decoders", decoders.any { it.startsWith("video:") })
                    assertTrue("No actual audio decoder: $decoders", decoders.any { it.startsWith("audio:") })
                    assertTrue("Video frames were not rendered", (player.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 1_000)
                    assertTrue("Audio buffers were not rendered", (player.audioDecoderCounters?.renderedOutputBufferCount ?: 0) > 1_000)
                }
                assertTrue("No Range continuation: $requests", requests.any { it.first > 0 })
                assertTrue("Unexpected request loop: $requests", requests.size <= 15)
            } finally {
                onMain {
                    android.util.Log.i("RealDecoderBufferingTest", "preset=$preset earlier=$earlierTransport elapsed=${SystemClock.elapsedRealtime()-startAt} " +
                        "initialBuffer=$initialBuffer loading=${loadingStarts.get()} rebuffer=${rebufferings.get()} underruns=${underruns.get()} " +
                        "transitions=${transitions.get()} decoders=$decoders requests=$requests failures=$failures " +
                        "videoFrames=${player.videoDecoderCounters?.renderedOutputBufferCount} audioBuffers=${player.audioDecoderCounters?.renderedOutputBufferCount} " +
                        player.playbackLoadDiagnosticsText())
                    reusable.closeProviderSession()
                    player.release()
                }
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
