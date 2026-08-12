package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisualGridKeyNavigationTest {
    @Test
    fun navigationMovesInsideGridBeforeCallingEdgeExit() {
        var movedIndex: Int? = null
        var edgeExitDirection: VisualGridDirection? = null

        val handled = handleVisualGridNavigationKey(
            key = Key.DirectionDown,
            itemCount = 9,
            columns = 3,
            sourceIndex = 1,
            moveFocusTo = { index -> movedIndex = index },
            onEdgeExit = { direction -> edgeExitDirection = direction },
        )

        assertTrue(handled)
        assertEquals(4, movedIndex)
        assertNull(edgeExitDirection)
    }

    @Test
    fun navigationUsesEventSourceIndex() {
        var movedIndex: Int? = null

        assertTrue(
            handleVisualGridNavigationKey(
                key = Key.DirectionRight,
                itemCount = 6,
                columns = 3,
                sourceIndex = 1,
                moveFocusTo = { index -> movedIndex = index },
                onEdgeExit = { },
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
                sourceIndex = 1,
                moveFocusTo = { },
                onEdgeExit = { direction -> edgeExitDirection = direction },
            ),
        )
        assertEquals(VisualGridDirection.Up, edgeExitDirection)
    }

    @Test
    fun navigationIgnoresNonDirectionalKeysAndInvalidIndexes() {
        val commonMove: (Int) -> Unit = { }
        val commonExit: (VisualGridDirection) -> Unit = { }

        assertFalse(handleVisualGridNavigationKey(Key.Enter, 6, 3, 1, commonMove, commonExit))
        assertFalse(handleVisualGridNavigationKey(Key.DirectionRight, 6, 3, 9, commonMove, commonExit))
    }

    @Test
    fun managedBoundaryNeverFallsThroughToComposeFocusSearch() {
        var exitAttempts = 0

        assertTrue(
            handleVisualGridNavigationKey(
                key = Key.DirectionRight,
                itemCount = 6,
                columns = 3,
                sourceIndex = 2,
                moveFocusTo = { },
                onEdgeExit = { exitAttempts += 1 },
            ),
        )
        assertEquals(1, exitAttempts)
    }

    @Test
    fun unmanagedDirectionCanBeDelegatedExplicitly() {
        var calls = 0

        assertFalse(
            handleManagedDpadNavigationKey(
                key = Key.DirectionDown,
                ownsDirection = { direction -> direction != VisualGridDirection.Down },
            ) { calls += 1 },
        )
        assertEquals(0, calls)
    }
}
