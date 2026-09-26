package me.yummydroid.app.ui

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import me.yummydroid.app.data.DeferredPlaybackSubtitle
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real Media3 audio queues and text decoding; all media is local test data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeferredSubtitlePlaybackTest {
    @Test fun disabledCaptionsNeverStartResolution() {
        val requests = AtomicInteger()
        withPlayer(DeferredPlaybackSubtitle { requests.incrementAndGet(); null }, disabled = true) { fixture ->
            fixture.player.prepare()
            fixture.await { fixture.player.playbackState == Player.STATE_READY }
            fixture.player.play()
            fixture.await { fixture.player.playbackState == Player.STATE_ENDED }
            assertEquals(0, requests.get())
            assertTrue(fixture.audio.sampleBufferReadCount > 100)
        }
    }

    @Test fun slowSelectedCaptionDoesNotBlockAudioAndAppearsWithoutPlaybackReset() {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String?>()
        val vtt = File.createTempFile("deferred-caption", ".vtt").apply {
            writeText("WEBVTT\n\n00:00:00.000 --> 00:00:19.000\nDeferred caption arrived\n\n")
        }
        try {
            withPlayer(DeferredPlaybackSubtitle { started.complete(Unit); response.await() }) { fixture ->
                fixture.player.prepare()
                fixture.await { fixture.player.playbackState == Player.STATE_READY && started.isCompleted }
                val resets = fixture.audio.positionResetCount
                val transitions = fixture.transitions
                val discontinuities = fixture.discontinuities
                fixture.player.play()
                fixture.await { fixture.player.currentPosition >= 1_000L }
                assertTrue(!response.isCompleted)
                assertTrue(fixture.audio.sampleBufferReadCount > 0)
                assertTrue(fixture.cues.isEmpty())
                response.complete(vtt.toURI().toString())
                fixture.await { fixture.cues.any { it == "Deferred caption arrived" } }
                assertTrue(fixture.player.currentPosition >= 1_000L)
                assertEquals(resets, fixture.audio.positionResetCount)
                assertEquals(transitions, fixture.transitions)
                assertEquals(discontinuities, fixture.discontinuities)
            }
        } finally {
            response.cancel()
            vtt.delete()
        }
    }

    @Test fun absentOptionalCaptionDoesNotFailAudio() = optionalCaptionCompletes(null)

    @Test fun missingCachedOptionalCaptionDoesNotFailAudio() =
        optionalCaptionCompletes("file:///nonexistent-yummydroid-caption.vtt")

    @Test fun releasingPlayerCancelsSuspendedCaption() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        withPlayer(DeferredPlaybackSubtitle {
            started.complete(Unit)
            try { CompletableDeferred<String?>().await() } finally { cancelled.complete(Unit) }
        }) { fixture ->
            fixture.player.prepare()
            fixture.await { started.isCompleted && fixture.player.playbackState == Player.STATE_READY }
            fixture.release()
            fixture.await { cancelled.isCompleted }
        }
    }

    private fun optionalCaptionCompletes(uri: String?) {
        val requests = AtomicInteger()
        withPlayer(DeferredPlaybackSubtitle { requests.incrementAndGet(); uri }) { fixture ->
            fixture.player.prepare()
            fixture.await { fixture.player.playbackState == Player.STATE_READY }
            fixture.player.play()
            fixture.await { fixture.player.playbackState == Player.STATE_ENDED }
            assertEquals(1, requests.get())
            assertTrue(fixture.cues.isEmpty())
            assertTrue(fixture.audio.sampleBufferReadCount > 100)
        }
    }

    private class Fixture(val player: ExoPlayer, val clock: FakeClock, val audio: FakeRenderer,
        val cues: MutableList<String>) {
        var transitions = 0
        var discontinuities = 0
        private var released = false
        init {
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo, reason: Int) { discontinuities++ }
            })
        }
        fun await(condition: () -> Boolean) {
            RobolectricUtil.runMainLooperUntil {
                assertNull(player.playerError)
                if (condition()) true else {
                    clock.advanceTime(10L)
                    Thread.sleep(1L)
                    false
                }
            }
        }
        fun release() {
            if (!released) { released = true; player.release() }
        }
    }

    private fun withPlayer(subtitle: DeferredPlaybackSubtitle, disabled: Boolean = false,
        action: (Fixture) -> Unit) {
        val bytes = javaClass.getResourceAsStream("/media/cvh-20s-aac.m4a")!!.use { it.readBytes() }
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path != "/audio.m4a") return MockResponse().setResponseCode(404)
                    val offset = request.getHeader("Range")?.substringAfter("bytes=")
                        ?.substringBefore('-')?.toIntOrNull() ?: 0
                    return MockResponse().setResponseCode(if (offset > 0) 206 else 200)
                        .setHeader("Content-Type", "audio/mp4")
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                        .setBody(Buffer().write(bytes, offset, bytes.size - offset))
                }
            }
            server.start()
            val client = OkHttpClient()
            val stream = ResolvedVideoStream(server.url("/audio.m4a").toString(), "audio/mp4", emptyMap(),
                subtitles = listOf(ResolvedSubtitleTrack(server.url("/deferred.vtt").toString(), "English", "en",
                    "text/vtt", deferredLoad = subtitle)))
            val dataSources = StreamHttpDataSourceFactory(client, stream)
            val clock = FakeClock(false)
            val audio = FakeRenderer(C.TRACK_TYPE_AUDIO)
            val cues = mutableListOf<String>()
            val text = TextRenderer(object : TextOutput {
                override fun onCues(cueGroup: CueGroup) {
                    cues += cueGroup.cues.mapNotNull { it.text?.toString() }
                }
            }, Looper.getMainLooper())
            val player = TestExoPlayerBuilder(RuntimeEnvironment.getApplication())
                .setClock(clock).setRenderers(audio, text)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources)).build()
            val fixture = Fixture(player, clock, audio, cues)
            try {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled).setPreferredTextLanguage("en").build()
                player.setMediaItem(stream.toMediaItem())
                action(fixture)
                assertNull(player.playerError)
            } finally {
                fixture.release()
                dataSources.close()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
            }
        }
    }
}
