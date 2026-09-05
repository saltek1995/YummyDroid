package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import java.nio.file.Files

class AnimeContentCacheKeyTest {
    @Test
    fun cacheClearInvalidatesMemoryInOtherRepositoriesUsingTheSameDirectory() {
        val directory = Files.createTempDirectory("anime-content-cache").toFile()
        val first = AnimeContentCacheStorage(directory)
        val second = AnimeContentCacheStorage(directory)
        try {
            first.saveVideos(ContentLanguage.Russian, null, 1L, emptyList())
            assertEquals(emptyList(), second.readVideos(ContentLanguage.Russian, null, 1L))

            first.clear()

            assertNull(second.readVideos(ContentLanguage.Russian, null, 1L))
            second.saveVideos(ContentLanguage.Russian, null, 2L, emptyList())
            assertEquals(emptyList(), first.readVideos(ContentLanguage.Russian, null, 2L))
            assertNull(first.readVideos(ContentLanguage.Russian, null, 1L))
        } finally {
            first.clear()
            directory.deleteRecursively()
        }
    }

    @Test
    fun compatibilityVectorKeepsExistingCacheNamespace() {
        assertEquals(
            "fb027dc79cca4006ec51f0bb3c90cb6d84bf894ab68223bf633efa4310d72f30",
            animeContentCacheName("featured", "ru", "anonymous", 0, 20),
        )
    }

    @Test
    fun partSeparatorPreventsAmbiguousKeys() {
        assertNotEquals(
            animeContentCacheName("ab", "c"),
            animeContentCacheName("a", "bc"),
        )
    }

    @Test
    fun userPartitionAcceptsOnlyPositiveIdentifiers() {
        assertEquals("anonymous", null.animeContentCacheUserPart())
        assertEquals("anonymous", 0L.animeContentCacheUserPart())
        assertEquals("anonymous", (-1L).animeContentCacheUserPart())
        assertEquals("user:42", 42L.animeContentCacheUserPart())
    }
}
