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
import kotlin.test.assertTrue

class VisualFocusGridStateTest {
    @Test
    fun disposingLoadingPlayerCannotUnregisterTheReadyPlayer() {
        val controller = AppNavigationController()
        fun adapter() = PlayerInputController({ true }, { true }, { true })
        val unregisterLoading = controller.bindPlayerInput(adapter())
        val ready = adapter()
        val unregisterReady = controller.bindPlayerInput(ready)
        unregisterLoading()
        assertEquals(ready, controller.playerInputController)
        unregisterReady()
        assertNull(controller.playerInputController)
    }

    @Test
    fun lastControlScrollsBothDirectionsToTheExactEdgeWithoutChangingFocus() {
        val controller = AppNavigationController()
        val scope = Any()
        var offset = 0
        val maximum = 1050
        val region = NavigationScrollRegion(
            canScroll = { if (it == VisualGridDirection.Up) offset > 0 else offset < maximum },
            scroll = { offset = (offset + if (it == VisualGridDirection.Up) -300 else 300).coerceIn(0, maximum) },
        )
        val source = NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 0f, 20f - offset, 100f, 70f - offset) },
            requestFocus = { error("Scrolling must keep the existing focus") }, scrollRegion = { region },
        ).apply { focused = true }
        controller.registerFocusTarget(scope, source)
        repeat(6) { assertTrue(controller.moveWindowFocus(scope, VisualGridDirection.Down)) }
        assertEquals(maximum, offset)
        assertTrue(source.focused)
        assertFalse(controller.scrollFocusedRegion(VisualGridDirection.Right))
        repeat(6) { assertTrue(controller.moveWindowFocus(scope, VisualGridDirection.Up)) }
        assertEquals(0, offset)
        assertTrue(source.focused)
    }

    @Test
    fun remainingScrollIsDrainedBeforeMovingToAFooterOutsideTheList() {
        val controller = AppNavigationController()
        val scope = Any()
        var remainingScroll = true
        var footerFocused = false
        val region = NavigationScrollRegion({ remainingScroll }, { remainingScroll = false })
        controller.registerFocusTarget(scope, NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 0f, 0f, 100f, 50f) },
            requestFocus = { true }, scrollRegion = { region },
        ).apply { focused = true })
        controller.registerFocusTarget(scope, NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 0f, 100f, 100f, 150f) },
            requestFocus = { footerFocused = true; true },
        ))
        controller.moveWindowFocus(scope, VisualGridDirection.Down)
        assertFalse(remainingScroll)
        assertFalse(footerFocused)
        controller.moveWindowFocus(scope, VisualGridDirection.Down)
        assertTrue(footerFocused)
    }

    @Test
    fun nestedScrollFallsBackToItsParentButNeverToAnotherDialog() {
        val events = mutableListOf<String>()
        val outer = NavigationScrollRegion({ true }, { events += "outer" })
        var innerCanScroll = true
        val inner = NavigationScrollRegion({ innerCanScroll }, { events += "inner" }).apply { parent = outer }
        assertFalse(scrollBeforeLeavingRegion(inner, inner, VisualGridDirection.Down))
        assertTrue(scrollBeforeLeavingRegion(inner, null, VisualGridDirection.Down))
        innerCanScroll = false
        assertTrue(scrollBeforeLeavingRegion(inner, null, VisualGridDirection.Down))
        val modal = NavigationScrollRegion({ false }, { error("Exhausted dialog must not scroll") })
        assertFalse(scrollBeforeLeavingRegion(modal, null, VisualGridDirection.Down))
        assertEquals(listOf("inner", "outer"), events)
    }

    @Test
    fun disposalOfAnOldModalRegistrationCannotRemoveItsReplacement() {
        val controller = AppNavigationController()
        val oldRegistration = Any()
        val currentRegistration = Any()
        val owner = AppScreenKey.Home
        controller.registerModalInputActionHandler(owner, { false }, oldRegistration)
        controller.registerModalInputActionHandler(owner, { true }, currentRegistration)
        controller.registerModalInputActionHandler(owner, null, oldRegistration)
        assertTrue(controller.activeModalInputActionHandler(owner, null)?.invoke(me.yummydroid.app.InputAction.Back) == true)
        controller.registerModalInputActionHandler(owner, null, currentRegistration)
        assertNull(controller.activeModalInputActionHandler(owner, null))
    }

    @Test
    fun windowTraversalExcludesOtherScopesAndDisabledTargets() {
        val controller = AppNavigationController()
        val dialog = Any()
        val screen = Any()
        val requested = mutableListOf<String>()
        fun target(name: String, x: Float, enabled: Boolean = true) = NavigationFocusTarget(
            enabled = { enabled },
            bounds = { VisualFocusBounds(0, x, 0f, x + 40f, 40f) },
            requestFocus = { requested += name; true },
        )
        val source = target("source", 0f).apply { focused = true }
        val neighbour = target("neighbour", 150f)
        controller.registerFocusTarget(dialog, source)
        controller.registerFocusTarget(dialog, target("disabled", 50f, enabled = false))
        controller.registerFocusTarget(screen, target("background", 50f))
        controller.registerFocusTarget(dialog, neighbour)
        assertTrue(controller.moveWindowFocus(dialog, VisualGridDirection.Right))
        assertEquals(listOf("neighbour"), requested)
        controller.unregisterFocusTarget(dialog, neighbour)
        requested.clear()
        assertTrue(controller.moveWindowFocus(dialog, VisualGridDirection.Right))
        assertTrue(requested.isEmpty())
    }

    @Test
    fun unavailableNeighbourDoesNotTriggerADifferentFocusSearch() {
        val controller = AppNavigationController()
        val scope = Any()
        var attempted = 0
        controller.registerFocusTarget(scope, NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 100f, 100f, 150f, 150f) },
            requestFocus = { true },
        ).apply { focused = true })
        controller.registerFocusTarget(scope, NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 200f, 100f, 250f, 150f) },
            requestFocus = { attempted++; false },
        ))
        controller.registerFocusTarget(scope, NavigationFocusTarget(
            enabled = { true }, bounds = { VisualFocusBounds(0, 300f, 300f, 350f, 350f) },
            requestFocus = { error("Must not fall through to a diagonal target") },
        ))
        assertTrue(controller.moveWindowFocus(scope, VisualGridDirection.Right))
        assertEquals(1, attempted)
    }

    @Test
    fun rootCancellationDoesNotCancelAnotherOwnersFocusRequest() = runBlocking {
        val controller = AppNavigationController()
        val finished = CompletableDeferred<Unit>()
        controller.launch(this, Any(), UiControlOperation.NavigationLatest) { finished.await() }
        controller.cancelRootUiTransition()
        assertTrue(controller.isActive(UiControlOperation.NavigationLatest))
        finished.complete(Unit)
        Unit
    }

    @Test
    fun cursorNavigationRequiresBothAnActiveEditorAndVisibleKeyboard() {
        assertTrue(shouldEditTextWithArrows(fieldFocused = true, keyboardVisible = true))
        assertFalse(shouldEditTextWithArrows(fieldFocused = true, keyboardVisible = false))
        assertFalse(shouldEditTextWithArrows(fieldFocused = false, keyboardVisible = true))
        assertFalse(shouldEditTextWithArrows(fieldFocused = false, keyboardVisible = false))
    }

    @Test
    fun explicitDirectionBoundaryIsConsumedWithoutDisablingOtherDirections() {
        val policy = VisualFocusNavigationPolicy(
            horizontal = true,
            vertical = true,
            blockedDirections = setOf(VisualGridDirection.Right),
        )

        assertFalse(policy.canNavigate(VisualGridDirection.Right))
        assertTrue(policy.owns(VisualGridDirection.Right))
        assertTrue(policy.canNavigate(VisualGridDirection.Left))
        assertTrue(policy.canNavigate(VisualGridDirection.Up))
        assertTrue(policy.canNavigate(VisualGridDirection.Down))
    }

    @Test
    fun disabledAxisPassesThroughUnlessItIsExplicitlyConsumed() {
        val passThrough = VisualFocusNavigationPolicy(horizontal = false, vertical = true)
        val consumed = passThrough.copy(consumeDisabledAxis = true)

        assertFalse(passThrough.owns(VisualGridDirection.Left))
        assertTrue(consumed.owns(VisualGridDirection.Left))
    }

    @Test
    fun inactiveLayerSuppressesUiControlEffects() {
        assertTrue(shouldRunUiControlEffect(layerEnabled = true, effectEnabled = true))
        assertFalse(shouldRunUiControlEffect(layerEnabled = false, effectEnabled = true))
        assertFalse(shouldRunUiControlEffect(layerEnabled = true, effectEnabled = false))
    }

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
    fun missingLayoutDoesNotGuessAHorizontalNeighbor() {
        val state = VisualFocusGridState(size = 3)

        assertNull(state.focusTarget(index = 0, direction = VisualGridDirection.Right, exit = null))
        assertNull(state.focusTarget(index = 0, direction = VisualGridDirection.Left, exit = null))
        assertTrue(state.requestFocusTarget(index = 0, direction = VisualGridDirection.Right, exit = null))
    }

    @Test
    fun serialFocusTransitionCoalescesRepeatedRequestFromCurrentOwner() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val coordinator = AppNavigationController()
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
        val coordinator = AppNavigationController()

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
        val coordinator = AppNavigationController()

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
        val coordinator = AppNavigationController()

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
        val coordinator = AppNavigationController()

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
    fun contentScrollCannotCancelFocusNavigation() = runBlocking {
        val navigationRelease = CompletableDeferred<Unit>()
        val contentScrollRelease = CompletableDeferred<Unit>()
        val coordinator = AppNavigationController()

        coordinator.launch(this, Any(), UiControlOperation.NavigationSerial) {
            navigationRelease.await()
        }
        coordinator.launch(this, Any(), UiControlOperation.ContentScrollLatest) {
            contentScrollRelease.await()
        }
        yield()

        assertTrue(coordinator.isActive(UiControlOperation.NavigationLatest))
        assertTrue(coordinator.isActive(UiControlOperation.ContentScrollLatest))
        navigationRelease.complete(Unit)
        contentScrollRelease.complete(Unit)
        Unit
    }

    @Test
    fun playbackCommandDoesNotCancelNavigation() = runBlocking {
        val navigationRelease = CompletableDeferred<Unit>()
        val playbackRelease = CompletableDeferred<Unit>()
        val coordinator = AppNavigationController()

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
    fun modalTransitionCancelsInteractiveWorkButKeepsPlayback() = runBlocking {
        val navigationCancelled = CompletableDeferred<Unit>()
        val playbackRelease = CompletableDeferred<Unit>()
        val coordinator = AppNavigationController()

        coordinator.launch(this, Any(), UiControlOperation.NavigationLatest) {
            try {
                awaitCancellation()
            } finally {
                navigationCancelled.complete(Unit)
            }
        }
        coordinator.launch(this, Any(), UiControlOperation.PlaybackLatest) {
            playbackRelease.await()
        }
        yield()

        coordinator.cancelInteractive()
        navigationCancelled.await()
        assertFalse(coordinator.isActive(UiControlOperation.NavigationLatest))
        assertTrue(coordinator.isActive(UiControlOperation.PlaybackLatest))
        playbackRelease.complete(Unit)
        Unit
    }

    @Test
    fun staleOwnerCannotCancelReplacement() = runBlocking {
        val replacementRelease = CompletableDeferred<Unit>()
        val coordinator = AppNavigationController()
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
        val coordinator = AppNavigationController()
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
