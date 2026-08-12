package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun disabledBackdropIgnoresEveryAlphaSource() {
        val policy = browseTvBackdropPolicy(
            drawBackdrop = false,
            backdropVisible = true,
            hasProgress = true,
            hasProgressProvider = true,
        )

        assertFalse(policy.visible)
        assertFalse(policy.animateAlpha)
        assertEquals(
            0f,
            resolveBrowseTvBackdropAlpha(
                policy = policy,
                backdropProgress = 0.7f,
                backdropProgressProvider = { 0.8f },
                animatedAlpha = 0.9f,
            ),
        )
    }

    @Test
    fun staticBackdropAnimatesOnlyWhileVisible() {
        val visiblePolicy = browseTvBackdropPolicy(
            drawBackdrop = true,
            backdropVisible = true,
            hasProgress = false,
            hasProgressProvider = false,
        )
        val hiddenPolicy = browseTvBackdropPolicy(
            drawBackdrop = true,
            backdropVisible = false,
            hasProgress = false,
            hasProgressProvider = false,
        )

        assertTrue(visiblePolicy.visible)
        assertTrue(visiblePolicy.animateAlpha)
        assertEquals(0.4f, resolveBrowseTvBackdropAlpha(visiblePolicy, null, null, 0.4f))
        assertFalse(hiddenPolicy.visible)
        assertFalse(hiddenPolicy.animateAlpha)
        assertEquals(0f, resolveBrowseTvBackdropAlpha(hiddenPolicy, null, null, null))
    }

    @Test
    fun dynamicBackdropUsesProviderBeforeProgressAndClampsResult() {
        val policy = browseTvBackdropPolicy(
            drawBackdrop = true,
            backdropVisible = false,
            hasProgress = true,
            hasProgressProvider = true,
        )

        assertTrue(policy.visible)
        assertFalse(policy.animateAlpha)
        assertEquals(0.25f, resolveBrowseTvBackdropAlpha(policy, 0.75f, { 0.25f }, 1f))
        assertEquals(1f, resolveBrowseTvBackdropAlpha(policy, 1.4f, null, null))
    }

    @Test
    fun actionBadgesHideInactiveCountsAndCapVisibleValues() {
        assertNull(filterActionBadgeText(activeFilters = -1, enabled = true))
        assertNull(filterActionBadgeText(activeFilters = 3, enabled = false))
        assertEquals("3", filterActionBadgeText(activeFilters = 3, enabled = true))
        assertEquals("9", filterActionBadgeText(activeFilters = 12, enabled = true))

        assertNull(downloadActionBadgeText(-1))
        assertNull(downloadActionBadgeText(0))
        assertEquals("9", downloadActionBadgeText(9))
        assertEquals("9+", downloadActionBadgeText(10))
    }
}
