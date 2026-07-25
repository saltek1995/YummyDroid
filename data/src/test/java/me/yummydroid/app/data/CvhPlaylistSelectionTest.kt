package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CvhPlaylistSelectionTest {
    @Test
    fun missingIframeSeasonDoesNotRejectSeasonTwoPlaylistItem() {
        assertTrue(
            cvhPlaylistItemMatchesEpisode(
                requestedSeason = null,
                requestedEpisode = 5,
                itemSeason = 2,
                itemEpisode = 5,
            ),
        )
    }

    @Test
    fun explicitIframeSeasonStillFiltersPlaylistItem() {
        assertFalse(
            cvhPlaylistItemMatchesEpisode(
                requestedSeason = 1,
                requestedEpisode = 5,
                itemSeason = 2,
                itemEpisode = 5,
            ),
        )
    }

    @Test
    fun differentEpisodeIsRejectedWhenSeasonIsMissing() {
        assertFalse(
            cvhPlaylistItemMatchesEpisode(
                requestedSeason = null,
                requestedEpisode = 5,
                itemSeason = 2,
                itemEpisode = 6,
            ),
        )
    }
}
