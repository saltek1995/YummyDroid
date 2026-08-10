package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseTopBarVisibilityPolicyTest {
    @Test
    fun nonPositiveCollapseDistanceUsesScrollability() {
        assertEquals(1f, browseTopBarVisibilityProgress(0, 0, false, 0f))
        assertEquals(0f, browseTopBarVisibilityProgress(0, 0, true, 0f))
    }

    @Test
    fun leadingItemsDoNotConsumeCollapseDistance() {
        assertEquals(1f, browseTopBarVisibilityProgress(0, 50, true, 100f, leadingScrollAnchorItems = 1))
    }

    @Test
    fun anchorOffsetProducesContinuousBoundedProgress() {
        assertEquals(0.75f, browseTopBarVisibilityProgress(1, 25, true, 100f, leadingScrollAnchorItems = 1))
        assertEquals(0f, browseTopBarVisibilityProgress(1, 150, true, 100f, leadingScrollAnchorItems = 1))
        assertEquals(0f, browseTopBarVisibilityProgress(2, 0, true, 100f, leadingScrollAnchorItems = 1))
    }
}
