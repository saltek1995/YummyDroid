package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import java.nio.file.Files

class AnimeContentCacheKeyTest {
    @Test
    fun detailsAndStandaloneVideoCachesShareTheLatestVideoSnapshot() {
        val directory = Files.createTempDirectory("anime-video-snapshots").toFile()
        val first = AnimeContentCacheStorage(directory)
        val second = AnimeContentCacheStorage(directory)
        val language = ContentLanguage.Russian
        val video = VideoVariant(id = 1L, animeId = 1L, player = "CVH", dubbing = "Voice", episode = "1", url = "https://example.test/video", index = 1, durationSeconds = null, views = 0L)
        val newer = video.copy(episode = "2", url = "https://example.test/video?episode=2")
        try {
            first.saveVideos(language, null, 1L, listOf(video))
            second.saveAnimeWithVideos(language, null, 1L, CachedAnimeWithVideos(cacheDetails(), listOf(newer)))
            assertEquals(listOf(newer), first.readVideos(language, null, 1L))
            assertEquals(listOf(newer), first.readAnimeWithVideos(language, null, 1L)?.videos)
            first.saveVideos(language, null, 1L, emptyList())
            assertEquals(emptyList(), second.readVideos(language, null, 1L))
            assertNull(second.readAnimeWithVideos(language, null, 1L))
        } finally {
            first.clear()
            directory.deleteRecursively()
        }
    }

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
    fun rawPageSchemaCannotReuseLegacyFilteredPages() {
        assertNotEquals(
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

    private fun cacheDetails() = AnimeDetails(
        id = 1L, title = "Anime", otherTitles = emptyList(), description = "", posterUrl = "",
        backdropUrl = null, year = null, rating = null, views = 0L, status = "ongoing", type = "TV",
        minAge = "", genreTags = emptyList(), genres = emptyList(), episodeSummary = "", episodeAired = 2,
        episodeCount = 12, nextEpisodeText = "", durationSeconds = 0, ratingDetails = RatingDetails(),
        studios = emptyList(), creators = emptyList(), original = "", commentsCount = 0L, listsCount = 0L,
        translations = emptyList(), relatedAnime = emptyList(), screenshots = emptyList(), blockedIn = emptyList(),
    )
}
