package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.PlaybackProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryRuntimeTest {
    @Test
    fun fullHistoryResponseCannotOverwriteProgressSavedWhileRequestWasPending() = runBlocking {
        assertStaleHistoryRejected(reset = false)
    }

    @Test
    fun fullHistoryResponseCannotResurrectAnimeResetWhileRequestWasPending() = runBlocking {
        assertStaleHistoryRejected(reset = true)
    }

    @Test
    fun animeHistoryResponseCannotOverwriteProgressSavedWhileRequestWasPending() = runBlocking {
        assertStaleHistoryRejected(reset = false, singleAnime = true)
    }

    @Test
    fun animeHistoryResponseCannotResurrectResetWhileRequestWasPending() = runBlocking {
        assertStaleHistoryRejected(reset = true, singleAnime = true)
    }

    private suspend fun assertStaleHistoryRejected(reset: Boolean, singleAnime: Boolean = false) = kotlinx.coroutines.coroutineScope {
        val old = watchHistoryProgress(1, 10, positionMs = 100, updatedAtMs = 100)
        val newer = old.copy(positionMs = 900, updatedAtMs = 900)
        val stored = mutableListOf(old)
        var revision = 0L
        val response = CompletableDeferred<List<PlaybackProgress>>()
        val requested = CompletableDeferred<Unit>()
        var replacements = 0
        val coordinator = WatchHistoryCoordinator(
            readProgress = { stored.toList() },
            saveProgressIfNewer = {},
            readHistoryRevision = { revision },
            replaceHistoryIfRevision = { entries, expected ->
                if (expected != revision) null else {
                    replacements++
                    stored.clear()
                    stored.addAll(entries)
                    ++revision
                }
            },
            replaceAnimeHistoryIfRevision = { animeId, entries, expected ->
                if (expected != revision) null else {
                    replacements++
                    stored.removeAll { it.animeId == animeId }
                    stored.addAll(entries)
                    ++revision
                }
            },
            readCachedAnime = { emptyMap() },
            saveCachedAnime = {},
            fetchHistoryPage = { _, offset ->
                if (offset == 0) {
                    requested.complete(Unit)
                    response.await()
                } else emptyList()
            },
            uploadProgress = { true },
            fetchAnimeSummary = ::watchHistoryAnime,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val load = async(start = CoroutineStart.UNDISPATCHED) {
            if (singleAnime) {
                val expected = coordinator.readHistoryRevision()
                val remote = coordinator.fetchRemoteHistory().getOrThrow()
                coordinator.storeRemoteAnimeHistory(1L, remote.filter { it.animeId == 1L }, expected)
            } else {
                coordinator.load(WatchHistoryRefreshPlan(false), { true }, {}, { false })
            }
        }
        requested.await()
        stored.clear()
        if (!reset) stored.add(newer)
        revision++
        response.complete(listOf(old))

        val result = load.await()
        if (singleAnime) {
            assertNull(result)
        } else {
            val resolution = result as WatchHistoryResolution.Ready
            assertEquals(if (reset) emptyList() else listOf(1L), resolution.anime.map { it.id })
        }
        assertEquals(0, replacements)
        assertEquals(if (reset) emptyList() else listOf(newer), stored)
    }

    @Test
    fun reconciliationReplacesLocalCacheWithRemoteEntriesWithoutUploadingLocalProgress() = runBlocking {
        val stored = mutableListOf(watchHistoryProgress(1, 10, updatedAtMs = 300))
        val remote = listOf(
            watchHistoryProgress(1, 11, updatedAtMs = 200),
            watchHistoryProgress(2, 20, updatedAtMs = 400),
        )
        val uploaded = mutableListOf<PlaybackProgress>()
        val coordinator = watchHistoryCoordinator(stored, uploaded)

        val resolution = coordinator.reconcileRemoteHistory(
            remoteResult = Result.success(remote),
            canUseRemote = true,
        ) as WatchHistoryResolution.Ready

        assertEquals(listOf(2L, 1L), resolution.anime.map { it.id })
        assertEquals(emptyList(), uploaded.map { it.videoId })
        assertEquals(listOf(11L, 20L), stored.map { it.videoId })
    }

    @Test
    fun reconciliationUsesRemoteHistoryWithoutAttemptingLocalUpload() = runBlocking {
        val stored = mutableListOf(watchHistoryProgress(3, 30, updatedAtMs = 300))
        val remote = listOf(watchHistoryProgress(1, 10, updatedAtMs = 100))
        val uploaded = mutableListOf<PlaybackProgress>()
        val coordinator = WatchHistoryCoordinator(
            readProgress = { stored.toList() },
            saveProgressIfNewer = { progress -> stored += progress },
            readCachedAnime = { emptyMap() },
            saveCachedAnime = {},
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; false },
            fetchAnimeSummary = ::watchHistoryAnime,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val resolution = coordinator.reconcileRemoteHistory(
            remoteResult = Result.success(remote),
            canUseRemote = true,
        ) as WatchHistoryResolution.Ready

        assertEquals(listOf(1L), resolution.anime.map { it.id })
        assertEquals(emptyList(), uploaded.map { it.videoId })
    }

    @Test
    fun remoteFailureKeepsLocalFallbackCache() = runBlocking {
        val stored = mutableListOf(watchHistoryProgress(1, 10, updatedAtMs = 100))
        val failure = IllegalStateException("offline")

        val resolution = watchHistoryCoordinator(stored).reconcileRemoteHistory(
            remoteResult = Result.failure(failure),
            canUseRemote = true,
        ) as WatchHistoryResolution.Ready

        assertEquals(listOf(1L), resolution.anime.map { it.id })
        assertEquals(listOf(10L), stored.map { it.videoId })
    }

    @Test
    fun animeRemoteSyncReplacesOnlyThatAnimeInLocalCache() = runBlocking {
        val stored = mutableListOf(
            watchHistoryProgress(1, 10, updatedAtMs = 100),
            watchHistoryProgress(2, 20, updatedAtMs = 200),
        )
        val coordinator = watchHistoryCoordinator(stored)

        coordinator.storeRemoteAnimeHistory(
            animeId = 1L,
            history = listOf(watchHistoryProgress(1, 11, updatedAtMs = 300)),
        )

        assertEquals(listOf(2L to 20L, 1L to 11L), stored.map { it.animeId to it.videoId })
    }

    @Test
    fun reconciliationReportsRemoteFailureWhenNoLocalFallbackExists() = runBlocking {
        val failure = IllegalStateException("offline")

        val resolution = watchHistoryCoordinator(mutableListOf()).reconcileRemoteHistory(
            remoteResult = Result.failure(failure),
            canUseRemote = true,
        ) as WatchHistoryResolution.Failed

        assertEquals(failure, resolution.cause)
    }

    @Test
    fun loadPublishesCachedSnapshotBeforeReturningFinalResolution() = runBlocking {
        val stored = mutableListOf(watchHistoryProgress(1, 10, updatedAtMs = 100))
        val snapshots = mutableListOf<List<Long>>()

        val resolution = watchHistoryCoordinator(stored).load(
            plan = WatchHistoryRefreshPlan(showCachedSnapshot = true),
            canUseRemote = { false },
            onCachedSnapshot = { anime -> snapshots += anime.map { it.id } },
            shouldRetryRemoteFailure = { false },
        ) as WatchHistoryResolution.Ready

        assertEquals(listOf(listOf(1L)), snapshots)
        assertEquals(listOf(1L), resolution.anime.map { it.id })
    }

    @Test
    fun loadStopsBeforeReconciliationWhenRemoteFailureSchedulesRetry() = runBlocking {
        val failure = IllegalStateException("captcha")
        val coordinator = WatchHistoryCoordinator(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            readCachedAnime = { emptyMap() },
            saveCachedAnime = {},
            fetchHistoryPage = { _, _ -> throw failure },
            uploadProgress = { true },
            fetchAnimeSummary = ::watchHistoryAnime,
            ioDispatcher = Dispatchers.Unconfined,
        )
        var retried: Throwable? = null

        val resolution = coordinator.load(
            plan = WatchHistoryRefreshPlan(showCachedSnapshot = true),
            canUseRemote = { true },
            onCachedSnapshot = {},
            shouldRetryRemoteFailure = { throwable -> retried = throwable; true },
        )

        assertNull(resolution)
        assertEquals(failure, retried)
    }
}
