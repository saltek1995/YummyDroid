package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant

class PlaybackActionRuntimeTest {
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
