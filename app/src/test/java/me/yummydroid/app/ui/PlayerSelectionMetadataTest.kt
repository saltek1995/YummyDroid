package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import me.yummydroid.app.R
import me.yummydroid.app.YummyCastPlaybackPayload
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerSelectionMetadataTest {
    @Test
    fun headerImmediatelyFollowsSelectedSourceAndVoiceWhilePlaybackIsRetained() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup()
        val context = ContextThemeWrapper(activity.get(), R.style.Theme_YummyDroid_Player)
        val view = LayoutInflater.from(context).inflate(
            R.layout.yummy_player_view, FrameLayout(context), false,
        ) as PlayerView
        activity.get().setContentView(view)
        val player = ExoPlayer.Builder(context).build()
        val working = VideoVariant(1, 10, "CVH", dubbing = "AniLibria", episode = "1",
            url = "https://working.test/video", index = 1, durationSeconds = 120, views = 0,
            localPlaybackUrl = "file:///working.mp4")
        val selected = listOf(
            working.copy(id = 2, player = "Kodik", dubbing = "AnimeVost", localPlaybackUrl = ""),
            working.copy(id = 3, player = "Alloha", dubbing = "AniDub", localPlaybackUrl = ""),
            working,
        )
        val stream = ResolvedVideoStream(working.localPlaybackUrl, null, emptyMap())
        val cast = PlayerCastSession.create(context, player, YummyCastPlaybackPayload(
            animeTitle = "Anime", video = working, episodeVideos = listOf(working), preferredQualityName = "Auto",
        ))
        try {
            for (pending in selected) {
                val binding = PlayerControllerBinding(
                    player = player, playbackPlayer = player, castSession = cast, isRemotePlayback = false,
                    stream = stream, animeTitle = "Anime", currentVideo = working, selectedVideo = pending,
                    isLocalPlayback = true, groups = selected.groupBy { it.matchingVoiceKey },
                    voiceOptions = emptyList(), selectedKey = null, sourceOptions = emptyList(), selectedSourceKey = null,
                    previousVideo = null, nextVideo = null, allowSubscription = false, subscriptionActive = false,
                    onToggleSubscription = {}, qualityOptions = emptyList(), selectedQualityKey = null,
                    onSelectedQualityKeyChange = {}, subtitleOptions = emptyList(), subtitlesLoading = false,
                    selectedSubtitleKey = SUBTITLE_OFF_KEY, onSelectedSubtitleKeyChange = {}, onSelectLocalQuality = {},
                    onSelectPreferredQuality = {}, onSelectGroup = { _, _, _ -> }, onSelectSource = { _, _ -> },
                    onPlayVideoAt = { _, _ -> }, canUsePictureInPicture = false, onEnterPictureInPicture = {},
                    settings = AppSettings(), skipControlsTimelineReady = false, texts = defaultPlayerControlTexts,
                    onSettingsChange = {}, onBack = {}, onRequestPlay = {}, onPausePlayback = {},
                    onRememberPlayerControlFocus = {},
                )
                view.bindPlayerMetadata(binding)
                val expectedSource = if (pending == working) "Local" else pending.player
                assertEquals("${pending.dubbing} \u2022 $expectedSource \u2022 Episode 1 of 1",
                    view.findViewById<TextView>(R.id.yummy_player_subtitle).text.toString())
                assertSame(working, binding.currentVideo)
                assertSame(stream, binding.stream)
                assertEquals(0, player.mediaItemCount)
            }
        } finally {
            cast.release()
            player.release()
            activity.pause().stop().destroy()
        }
    }
}
