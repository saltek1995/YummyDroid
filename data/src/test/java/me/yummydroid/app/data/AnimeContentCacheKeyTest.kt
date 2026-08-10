package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnimeContentCacheKeyTest {
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
