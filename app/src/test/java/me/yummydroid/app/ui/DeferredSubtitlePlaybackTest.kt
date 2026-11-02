package me.yummydroid.app.ui

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.TestExoPlayerBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import me.yummydroid.app.data.DeferredPlaybackSubtitle
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import okhttp3.OkHttpClient
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
                assertEquals(1, fixture.player.currentMediaItem!!.localConfiguration!!.subtitleConfigurations.size)
                val updated = fixture.player.currentMediaItem!!.buildUpon()
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("Updated while captions load").build()).build()
                fixture.player.replaceMediaItem(0, updated)
                fixture.await { fixture.player.currentMediaItem?.mediaMetadata?.title == "Updated while captions load" }
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
                assertEquals(updated, fixture.player.currentMediaItem)
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
        repeat(20) { verifyReleaseCancelsActiveCaption() }
    }

    private fun verifyReleaseCancelsActiveCaption() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        withPlayer(DeferredPlaybackSubtitle {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally { cancelled.complete(Unit) }
        }) { fixture ->
            fixture.player.prepare()
            fixture.await { started.isCompleted }
            assertFalse(cancelled.isCompleted)
            fixture.release()
            fixture.await { cancelled.isCompleted }
        }
    }

    @Test fun httpFactoryRoutesDeferredCaptionsWithoutOpeningTheNetwork() {
        val requests = AtomicInteger()
        val uri = "https://fixture.invalid/deferred.vtt"
        val subtitle = DeferredPlaybackSubtitle { requests.incrementAndGet(); null }
        val client = OkHttpClient.Builder().addInterceptor { error("Deferred captions must not open the network") }.build()
        val stream = ResolvedVideoStream("https://fixture.invalid/audio.m4a", "audio/mp4", emptyMap(),
            subtitles = listOf(ResolvedSubtitleTrack(uri, "English", "en", "text/vtt", deferredLoad = subtitle)))
        val factory = StreamHttpDataSourceFactory(client, stream)
        val source = factory.createDataSource()
        try {
            assertEquals(0, requests.get())
            assertEquals(8L, source.open(DataSpec.Builder().setUri(uri).build()))
            val bytes = ByteArray(8)
            assertEquals(8, source.read(bytes, 0, bytes.size))
            assertEquals("WEBVTT\n\n", bytes.decodeToString())
            assertEquals(1, requests.get())
        } finally {
            source.close()
            factory.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
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
            try {
                advancePlaybackTestClockUntil(clock, stepMs = 10L) {
                    assertNull(player.playerError)
                    condition()
                }
            } catch (timeout: java.util.concurrent.TimeoutException) {
                throw AssertionError("Playback stalled: state=${player.playbackState}, loading=${player.isLoading}, " +
                    "buffered=${player.bufferedPosition}, samples=${audio.sampleBufferReadCount}, tracks=${player.currentTracks}", timeout)
            }
        }
        fun release() {
            if (!released) { released = true; player.release() }
        }
    }

    private fun withPlayer(subtitle: DeferredPlaybackSubtitle, disabled: Boolean = false,
        action: (Fixture) -> Unit) {
        val bytes = javaClass.getResourceAsStream("/media/cvh-20s-aac.m4a")!!.use { it.readBytes() }
        val subtitleUri = "memory://fixture/deferred.vtt"
        val stream = ResolvedVideoStream("memory://fixture/audio.m4a", "audio/mp4", emptyMap(),
            subtitles = listOf(ResolvedSubtitleTrack(subtitleUri, "English", "en",
                "text/vtt", deferredLoad = subtitle)))
        // Keep real Media3 extraction/rendering and production deferred loading, without socket scheduling.
        val dataSources = DataSource.Factory {
            DeferredSubtitleDataSource(ByteArrayDataSource(bytes), mapOf(subtitleUri to subtitle))
        }
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
            .setMediaSourceFactory(SubtitleMediaSourceFactory(dataSources)).build()
        val fixture = Fixture(player, clock, audio, cues)
        try {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled).setPreferredTextLanguage("en").build()
            player.setMediaItem(stream.toMediaItem())
            action(fixture)
            assertNull(player.playerError)
        } finally {
            fixture.release()
        }
    }
}
