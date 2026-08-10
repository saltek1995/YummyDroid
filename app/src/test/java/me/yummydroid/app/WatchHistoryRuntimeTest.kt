package me.yummydroid.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.PlaybackProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryRuntimeTest {
    @Test
    fun reconciliationPersistsRemoteEntriesAndUploadsOnlyNewerLocalProgress() = runBlocking {
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
        assertEquals(listOf(10L), uploaded.map { it.videoId })
        assertEquals(listOf(10L, 20L), stored.map { it.videoId })
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
