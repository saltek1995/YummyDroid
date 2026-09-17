package me.yummydroid.app.ui

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import java.io.IOException
import java.net.InetAddress
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.CvhMediaRequestRecovery
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.defaultVideoPlaybackClient
import me.yummydroid.app.data.defaultVideoResolveClient
import okhttp3.Dns
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowSystemClock

/**
 * Actual wall time: real sockets, call deadlines, MP4 extraction and A/V queues.
 * Clock.DEFAULT reads Robolectric's Android clock, paced 1:1 from System.nanoTime below because
 * PAUSED mode otherwise freezes it. No FakeClock or accelerated playback. FakeRenderer consumes
 * encoded samples: hardware decoding/output, provider authentication and TV performance are absent.
 * Baseline reconstructs the v1.4.50 transport/policy settings, not that entire app revision.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class RealTimeBufferingIntegrationTest {
    @Test fun baselineTwentySecondCallDeadlineRefillsRealTimeAv() = exercise(current = false)
    @Test fun currentUnlimitedCallDeadlineRefillsRealTimeAv() = exercise(current = true)
    @Test fun currentMaximumBufferResumesAcrossLongIdle() = exercise(current = true, maximum = true)

    private data class Request(val host: String, val offset: Long, val wallMs: Long)

    private fun exercise(current: Boolean, maximum: Boolean = false) {
        val started = System.nanoTime()
        fun wallMs() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        val durationSeconds = if (maximum) 540L else 180L
        val preset = if (maximum) PlayerBufferPreset.Maximum else PlayerBufferPreset.Standard
        val media = javaClass.getResourceAsStream("/media/realtime-buffer/av-${durationSeconds}s.mp4")!!.use { it.readBytes() }
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val host = request.getHeader("Host")!!.substringBefore(':')
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull() ?: 0L
                    requests += Request(host, offset, wallMs())
                    val response = MockResponse().setResponseCode(if (offset == 0L) 200 else 206)
                        .setHeader("Content-Type", "video/mp4").setHeader("Accept-Ranges", "bytes")
                    if (offset == 0L) {
                        // About 75% of constant-bitrate A/V; beyond the maximum buffer plus the
                        // production 1MiB extraction interval. The body ends before Content-Length.
                        response.setBody(Buffer().write(media, 0, media.size * 3 / 4))
                            .setHeader("Content-Length", media.size).setSocketPolicy(SocketPolicy.DISCONNECT_AT_END)
                    } else {
                        response.setBody(Buffer().write(media, offset.toInt(), media.size - offset.toInt()))
                            .setHeader("Content-Range", "bytes $offset-${media.lastIndex}/${media.size}")
                    }
                    return response
                }
            }
            server.start()
            val base = (if (current) defaultVideoPlaybackClient() else defaultVideoResolveClient()).newBuilder()
                .dns(object : Dns { override fun lookup(hostname: String) = listOf(InetAddress.getByName("127.0.0.1")) })
                .build()
            val client = if (current) CvhMediaRequestRecovery("primary.test", "fallback.test").createClient(base) else base
            assertEquals(if (current) 0 else 20_000, client.callTimeoutMillis)
            val factory = ProgressiveMediaSource.Factory(OkHttpDataSource.Factory(client))
            // Deliberately retain Media3's default 1MiB continueLoadingCheckIntervalBytes.
            if (current) factory.setLoadErrorHandlingPolicy(PlaybackLoadErrorHandlingPolicy(PlaybackProvider.Cvh))
            val video = FakeRenderer(C.TRACK_TYPE_VIDEO)
            val audio = FakeRenderer(C.TRACK_TYPE_AUDIO)
            val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(Clock.DEFAULT)
                .setRenderers(video, audio).setMediaSourceFactory(factory)
                .setLoadControl(preset.toLoadControl()).build()
            var transitions = 0
            var loadingStarts = 0
            var rebufferCount = 0
            var playing = false
            var loadErrors = 0
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onIsLoadingChanged(isLoading: Boolean) {
                    if (isLoading) loadingStarts++
                    println("realtime current=$current wall=${wallMs()} loading=$isLoading pos=${player.currentPosition} buf=${player.totalBufferedDuration}")
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playing && playbackState == Player.STATE_BUFFERING) rebufferCount++
                }
            })
            player.addAnalyticsListener(object : AnalyticsListener {
                override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo,
                    mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
                    loadErrors++
                    println("realtime current=$current wall=${wallMs()} loadError=${error.javaClass.simpleName} stopped=$wasCanceled pos=${player.currentPosition} buf=${eventTime.totalBufferedDurationMs}")
                }
            })
            var lastTick = System.nanoTime()
            fun waitFor(condition: () -> Boolean) {
                while (!condition() && player.playerError == null) {
                    check(wallMs() < (durationSeconds + 60L) * 1_000L) { "Wall-clock test deadline exceeded" }
                    Thread.sleep(10)
                    val now = System.nanoTime()
                    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(now - lastTick)
                    if (elapsedMs > 0) {
                        ShadowSystemClock.advanceBy(Duration.ofMillis(elapsedMs))
                        lastTick += TimeUnit.MILLISECONDS.toNanos(elapsedMs)
                    }
                    shadowOf(Looper.getMainLooper()).idle()
                }
                assertNull(player.playerError)
            }
            try {
                player.setMediaItem(MediaItem.fromUri(server.url("/fixture.mp4").newBuilder().host("primary.test").build().toString()))
                player.prepare()
                waitFor { player.playbackState == Player.STATE_READY && !player.isLoading && player.totalBufferedDuration >= preset.maxBufferMs }
                val fullBuffer = player.totalBufferedDuration
                assertTrue(fullBuffer >= preset.maxBufferMs, "$preset buffer never filled: $fullBuffer")
                assertTrue(fullBuffer < if (maximum) 378_000L else 130_000L, "Body must fail after the initial fill: $fullBuffer")
                val videoResets = video.positionResetCount
                val audioResets = audio.positionResetCount
                val playAt = wallMs()
                playing = true
                player.play()
                waitFor { player.playbackState == Player.STATE_ENDED }
                assertTrue(wallMs() - playAt >= (durationSeconds - 5L) * 1_000L, "Playback was accelerated")
                assertEquals(1, transitions)
                assertEquals(videoResets, video.positionResetCount)
                assertEquals(audioResets, audio.positionResetCount)
                assertTrue(video.sampleBufferReadCount >= if (maximum) 13_490 else 4_490)
                assertTrue(audio.sampleBufferReadCount >= if (maximum) 25_300 else 8_400)
                assertTrue(loadingStarts >= if (maximum) 3 else 2)
                if (maximum) assertEquals(0, rebufferCount, "Maximum buffer must refill without draining")
                assertTrue(loadErrors >= 1)
                val captured = synchronized(requests) { requests.toList() }
                assertTrue(captured.any { it.offset > 0L }, "Media3 must resume the body using Range")
                assertTrue(captured.all { it.host == "primary.test" })
                println("realtime COMPLETE current=$current preset=$preset fullBuffer=$fullBuffer wall=${wallMs()} rebuffers=$rebufferCount videoSamples=${video.sampleBufferReadCount} audioSamples=${audio.sampleBufferReadCount}")
            } finally {
                println("realtime FINAL current=$current preset=$preset wall=${wallMs()} pos=${player.currentPosition} buffer=${player.totalBufferedDuration} state=${player.playbackState} error=${player.playerError?.errorCodeName} requests=${synchronized(requests) { requests.toList() }} loadingStarts=$loadingStarts loadErrors=$loadErrors rebuffers=$rebufferCount transitions=$transitions videoResets=${video.positionResetCount} audioResets=${audio.positionResetCount}")
                player.release()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
