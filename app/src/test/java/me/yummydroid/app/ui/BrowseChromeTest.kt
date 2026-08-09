package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseChromeTest {
    @Test
    fun indicatorIsFullySelectedAtActivePosition() {
        assertEquals(1f, browseSectionIndicatorFraction(activePosition = 2f, index = 2))
    }

    @Test
    fun indicatorInterpolatesBetweenAdjacentSections() {
        assertEquals(0.5f, browseSectionIndicatorFraction(activePosition = 1.5f, index = 1))
        assertEquals(0.5f, browseSectionIndicatorFraction(activePosition = 1.5f, index = 2))
    }

    @Test
    fun indicatorIsHiddenWithoutAValidPosition() {
        assertEquals(0f, browseSectionIndicatorFraction(activePosition = null, index = 0))
        assertEquals(0f, browseSectionIndicatorFraction(activePosition = 3f, index = 0))
    }
}
