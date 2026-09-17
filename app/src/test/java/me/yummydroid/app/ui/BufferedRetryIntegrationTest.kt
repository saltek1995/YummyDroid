package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.ResolvedVideoStream
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * Real loaders/extractors/sample queues and the actual Standard 35s/70s load-control preset.
 * Fake renderer/player clock; loopback synthetic media; no provider credentials, leases,
 * CVH advertised-host routing, hardware output, Compose recreation or real socket-idle timing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class BufferedRetryIntegrationTest {
    @Test fun cvhHlsKeepsHealthyQueueAcrossFourForbiddenResponses() = exercise(PlaybackProvider.Cvh)
    @Test fun allohaHlsKeepsHealthyQueueAcrossFourForbiddenResponses() = exercise(PlaybackProvider.Alloha)
    @Test fun kodikHlsKeepsHealthyQueueAcrossFourForbiddenResponses() = exercise(PlaybackProvider.Kodik)
    @Test fun aksorHlsKeepsHealthyQueueAcrossFourForbiddenResponses() = exercise(PlaybackProvider.Aksor)
    @Test fun sibnetHlsKeepsHealthyQueueAcrossFourForbiddenResponses() = exercise(PlaybackProvider.Sibnet)
    @Test fun cvhMp4KeepsHealthyQueueAcrossFourForbiddenResumeResponses() = exercise(PlaybackProvider.Cvh, hls = false)
    @Test fun allohaMp4KeepsHealthyQueueAcrossFourForbiddenResumeResponses() = exercise(PlaybackProvider.Alloha, hls = false)
    @Test fun ordinaryServerErrorsControlKeepsHealthyQueueAcrossFourResponses() = exercise(PlaybackProvider.Cvh, failureStatus = 500)

    private data class RequestObservation(val path: String, val offset: Long, val atMs: Long)
    private data class FailureObservation(val atMs: Long, val bufferedMs: Long, val loadStopped: Boolean)

    private fun exercise(provider: PlaybackProvider, hls: Boolean = true, failureStatus: Int = 403) {
        val clock = FakeClock(false)
        val requests = Collections.synchronizedList(mutableListOf<RequestObservation>())
        val failureRequests = Collections.synchronizedList(mutableListOf<RequestObservation>())
        val failures = mutableListOf<FailureObservation>()
        val mp4 = resource("audio.m4a")
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path!!.substringBefore('?').substringAfterLast('/')
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                    val observation = RequestObservation(path, offset, clock.elapsedRealtime())
                    requests += observation
                    val target = if (hls) path == "segment008.m4s" else offset > 0L
                    if (target) {
                        failureRequests += observation
                        if (failureRequests.size <= 4) {
                            return MockResponse().setResponseCode(failureStatus).setBody("temporary fixture refusal")
                        }
                    }
                    val bytes = if (hls) resource(path) else mp4
                    val response = MockResponse().setResponseCode(if (offset > 0L) 206 else 200)
                        .setHeader("Content-Type", if (path.endsWith("m3u8")) "application/vnd.apple.mpegurl" else "audio/mp4")
                        .setHeader("Accept-Ranges", "bytes")
                    if (!hls && offset == 0L) {
                        response.setBody(Buffer().write(bytes, 0, (bytes.size * 85L / 100L).toInt()))
                            .setHeader("Content-Length", bytes.size)
                            .setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
                    } else {
                        response.setBody(Buffer().write(bytes, offset.toInt(), bytes.size - offset.toInt()))
                        if (offset > 0L) response.setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                    }
                    return response
                }
            }
            server.start()
            val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
            val uri = server.url(if (hls) "/index.m3u8" else "/audio.m4a")
            val stream = ResolvedVideoStream(uri.toString(), if (hls) "application/x-mpegURL" else "audio/mp4",
                mapOf("User-Agent" to "BufferedRetryFixture/1", "Referer" to "https://fixture.test/"), provider = provider)
            val dataSources = StreamHttpDataSourceFactory(client, stream)
            dataSources.update(stream)
            val policy = PlaybackLoadErrorHandlingPolicy(provider)
            val factory: MediaSource.Factory = if (hls) HlsMediaSource.Factory(dataSources).setLoadErrorHandlingPolicy(policy)
                else ProgressiveMediaSource.Factory(dataSources).setContinueLoadingCheckIntervalBytes(4_096).setLoadErrorHandlingPolicy(policy)
            val renderer = FakeRenderer(C.TRACK_TYPE_AUDIO)
            val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(clock).setRenderers(renderer)
                .setMediaSourceFactory(factory).setLoadControl(PlayerBufferPreset.Standard.toLoadControl()).build()
            var transitions = 0
            var discontinuities = 0
            var playingStarted = false
            var bufferingAfterPlay = 0
            var loadingStarts = 0
            var initialBufferMs = 0L
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { discontinuities++ }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playingStarted && playbackState == Player.STATE_BUFFERING) bufferingAfterPlay++
                }
                override fun onIsLoadingChanged(isLoading: Boolean) { if (isLoading) loadingStarts++ }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
                    if (error.playbackHttpDetails()?.statusCode == failureStatus) {
                        failures += FailureObservation(eventTime.realtimeMs, eventTime.totalBufferedDurationMs, wasCanceled)
                    }
                }
            })
            try {
                player.setMediaItem(MediaItem.fromUri(uri.toString()))
                player.prepare()
                advanceUntil(clock) { player.playbackState == Player.STATE_READY || player.playerError != null }
                advanceUntil(clock) {
                    (player.totalBufferedDuration >= PlayerBufferPreset.Standard.maxBufferMs && !player.isLoading) || player.playerError != null
                }
                assertNull(player.playerError)
                initialBufferMs = player.totalBufferedDuration
                assertTrue(initialBufferMs >= 70_000L, "Standard preset never filled: $initialBufferMs")
                assertTrue(initialBufferMs < 95_000L, "Must stop before the selected failure point")
                val initialResets = renderer.positionResetCount
                val initialDiscontinuities = discontinuities
                playingStarted = true
                player.play()
                advanceUntil(clock) { player.playbackState == Player.STATE_ENDED || player.playerError != null }
                assertNull(player.playerError)
                assertEquals(4, failures.size)
                assertTrue(failures.all { it.bufferedMs > 0L }, "Each transient error must occur with queued media: $failures")
                assertTrue(failures.none { it.loadStopped }, "Temporary failure must not stop the load: $failures")
                val attempts = synchronized(failureRequests) { failureRequests.toList() }
                assertEquals(5, attempts.size)
                if (failureStatus == 403) {
                    assertTrue(attempts.zipWithNext().all { (before, after) -> after.atMs - before.atMs >= 2_000L },
                        "Retries must be paced by at least 2s on the player clock: $attempts")
                }
                assertEquals(0, bufferingAfterPlay)
                assertEquals(1, transitions)
                assertEquals(initialResets, renderer.positionResetCount)
                assertEquals(initialDiscontinuities, discontinuities)
                assertTrue(renderer.sampleBufferReadCount > 5_000)
                assertTrue(loadingStarts >= 2)
                if (!hls) assertTrue(attempts.all { it.offset == attempts.first().offset && it.offset > 0L })
            } finally {
                println("Buffered retry provider=$provider hls=$hls status=$failureStatus initialBufferMs=$initialBufferMs " +
                    "failureRequests=${synchronized(failureRequests) { failureRequests.toList() }} failures=$failures " +
                    "positionMs=${player.currentPosition} bufferMs=${player.totalBufferedDuration} error=${player.playerError?.errorCodeName} " +
                    "bufferingAfterPlay=$bufferingAfterPlay transitions=$transitions rendererResets=${renderer.positionResetCount} " +
                    "samples=${renderer.sampleBufferReadCount} loadingStarts=$loadingStarts")
                player.release()
                dataSources.close()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }

    private fun resource(name: String): ByteArray = javaClass.getResourceAsStream("/media/long-buffer/$name")!!.use { it.readBytes() }

    private fun advanceUntil(clock: FakeClock, condition: () -> Boolean) {
        // An unconstrained auto-advancing clock can consume tens of seconds while a loopback
        // socket thread is merely waiting for CPU. Bound test-clock progress and give real I/O
        // a scheduling turn; this is simulated playback time, not a network-latency measurement.
        RobolectricUtil.runMainLooperUntil {
            if (condition()) true else {
                clock.advanceTime(100L)
                Thread.sleep(1L)
                false
            }
        }
    }
}
