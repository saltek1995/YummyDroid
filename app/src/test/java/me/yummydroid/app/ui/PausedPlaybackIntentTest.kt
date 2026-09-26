package me.yummydroid.app.ui

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.annotation.OptIn(UnstableApi::class)
class PausedPlaybackIntentTest {
    @Test fun playDuringBufferingAppliesIntentAndSecondToggleCancelsIt() {
        val context = RuntimeEnvironment.getApplication()
        val local = ExoPlayer.Builder(context).build()
        try {
            val player = ProbePlayer(local)
            val actions = NativePlayerPlaybackActions(player, AppNavigationController())
            val view = PlayerView(context).apply { this.player = player }
            actions.requestStart()
            assertTrue("Play intent must not wait for READY", player.playWhenReady)
            assertFalse("buffering is not actual playback", player.isPlaying)
            assertTrue(view.togglePlayerPlayback(actions::requestStart, actions::pause))
            assertFalse("a second toggle cancels pending playback", player.playWhenReady)
            player.state = Player.STATE_READY
            assertFalse("no delayed Play may survive Pause", player.playWhenReady)
            assertEquals(1, player.plays)
            assertEquals(1, player.pauses)
            view.player = null
        } finally {
            local.release()
        }
    }

    @Test fun playDoesNotPrepareAnIdlePlayer() {
        val local = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        try {
            val player = ProbePlayer(local).apply { state = Player.STATE_IDLE }
            NativePlayerPlaybackActions(player, AppNavigationController()).requestStart()
            assertFalse(player.playWhenReady)
            assertEquals(0, player.plays)
        } finally {
            local.release()
        }
    }

    private class ProbePlayer(local: Player) : ForwardingPlayer(local) {
        var state = Player.STATE_BUFFERING
        private var intent = false
        var plays = 0
        var pauses = 0
        override fun getPlaybackState() = state
        override fun getPlayWhenReady() = intent
        override fun isPlaying() = state == Player.STATE_READY && intent
        override fun play() { intent = true; plays++ }
        override fun pause() { intent = false; pauses++ }
    }
}
