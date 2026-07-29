package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerFocusNavigationTest {
    @Test
    fun timelineUpUsesNearestControlRow() {
        val bounds = listOf(
            playerBounds(id = BACK, left = 16, top = 16, right = 72, bottom = 72),
            playerBounds(id = PREVIOUS, left = 790, top = 500, right = 850, bottom = 552),
            playerBounds(id = PLAY_PAUSE, left = 918, top = 484, right = 1002, bottom = 568),
            playerBounds(id = NEXT, left = 1070, top = 500, right = 1130, bottom = 552),
            playerBounds(id = TIMELINE, left = 0, top = 908, right = 1920, bottom = 956),
        )

        assertEquals(
            PLAY_PAUSE,
            playerFocusDirectionalTarget(
                bounds = bounds,
                sourceId = TIMELINE,
                direction = PlayerFocusDirection.Up,
            ),
        )
    }

    @Test
    fun centerControlDownReturnsToTimeline() {
        val bounds = listOf(
            playerBounds(id = BACK, left = 16, top = 16, right = 72, bottom = 72),
            playerBounds(id = PREVIOUS, left = 790, top = 500, right = 850, bottom = 552),
            playerBounds(id = PLAY_PAUSE, left = 918, top = 484, right = 1002, bottom = 568),
            playerBounds(id = NEXT, left = 1070, top = 500, right = 1130, bottom = 552),
            playerBounds(id = TIMELINE, left = 0, top = 908, right = 1920, bottom = 956),
            playerBounds(id = QUALITY, left = 880, top = 980, right = 1040, bottom = 1024),
        )

        assertEquals(
            TIMELINE,
            playerFocusDirectionalTarget(
                bounds = bounds,
                sourceId = PLAY_PAUSE,
                direction = PlayerFocusDirection.Down,
            ),
        )
    }

    @Test
    fun verticalNavigationDoesNotSkipShiftedNearestPlayerRow() {
        val bounds = listOf(
            playerBounds(id = BACK, left = 16, top = 16, right = 72, bottom = 72),
            playerBounds(id = PREVIOUS, left = 790, top = 500, right = 850, bottom = 552),
            playerBounds(id = TIMELINE, left = 16, top = 908, right = 1904, bottom = 956),
        )

        assertEquals(
            PREVIOUS,
            playerFocusDirectionalTarget(
                bounds = bounds,
                sourceId = BACK,
                direction = PlayerFocusDirection.Down,
            ),
        )
    }

    @Test
    fun centerRowHorizontalNavigationIsSymmetric() {
        val bounds = listOf(
            playerBounds(id = PREVIOUS, left = 790, top = 500, right = 850, bottom = 552),
            playerBounds(id = PLAY_PAUSE, left = 918, top = 484, right = 1002, bottom = 568),
            playerBounds(id = NEXT, left = 1070, top = 500, right = 1130, bottom = 552),
        )

        assertEquals(
            PLAY_PAUSE,
            playerFocusDirectionalTarget(bounds, PREVIOUS, PlayerFocusDirection.Right),
        )
        assertEquals(
            PREVIOUS,
            playerFocusDirectionalTarget(bounds, PLAY_PAUSE, PlayerFocusDirection.Left),
        )
        assertEquals(
            NEXT,
            playerFocusDirectionalTarget(bounds, PLAY_PAUSE, PlayerFocusDirection.Right),
        )
    }

    @Test
    fun unusablePlayerBoundsAreIgnored() {
        val bounds = listOf(
            playerBounds(id = PLAY_PAUSE, left = 918, top = 484, right = 1002, bottom = 568),
            playerBounds(id = TIMELINE, left = 0, top = 0, right = 0, bottom = 0),
        )

        assertNull(
            playerFocusDirectionalTarget(
                bounds = bounds,
                sourceId = PLAY_PAUSE,
                direction = PlayerFocusDirection.Down,
            ),
        )
    }

    private fun playerBounds(
        id: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) = PlayerFocusBounds(
        id = id,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private companion object {
        const val BACK = 1
        const val PREVIOUS = 2
        const val PLAY_PAUSE = 3
        const val NEXT = 4
        const val TIMELINE = 5
        const val QUALITY = 6
    }
}
