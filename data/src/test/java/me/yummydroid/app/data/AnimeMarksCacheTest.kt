package me.yummydroid.app.data

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimeMarksCacheTest {
    @Test
    fun marksPersistWithAccountIsolationAndAnEmptySnapshotIsDistinctFromMissingCache() = runBlocking {
        val directory = Files.createTempDirectory("anime-marks-cache").toFile()
        val cache = AnimeContentCacheStorage(directory)
        try {
            val marks = mapOf(10L to UserAnimeMark(UserAnimeListMark.Watched, true))
            assertNull(cache.readUserAnimeMarks(1))
            cache.saveUserAnimeMarks(1, marks)
            cache.saveUserAnimeMarks(2, emptyMap())
            cache.awaitPersistence()
            // Reading a copied directory exercises JSON disk restoration, not the shared memory cache.
            val copy = Files.createTempDirectory("restored-anime-marks").toFile()
            try {
                directory.copyRecursively(copy, overwrite = true)
                val restored = AnimeContentCacheStorage(copy)
                assertEquals(marks, restored.readUserAnimeMarks(1))
                assertEquals(emptyMap(), restored.readUserAnimeMarks(2))
                assertNull(restored.readUserAnimeMarks(3))
                restored.clear()
                restored.awaitPersistence()
                assertNull(restored.readUserAnimeMarks(1))
            } finally { copy.deleteRecursively() }
        } finally {
            cache.clear()
            cache.awaitPersistence()
            directory.deleteRecursively()
        }
    }
}
