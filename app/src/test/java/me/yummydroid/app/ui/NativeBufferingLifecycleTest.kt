package me.yummydroid.app.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

/** Exercises the production lifecycle listener without preparing media or using network traffic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class NativeBufferingLifecycleTest {
    private lateinit var localPlayer: ExoPlayer
    private lateinit var player: ProbePlayer
    private lateinit var harness: LifecycleHarness

    @Before
    fun setUp() {
        localPlayer = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        player = ProbePlayer(localPlayer)
        harness = LifecycleHarness()
    }

    @After
    fun tearDown() {
        harness.close()
        localPlayer.release()
    }

    @Test
    fun readyWithAdvancingBufferForSeventySecondsDoesNotReportAZeroTimeout() {
        val timeouts = mutableListOf<Long>()
        mount(timeouts)
        repeat(70) {
            player.positionMs += 1_000L
            harness.advanceSecond()
        }

        assertEquals(emptyList<Long>(), timeouts)
    }

    @Test
    fun frozenBufferTimesOutOnceAfterThreeSecondsWhenNotLoading() {
        val timeouts = mutableListOf<Long>()
        mount(timeouts)

        player.playbackStateValue = Player.STATE_BUFFERING
        player.isLoadingValue = false
        player.emitPlaybackState()
        harness.flush()
        harness.advanceSeconds(2)
        assertEquals(emptyList<Long>(), timeouts)
        harness.advanceSecond()
        assertEquals(listOf(100_000L), timeouts)
        harness.advanceSeconds(60)
        player.emitPlaybackState()
        harness.advanceSeconds(3)
        assertEquals("a reported fallback must not be scheduled again", 1, timeouts.size)
    }

    @Test
    fun frozenBufferTimesOutOnceAfterSixtySecondsWhenLoading() {
        val timeouts = mutableListOf<Long>()
        mount(timeouts)

        player.playbackStateValue = Player.STATE_BUFFERING
        player.isLoadingValue = true
        player.emitPlaybackState()
        harness.flush()
        harness.advanceSeconds(59)
        assertEquals(emptyList<Long>(), timeouts)
        harness.advanceSecond()
        assertEquals(listOf(100_000L), timeouts)
        harness.advanceSeconds(60)
        player.emitPlaybackState()
        harness.advanceSecond()
        assertEquals("a loading timeout is also single-shot", 1, timeouts.size)
    }

    @Test
    fun advancingReceivedBytesRestartsTheFullLoadingInactivityWindow() {
        val timeouts = mutableListOf<Long>()
        val bytes = mutableStateOf(0L)
        mount(timeouts, receivedBytes = { bytes.value })
        player.playbackStateValue = Player.STATE_BUFFERING
        player.isLoadingValue = true
        player.emitPlaybackState()
        harness.flush()
        repeat(70) {
            bytes.value += 1L
            harness.advanceSecond()
        }

        assertEquals(emptyList<Long>(), timeouts)
        harness.advanceSeconds(59)
        assertEquals("a prior transfer must restart the full 60-second loading window", emptyList<Long>(), timeouts)
        harness.advanceSecond()
        assertEquals(listOf(100_000L), timeouts)
    }

    @Test
    fun metadataAndCallbackUpdatesKeepListenerAndUseLatestCallback() {
        val original = mutableListOf<Long>()
        val refreshed = mutableListOf<Long>()
        val current = mutableStateOf(binding(original, metadataDurationSeconds = 600))
        harness.setContent { NativePlayerLifecycle(current.value) }
        harness.flush()
        player.emitIsPlaying(true)
        harness.flush()
        assertEquals(1, player.addCount)

        current.value = binding(refreshed, metadataDurationSeconds = 601)
        harness.flush()
        harness.flush()
        assertEquals("metadata refresh must retain the listener", 1, player.addCount)
        assertEquals(0, player.removeCount)

        player.playbackStateValue = Player.STATE_BUFFERING
        player.emitPlaybackState()
        harness.flush()
        harness.advanceSeconds(3)
        assertEquals(emptyList<Long>(), original)
        assertEquals(listOf(100_000L), refreshed)
    }

    private fun mount(timeouts: MutableList<Long>, receivedBytes: () -> Long = { 0L }) {
        harness.setContent { NativePlayerLifecycle(binding(timeouts, receivedBytes = receivedBytes)) }
        harness.flush()
        // A real start cancels the startup watchdog; these tests only cover buffering behavior.
        player.emitIsPlaying(true)
        harness.flush()
    }

    private fun binding(
        timeouts: MutableList<Long>,
        metadataDurationSeconds: Int = 600,
        receivedBytes: () -> Long = { 0L },
    ) = NativePlayerLifecycleBinding(
        player = player,
        localPlayer = localPlayer,
        stream = ResolvedVideoStream("https://fixture.invalid/paused.mp4", null, emptyMap()),
        videoId = 1L,
        pipPlayerHandle = object : PipPlayerHandle {
            override val isPlaying = false
            override fun play() = Unit
            override fun pause() = Unit
        },
        metadataDurationSeconds = metadataDurationSeconds,
        state = NativePlayerEventState(
            playerView = { null },
            settings = { AppSettings() },
            qualityOptions = { emptyList() },
            selectedQualityKey = { null },
            playbackPreferredQuality = { PreferredQuality.Auto },
            streamSelectedQualityKey = { null },
            fallbackSuppressedUntilMs = { 0L },
            onFallbackSuppressedUntilChanged = {},
            skipControlsTimelineReady = { true },
            onSkipControlsTimelineReady = {},
            onPlaybackReady = {},
            onTracksChanged = {},
            onSelectedSubtitleKeyChanged = {},
            onSelectedQualityKeyChanged = {},
        ),
        callbacks = NativePlayerEventCallbacks(
            onPlaybackStarted = {},
            onPlaybackEnded = {},
            onBufferingTimeout = timeouts::add,
            onAutoAdvance = {},
            onPlaybackError = { _, _ -> },
            onProgressSnapshot = { _, _ -> },
            onDisplayModeUpdate = {},
            onDispose = {},
        ),
        receivedNetworkBytes = receivedBytes,
    )

    private class ProbePlayer(local: ExoPlayer) : ForwardingPlayer(local) {
        private val listeners = linkedSetOf<Player.Listener>()
        var addCount = 0
            private set
        var removeCount = 0
            private set
        var playbackStateValue = Player.STATE_READY
        var positionMs = 100_000L
        var bufferedPositionMs = 170_000L
        var isLoadingValue = false
        var playWhenReadyValue = true

        override fun addListener(listener: Player.Listener) {
            addCount++
            listeners += listener
        }

        override fun removeListener(listener: Player.Listener) {
            removeCount++
            listeners -= listener
        }

        override fun getPlaybackState() = playbackStateValue
        override fun getCurrentPosition() = positionMs
        override fun getBufferedPosition() = bufferedPositionMs
        override fun getDuration() = 600_000L
        override fun getContentDuration() = 600_000L
        override fun getPlayWhenReady() = playWhenReadyValue
        override fun isLoading() = isLoadingValue
        override fun getPlaybackSuppressionReason() = Player.PLAYBACK_SUPPRESSION_REASON_NONE

        fun emitPlaybackState() = listeners.toList().forEach { it.onPlaybackStateChanged(playbackStateValue) }
        fun emitIsPlaying(isPlaying: Boolean) = listeners.toList().forEach { it.onIsPlayingChanged(isPlaying) }
    }

    private class LifecycleHarness {
        private val frameClock = BroadcastFrameClock()
        private val scope = CoroutineScope(Dispatchers.Main.immediate + frameClock)
        private val recomposer = Recomposer(scope.coroutineContext)
        private val composition = Composition(UnitApplier(), recomposer)
        private val recomposeJob: Job = scope.launch { recomposer.runRecomposeAndApplyChanges() }

        fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
            composition.setContent(content)
        }

        fun advanceSecond() {
            shadowOf(android.os.Looper.getMainLooper()).idleFor(1, TimeUnit.SECONDS)
            flush()
        }

        fun advanceSeconds(seconds: Int) = repeat(seconds) { advanceSecond() }

        fun flush() = runBlocking {
            Snapshot.sendApplyNotifications()
            shadowOf(android.os.Looper.getMainLooper()).idle()
            frameClock.sendFrame(System.nanoTime())
            shadowOf(android.os.Looper.getMainLooper()).idle()
        }

        fun close() {
            composition.dispose()
            recomposer.cancel()
            recomposeJob.cancel()
            scope.cancel()
        }
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
