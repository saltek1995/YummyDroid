package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerPopupMenuSizingTest {
    @Test
    fun contentWidthMatchesLongestLabelAndStructuralInsets() {
        assertEquals(
            155,
            playerMenuContentWidthPx(
                longestLabelWidth = 100,
                listHorizontalPadding = 16,
                rowHorizontalPadding = 24,
                markerWidthWithMargin = 15,
            ),
        )
    }
}
