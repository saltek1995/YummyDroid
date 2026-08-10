package me.yummydroid.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchHistoryAnimeResolverTest {
    @Test
    fun resolverUsesCacheFetchesMissingAnimeAndPersistsValidResults() = runBlocking {
        val cached = mutableMapOf(1L to watchHistoryAnime(1))
        val fetchedIds = mutableListOf<Long>()
        val resolver = WatchHistoryAnimeResolver(
            readCachedAnime = { ids -> cached.filterKeys { it in ids } },
            saveCachedAnime = { anime -> cached[anime.id] = anime },
            fetchAnimeSummary = { id -> fetchedIds += id; watchHistoryAnime(id) },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val history = listOf(
            watchHistoryProgress(1, 10, updatedAtMs = 200),
            watchHistoryProgress(2, 20, updatedAtMs = 100),
        )

        val resolved = resolver.resolveAnimeSummaries(history)

        assertEquals(listOf(1L, 2L), resolved.map { it.id })
        assertEquals(listOf(2L), fetchedIds)
        assertEquals(setOf(1L, 2L), cached.keys)
    }

    @Test
    fun failedFetchFallsBackToProgressSummary() = runBlocking {
        val resolver = WatchHistoryAnimeResolver(
            readCachedAnime = { emptyMap() },
            saveCachedAnime = {},
            fetchAnimeSummary = { throw IllegalStateException("offline") },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val resolved = resolver.resolveAnimeSummaries(
            listOf(watchHistoryProgress(3, 30, updatedAtMs = 100)),
        )

        assertEquals(listOf(3L), resolved.map { it.id })
        assertEquals("Anime 3", resolved.single().title)
    }
}
