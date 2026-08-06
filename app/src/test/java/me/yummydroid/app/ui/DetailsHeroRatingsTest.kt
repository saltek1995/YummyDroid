package me.yummydroid.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.RatingDetails

class DetailsHeroRatingsTest {
    @Test
    fun externalRatingPresenceFollowsProviderRatings() {
        assertFalse(RatingDetails().hasExternalRatings())
        assertTrue(RatingDetails(kinopoisk = 7.5).hasExternalRatings())
    }

    @Test
    fun externalRatingDisplaysKeepStableProviderOrder() {
        val displays = detailsHeroExternalRatingDisplays(
            RatingDetails(
                kinopoisk = 7.5,
                worldArt = 8.1,
                aniDub = 6.9,
            ),
        )

        assertEquals(listOf("World Art", "Kinopoisk", "Anilibria"), displays.map { it.title })
        assertEquals(listOf("8.1", "7.5", "6.9"), displays.map { it.value })
    }

    @Test
    fun siteRatingColorUsesExistingThresholds() {
        assertEquals(Color(0xFFFF6666), ratingColorForSiteScale(4.9))
        assertEquals(Color(0xFFF2B800), ratingColorForSiteScale(5.0))
        assertEquals(Color(0xFFF2B800), ratingColorForSiteScale(6.9))
        assertEquals(Color(0xFF3CCE7B), ratingColorForSiteScale(7.0))
    }
}
