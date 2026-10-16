package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.R
import me.yummydroid.app.YummyCastPlaybackPayload
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerPipControlTest {
    @Test
    fun readyPlaybackRestoresPipOnTheSameViewAfterEveryLoadingTransition() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ContextThemeWrapper(activity.get(), R.style.Theme_YummyDroid_Player)
        val view = LayoutInflater.from(context).inflate(R.layout.yummy_player_view, FrameLayout(context), false) as PlayerView
        activity.get().setContentView(view)
        val player = ExoPlayer.Builder(context).build()
        val video = VideoVariant(1, 1, "CVH", dubbing = "Test", episode = "1",
            url = "https://example.invalid/video", index = 1, durationSeconds = 120, views = 0)
        val cast = PlayerCastSession.create(context, player, YummyCastPlaybackPayload(
            animeTitle = "Test", video = video, episodeVideos = listOf(video), preferredQualityName = "Auto"))
        var entries = 0
        fun ready(supported: Boolean = true, remote: Boolean = false) {
            view.bindPlayerPictureInPictureControl(PlayerControllerBinding(
                player = player, playbackPlayer = player, castSession = cast, isRemotePlayback = remote,
                stream = ResolvedVideoStream(url = video.url, mimeType = null, headers = emptyMap()),
                animeTitle = "Test", currentVideo = video, isLocalPlayback = false, groups = emptyMap(),
                voiceOptions = emptyList(), selectedKey = null, sourceOptions = emptyList(), selectedSourceKey = null,
                previousVideo = null, nextVideo = null, allowSubscription = false, subscriptionActive = false,
                onToggleSubscription = {}, qualityOptions = emptyList(), selectedQualityKey = null,
                onSelectedQualityKeyChange = {}, subtitleOptions = emptyList(), subtitlesLoading = false,
                selectedSubtitleKey = SUBTITLE_OFF_KEY, onSelectedSubtitleKeyChange = {}, onSelectLocalQuality = {},
                onSelectPreferredQuality = {}, onSelectGroup = { _, _, _ -> }, onSelectSource = { _, _ -> },
                onPlayVideoAt = { _, _ -> }, canUsePictureInPicture = supported, onEnterPictureInPicture = { entries++ },
                settings = AppSettings(), skipControlsTimelineReady = false, texts = defaultPlayerControlTexts,
                onSettingsChange = {}, onBack = {}, onRequestPlay = {}, onPausePlayback = {}, onRememberPlayerControlFocus = {},
            ))
        }
        val button = view.findViewById<View>(R.id.yummy_player_pip)
        try {
            repeat(2) {
                view.bindYummyShellController(
                    bindingToken = Any(), animeTitle = "Test", currentVideo = video, settings = AppSettings(),
                    groups = emptyMap(), selectedKey = null, sourceOptions = emptyList(), selectedSourceKey = null,
                    previousVideo = null, nextVideo = null, allowSubscription = false, subscriptionActive = false,
                    canUsePictureInPicture = true, showCenterControls = true, texts = defaultPlayerControlTexts,
                    onToggleSubscription = {}, onSelectGroup = { _, _ -> }, onSelectSource = {}, onPlayVideo = {}, onBack = {},
                )
                assertFalse(button.isEnabled)
                assertFalse(button.isFocusable)
                assertEquals(0.45f, button.alpha)
                ready()
                assertEquals(View.VISIBLE, button.visibility)
                assertTrue(button.isEnabled)
                assertTrue(button.isFocusable)
                assertEquals(1f, button.alpha)
            }
            for ((supported, remote) in listOf(false to false, true to true)) {
                ready(supported, remote)
                assertEquals(View.GONE, button.visibility)
                assertFalse(button.isEnabled)
                assertFalse(button.isFocusable)
                ready()
                assertTrue(button.isEnabled)
                assertEquals(1f, button.alpha)
            }
            button.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
            assertEquals(1, entries)
        } finally {
            cast.release()
            player.release()
            activity.pause().stop().destroy()
        }
    }
}
