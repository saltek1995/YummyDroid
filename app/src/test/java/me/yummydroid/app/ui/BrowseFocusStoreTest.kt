package me.yummydroid.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.BrowseSection

class BrowseFocusStoreTest {
    @Test
    fun sectionDefaultsMatchTheirInitialFocusContracts() {
        val store = BrowseFocusStore()

        assertEquals(-1, store.focusedIndex(BrowseSection.Catalog))
        assertEquals(0, store.focusedIndex(BrowseSection.Schedule))
        assertEquals(-1, store.focusedIndex(BrowseSection.History))
        assertEquals(-1, store.focusedIndex(BrowseSection.Downloads))
    }

    @Test
    fun focusIsStoredIndependentlyForEachNavigableSection() {
        val store = BrowseFocusStore()

        store.setFocusedIndex(BrowseSection.Catalog, 2)
        store.setFocusedIndex(BrowseSection.Schedule, 3)
        store.setFocusedIndex(BrowseSection.History, 4)
        store.setFocusedIndex(BrowseSection.Downloads, 9)

        assertEquals(2, store.focusedIndex(BrowseSection.Catalog))
        assertEquals(3, store.focusedIndex(BrowseSection.Schedule))
        assertEquals(4, store.focusedIndex(BrowseSection.History))
        assertEquals(-1, store.focusedIndex(BrowseSection.Downloads))
    }

    @Test
    fun focusRequestRetriesUntilTargetAcceptsFocus() {
        val attempts = mutableListOf<Int>()
        val focusRequest = FocusRequestJobRef(awaitFrame = {})
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        focusRequest.requestFocusWhenReady(index = 4, focusScope = scope) { index ->
            attempts += index
            attempts.size == 3
        }

        assertEquals(listOf(4, 4, 4), attempts)
        scope.cancel()
    }

    @Test
    fun newerFocusRequestSupersedesPendingTarget() = runBlocking {
        val frames = Channel<Unit>(Channel.UNLIMITED)
        val attempts = mutableListOf<Int>()
        val focusRequest = FocusRequestJobRef(awaitFrame = { frames.receive() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        focusRequest.requestFocusWhenReady(index = 1, focusScope = scope) { index ->
            attempts += index
            true
        }
        focusRequest.requestFocusWhenReady(index = 2, focusScope = scope) { index ->
            attempts += index
            true
        }
        frames.send(Unit)
        frames.send(Unit)
        focusRequest.job?.join()

        assertEquals(listOf(2), attempts)
        scope.cancel()
    }
}
