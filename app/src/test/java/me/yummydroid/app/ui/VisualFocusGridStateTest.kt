package me.yummydroid.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VisualFocusGridStateTest {
    @Test
    fun requesterAccessStaysInsideConfiguredGridSize() {
        val state = VisualFocusGridState(size = 3)

        assertEquals(3, state.size)
        assertNull(state.requester(-1))
        assertNotNull(state.requester(0))
        assertNotNull(state.requester(2))
        assertNull(state.requester(3))
    }

    @Test
    fun focusUpdatesKeepLastFocusedIndexAfterFocusLeaves() {
        val state = VisualFocusGridState(size = 3)

        state.updateFocusedIndex(index = 1, focused = true)

        assertEquals(1, state.focusedIndex)
        assertEquals(1, state.lastFocusedIndex)

        state.updateFocusedIndex(index = 1, focused = false)

        assertNull(state.focusedIndex)
        assertEquals(1, state.lastFocusedIndex)
    }

    @Test
    fun directionalFallbackUsesAdjacentRequesterBeforeLayout() {
        val state = VisualFocusGridState(size = 3)

        assertSame(
            state.requester(1),
            state.focusTarget(index = 0, direction = VisualGridDirection.Right, exit = null),
        )
        assertNull(state.focusTarget(index = 0, direction = VisualGridDirection.Left, exit = null))
    }

    @Test
    fun serialFocusTransitionCoalescesRepeatedRequestFromCurrentOwner() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()
        val owner = Any()

        assertTrue(coordinator.launch(this, owner, UiControlOperation.NavigationSerial) { release.await() })
        yield()
        assertTrue(coordinator.isActive(UiControlOperation.NavigationSerial))
        assertFalse(coordinator.launch(this, owner, UiControlOperation.NavigationSerial) { })

        release.complete(Unit)
        yield()
        assertFalse(coordinator.isActive(UiControlOperation.NavigationSerial))
    }

    @Test
    fun latestFocusTransitionCancelsStaleOwner() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()

        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        yield()
        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) { }

        cancelled.await()
        yield()
        assertFalse(coordinator.isActive(UiControlOperation.NavigationLatest))
    }

    @Test
    fun serialFocusTransitionAllowsNewOwnerToReplaceStaleOwner() = runBlocking {
        val staleCancelled = CompletableDeferred<Unit>()
        val replacementFinished = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()

        coordinator.launch(this, Any(), UiControlOperation.NavigationSerial) {
            try {
                awaitCancellation()
            } finally {
                staleCancelled.complete(Unit)
            }
        }
        yield()
        coordinator.launch(this, Any(), UiControlOperation.NavigationSerial) {
            replacementFinished.complete(Unit)
        }

        staleCancelled.await()
        replacementFinished.await()
        assertFalse(coordinator.isActive(UiControlOperation.NavigationSerial))
    }

    @Test
    fun operationArgumentKeepsNavigationAndRelocationIndependent() = runBlocking {
        val navigationRelease = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()

        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            navigationRelease.await()
        }
        coordinator.launch(this, Any(), UiControlOperation.RelocationLatest) { }
        yield()

        assertTrue(coordinator.isActive(UiControlOperation.NavigationLatest))
        assertFalse(coordinator.isActive(UiControlOperation.RelocationLatest))
        navigationRelease.complete(Unit)
        Unit
    }

    @Test
    fun pageTransitionCannotBeCancelledByFocusNavigation() = runBlocking {
        val pageTransitionRelease = CompletableDeferred<Unit>()
        val navigationRelease = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()

        coordinator.launch(this, Any(), UiControlOperation.PageTransitionLatest) {
            pageTransitionRelease.await()
        }
        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            navigationRelease.await()
        }
        yield()

        assertTrue(coordinator.isActive(UiControlOperation.PageTransitionLatest))
        assertTrue(coordinator.isActive(UiControlOperation.NavigationLatest))
        pageTransitionRelease.complete(Unit)
        navigationRelease.complete(Unit)
        Unit
    }

    @Test
    fun playbackCommandDoesNotCancelNavigation() = runBlocking {
        val navigationRelease = CompletableDeferred<Unit>()
        val playbackRelease = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()

        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            navigationRelease.await()
        }
        coordinator.launch(this, Any(), UiControlOperation.PlaybackLatest) {
            playbackRelease.await()
        }
        yield()

        assertTrue(coordinator.isActive(UiControlOperation.NavigationLatest))
        assertTrue(coordinator.isActive(UiControlOperation.PlaybackLatest))
        navigationRelease.complete(Unit)
        playbackRelease.complete(Unit)
        Unit
    }

    @Test
    fun staleOwnerCannotCancelReplacement() = runBlocking {
        val replacementRelease = CompletableDeferred<Unit>()
        val coordinator = UiControlCoordinator()
        val staleOwner = Any()

        coordinator.launch(this, staleOwner, UiControlOperation.NavigationLatest) { awaitCancellation() }
        val replacementOwner = Any()
        coordinator.launch(this, replacementOwner, UiControlOperation.NavigationLatest) {
            replacementRelease.await()
        }
        coordinator.cancel(staleOwner, UiControlOperation.NavigationLatest)
        yield()

        assertTrue(coordinator.isActive(UiControlOperation.NavigationLatest))
        replacementRelease.complete(Unit)
        Unit
    }

    @Test
    fun latestUiOperationWaitsForCancelledCriticalSectionBeforeReplacement() = runBlocking {
        val coordinator = UiControlCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondFinished = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            firstStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirst.await() }
            events += "first"
        }
        firstStarted.await()
        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            events += "second"
            secondFinished.complete(Unit)
        }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        secondFinished.await()
        assertEquals(listOf("first", "second"), events)
    }
}
