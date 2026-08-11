package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseGridFocusRestoreTest {
    @Test
    fun openingDetailsProtectsRetainedIndexFromOutgoingFocusEvents() {
        assertTrue(
            browseGridFocusUpdateBlocked(
                retainedIndexOnOpen = 7,
                contentFocusEnabled = true,
                requestNonce = 12L,
                handledRequestNonce = 12L,
            ),
        )
    }

    @Test
    fun retainedOpenIndexWinsOverIndexChangedDuringLayerTransition() {
        assertEquals(
            7,
            preferredBrowseGridRestoreIndex(
                retainedIndexOnOpen = 7,
                currentFocusedIndex = 9,
                itemCount = 20,
            ),
        )
    }

    @Test
    fun completedRestoreAllowsRegularFocusUpdates() {
        assertFalse(
            browseGridFocusUpdateBlocked(
                retainedIndexOnOpen = -1,
                contentFocusEnabled = true,
                requestNonce = 12L,
                handledRequestNonce = 12L,
            ),
        )
    }

    @Test
    fun paginationStartsOnlyWithinLastTwoRows() {
        assertFalse(
            shouldLoadMoreNearBrowseIndex(
                index = 3,
                itemCount = 20,
                columnsCount = 4,
                canLoadMore = true,
                isLoadingMore = false,
                hasError = false,
                lastRequestItemCount = -1,
            ),
        )
        assertTrue(
            shouldLoadMoreNearBrowseIndex(
                index = 12,
                itemCount = 20,
                columnsCount = 4,
                canLoadMore = true,
                isLoadingMore = false,
                hasError = false,
                lastRequestItemCount = -1,
            ),
        )
    }

    @Test
    fun paginationRejectsDuplicateLoadingAndErrorRequests() {
        assertFalse(shouldLoadMoreNearBrowseIndex(12, 20, 4, false, false, false, -1))
        assertFalse(shouldLoadMoreNearBrowseIndex(12, 20, 4, true, true, false, -1))
        assertFalse(shouldLoadMoreNearBrowseIndex(12, 20, 4, true, false, true, -1))
        assertFalse(shouldLoadMoreNearBrowseIndex(12, 20, 4, true, false, false, 20))
    }

    @Test
    fun focusedIndexBoundsMatchExistingGridPolicy() {
        assertEquals(-1, boundedAnimeFocusedIndexUpdate(itemCount = 0, currentIndex = 5))
        assertEquals(4, boundedAnimeFocusedIndexUpdate(itemCount = 5, currentIndex = 8))
        assertEquals(null, boundedAnimeFocusedIndexUpdate(itemCount = 5, currentIndex = -1))
        assertEquals(null, boundedAnimeFocusedIndexUpdate(itemCount = 5, currentIndex = 3))
    }
}
