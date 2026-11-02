package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Looper
import android.view.Display
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.internal.DisplayConfig
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, qualifiers = "television")
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class DisplayModePlayerTest {
    private lateinit var local: ExoPlayer
    private lateinit var raw: ProbePlayer
    private lateinit var scope: CoroutineScope
    private lateinit var player: DisplayModePlayer
    private val switches = mutableListOf<CompletableDeferred<Unit>>()
    private var enabled = true
    private lateinit var matcher: suspend (Player) -> Unit

    @Before fun setUp() {
        local = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        raw = ProbePlayer(local)
        scope = CoroutineScope(Dispatchers.Main.immediate + Job())
        matcher = {
            CompletableDeferred<Unit>().also(switches::add).await()
        }
        player = DisplayModePlayer(raw, scope, { enabled }) { matcher(it) }
    }

    @After fun tearDown() {
        scope.cancel()
        player.release()
    }

    @Test fun autoplayAndFirstFrameWaitUntilTheDisplayHandoffFinishes() {
        var frames = 0
        val intents = mutableListOf<Boolean>()
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { frames++ }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { intents += playWhenReady }
        })
        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()

        assertTrue(player.preparingDisplay)
        assertTrue("controls must retain autoplay intent", player.playWhenReady)
        assertFalse("audio must stay paused during the mode switch", raw.playWhenReady)
        assertFalse(player.isPlaying)
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
        assertEquals(0, frames)
        assertFalse("the internal hold must not look like a user pause", false in intents)

        switches.single().complete(Unit)
        flush()
        assertFalse(player.preparingDisplay)
        assertFalse("audio must wait until the uncovered view is drawn", raw.playWhenReady)
        assertTrue("the presentation wait must retain autoplay intent", player.playWhenReady)
        drawPresentation()
        assertTrue(raw.playWhenReady)
        assertTrue(player.isPlaying)
        assertEquals(1, frames)
    }

    @Test fun pauseDuringTheHandoffCancelsAutoplayAndAPlayRequestCanRestoreIt() {
        load("one")
        raw.ready()
        player.pause()
        flush()
        assertFalse(player.playWhenReady)
        assertFalse(raw.playWhenReady)
        switches.single().complete(Unit)
        flush()
        drawPresentation()
        assertFalse("handoff completion must respect Pause", raw.playWhenReady)

        load("two", autoplay = false)
        raw.ready()
        player.play()
        assertTrue(player.playWhenReady)
        assertFalse(raw.playWhenReady)
        switches.last().complete(Unit)
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun replacingAnEpisodeCancelsItsOldHandoffWithoutStartingTheNewOne() {
        load("one")
        val first = switches.single()
        load("two")
        raw.ready()
        first.complete(Unit)
        flush()
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        assertEquals("two", player.currentMediaItem?.mediaId)

        switches.last().complete(Unit)
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
        assertFalse(player.preparingDisplay)
    }

    @Test fun disablingMatchingFinishesTheCurrentSwitchAndSubsequentLoadsOnlyWaitForPresentation() {
        load("one")
        raw.ready()
        enabled = false
        player.updateDisplayMatching()
        flush()
        assertTrue("an in-flight mode change must settle before playback", player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        switches.single().complete(Unit)
        flush()
        assertFalse(player.preparingDisplay)
        drawPresentation()
        assertTrue(raw.playWhenReady)

        load("two")
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        raw.ready()
        flush()
        assertFalse(player.preparingDisplay)
        drawPresentation()
        assertTrue(raw.playWhenReady)
        assertEquals(1, switches.size)
    }

    @Test fun stoppingWhileTheDisplayIsSwitchingCannotRestartPlayback() {
        load("one")
        val switch = switches.single()
        player.stop()
        switch.complete(Unit)
        flush()
        assertFalse(player.preparingDisplay)
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertFalse(raw.playWhenReady)
    }

    @Test fun aPreparationFailureDoesNotTurnAutoplayIntoAUserPause() {
        val intents = mutableListOf<Boolean>()
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { intents += playWhenReady }
        })
        load("one")
        raw.stop() // ExoPlayer also becomes IDLE after a preparation error.
        switches.single().complete(Unit)
        flush()
        assertEquals(Player.STATE_IDLE, player.playbackState)
        assertTrue(player.playWhenReady)
        assertFalse("recovery must not receive the gate's temporary pause", false in intents)
    }

    @Test fun theReportedDisplayModeMustChangeBeforeAudioStarts() = withTvActivity { activity ->
        @Suppress("DEPRECATION") val display = activity.windowManager.defaultDisplay
        val current = display.mode
        val target = mode(current.modeId + 100, current.physicalWidth, current.physicalHeight, 24f)
        ShadowDisplayManager.setSupportedModes(display.displayId, current, target)
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(target.physicalWidth).setHeight(target.physicalHeight).setFrameRate(24f).build()
        matcher = { activity.prepareVideoDisplayMode(it) }

        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        assertEquals(target.modeId, activity.window.attributes.preferredDisplayModeId)
        assertTrue(player.preparingDisplay)
        assertFalse("requesting a mode is not confirmation of the switch", raw.playWhenReady)

        changeMode(display.displayId, target.modeId)
        flush()
        assertFalse("surface settling must also finish before audio starts", raw.playWhenReady)
        shadowOf(Looper.getMainLooper()).idleFor(300L, TimeUnit.MILLISECONDS)
        assertFalse(player.preparingDisplay)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun anAlreadyMatchingDisplayDoesNotAddASwitchDelay() = withTvActivity { activity ->
        @Suppress("DEPRECATION") val mode = activity.windowManager.defaultDisplay.mode
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(mode.physicalWidth).setHeight(mode.physicalHeight).setFrameRate(mode.refreshRate).build()
        matcher = { activity.prepareVideoDisplayMode(it) }

        repeat(2) { episode ->
            load("episode-$episode")
            assertFalse(raw.playWhenReady)
            raw.ready()
            raw.renderFirstFrame()
            flush()
            assertFalse(player.preparingDisplay)
            drawPresentation()
            assertTrue(raw.playWhenReady)
            assertEquals(mode.modeId, activity.window.attributes.preferredDisplayModeId)
        }
    }

    @Test fun anUnacknowledgedModeRequestTimesOutWithoutHangingOrLeavingALateRequest() = withTvActivity { activity ->
        @Suppress("DEPRECATION") val display = activity.windowManager.defaultDisplay
        val current = display.mode
        val target = mode(current.modeId + 100, current.physicalWidth, current.physicalHeight, 24f)
        ShadowDisplayManager.setSupportedModes(display.displayId, current, target)
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(target.physicalWidth).setHeight(target.physicalHeight).setFrameRate(24f).build()
        matcher = { activity.prepareVideoDisplayMode(it) }

        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        shadowOf(Looper.getMainLooper()).idleFor(3_000L, TimeUnit.MILLISECONDS)
        assertFalse(player.preparingDisplay)
        drawPresentation()
        assertTrue(raw.playWhenReady)
        assertEquals(current.modeId, activity.window.attributes.preferredDisplayModeId)
    }

    @Test fun missingVideoMetadataPreservesTheModeUntilMatchingIsDisabled() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            shadowOf(activity.getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = 42 }
            activity.applyVideoDisplayMode(enabled = true, video = null)
            assertEquals(42, activity.window.attributes.preferredDisplayModeId)
            activity.applyVideoDisplayMode(enabled = true, video = VideoDisplayInfo(0, 1080, 24f))
            assertEquals(42, activity.window.attributes.preferredDisplayModeId)
            activity.applyVideoDisplayMode(enabled = false, video = null)
            assertEquals(0, activity.window.attributes.preferredDisplayModeId)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun readyAudioWaitsForVideoAndTheUncoveredDrawEvenWhenMatchingIsDisabled() {
        enabled = false
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("first-open")
        raw.ready() // Audio is ready, but the SurfaceView has not shown its first video frame.
        flush()
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        assertNull(player.presentationToken)
        assertTrue(switches.isEmpty())

        raw.renderFirstFrame()
        flush()
        assertFalse(player.preparingDisplay)
        assertNotNull(player.presentationToken)
        assertFalse("a decoded frame does not confirm removal of the black UI cover", raw.playWhenReady)
        assertTrue(player.playWhenReady)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun aDrawForThePreviousEpisodeCannotReleaseTheNewEpisodesAudio() {
        enabled = false
        load("one")
        raw.ready()
        flush()
        val oldToken = requireNotNull(player.presentationToken)
        load("two")
        player.acknowledgeVideoPresentation(oldToken)
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        raw.ready()
        flush()
        player.acknowledgeVideoPresentation(oldToken)
        assertFalse(raw.playWhenReady)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun pauseWhileTheUncoveredFrameIsAwaitingDrawMustBeRespected() {
        enabled = false
        load("one")
        raw.ready()
        flush()
        assertNotNull(player.presentationToken)
        player.pause()
        drawPresentation()
        assertFalse(raw.playWhenReady)
        assertFalse(player.playWhenReady)
    }

    @Test fun replacingTheSurfaceHoldsPlaybackUntilItsNewFirstFrameAndDraw() {
        enabled = false
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
        val output = android.view.SurfaceView(RuntimeEnvironment.getApplication())
        player.setVideoSurfaceView(output)
        flush()
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        assertNull(player.presentationToken)
        raw.renderFirstFrame()
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun enablingMatchingOnTheSameSurfaceReusesItsAlreadyRenderedFrame() {
        enabled = false
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        drawPresentation()
        enabled = true
        matcher = {} // Current display already matches; no new surface or frame event follows.
        player.updateDisplayMatching()
        flush()
        assertFalse(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun lateSubtitleMetadataCannotReplaceMediaWhilePresentationIsHeld() {
        enabled = false
        load("one")
        raw.ready()
        flush()
        val current = requireNotNull(player.currentMediaItem)
        val replacement = current.buildUpon().setMediaId("metadata-update").build()
        assertTrue("physical pause is not a user pause", player.playWhenReady)
        assertFalse(player.updateCurrentMediaItemIfSameVideo(replacement))
        assertEquals(current, player.currentMediaItem)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun readinessLostBeforeDrawingCannotStartAudio() {
        enabled = false
        load("one")
        raw.ready()
        flush()
        val token = requireNotNull(player.presentationToken)
        raw.prepare() // Renderer/source is rebuffering before the posted draw acknowledgement.
        player.acknowledgeVideoPresentation(token)
        flush()
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        raw.ready()
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun aDrawForThePreviousSurfaceCannotReleasePlayback() {
        enabled = false
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        val oldToken = requireNotNull(player.presentationToken)
        player.setVideoSurfaceView(android.view.SurfaceView(RuntimeEnvironment.getApplication()))
        flush()
        player.acknowledgeVideoPresentation(oldToken)
        assertFalse(raw.playWhenReady)
        assertTrue(player.preparingDisplay)
        raw.renderFirstFrame()
        flush()
        player.acknowledgeVideoPresentation(oldToken)
        assertFalse(raw.playWhenReady)
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun subtitleMetadataForAPausedLoadWaitsForPresentationAndThenApplies() {
        enabled = false
        load("one", autoplay = false)
        raw.ready()
        flush()
        val replacement = requireNotNull(player.currentMediaItem).buildUpon().setMediaId("updated").build()
        var applied: Boolean? = null
        scope.launch { applied = player.updateResolvedSubtitleMediaItemWhenReady(replacement) }
        flush()
        assertNull(applied)
        assertFalse(raw.playWhenReady)
        drawPresentation()
        assertEquals(true, applied)
        assertEquals("updated", player.currentMediaItem?.mediaId)
        assertFalse(raw.playWhenReady)
    }

    @Test fun surfaceChangesDuringMatchingKeepTheSameSequentialDisplayOperation() {
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("one")
        raw.ready()
        raw.renderFirstFrame()
        flush()
        player.setVideoSurfaceView(android.view.SurfaceView(RuntimeEnvironment.getApplication()))
        flush()
        assertEquals(1, switches.size)
        switches.single().complete(Unit)
        flush()
        assertTrue(player.preparingDisplay)
        assertFalse(raw.playWhenReady)
        raw.renderFirstFrame()
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    @Test fun aBatchedFrameEventFromThePreviousSurfaceCannotMarkTheNewOneReady() {
        raw.videoFormat = Format.Builder().setSampleMimeType("video/avc")
            .setWidth(1920).setHeight(1080).setFrameRate(24f).build()
        load("one")
        raw.ready()
        raw.renderFirstFrame(dispatchEvents = false)
        player.setVideoSurfaceView(android.view.SurfaceView(RuntimeEnvironment.getApplication()))
        raw.flushFrameEvents()
        switches.single().complete(Unit)
        flush()
        assertTrue(player.preparingDisplay)
        assertNull(player.presentationToken)
        assertFalse(raw.playWhenReady)
        raw.renderFirstFrame()
        flush()
        drawPresentation()
        assertTrue(raw.playWhenReady)
    }

    private fun load(id: String, autoplay: Boolean = true) {
        player.prepareMediaItemForPlayback(MediaItem.Builder().setMediaId(id).setUri("file:///fixture/$id.mp4").build(), 0L, autoplay)
        flush()
    }

    private fun drawPresentation() {
        val token = requireNotNull(player.presentationToken)
        player.acknowledgeVideoPresentation(token)
        flush()
    }

    private fun flush() = shadowOf(Looper.getMainLooper()).idle()

    private fun withTvActivity(test: (Activity) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            shadowOf(activity.getSystemService(UiModeManager::class.java)).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)
            test(activity)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun mode(id: Int, width: Int, height: Int, rate: Float): Display.Mode = ReflectionHelpers.callConstructor(
        Display.Mode::class.java, ClassParameter.from(Int::class.javaPrimitiveType, id),
        ClassParameter.from(Int::class.javaPrimitiveType, width), ClassParameter.from(Int::class.javaPrimitiveType, height),
        ClassParameter.from(Float::class.javaPrimitiveType, rate),
    )

    private fun changeMode(displayId: Int, modeId: Int) {
        ReflectionHelpers.callStaticMethod<Unit>(ShadowDisplayManager::class.java, "changeDisplay",
            ClassParameter.from(Int::class.javaPrimitiveType, displayId),
            ClassParameter.from(Consumer::class.java, Consumer<DisplayConfig> { it.modeId = modeId }))
    }

    /** The playlist is real; prepare and playback are synthetic and never open a media source. */
    private class ProbePlayer(local: Player) : ForwardingPlayer(local) {
        private val listeners = linkedSetOf<Player.Listener>()
        private var state = STATE_IDLE
        private var intent = false
        var videoFormat: Format? = null
        override fun addListener(listener: Player.Listener) { listeners += listener }
        override fun removeListener(listener: Player.Listener) { listeners -= listener }
        override fun getPlaybackState() = state
        override fun getPlayWhenReady() = intent
        override fun isPlaying() = state == STATE_READY && intent
        override fun getCurrentTracks(): Tracks = videoFormat?.let {
            Tracks(listOf(Tracks.Group(TrackGroup(it), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
        } ?: Tracks.EMPTY
        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (intent == playWhenReady) return
            intent = playWhenReady
            listeners.toList().forEach { it.onPlayWhenReadyChanged(intent, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
            events(EVENT_PLAY_WHEN_READY_CHANGED)
        }
        override fun play() = setPlayWhenReady(true)
        override fun pause() = setPlayWhenReady(false)
        override fun prepare() { state = STATE_BUFFERING; events(EVENT_PLAYBACK_STATE_CHANGED) }
        override fun stop() { state = STATE_IDLE; events(EVENT_PLAYBACK_STATE_CHANGED) }
        fun ready() { state = STATE_READY; events(EVENT_PLAYBACK_STATE_CHANGED) }
        fun renderFirstFrame(dispatchEvents: Boolean = true) {
            listeners.toList().forEach { it.onRenderedFirstFrame() }
            if (dispatchEvents) flushFrameEvents()
        }
        fun flushFrameEvents() = events(EVENT_RENDERED_FIRST_FRAME)
        private fun events(event: Int) {
            if (event == EVENT_PLAYBACK_STATE_CHANGED) listeners.toList().forEach { it.onPlaybackStateChanged(state) }
            val events = Player.Events(FlagSet.Builder().add(event).build())
            listeners.toList().forEach { it.onEvents(this, events) }
        }
    }
}
