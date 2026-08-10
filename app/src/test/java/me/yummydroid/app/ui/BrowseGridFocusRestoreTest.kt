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
}
