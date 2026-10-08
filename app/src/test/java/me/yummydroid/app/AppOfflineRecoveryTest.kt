package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.YummyAnimeRepository

class AppOfflineRecoveryTest {
    @Test
    fun backgroundNetworkLossDoesNotSwitchModeAndResumeChecksImmediately() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.connected = false
            fixture.start()
            fixture.networkEvents.send(Unit)
            drainEvents()
            assertEquals(0, fixture.confirmations)
            assertEquals(0, fixture.offlineCalls)

            fixture.foreground.value = true
            withTimeout(2_000) { fixture.confirmationStarted.await() }
            fixture.confirmation.complete(Unit)
            withTimeout(2_000) { fixture.offlineEntered.await() }
            assertTrue(fixture.state.forcedOfflineMode)

            fixture.networkEvents.send(Unit)
            drainEvents()
            assertEquals(1, fixture.offlineCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun transientLossDoesNotEnterOfflineEvenWithoutAnotherNetworkCallback() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.connected = false
            fixture.foreground.value = true
            fixture.start()
            withTimeout(2_000) { fixture.confirmationStarted.await() }
            fixture.connected = true
            fixture.confirmation.complete(Unit)
            drainEvents()
            assertEquals(0, fixture.offlineCalls)
            assertFalse(fixture.state.forcedOfflineMode)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun backgroundingCancelsPendingLossConfirmation() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.connected = false
            fixture.foreground.value = true
            fixture.start()
            withTimeout(2_000) { fixture.confirmationStarted.await() }
            fixture.foreground.value = false
            drainEvents()
            fixture.confirmation.complete(Unit)
            drainEvents()
            assertEquals(0, fixture.offlineCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun resumeRecoversOfflineModeWithoutWaitingForNetworkEventOrPoll() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.state = fixture.state.copy(forcedOfflineMode = true)
            fixture.start()
            drainEvents()
            assertEquals(0, fixture.probes)
            fixture.foreground.value = true
            withTimeout(2_000) { fixture.probeStarted.await() }
            fixture.reachableSite.complete("https://example.invalid")
            withTimeout(2_000) { fixture.routeReloaded.await() }
            assertFalse(fixture.state.forcedOfflineMode)
            assertEquals("https://example.invalid", fixture.state.siteBaseUrl)
            assertEquals(1, fixture.reloads)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun backgroundingCancelsInFlightRecovery() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.state = fixture.state.copy(forcedOfflineMode = true)
            fixture.foreground.value = true
            fixture.start()
            withTimeout(2_000) { fixture.probeStarted.await() }
            fixture.foreground.value = false
            drainEvents()
            fixture.reachableSite.complete("https://example.invalid")
            drainEvents()
            assertTrue(fixture.state.forcedOfflineMode)
            assertEquals(0, fixture.reloads)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryCannotPublishIfNetworkDisappearedDuringProbe() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.state = fixture.state.copy(forcedOfflineMode = true)
            fixture.foreground.value = true
            fixture.start()
            withTimeout(2_000) { fixture.probeStarted.await() }
            fixture.connected = false
            fixture.reachableSite.complete("https://example.invalid")
            drainEvents()
            assertTrue(fixture.state.forcedOfflineMode)
            assertEquals(0, fixture.reloads)
        } finally {
            fixture.close()
        }
    }

    private suspend fun drainEvents() {
        repeat(20) { yield() }
    }

    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val foreground = MutableStateFlow(false)
        val networkEvents = Channel<Unit>(Channel.UNLIMITED)
        val confirmationStarted = CompletableDeferred<Unit>()
        val confirmation = CompletableDeferred<Unit>()
        val offlineEntered = CompletableDeferred<Unit>()
        val probeStarted = CompletableDeferred<Unit>()
        val reachableSite = CompletableDeferred<String?>()
        val routeReloaded = CompletableDeferred<Unit>()
        var connected = true
        var state = YummyDroidUiState()
        var confirmations = 0
        var offlineCalls = 0
        var probes = 0
        var reloads = 0
        private val runtime = AppContentRefreshRuntime(
            scope = scope,
            repository = YummyAnimeRepository(isNetworkAvailable = { connected }),
            currentState = { state },
            updateState = { state = it(state) },
            reloadCurrentRoute = { reloads++; routeReloaded.complete(Unit) },
            networkChanges = networkEvents.receiveAsFlow(),
            onOffline = {
                offlineCalls++
                state = state.copy(forcedOfflineMode = true)
                offlineEntered.complete(Unit)
            },
            appForeground = foreground,
            awaitOfflineConfirmation = {
                confirmations++
                confirmationStarted.complete(Unit)
                confirmation.await()
            },
            checkReachableSiteBaseUrl = {
                probes++
                probeStarted.complete(Unit)
                reachableSite.await()
            },
        )

        fun start() = runtime.startOfflineRecoveryMonitor()
        fun close() = scope.cancel()
    }
}
