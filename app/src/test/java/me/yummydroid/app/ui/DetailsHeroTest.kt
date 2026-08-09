package me.yummydroid.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailsHeroTest {
    @Test
    fun wideLayoutKeepsDesktopSpacingAndCappedPoster() {
        val geometry = resolveDetailsHeroLayoutGeometry(
            maxWidth = 1200.dp,
            windowHeight = 800.dp,
        )

        assertTrue(geometry.expanded)
        assertFalse(geometry.compact)
        assertEquals(24.dp, geometry.horizontalPadding)
        assertEquals(22.dp, geometry.verticalPadding)
        assertEquals(264.dp, geometry.posterWidth)
        assertEquals(geometry.posterWidth, geometry.markMaxWidth)
    }

    @Test
    fun shallowWideLayoutUsesCompactVerticalSpacing() {
        val geometry = resolveDetailsHeroLayoutGeometry(
            maxWidth = 1200.dp,
            windowHeight = 500.dp,
        )

        assertTrue(geometry.expanded)
        assertTrue(geometry.compact)
        assertEquals(14.dp, geometry.verticalPadding)
    }

    @Test
    fun phoneLayoutStacksContentAtFullPosterWidth() {
        val geometry = resolveDetailsHeroLayoutGeometry(
            maxWidth = 480.dp,
            windowHeight = 900.dp,
        )

        assertFalse(geometry.expanded)
        assertTrue(geometry.compact)
        assertEquals(18.dp, geometry.horizontalPadding)
        assertEquals(14.dp, geometry.verticalPadding)
        assertEquals(480.dp, geometry.posterWidth)
        assertEquals(480.dp, geometry.markMaxWidth)
    }

    @Test
    fun focusIndicesFitInsideHeroGraphAndDoNotCollide() {
        val fixedIndices = listOf(
            DetailsHeroFocusIndex.PrimaryAction,
            DetailsHeroFocusIndex.DownloadAction,
            DetailsHeroFocusIndex.ResetAction,
            DetailsHeroFocusIndex.RatingBadge,
            DetailsHeroFocusIndex.Poster,
            DetailsHeroFocusIndex.MarkStart,
            DetailsHeroFocusIndex.FactGenreStart,
            DetailsHeroFocusIndex.FactYear,
            DetailsHeroFocusIndex.FactStudioStart,
            DetailsHeroFocusIndex.FactCreatorStart,
        )

        assertEquals(fixedIndices.size, fixedIndices.distinct().size)
        assertTrue(fixedIndices.all { index -> index in 0 until DETAILS_HERO_FOCUS_GRAPH_SIZE })
    }
}
