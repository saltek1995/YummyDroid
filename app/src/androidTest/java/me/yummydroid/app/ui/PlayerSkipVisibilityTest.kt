package me.yummydroid.app.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.R
import me.yummydroid.app.YummyCastPlaybackPayload
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerSkipVisibilityTest {
    @Test
    fun cachedControllerRepairsRemovedSkipSessionWithTimelineStillBound() = withPrompt { view, _ ->
        val player = ExoPlayer.Builder(view.context).build()
        val video = VideoVariant(
            id = 1L, animeId = 1L, player = "test", dubbing = "test", episode = "1",
            url = "https://example.com/video", index = 1, durationSeconds = 120, views = 0,
            skipSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 0L, 90_000L)),
        )
        val cast = PlayerCastSession.create(
            view.context, player, YummyCastPlaybackPayload(
                animeTitle = "Test", video = video, episodeVideos = listOf(video), preferredQualityName = "Auto",
            ),
        )
        try {
            val binding = controllerBinding(player, cast, video)
            view.bindYummyController(binding)
            // The previous playback effect used to dispose the shared view's new skip session.
            view.unbindSkipControls()
            assertSame(binding, view.getTag(R.id.yummy_player_controller_binding))
            view.bindYummyController(binding)
            assertNotNull("Cached controller must restore the skip listener", view.getTag(R.id.yummy_player_skip_listener))
            val check = view.tagValue<Runnable>(R.id.yummy_player_skip_poll_runnable)
            assertNotNull("Cached controller must restore position checks", check)
            check!!.run()
            assertEquals(video.skipSegments.single().key, view.getTag(R.id.yummy_player_active_skip_key))
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.yummy_skip_controls).visibility)
        } finally {
            view.unbindSkipControls()
            cast.release()
        }
    }

    @Test
    fun watchingKeepsSkipControlsAvailableWhenFullControlsAreReopened() = withPrompt { view, prompt ->
        view.hidePlayerControls()
        assertFalse("Floating prompt must close without showing full controls during a fade", view.isControllerFullyVisible)
        assertNull(view.getTag(R.id.yummy_player_controls_hide_runnable))
        assertEquals(0f, view.findViewById<View>(R.id.yummy_player_top_bar).alpha)
        assertFalse(view.hasVisiblePlayerControls())
        assertFalse(view.isSkipOnlyControllerMode())
        assertFalse(view.dismissedSkipKeys().contains(prompt.key))
        assertFalse(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)!!.showWhenControlsHidden)

        view.showPlayerControls()
        assertTrue(view.hasVisiblePlayerControls())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.yummy_skip_controls).visibility)
        assertNotNull(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment))

        view.autoHidePlayerControls()
        assertFalse(view.hasVisiblePlayerControls())
        assertNotNull(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment))
    }

    @Test
    fun automaticHidingPreservesFloatingPromptUntilUserChoosesToWatch() = withPrompt { view, _ ->
        view.setSkipOnlyControllerMode(false)
        view.autoHidePlayerControls()
        assertTrue(view.isSkipOnlyControllerMode())
        assertTrue(view.hasVisiblePlayerControls())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.yummy_skip_controls).visibility)
    }

    private fun withPrompt(test: (PlayerView, ActiveSkipPrompt) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_YummyDroid_Player)
            val view = LayoutInflater.from(context)
                .inflate(R.layout.yummy_player_view, FrameLayout(context), false) as PlayerView
            val segment = VideoSkipSegment(VideoSkipKind.Opening, 0L, 90_000L)
            val prompt = ActiveSkipPrompt(segment.key, segment)
            view.setControllerAnimationEnabled(false)
            view.setTag(R.id.yummy_player_active_skip_key, prompt.key)
            view.setTag(R.id.yummy_player_active_skip_segment, prompt)
            view.setSkipControlsActive(true)
            view.setSkipOnlyControllerMode(true)
            view.showPlayerControls()
            try {
                test(view, prompt)
            } finally {
                view.unbindSkipControls()
                view.hidePlayerControls()
            }
        }
    }

    private fun controllerBinding(player: ExoPlayer, cast: PlayerCastSession, video: VideoVariant) = PlayerControllerBinding(
        player = player, playbackPlayer = player, castSession = cast, isRemotePlayback = false,
        stream = ResolvedVideoStream(url = video.url, mimeType = null, headers = emptyMap()), animeTitle = "Test", currentVideo = video,
        isLocalPlayback = false, groups = emptyMap(), voiceOptions = emptyList(), selectedKey = null,
        sourceOptions = emptyList(), selectedSourceKey = null, previousVideo = null, nextVideo = null,
        allowSubscription = false, subscriptionActive = false, onToggleSubscription = {},
        qualityOptions = emptyList(), selectedQualityKey = null, onSelectedQualityKeyChange = {},
        subtitleOptions = emptyList(), subtitlesLoading = false, selectedSubtitleKey = SUBTITLE_OFF_KEY,
        onSelectedSubtitleKeyChange = {}, onSelectLocalQuality = {}, onSelectPreferredQuality = {},
        onSelectGroup = { _, _, _ -> }, onSelectSource = { _, _ -> }, onPlayVideoAt = { _, _ -> },
        canUsePictureInPicture = false, onEnterPictureInPicture = {}, settings = AppSettings(),
        skipControlsTimelineReady = false, texts = defaultPlayerControlTexts, onSettingsChange = {},
        onBack = {}, onRequestPlay = {}, onPausePlayback = {}, onRememberPlayerControlFocus = {},
    )
}
