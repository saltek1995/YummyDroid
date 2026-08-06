package me.yummydroid.app.ui

import android.content.res.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.Anime

class AnimeCardPresentationTest {
    @Test
    fun touchScaleIsDisabledForTvUiMode() {
        assertFalse(animeCardTouchScaleEnabled(Configuration.UI_MODE_TYPE_TELEVISION))
        assertTrue(animeCardTouchScaleEnabled(Configuration.UI_MODE_TYPE_NORMAL))
    }

    @Test
    fun expandedAndScaledStatesFollowInputModeFlags() {
        assertTrue(animeCardExpanded(dpadFocused = true, touchHeld = false))
        assertTrue(animeCardExpanded(dpadFocused = false, touchHeld = true))
        assertFalse(animeCardExpanded(dpadFocused = false, touchHeld = false))

        assertTrue(animeCardScaled(touchScaleEnabled = true, touchHeld = true))
        assertFalse(animeCardScaled(touchScaleEnabled = false, touchHeld = true))
        assertFalse(animeCardScaled(touchScaleEnabled = true, touchHeld = false))
    }

    @Test
    fun metaTextUsesOverrideBeforeAnimeMeta() {
        val anime = anime()

        assertEquals("custom", animeCardMetaText(anime, "custom"))
        assertEquals(anime.meta, animeCardMetaText(anime, null))
    }

    private fun anime(): Anime {
        return Anime(
            id = 1,
            title = "Anime",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = 2026,
            rating = 9.5,
            views = 1000,
            status = "released",
            type = "ONA",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }
}
