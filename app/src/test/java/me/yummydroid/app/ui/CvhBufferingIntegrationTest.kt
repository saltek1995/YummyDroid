package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import java.io.IOException
import java.net.InetAddress
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.CvhMediaRequestRecovery
import me.yummydroid.app.data.PlaybackProvider
import okhttp3.Dns
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
 * Real Media3 loader/extractor/sample queues with a fake renderer and player clock.
 * Baseline means default transport/load-error policy, not a reconstruction of an earlier app version.
 * No wall-clock socket-idle timeout, hardware decoder or Compose/player recreation is simulated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CvhBufferingIntegrationTest {
    @Test
    fun initialForbiddenHostRecoversWithinOneMediaItem() = exercise(closeBodyWhileBuffered = false)

    @Test
    fun closedPrimaryConnectionAfterBufferFillResumesWithoutResettingRendererQueues() =
        exercise(closeBodyWhileBuffered = true)

    @Test
    fun baselineDefaultTransportAlsoResumesClosedBodyWithoutResettingRendererQueues() =
        exercise(closeBodyWhileBuffered = true, withRecovery = false)

    @Test
    fun unknownLengthCleanEofRetriesThroughExtractorWithoutRendererReset() =
        exercise(closeBodyWhileBuffered = true, cleanEof = true)

    @Test
    fun baselineDefaultTransportHasSameUnknownLengthCleanEofBehavior() =
        exercise(closeBodyWhileBuffered = true, withRecovery = false, cleanEof = true)

    @Test
    fun resumedPrimaryMustRemainUsableWhenBodyDisconnectsAndAdvertisedBackupRejects() =
        exercise(closeBodyWhileBuffered = true, brokenBackup = true)

    @Test
    fun baselineDefaultTransportResumesPrimaryDespiteBrokenAdvertisedBackup() =
        exercise(closeBodyWhileBuffered = true, withRecovery = false, brokenBackup = true)

    @Test
    fun primaryResumeForbiddenStillUsesAdvertisedBackupWithoutResettingQueues() =
        exercise(closeBodyWhileBuffered = true, rejectPrimaryResume = true)

    @Test
    fun transientPrimaryResumeFailureMustNotStickToRejectingBackup() =
        exercise(closeBodyWhileBuffered = true, brokenBackup = true, transientPrimaryResumeFailure = true)

    @Test
    fun baselineRetriesTransientPrimaryResumeDespiteRejectingAdvertisedBackup() =
        exercise(closeBodyWhileBuffered = true, withRecovery = false, brokenBackup = true, transientPrimaryResumeFailure = true)

    private fun exercise(closeBodyWhileBuffered: Boolean, withRecovery: Boolean = true, cleanEof: Boolean = false,
        brokenBackup: Boolean = false, rejectPrimaryResume: Boolean = false, transientPrimaryResumeFailure: Boolean = false) {
        // Synthetic fixture: ffmpeg -f lavfi -i sine=frequency=440:sample_rate=44100:duration=20
        // -c:a aac -b:a 64k -movflags +faststart -fflags +bitexact -flags:a +bitexact -map_metadata -1 output.m4a
        val bytes = javaClass.getResourceAsStream("/media/cvh-20s-aac.m4a")!!.use { it.readBytes() }
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val requestTimes = Collections.synchronizedList(mutableListOf<Long>())
        val clock = FakeClock(true)
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val host = request.getHeader("Host")!!.substringBefore(':')
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")
                        ?.substringBefore('-')?.toLongOrNull() ?: 0L
                    requests += host to offset
                    requestTimes += clock.elapsedRealtime()
                    if (host == "fallback.test" && brokenBackup) {
                        return MockResponse().setResponseCode(403).setBody("fixture backup rejects signed context")
                    }
                    if (host == "primary.test" && !closeBodyWhileBuffered) {
                        return MockResponse().setResponseCode(403).setBody("temporary edge failure")
                    }
                    if (host == "primary.test" && offset > 0L && transientPrimaryResumeFailure &&
                        synchronized(requests) { requests.count { it.first == "primary.test" && it.second > 0L } } == 1) {
                        return MockResponse().setResponseCode(500).setBody("fixture transient primary resume failure")
                    }
                    if (host == "primary.test" && offset > 0L && rejectPrimaryResume) {
                        return MockResponse().setResponseCode(403).setBody("fixture primary rejects resume")
                    }
                    val response = MockResponse().setResponseCode(if (offset > 0) 206 else 200)
                        .setHeader("Content-Type", "audio/mp4")
                        .setHeader("Accept-Ranges", "bytes")
                        .setBody(Buffer().write(bytes, offset.toInt(), bytes.size - offset.toInt()))
                    if (offset > 0) response.setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                    if (host == "primary.test" && closeBodyWhileBuffered && offset == 0L) {
                        if (cleanEof) {
                            // Valid terminating HTTP chunk, but an incomplete MP4 sample/container.
                            // This failure is detected by Media3's extractor, above OkHttp's body.
                            response.setChunkedBody(Buffer().write(bytes, 0, bytes.size / 2), 4_096)
                        } else {
                            response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                        }
                    }
                    return response
                }
            }
            server.start()
            val baseClient = OkHttpClient.Builder()
                .dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1"))
                })
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val client = if (withRecovery) CvhMediaRequestRecovery("primary.test", "fallback.test")
                .createClient(baseClient) else baseClient
            val renderer = FakeRenderer(C.TRACK_TYPE_AUDIO)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_000, 4_000, 250, 500)
                .setPrioritizeTimeOverSizeThresholds(true).build()
            val factory = ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(client))
                .setContinueLoadingCheckIntervalBytes(4_096)
            if (withRecovery) factory.setLoadErrorHandlingPolicy(PlaybackLoadErrorHandlingPolicy(PlaybackProvider.Cvh))
            val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication())
                .setClock(clock).setRenderers(renderer).setLoadControl(loadControl)
                .setMediaSourceFactory(factory).build()
            var transitions = 0
            var discontinuities = 0
            var loadErrors = 0
            var loadingStarts = 0
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    discontinuities++
                }
                override fun onIsLoadingChanged(isLoading: Boolean) { if (isLoading) loadingStarts++ }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) { loadErrors++ }
            })
            try {
                val uri = server.url("/signed/audio.m4a?token=local-fixture").newBuilder().host("primary.test").build()
                player.setMediaItem(MediaItem.fromUri(uri.toString()))
                player.prepare()
                TestPlayerRunHelper.advance(player).ignoringNonFatalErrors().untilState(Player.STATE_READY)
                TestPlayerRunHelper.advance(player).ignoringNonFatalErrors().untilLoadingIs(false)
                val fullBufferMs = player.totalBufferedDuration
                assertTrue(fullBufferMs >= 3_000L, "Expected full bounded buffer, got $fullBufferMs")
                assertTrue(fullBufferMs < 15_000L, "Fixture must not load entirely before the recovery exercise")
                val queueResetCount = renderer.positionResetCount
                val initialTransitions = transitions
                val initialDiscontinuities = discontinuities
                // Only Media3's clock advances. MockWebServer explicitly closed/truncated the body;
                // this does not reproduce a real 30-second OkHttp/TCP idle timeout.
                clock.advanceTime(30_000L)
                TestPlayerRunHelper.advance(player).untilPendingCommandsAreFullyHandled()
                assertEquals(0L, player.currentPosition)
                TestPlayerRunHelper.play(player).ignoringNonFatalErrors().untilPositionAtLeast(12_000L)
                assertEquals(queueResetCount, renderer.positionResetCount)
                assertEquals(initialTransitions, transitions)
                assertEquals(initialDiscontinuities, discontinuities)
                TestPlayerRunHelper.play(player).ignoringNonFatalErrors().untilState(Player.STATE_ENDED)
                assertNull(player.playerError)
                assertEquals(1, transitions)
                assertEquals(queueResetCount, renderer.positionResetCount)
                assertTrue(renderer.sampleBufferReadCount > 800, "Real extractor samples must reach renderer queues")
                assertTrue(loadingStarts >= 2, "Playback must pass a full-buffer stop and later refill")
                val captured = synchronized(requests) { requests.toList() }
                assertEquals("primary.test", captured.first().first)
                val expectedResumeHost = if (withRecovery && (!closeBodyWhileBuffered || rejectPrimaryResume))
                    "fallback.test" else "primary.test"
                assertEquals(expectedResumeHost, captured.last().first)
                if (closeBodyWhileBuffered) {
                    assertTrue(loadErrors >= 1, "Body disconnect must reach Media3 loader, not only the interceptor")
                    assertTrue(captured.any { it.first == expectedResumeHost && it.second > 0L },
                        "Media3 must resume with a nonzero byte range")
                }
                if (rejectPrimaryResume) {
                    assertEquals(listOf("primary.test" to 0L, "primary.test" to captured[1].second,
                        "fallback.test" to captured[1].second), captured)
                    assertTrue(captured[1].second > 0L)
                }
                if (transientPrimaryResumeFailure) {
                    val expectedHosts = if (withRecovery) listOf("primary.test", "primary.test", "fallback.test", "primary.test")
                        else listOf("primary.test", "primary.test", "primary.test")
                    assertEquals(expectedHosts, captured.map { it.first })
                    assertTrue(captured.drop(1).all { it.second == captured[1].second && it.second > 0L })
                    val times = synchronized(requestTimes) { requestTimes.toList() }
                    assertTrue(times.last() - times[times.lastIndex - 1] >= 1_000L,
                        "Media3 must pace the next Range request after the failed open: $times")
                }
                println("CVH integration recovery=$withRecovery cleanEof=$cleanEof bodyFailure=$closeBodyWhileBuffered bufferMs=$fullBufferMs " +
                    "requests=$captured loadErrors=$loadErrors loadingStarts=$loadingStarts " +
                    "itemTransitions=$transitions rendererPositionResets=${renderer.positionResetCount} " +
                    "samples=${renderer.sampleBufferReadCount}")
            } finally {
                if (brokenBackup) {
                    println("CVH broken-backup candidate recovery=$withRecovery requests=" +
                        synchronized(requests) { requests.toList() } +
                        " requestTimes=${synchronized(requestTimes) { requestTimes.toList() }}" +
                        " positionMs=${player.currentPosition} bufferedMs=${player.totalBufferedDuration}" +
                        " state=${player.playbackState} error=${player.playerError?.errorCodeName}" +
                        " loadErrors=$loadErrors itemTransitions=$transitions rendererResets=${renderer.positionResetCount}")
                }
                player.release()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
