package me.yummydroid.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseGridLayoutTest {
    @Test
    fun horizontalPaddingSwitchesAtWideLayoutBoundary() {
        assertEquals(16.dp, browseGridHorizontalContentPadding(719.dp))
        assertEquals(24.dp, browseGridHorizontalContentPadding(720.dp))
    }

    @Test
    fun itemHeightUsesAvailableWidthAndPosterAspectRatio() {
        assertEquals(
            232.5.dp,
            browseGridItemHeight(
                maxWidth = 360.dp,
                columns = 2,
                horizontalPadding = 16.dp,
            ),
        )
        assertEquals(
            540.dp,
            browseGridItemHeight(
                maxWidth = 1920.dp,
                columns = 5,
                horizontalPadding = 24.dp,
            ),
        )
    }

    @Test
    fun invalidGridGeometryHasZeroItemHeight() {
        assertEquals(0.dp, browseGridItemHeight(360.dp, columns = 0, horizontalPadding = 16.dp))
        assertEquals(0.dp, browseGridItemHeight(0.dp, columns = 2, horizontalPadding = 16.dp))
    }

    @Test
    fun focusedCardTopInsetPreservesPhonePaddingAndTvChromeProtection() {
        assertEquals(0.dp, browseGridFocusedCardTopInset(contentTopPadding = 0.dp, maxWidth = 1920.dp))
        assertEquals(100.dp, browseGridFocusedCardTopInset(contentTopPadding = 100.dp, maxWidth = 360.dp))
        assertEquals(76.dp, browseGridFocusedCardTopInset(contentTopPadding = 100.dp, maxWidth = 1920.dp))
    }

    @Test
    fun bottomPaddingCentersLastCardInsideProtectedViewport() {
        assertEquals(
            213.75.dp,
            browseGridFocusedCardBottomPadding(
                maxWidth = 360.dp,
                maxHeight = 640.dp,
                columns = 2,
                horizontalPadding = 16.dp,
                topInset = 0.dp,
                bottomInset = 20.dp,
                basePadding = 44.dp,
            ),
        )
    }

    @Test
    fun invalidViewportKeepsExistingBottomPadding() {
        assertEquals(
            44.dp,
            browseGridFocusedCardBottomPadding(
                maxWidth = 360.dp,
                maxHeight = 0.dp,
                columns = 2,
                horizontalPadding = 16.dp,
                topInset = 0.dp,
                bottomInset = 20.dp,
                basePadding = 44.dp,
            ),
        )
    }
}
