package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile

class AnimeCardMarksTest {
    private val watching = UserAnimeMark(UserAnimeListMark.Watching)
    private val favorite = UserAnimeMark(isFavorite = true)

    @Test
    fun cardsWaitForFirstMarkSnapshotWhenCacheIsMissing() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val harness = Harness({ finish.await(); mapOf(10L to watching) })
        harness.state = harness.state.copy(featured = LoadState.Ready(listOf(watchHistoryAnime(10))))
        val load = launch { harness.coordinator.synchronize() }
        yield()
        assertFalse(harness.state.cardAnimeMarksReady)
        assertIs<LoadState.Loading>(harness.state.withReadyCardMarks().featured)
        assertIs<LoadState.Ready<*>>(harness.state.featured)
        finish.complete(Unit)
        load.join()
        val displayed = harness.state.withReadyCardMarks()
        assertIs<LoadState.Ready<*>>(displayed.featured)
        assertEquals(watching, displayed.cardAnimeMarks[10])
    }

    @Test
    fun savedSnapshotReleasesCardsWithMarksBeforeNetworkRefreshCompletes() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val harness = Harness({ finish.await(); mapOf(10L to favorite) }, cached = { mapOf(10L to watching) })
        harness.state = harness.state.copy(featured = LoadState.Ready(listOf(watchHistoryAnime(10))))
        val load = launch { harness.coordinator.synchronize() }
        yield()
        val displayed = harness.state.withReadyCardMarks()
        assertTrue(displayed.cardAnimeMarksReady)
        assertIs<LoadState.Ready<*>>(displayed.featured)
        assertEquals(watching, displayed.cardAnimeMarks[10])
        finish.complete(Unit)
        load.join()
        assertEquals(favorite, harness.state.cardAnimeMarks[10])
    }

    @Test
    fun cachedMarksAreAvailableOfflineAndBeforeAuthRefreshFinishes() = runBlocking {
        var requests = 0
        val harness = Harness({ requests++; emptyMap() }, cached = { mapOf(10L to watching) })
        harness.state = harness.state.copy(auth = harness.state.auth.copy(loading = true))
        harness.coordinator.synchronize()
        assertTrue(harness.state.cardAnimeMarksReady)
        assertEquals(watching, harness.state.cardAnimeMarks[10])
        harness.state = harness.state.copy(forcedOfflineMode = true, auth = harness.state.auth.copy(loading = false))
        harness.coordinator.synchronize()
        assertEquals(0, requests)
        assertTrue(harness.state.cardAnimeMarksReady)
    }

    @Test
    fun persistenceStoresConfirmedChangesAndRemovalButNeverOptimisticState() = runBlocking {
        val saved = mutableListOf<Map<Long, UserAnimeMark>>()
        val harness = Harness({ mapOf(10L to watching) }, saved = { _, marks -> saved += marks })
        harness.coordinator.synchronize()
        harness.coordinator.recordMark(1, 10, LoadState.Ready(favorite), LoadState.Ready(watching))
        assertEquals(watching, saved.last()[10])
        assertEquals(favorite, harness.state.cardAnimeMarks[10])
        harness.coordinator.recordMark(1, 10, LoadState.Ready(favorite))
        assertEquals(favorite, saved.last()[10])
        harness.coordinator.recordMark(1, 10, LoadState.Ready(UserAnimeMark()))
        assertTrue(saved.last().isEmpty())
    }

    @Test
    fun failedInitialRequestReleasesCardsAndEmptyCacheIsAValidSnapshot() = runBlocking {
        val harness = Harness({ error("unavailable") })
        harness.coordinator.synchronize()
        assertTrue(harness.state.cardAnimeMarksReady)
        val cached = Harness({ error("unavailable") }, cached = { emptyMap() })
        cached.state = cached.state.copy(auth = cached.state.auth.copy(loading = true))
        cached.coordinator.synchronize()
        assertTrue(cached.state.cardAnimeMarksReady)
        assertTrue(cached.state.cardAnimeMarks.isEmpty())
    }

    @Test
    fun individualReadsAfterFailedBulkLoadDoNotSaveAnIncompleteFullSnapshot() = runBlocking {
        val saved = mutableListOf<Map<Long, UserAnimeMark>>()
        val harness = Harness({ error("unavailable") }, saved = { _, marks -> saved += marks })
        harness.coordinator.synchronize()
        harness.coordinator.recordMark(1, 10, LoadState.Ready(watching))
        assertEquals(watching, harness.state.cardAnimeMarks[10])
        assertTrue(saved.isEmpty())
    }

    @Test
    fun goingOfflineWhileAuthIsLoadingReleasesCardsWithoutWaitingForAuth() = runBlocking {
        val state = MutableStateFlow(YummyDroidUiState(auth = AuthUiState(profile = profile(1), loading = true)))
        val coordinator = AnimeCardMarksCoordinator({ state.value }, { state.value = it(state.value) },
            fetchMarks = { error("Offline startup must not request marks") })
        val observer = launch { observeAnimeCardMarks(state, coordinator) }
        yield()
        assertFalse(state.value.cardAnimeMarksReady)
        state.value = state.value.copy(forcedOfflineMode = true)
        withTimeout(5_000) { state.first { it.cardAnimeMarksReady } }
        assertTrue(state.value.auth.loading)
        observer.cancel()
        observer.join()
    }

    @Test
    fun loadsOnceAndKeepsMarksWhenGoingOffline() = runBlocking {
        var calls = 0
        val harness = Harness(fetch = { calls++; mapOf(10L to watching) })
        harness.coordinator.synchronize()
        harness.coordinator.synchronize()
        harness.state = harness.state.copy(forcedOfflineMode = true)
        harness.coordinator.synchronize()
        assertEquals(mapOf(10L to watching), harness.state.cardAnimeMarks)
        harness.state = harness.state.copy(forcedOfflineMode = false)
        harness.coordinator.synchronize()
        assertEquals(1, calls)
    }

    @Test
    fun waitsForAuthAndConnectivityAndCanRetryFailedLoad() = runBlocking {
        var calls = 0
        val harness = Harness(fetch = {
            calls++
            if (calls == 1) error("network unavailable")
            mapOf(10L to watching)
        })
        harness.state = harness.state.copy(auth = harness.state.auth.copy(loading = true))
        harness.coordinator.synchronize()
        harness.state = harness.state.copy(auth = harness.state.auth.copy(loading = false), forcedOfflineMode = true)
        harness.coordinator.synchronize()
        assertEquals(0, calls)
        harness.state = harness.state.copy(forcedOfflineMode = false)
        harness.coordinator.synchronize()
        assertTrue(harness.state.cardAnimeMarks.isEmpty())
        harness.coordinator.synchronize()
        assertEquals(mapOf(10L to watching), harness.state.cardAnimeMarks)
        assertEquals(2, calls)
    }

    @Test
    fun staleBulkResponseDoesNotOverwriteUpdatesOrResurrectRemovedMarks() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val harness = Harness(fetch = { finish.await(); mapOf(10L to watching, 20L to favorite, 30L to watching) })
        val load = launch { harness.coordinator.synchronize() }
        yield()
        harness.coordinator.recordMark(1, 10, LoadState.Ready(favorite))
        harness.coordinator.recordMark(1, 20, LoadState.Ready(UserAnimeMark()))
        finish.complete(Unit)
        load.join()
        assertEquals(mapOf(10L to favorite, 30L to watching), harness.state.cardAnimeMarks)
    }

    @Test
    fun obsoleteRequestCannotLeakMarksAcrossAccountChangesEvenWhenCancellationIsIgnored() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val harness = Harness(fetch = { id ->
            if (id == 1L) withContext(NonCancellable) { finish.await(); mapOf(10L to watching) }
            else mapOf(20L to favorite)
        })
        val old = launch { harness.coordinator.synchronize() }
        yield()
        harness.state = harness.state.copy(auth = AuthUiState(profile = profile(2)))
        harness.coordinator.synchronize()
        old.cancel()
        finish.complete(Unit)
        old.join()
        assertEquals(mapOf(20L to favorite), harness.state.cardAnimeMarks)
        harness.coordinator.recordMark(1, 10, LoadState.Ready(watching))
        assertEquals(mapOf(20L to favorite), harness.state.cardAnimeMarks)
        harness.state = harness.state.copy(auth = AuthUiState())
        harness.coordinator.synchronize()
        assertTrue(harness.state.cardAnimeMarks.isEmpty())
    }

    @Test
    fun failedReadsKeepLastConfirmedMarkAndFailedUnknownWritesRestoreBulkBaseline() = runBlocking {
        val harness = Harness(fetch = { mapOf(10L to watching, 20L to watching) })
        harness.coordinator.synchronize()
        harness.coordinator.recordMark(1, 10, LoadState.Ready(favorite))
        harness.coordinator.recordMark(1, 10, LoadState.Error("read failed"))
        assertEquals(favorite, harness.state.cardAnimeMarks[10])
        harness.coordinator.recordMark(1, 20, LoadState.Ready(favorite), LoadState.Loading)
        assertEquals(favorite, harness.state.cardAnimeMarks[20])
        harness.coordinator.recordMark(1, 20, LoadState.Error("write failed"))
        assertEquals(watching, harness.state.cardAnimeMarks[20])
    }

    @Test
    fun newSessionForSameAccountClearsOverridesAndReloads() = runBlocking {
        var calls = 0
        val harness = Harness(fetch = { calls++; mapOf(10L to watching) })
        harness.coordinator.synchronize()
        harness.coordinator.recordMark(1, 10, LoadState.Ready(favorite))
        harness.state = harness.state.copy(contentSessionRevision = 1)
        harness.coordinator.synchronize()
        assertEquals(mapOf(10L to watching), harness.state.cardAnimeMarks)
        assertEquals(2, calls)
    }

    @Test
    fun accountTransitionClearsMarksImmediatelyButOfflineAndLanguageChangesKeepThem() {
        val state = YummyDroidUiState(auth = AuthUiState(profile = profile(1)), cardAnimeMarks = mapOf(10L to watching))
        assertTrue(state.copy(auth = AuthUiState(profile = profile(2))).withContentContextTransition(state).cardAnimeMarks.isEmpty())
        assertTrue(state.copy(contentSessionRevision = 1).withContentContextTransition(state).cardAnimeMarks.isEmpty())
        assertEquals(state.cardAnimeMarks, state.copy(forcedOfflineMode = true).withContentContextTransition(state).cardAnimeMarks)
        assertEquals(state.cardAnimeMarks, state.copy(settings = state.settings.copy(contentLanguage = me.yummydroid.app.data.ContentLanguage.English))
            .withContentContextTransition(state).cardAnimeMarks)
    }

    private class Harness(
        fetch: suspend (Long) -> Map<Long, UserAnimeMark>,
        cached: suspend (Long) -> Map<Long, UserAnimeMark>? = { null },
        saved: (Long, Map<Long, UserAnimeMark>) -> Unit = { _, _ -> },
    ) {
        var state = YummyDroidUiState(auth = AuthUiState(profile = profile(1)))
        val coordinator = AnimeCardMarksCoordinator({ state }, { state = it(state) }, fetch,
            readCachedMarks = cached, saveCachedMarks = saved)
    }

    companion object {
        private fun profile(id: Long) = UserProfile(id, "User $id", "")
    }
}
