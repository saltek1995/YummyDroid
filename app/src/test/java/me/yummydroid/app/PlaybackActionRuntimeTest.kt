package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.PreferredQuality

class PlaybackActionRuntimeTest {
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
