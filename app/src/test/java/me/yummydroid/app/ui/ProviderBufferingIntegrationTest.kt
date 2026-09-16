package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.media3.test.utils.robolectric.RobolectricUtil
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlaybackProvider
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
 * Exercises production HTTP headers and per-provider load policy with real Media3 HLS/MP4 queues.
 * Provider credentials, Alloha WebSocket leases, websites, hardware audio and real idle timeouts
 * are not simulated. These tests do not establish audible volume or real-provider availability.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ProviderBufferingIntegrationTest {
    @Test fun allohaHlsRecoversTransientSegmentFailure() = exercise(PlaybackProvider.Alloha)
    @Test fun kodikHlsRecoversTransientSegmentFailure() = exercise(PlaybackProvider.Kodik)
    @Test fun aksorHlsRecoversTransientSegmentFailure() = exercise(PlaybackProvider.Aksor)
    @Test fun sibnetHlsRecoversTransientSegmentFailure() = exercise(PlaybackProvider.Sibnet)

    @Test fun allohaMp4ResumesTruncatedBodyAndTransientFailure() = exercise(PlaybackProvider.Alloha, hls = false)
    @Test fun kodikMp4ResumesTruncatedBodyAndTransientFailure() = exercise(PlaybackProvider.Kodik, hls = false)
    @Test fun aksorMp4ResumesTruncatedBodyAndTransientFailure() = exercise(PlaybackProvider.Aksor, hls = false)
    @Test fun sibnetMp4ResumesTruncatedBodyAndTransientFailure() = exercise(PlaybackProvider.Sibnet, hls = false)

    @Test fun allohaHlsRateLimitIsTerminalWithoutRequestFlood() = exercise(PlaybackProvider.Alloha, terminal = true)
    @Test fun kodikHlsRateLimitIsTerminalWithoutRequestFlood() = exercise(PlaybackProvider.Kodik, terminal = true)
    @Test fun aksorHlsRateLimitIsTerminalWithoutRequestFlood() = exercise(PlaybackProvider.Aksor, terminal = true)
    @Test fun sibnetHlsRateLimitIsTerminalWithoutRequestFlood() = exercise(PlaybackProvider.Sibnet, terminal = true)

    @Test fun allohaHlsRetriesTransientForbiddenSegment() = exercise(PlaybackProvider.Alloha, failureStatus = 403)
    @Test fun kodikHlsRetriesTransientForbiddenSegment() = exercise(PlaybackProvider.Kodik, failureStatus = 403)
    @Test fun aksorHlsRetriesTransientForbiddenSegment() = exercise(PlaybackProvider.Aksor, failureStatus = 403)
    @Test fun sibnetHlsRetriesTransientForbiddenSegment() = exercise(PlaybackProvider.Sibnet, failureStatus = 403)
    @Test fun kodikHlsPermanentForbiddenStopsAfterOneRetry() =
        exercise(PlaybackProvider.Kodik, terminal = true, failureStatus = 403, permanentFailure = true)
    @Test fun sibnetHlsPermanentForbiddenStopsAfterOneRetry() =
        exercise(PlaybackProvider.Sibnet, terminal = true, failureStatus = 403, permanentFailure = true)

    private data class CapturedRequest(val path: String, val offset: Long, val referer: String?, val userAgent: String?, val fixture: String?)

    private fun exercise(provider: PlaybackProvider, hls: Boolean = true, terminal: Boolean = false,
        failureStatus: Int = if (terminal) 429 else 500, permanentFailure: Boolean = false) {
        val requests = Collections.synchronizedList(mutableListOf<CapturedRequest>())
        val mp4 = resource("/media/cvh-20s-aac.m4a")
        var failedRequestCount = 0
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path!!.substringBefore('?').substringAfterLast('/')
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                    requests += CapturedRequest(path, offset, request.getHeader("Referer"), request.getHeader("User-Agent"), request.getHeader("X-Fixture"))
                    val failurePoint = if (hls) path == "segment004.m4s" else offset > 0L
                    if (failurePoint && (permanentFailure || failedRequestCount == 0)) {
                        failedRequestCount++
                        return MockResponse().setResponseCode(failureStatus).setBody("fixture transient failure")
                    }
                    val data = if (hls) resource("/media/provider-hls/$path") else mp4
                    val response = MockResponse().setResponseCode(if (offset > 0L) 206 else 200)
                        .setHeader("Content-Type", if (path.endsWith("m3u8")) "application/vnd.apple.mpegurl" else "audio/mp4")
                        .setHeader("Accept-Ranges", "bytes")
                        .setBody(Buffer().write(data, offset.toInt(), data.size - offset.toInt()))
                    if (offset > 0L) response.setHeader("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}")
                    if (!hls && offset == 0L) response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                    return response
                }
            }
            server.start()
            val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
            val uri = server.url(if (hls) "/hls/index.m3u8" else "/media/audio.m4a")
            val stream = ResolvedVideoStream(uri.toString(), if (hls) "application/x-mpegURL" else "audio/mp4",
                mapOf("Referer" to "https://fixture-origin.test/", "User-Agent" to "ProviderFixture/1", "X-Fixture" to provider.name),
                provider = provider)
            val dataSources = StreamHttpDataSourceFactory(client, stream)
            dataSources.update(stream)
            val policy = PlaybackLoadErrorHandlingPolicy(provider)
            val factory: MediaSource.Factory = if (hls) HlsMediaSource.Factory(dataSources).setLoadErrorHandlingPolicy(policy)
                else ProgressiveMediaSource.Factory(dataSources).setContinueLoadingCheckIntervalBytes(4_096).setLoadErrorHandlingPolicy(policy)
            val clock = FakeClock(true)
            val renderer = FakeRenderer(C.TRACK_TYPE_AUDIO)
            val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(clock).setRenderers(renderer)
                .setMediaSourceFactory(factory).setLoadControl(DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_000, 4_000, 250, 500).setPrioritizeTimeOverSizeThresholds(true).build()).build()
            var transitions = 0
            var discontinuities = 0
            var loadErrors = 0
            var loadingStarts = 0
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { discontinuities++ }
                override fun onIsLoadingChanged(isLoading: Boolean) { if (isLoading) loadingStarts++ }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) { loadErrors++ }
            })
            try {
                player.setMediaItem(MediaItem.fromUri(uri.toString()))
                player.prepare()
                TestPlayerRunHelper.advance(player).ignoringNonFatalErrors().untilState(Player.STATE_READY)
                // HLS briefly reports loading=false between segments; wait for the load-control stop.
                RobolectricUtil.runMainLooperUntil {
                    (player.totalBufferedDuration >= 4_000L && !player.isLoading) || player.playerError != null
                }
                assertNull(player.playerError)
                val fullBufferMs = player.totalBufferedDuration
                assertTrue(fullBufferMs in 3_000L..14_999L, "Expected bounded buffer for $provider, got $fullBufferMs")
                val initialResets = renderer.positionResetCount
                val initialDiscontinuities = discontinuities
                // Advances the player clock, not wall-clock socket time or provider session expiry.
                clock.advanceTime(30_000L)
                TestPlayerRunHelper.advance(player).untilPendingCommandsAreFullyHandled()
                assertEquals(0L, player.currentPosition)
                if (terminal) {
                    player.play()
                    val error = TestPlayerRunHelper.advance(player).ignoringNonFatalErrors().untilPlayerError()
                    assertEquals(failureStatus, error.playbackHttpDetails()?.statusCode)
                } else {
                    TestPlayerRunHelper.play(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED)
                    assertNull(player.playerError)
                    assertTrue(renderer.sampleBufferReadCount > 800)
                    assertTrue(loadingStarts >= 2)
                    assertEquals(initialResets, renderer.positionResetCount)
                    assertEquals(initialDiscontinuities, discontinuities)
                }
                assertEquals(1, transitions)
                val captured = synchronized(requests) { requests.toList() }
                assertTrue(captured.all { it.referer == "https://fixture-origin.test/" && it.userAgent == "ProviderFixture/1" && it.fixture == provider.name })
                if (hls) {
                    assertEquals(if (terminal && !permanentFailure) 1 else 2, captured.count { it.path == "segment004.m4s" })
                    assertTrue(captured.size <= 14, "Unexpected request retry loop: $captured")
                    assertEquals(if (permanentFailure) 2 else 1, loadErrors)
                } else {
                    assertEquals(3, captured.size)
                    assertTrue(captured[1].offset > 0L)
                    assertEquals(captured[1].offset, captured[2].offset)
                    assertEquals(2, loadErrors)
                }
                println("Provider integration provider=$provider hls=$hls status=$failureStatus terminal=$terminal bufferMs=$fullBufferMs " +
                    "requests=${captured.map { it.path to it.offset }} loadErrors=$loadErrors loadingStarts=$loadingStarts " +
                    "itemTransitions=$transitions rendererResets=${renderer.positionResetCount} samples=${renderer.sampleBufferReadCount}")
            } finally {
                player.release()
                dataSources.close()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }

    private fun resource(path: String): ByteArray = javaClass.getResourceAsStream(path)!!.use { it.readBytes() }
}
