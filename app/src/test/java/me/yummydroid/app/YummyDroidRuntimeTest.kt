package me.yummydroid.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YummyDroidRuntimeTest {
    @Test
    fun runtimeRemainsIndependentFromAndroidViewModelLifecycle() {
        assertFalse(ViewModel::class.java.isAssignableFrom(YummyDroidRuntime::class.java))
    }

    @Test
    fun runtimeKeepsActionsExposedByLifecycleFacade() {
        val runtimeMethods = YummyDroidRuntime::class.java.methods.mapTo(mutableSetOf()) { it.name }
        val facadeActions = setOf(
            "refresh",
            "updateSearchQuery",
            "openAnime",
            "playVideo",
            "navigateBack",
            "updateSettings",
            "logout",
        )

        assertTrue(runtimeMethods.containsAll(facadeActions))
    }

    @Test
    fun latestOperationWaitsForCancelledCriticalSectionBeforeStartingReplacement() = runBlocking {
        val coordinator = LatestStateOperationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launchLatest(this) { lease ->
            firstStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirst.await() }
            events += "first:${lease.isCurrent}"
        }
        firstStarted.await()
        val replacement = coordinator.launchLatest(this) { lease ->
            events += "second:${lease.isCurrent}"
        }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        replacement.join()
        assertEquals(listOf("first:false", "second:true"), events)
    }

    @Test
    fun serialOperationPreservesInvocationOrderAndOnlyLatestLeasePublishes() = runBlocking {
        val coordinator = SerialStateOperationCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launch(this) { lease ->
            releaseFirst.await()
            events += "first:${lease.isCurrent}"
        }
        val second = coordinator.launch(this) { lease ->
            events += "second:${lease.isCurrent}"
        }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        second.join()
        assertEquals(listOf("first:false", "second:true"), events)
    }

    @Test
    fun serialReplacementWaitsForCancelledNonCancellableOperation() = runBlocking {
        val coordinator = SerialStateOperationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launch(this) {
            firstStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirst.await() }
            events += "first"
        }
        firstStarted.await()
        coordinator.cancel()
        val replacement = coordinator.launch(this) { events += "second" }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        replacement.join()
        assertEquals(listOf("first", "second"), events)
    }
}
