package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class UserContentRefreshCoordinatorTest {
    @Test
    fun onlineRefreshWaitsForCacheInvalidationBeforeReloading() = runBlocking {
        val invalidated = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val coordinator = UserContentRefreshCoordinator(this, { YummyDroidUiState() },
            invalidateRemoteCache = { events += "remote"; invalidated.await() },
            invalidateRouteCaches = { events += "routes" }, reloadRoute = { events += "reload" },
        )
        coordinator.refresh()
        yield()
        assertEquals(listOf("remote"), events)
        invalidated.complete(Unit)
        yield()
        assertEquals(listOf("remote", "routes", "reload"), events)
    }

    @Test
    fun changedRouteOrContextPreventsLateRefreshFromReplacingNewContent() = runBlocking {
        for (changeRoute in listOf(false, true)) {
            val invalidated = CompletableDeferred<Unit>()
            var state = YummyDroidUiState()
            var cleared = 0
            var reloaded = 0
            val coordinator = UserContentRefreshCoordinator(this, { state }, { invalidated.await() },
                { cleared++ }, { reloaded++ },
            )
            coordinator.refresh()
            yield()
            state = if (changeRoute) state.copy(route = AppRoute.Details(42))
                else state.copy(contentSessionRevision = 1)
            invalidated.complete(Unit)
            yield()
            assertEquals(0, cleared)
            assertEquals(0, reloaded)
        }
    }

    @Test
    fun offlineRefreshSkipsRemoteInvalidation() = runBlocking {
        var invalidations = 0
        var reloads = 0
        val coordinator = UserContentRefreshCoordinator(this, { YummyDroidUiState(forcedOfflineMode = true) },
            { invalidations++ }, {}, { reloads++ },
        )
        coordinator.refresh()
        yield()
        assertEquals(0, invalidations)
        assertEquals(1, reloads)
    }
}
