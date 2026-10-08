package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream

class PlaybackActionRuntimeTest {
    @Test
    fun completionFromRetainedSourceCannotInterruptSourceOrVoiceSwitch() {
        val previous = cvhSourceVideo()
        val replacement = kodikSourceVideo()
        val ready = LoadState.Ready(ResolvedVideoStream("https://fixture.invalid/video.mp4", null, emptyMap()))
        val switching = YummyDroidUiState(
            route = AppRoute.Player(replacement, "Title"),
            playerStream = LoadState.Loading,
        )
        assertFalse(switching.acceptsPlaybackCompletion(previous))
        assertFalse(switching.acceptsPlaybackCompletion(replacement))
        assertFalse(switching.copy(playerStream = ready).acceptsPlaybackCompletion(previous))
        assertTrue(switching.copy(playerStream = ready).acceptsPlaybackCompletion(replacement))

        val differentVoice = previous.copy(dubbing = "Other voice")
        val differentEpisode = previous.copy(episode = "6")
        val differentAnime = previous.copy(animeId = previous.animeId + 1)
        listOf(differentVoice, differentEpisode, differentAnime).forEach { target ->
            val state = switching.copy(route = AppRoute.Player(target, "Title"), playerStream = ready)
            assertFalse(state.acceptsPlaybackCompletion(previous))
            assertTrue(state.acceptsPlaybackCompletion(target))
        }
    }

    @Test
    fun completionAfterLeavingPlayerOrWhileRetryingDoesNotReopenDetails() {
        val video = video()
        val ready = LoadState.Ready(ResolvedVideoStream("https://fixture.invalid/video.mp4", null, emptyMap()))
        val state = YummyDroidUiState(route = AppRoute.Player(video, "Title"), playerStream = ready)
        assertTrue(state.acceptsPlaybackCompletion(video))
        assertFalse(state.copy(playerStream = LoadState.Loading).acceptsPlaybackCompletion(video))
        assertFalse(state.copy(playerStream = LoadState.Error("failed")).acceptsPlaybackCompletion(video))
        assertFalse(state.copy(route = AppRoute.Home).acceptsPlaybackCompletion(video))
        assertFalse(state.copy(route = AppRoute.Details(video.animeId)).acceptsPlaybackCompletion(video))
    }

    @Test
    fun manualQualitySurvivesEpisodeChangesAndChangesToTheAppDefault() {
        val preferences = PlaybackQualityPreferences()
        assertEquals(PreferredQuality.P720, preferences.forAnime(10, PreferredQuality.P720))
        preferences.select(10, PreferredQuality.P720)
        assertEquals(PreferredQuality.P720, preferences.forAnime(10, PreferredQuality.P1080))
        assertEquals(PreferredQuality.P1080, preferences.forAnime(20, PreferredQuality.P1080))

        preferences.select(10, PreferredQuality.Auto)
        assertEquals(PreferredQuality.Auto, preferences.forAnime(10, PreferredQuality.P1080))
        preferences.select(10, PreferredQuality.P480)
        assertEquals(PreferredQuality.P480, preferences.forAnime(10, PreferredQuality.Auto))
    }

    @Test
    fun playbackProgressDoesNotPublishUiStateWhilePlayerRouteIsVisible() {
        assertFalse(
            shouldPublishPlaybackProgressToUi(
                AppRoute.Player(
                    video = video(),
                    animeTitle = "Title",
                ),
            ),
        )
    }

    @Test
    fun playbackProgressStillPublishesUiStateOutsidePlayerRoute() {
        assertTrue(shouldPublishPlaybackProgressToUi(AppRoute.Home))
        assertTrue(shouldPublishPlaybackProgressToUi(AppRoute.Details(animeId = 10)))
    }

    private fun video(): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 10,
            player = "CVH",
            dubbing = "Voice",
            episode = "1",
            url = "https://video.test/player",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }
}
