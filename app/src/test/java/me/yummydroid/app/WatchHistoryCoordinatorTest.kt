package me.yummydroid.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.progressSyncKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryCoordinatorTest {
    @Test
    fun latestHistorySelectsNewestEntryPerAnimeAndSortsByUpdateTime() {
        val history = listOf(
            progress(animeId = 1, videoId = 10, updatedAtMs = 100),
            progress(animeId = 2, videoId = 20, updatedAtMs = 300),
            progress(animeId = 1, videoId = 11, updatedAtMs = 200),
        )

        assertEquals(listOf(2L, 1L), history.latestHistoryByAnime().map { it.animeId })
        assertEquals(listOf(20L, 11L), history.latestHistoryByAnime().map { it.videoId })
    }

    @Test
    fun newerLocalEntriesCompareMatchingEpisodesAndIgnoreUnsyncableVideos() {
        val remote = listOf(
            progress(animeId = 1, videoId = 10, episode = "1", updatedAtMs = 200),
            progress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 100),
        )
        val local = listOf(
            progress(animeId = 1, videoId = 12, episode = "1", updatedAtMs = 100),
            progress(animeId = 1, videoId = 13, episode = "2", updatedAtMs = 300),
            progress(animeId = 1, videoId = 0, episode = "3", updatedAtMs = 400),
        )

        assertEquals(listOf(13L), newerLocalHistoryEntries(local, remote).map { it.videoId })
    }

    @Test
    fun failedRemoteHistoryUsesLocalFallbackButReportsFailureWhenBothAreEmpty() {
        val local = listOf(progress(animeId = 1, videoId = 10, updatedAtMs = 100))

        assertEquals(
            local,
            selectHistoryProgress(local, emptyList(), remoteFailed = true, canUseRemote = true),
        )
        assertNull(selectHistoryProgress(emptyList(), emptyList(), remoteFailed = true, canUseRemote = true))
        assertEquals(
            emptyList(),
            selectHistoryProgress(emptyList(), emptyList(), remoteFailed = true, canUseRemote = false),
        )
    }

    @Test
    fun pageCollectorDeduplicatesEntriesAndStopsAfterTwoDuplicatePages() = runBlocking {
        val firstPage = listOf(progress(animeId = 1, videoId = 10, updatedAtMs = 100))
        val requestedOffsets = mutableListOf<Int>()

        val result = collectWatchHistoryPages(pageSize = 100, maxOffset = 1_000) { _, offset ->
            requestedOffsets += offset
            firstPage
        }

        assertEquals(firstPage, result)
        assertEquals(listOf(0, 100, 200), requestedOffsets)
    }

    @Test
    fun pageCollectorIncludesNewEntriesAcrossPagesUntilTheRemoteEnds() = runBlocking {
        val result = collectWatchHistoryPages(pageSize = 2, maxOffset = 10) { _, offset ->
            when (offset) {
                0 -> listOf(
                    progress(animeId = 1, videoId = 10, episode = "1", updatedAtMs = 100),
                    progress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 200),
                )
                2 -> listOf(
                    progress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 200),
                    progress(animeId = 2, videoId = 20, episode = "1", updatedAtMs = 300),
                )
                else -> emptyList()
            }
        }

        assertEquals(listOf(10L, 11L, 20L), result.map { it.videoId })
    }

    @Test
    fun reconciliationPersistsRemoteEntriesAndUploadsOnlyNewerLocalProgress() = runBlocking {
        val stored = mutableListOf(
            progress(animeId = 1, videoId = 10, episode = "1", updatedAtMs = 300),
        )
        val remote = listOf(
            progress(animeId = 1, videoId = 11, episode = "1", updatedAtMs = 200),
            progress(animeId = 2, videoId = 20, episode = "1", updatedAtMs = 400),
        )
        val uploaded = mutableListOf<PlaybackProgress>()
        val coordinator = coordinator(
            stored = stored,
            uploaded = uploaded,
            fetchedAnime = { anime(it) },
        )

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
        val coordinator = coordinator(stored = mutableListOf())

        val resolution = coordinator.reconcileRemoteHistory(
            remoteResult = Result.failure(failure),
            canUseRemote = true,
        ) as WatchHistoryResolution.Failed

        assertEquals(failure, resolution.cause)
    }

    @Test
    fun refreshPolicyUsesOneCacheClockAndDoesNotStartCompetingLoads() {
        var nowMs = 1_000L
        val coordinator = coordinator(
            stored = mutableListOf(),
            monotonicClockMs = { nowMs },
            refreshIntervalMs = 60_000L,
        )

        val first = coordinator.beginRefresh(
            force = false,
            hasReadyHistory = false,
            canUseRemote = true,
            loadActive = false,
        )
        assertEquals(true, first?.showCachedSnapshot)

        coordinator.markRemoteSynchronized()
        assertNull(
            coordinator.beginRefresh(
                force = false,
                hasReadyHistory = true,
                canUseRemote = true,
                loadActive = false,
            ),
        )

        nowMs += 60_001L
        val due = coordinator.beginRefresh(
            force = false,
            hasReadyHistory = true,
            canUseRemote = true,
            loadActive = false,
        )
        assertEquals(false, due?.showCachedSnapshot)

        coordinator.resetRefreshState()
        assertNull(
            coordinator.beginRefresh(
                force = false,
                hasReadyHistory = false,
                canUseRemote = true,
                loadActive = true,
            ),
        )
        assertEquals(
            true,
            coordinator.beginRefresh(
                force = true,
                hasReadyHistory = true,
                canUseRemote = true,
                loadActive = true,
            )?.showCachedSnapshot,
        )
    }

    @Test
    fun loadPublishesCachedSnapshotBeforeReturningFinalResolution() = runBlocking {
        val stored = mutableListOf(progress(animeId = 1, videoId = 10, updatedAtMs = 100))
        val coordinator = coordinator(stored = stored)
        val snapshots = mutableListOf<List<Long>>()

        val resolution = coordinator.load(
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
            fetchAnimeSummary = { anime(it) },
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

    private fun coordinator(
        stored: MutableList<PlaybackProgress>,
        uploaded: MutableList<PlaybackProgress> = mutableListOf(),
        fetchedAnime: suspend (Long) -> Anime = { anime(it) },
        monotonicClockMs: () -> Long = System::currentTimeMillis,
        refreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
    ): WatchHistoryCoordinator {
        val cachedAnime = mutableMapOf<Long, Anime>()
        return WatchHistoryCoordinator(
            readProgress = { stored.toList() },
            saveProgressIfNewer = { incoming ->
                val index = stored.indexOfFirst { it.progressSyncKey() == incoming.progressSyncKey() }
                if (index < 0) {
                    stored += incoming
                } else if (incoming.updatedAtMs > stored[index].updatedAtMs) {
                    stored[index] = incoming
                }
            },
            readCachedAnime = { animeIds -> cachedAnime.filterKeys { it in animeIds } },
            saveCachedAnime = { anime -> cachedAnime[anime.id] = anime },
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { progress -> uploaded += progress; true },
            fetchAnimeSummary = fetchedAnime,
            ioDispatcher = Dispatchers.Unconfined,
            monotonicClockMs = monotonicClockMs,
            refreshIntervalMs = refreshIntervalMs,
        )
    }

    private fun anime(id: Long): Anime {
        return Anime(
            id = id,
            title = "Anime $id",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = 2026,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun progress(
        animeId: Long,
        videoId: Long,
        episode: String = "1",
        updatedAtMs: Long,
    ): PlaybackProgress {
        return PlaybackProgress(
            animeId = animeId,
            videoId = videoId,
            animeTitle = "Anime $animeId",
            posterUrl = "",
            groupKey = "CVH|Voice",
            episode = episode,
            positionMs = 1_000,
            durationMs = 2_000,
            updatedAtMs = updatedAtMs,
        )
    }
}
