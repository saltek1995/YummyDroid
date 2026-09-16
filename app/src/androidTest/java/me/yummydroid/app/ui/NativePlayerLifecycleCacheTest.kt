package me.yummydroid.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class NativePlayerLifecycleCacheTest {
    @get:Rule val compose = createComposeRule()
    private val visible = mutableStateOf(true)

    @Test
    fun unchangedPlaybackIdentityUsesLatestEndedAndNextEpisodeCallbacks() = withPlayer { local, player ->
        val events = mutableListOf<String>()
        val current = mutableStateOf(binding(local, player, 1L, "original", 2L, events))
        compose.setContent { if (visible.value) NativePlayerLifecycle(current.value) }
        compose.runOnIdle {
            current.value = binding(local, player, 1L, "refreshed", 3L, events)
        }
        compose.runOnIdle {
            assertEquals("Metadata refresh must retain the listener", 1, player.addCount)
            assertEquals(0, player.removeCount)
            player.emitEnded()
            assertEquals(listOf("ended:1:refreshed", "next:3:refreshed"), events)
            player.emitEnded()
            assertEquals("Repeated ended events must not advance twice", 2, events.size)
        }
    }

    @Test
    fun identityChangeDisposesDepartingEpisodeWithItsLatestCallbacks() = withPlayer { local, player ->
        val events = mutableListOf<String>()
        val current = mutableStateOf(binding(local, player, 1L, "original", 2L, events))
        compose.setContent { if (visible.value) NativePlayerLifecycle(current.value) }
        compose.runOnIdle {
            current.value = binding(local, player, 1L, "refreshed", 3L, events)
        }
        compose.runOnIdle {
            assertEquals(1, player.addCount)
            current.value = binding(local, player, 2L, "new-episode", 4L, events)
        }
        compose.runOnIdle {
            assertEquals(2, player.addCount)
            assertEquals(1, player.removeCount)
            assertEquals(
                listOf("progress:1:refreshed:12345:90000", "dispose:1:refreshed"),
                events,
            )
            player.emitEnded()
            assertEquals(
                listOf("ended:2:new-episode", "next:4:new-episode"),
                events.takeLast(2),
            )
        }
    }

    private fun withPlayer(test: (ExoPlayer, ControllablePlayer) -> Unit) {
        lateinit var local: ExoPlayer
        lateinit var player: ControllablePlayer
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            local = ExoPlayer.Builder(instrumentation.targetContext).build()
            player = ControllablePlayer(local)
        }
        try {
            test(local, player)
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            instrumentation.runOnMainSync { local.release() }
        }
    }

    private fun binding(
        local: ExoPlayer,
        player: ControllablePlayer,
        videoId: Long,
        callbackVersion: String,
        nextEpisode: Long,
        events: MutableList<String>,
    ) = NativePlayerLifecycleBinding(
        player = player,
        localPlayer = local,
        stream = ResolvedVideoStream("https://example.com/stable-video.mp4", null, emptyMap()),
        videoId = videoId,
        pipPlayerHandle = object : PipPlayerHandle {
            override val isPlaying = false
            override fun play() = Unit
            override fun pause() = Unit
        },
        metadataDurationSeconds = 90,
        state = NativePlayerEventState(
            playerView = { null },
            settings = { AppSettings(autoplayNextEpisode = true) },
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
            onPlaybackEnded = { events += "ended:$videoId:$callbackVersion" },
            onBufferingTimeout = {},
            onAutoAdvance = { events += "next:$nextEpisode:$callbackVersion" },
            onPlaybackError = { _, _ -> },
            onProgressSnapshot = { position, duration ->
                events += "progress:$videoId:$callbackVersion:$position:$duration"
            },
            onDisplayModeUpdate = {},
            onDispose = { events += "dispose:$videoId:$callbackVersion" },
        ),
    )

    private class ControllablePlayer(local: ExoPlayer) : ForwardingPlayer(local) {
        private val listeners = linkedSetOf<Player.Listener>()
        var addCount = 0
            private set
        var removeCount = 0
            private set
        override fun addListener(listener: Player.Listener) {
            addCount++
            listeners += listener
        }
        override fun removeListener(listener: Player.Listener) {
            removeCount++
            listeners -= listener
        }
        override fun getPlaybackState() = Player.STATE_READY
        override fun getCurrentPosition() = 12_345L
        override fun getDuration() = 90_000L
        fun emitEnded() = listeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_ENDED) }
    }
}
