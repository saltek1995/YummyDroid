package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisualGridNavigationTest {
    @Test
    fun navigationMovesInsideGridBeforeCallingEdgeExit() {
        var movedIndex: Int? = null
        var edgeExitDirection: VisualGridDirection? = null

        val handled = handleVisualGridNavigationKey(
            key = Key.DirectionDown,
            itemCount = 9,
            columns = 3,
            currentFocusedIndex = 1,
            fallbackIndex = 0,
            moveFocusTo = { index -> movedIndex = index; true },
            onEdgeExit = { direction -> edgeExitDirection = direction; true },
        )

        assertTrue(handled)
        assertEquals(4, movedIndex)
        assertNull(edgeExitDirection)
    }

    @Test
    fun navigationUsesFallbackIndexWhenCurrentFocusIsInvalid() {
        var movedIndex: Int? = null

        assertTrue(
            handleVisualGridNavigationKey(
                key = Key.DirectionRight,
                itemCount = 6,
                columns = 3,
                currentFocusedIndex = -1,
                fallbackIndex = 1,
                moveFocusTo = { index -> movedIndex = index; true },
                onEdgeExit = { false },
            ),
        )
        assertEquals(2, movedIndex)
    }

    @Test
    fun navigationCallsEdgeExitAtGridBoundary() {
        var edgeExitDirection: VisualGridDirection? = null

        assertTrue(
            handleVisualGridNavigationKey(
                key = Key.DirectionUp,
                itemCount = 6,
                columns = 3,
                currentFocusedIndex = 1,
                fallbackIndex = 1,
                moveFocusTo = { false },
                onEdgeExit = { direction -> edgeExitDirection = direction; true },
            ),
        )
        assertEquals(VisualGridDirection.Up, edgeExitDirection)
    }

    @Test
    fun navigationIgnoresNonDirectionalKeysAndInvalidIndexes() {
        val commonMove: (Int) -> Boolean = { true }
        val commonExit: (VisualGridDirection) -> Boolean = { true }

        assertFalse(handleVisualGridNavigationKey(Key.Enter, 6, 3, 1, 1, commonMove, commonExit))
        assertFalse(handleVisualGridNavigationKey(Key.DirectionRight, 6, 3, 1, 9, commonMove, commonExit))
    }

    @Test
    fun horizontalNavigationDoesNotWrapRows() {
        assertNull(visualGridMoveTarget(4, 12, 5, VisualGridDirection.Right))
        assertNull(visualGridMoveTarget(5, 12, 5, VisualGridDirection.Left))
        assertEquals(3, visualGridMoveTarget(4, 12, 5, VisualGridDirection.Left))
        assertEquals(6, visualGridMoveTarget(5, 12, 5, VisualGridDirection.Right))
    }

    @Test
    fun verticalNavigationKeepsVisualColumn() {
        assertEquals(7, visualGridMoveTarget(2, 12, 5, VisualGridDirection.Down))
        assertEquals(2, visualGridMoveTarget(7, 12, 5, VisualGridDirection.Up))
        assertNull(visualGridMoveTarget(8, 12, 5, VisualGridDirection.Down))
    }
}
