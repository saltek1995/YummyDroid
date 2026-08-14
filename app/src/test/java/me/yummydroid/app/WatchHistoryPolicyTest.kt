package me.yummydroid.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryPolicyTest {
    @Test
    fun latestHistorySelectsNewestEntryPerAnimeAndSortsByUpdateTime() {
        val history = listOf(
            watchHistoryProgress(animeId = 1, videoId = 10, updatedAtMs = 100),
            watchHistoryProgress(animeId = 2, videoId = 20, updatedAtMs = 300),
            watchHistoryProgress(animeId = 1, videoId = 11, updatedAtMs = 200),
        )

        assertEquals(listOf(2L, 1L), history.latestHistoryByAnime().map { it.animeId })
        assertEquals(listOf(20L, 11L), history.latestHistoryByAnime().map { it.videoId })
    }

    @Test
    fun newerLocalEntriesCompareMatchingEpisodesAndIgnoreUnsyncableVideos() {
        val remote = listOf(
            watchHistoryProgress(animeId = 1, videoId = 10, episode = "1", updatedAtMs = 200),
            watchHistoryProgress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 100),
        )
        val local = listOf(
            watchHistoryProgress(animeId = 1, videoId = 12, episode = "1", updatedAtMs = 100),
            watchHistoryProgress(animeId = 1, videoId = 13, episode = "2", updatedAtMs = 300),
            watchHistoryProgress(animeId = 1, videoId = 0, episode = "3", updatedAtMs = 400),
        )

        assertEquals(listOf(13L), newerLocalHistoryEntries(local, remote).map { it.videoId })
    }

    @Test
    fun successfulRemoteHistoryIsAuthoritativeWhenRemoteIsAvailable() {
        val local = listOf(watchHistoryProgress(animeId = 1, videoId = 10, updatedAtMs = 300))
        val remote = listOf(watchHistoryProgress(animeId = 2, videoId = 20, updatedAtMs = 100))

        assertEquals(remote, selectHistoryProgress(local, remote, false, true))
        assertEquals(emptyList(), selectHistoryProgress(local, emptyList(), false, true))
        assertEquals(local, selectHistoryProgress(local, emptyList(), false, false))
    }

    @Test
    fun failedRemoteHistoryUsesLocalFallbackButReportsFailureWhenBothAreEmpty() {
        val local = listOf(watchHistoryProgress(animeId = 1, videoId = 10, updatedAtMs = 100))

        assertEquals(local, selectHistoryProgress(local, emptyList(), true, true))
        assertNull(selectHistoryProgress(emptyList(), emptyList(), true, true))
        assertEquals(emptyList(), selectHistoryProgress(emptyList(), emptyList(), true, false))
    }

    @Test
    fun pageCollectorDeduplicatesEntriesAndStopsAfterTwoDuplicatePages() = runBlocking {
        val firstPage = listOf(watchHistoryProgress(animeId = 1, videoId = 10, updatedAtMs = 100))
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
                    watchHistoryProgress(animeId = 1, videoId = 10, episode = "1", updatedAtMs = 100),
                    watchHistoryProgress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 200),
                )
                2 -> listOf(
                    watchHistoryProgress(animeId = 1, videoId = 11, episode = "2", updatedAtMs = 200),
                    watchHistoryProgress(animeId = 2, videoId = 20, episode = "1", updatedAtMs = 300),
                )
                else -> emptyList()
            }
        }

        assertEquals(listOf(10L, 11L, 20L), result.map { it.videoId })
    }

    @Test
    fun refreshPlanSeparatesForcedCachedAndTimedRemoteCases() {
        assertEquals(
            WatchHistoryRefreshPlan(showCachedSnapshot = true),
            watchHistoryRefreshPlan(true, true, true, true, true, false),
        )
        assertEquals(
            WatchHistoryRefreshPlan(showCachedSnapshot = true),
            watchHistoryRefreshPlan(false, false, false, false, true, false),
        )
        assertEquals(
            WatchHistoryRefreshPlan(showCachedSnapshot = false),
            watchHistoryRefreshPlan(false, true, true, false, true, true),
        )
        assertNull(watchHistoryRefreshPlan(false, true, true, false, true, false))
    }
}
