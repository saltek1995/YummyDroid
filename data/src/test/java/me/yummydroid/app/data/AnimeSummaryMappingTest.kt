package me.yummydroid.app.data

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimeSummaryMappingTest {
    @Test
    fun mapsDetailsToAnimeSummaryWithoutChangingEpisodeFields() {
        val details = AnimeDetails(
            id = 42,
            title = "Title",
            otherTitles = listOf("Other"),
            description = "Description",
            posterUrl = "https://cdn.example/poster.jpg",
            backdropUrl = "https://cdn.example/backdrop.jpg",
            year = 2026,
            rating = 9.1,
            userRating = 8,
            views = 1_200,
            status = "ongoing",
            type = "series",
            minAge = "16+",
            genreTags = emptyList(),
            genres = listOf("Action"),
            episodeSummary = "1 of 12",
            episodeAired = 1,
            episodeCount = 12,
            nextEpisodeText = "soon",
            durationSeconds = 1440,
            ratingDetails = RatingDetails(),
            studios = emptyList(),
            creators = emptyList(),
            original = "",
            commentsCount = 0,
            listsCount = 0,
            translations = emptyList(),
            relatedAnime = emptyList(),
            screenshots = emptyList(),
            blockedIn = listOf("AA"),
        )

        val summary = details.toAnimeSummary()

        assertEquals(details.id, summary.id)
        assertEquals(details.title, summary.title)
        assertEquals(details.description, summary.description)
        assertEquals(details.posterUrl, summary.posterUrl)
        assertEquals("", summary.animeUrl)
        assertEquals(details.year, summary.year)
        assertEquals(details.rating, summary.rating)
        assertEquals(details.userRating, summary.userRating)
        assertEquals(details.views, summary.views)
        assertEquals(details.status, summary.status)
        assertEquals(details.type, summary.type)
        assertEquals(details.genres, summary.genres)
        assertEquals(details.blockedIn, summary.blockedIn)
        assertEquals(details.episodeAired, summary.episodeAired)
        assertEquals(details.episodeCount, summary.episodeCount)

        val releasedDetails = details.copy(status = "\u0432\u044b\u0448\u0435\u043b")
        assertTrue(releasedDetails.isFullyReleased())
        assertTrue(releasedDetails.canShowVideoSubscriptions())
    }

    @Test
    fun mapsPlaybackProgressToFallbackAnimeSummary() {
        val progress = PlaybackProgress(
            animeId = 42,
            videoId = 7,
            animeTitle = "",
            posterUrl = "https://cdn.example/poster.jpg",
            groupKey = "CVH|Voice",
            episode = "3",
            positionMs = 10,
            durationMs = 100,
            updatedAtMs = 1_000,
        )

        val summary = progress.toAnimeSummary()

        assertEquals(42, summary.id)
        assertEquals("Anime #42", summary.title)
        assertEquals(progress.posterUrl, summary.posterUrl)
        assertEquals(0L, summary.views)
        assertEquals(0, summary.episodeAired)
        assertEquals(0, summary.episodeCount)
    }

    @Test
    fun mapsAndSortsVideoVariantsWithoutBuildingAnimeDetails() {
        val dto = AnimeDto(
            animeId = 42,
            videos = listOf(
                VideoDto(
                    videoId = 2,
                    data = VideoDataDto(player = "CVH", playerId = 12, dubbing = "Voice"),
                    number = "2",
                    iframeUrl = "//video.example/2",
                    index = 2,
                ),
                VideoDto(
                    videoId = 1,
                    data = VideoDataDto(player = "CVH", playerId = 11, dubbing = "Voice"),
                    number = "1",
                    iframeUrl = "https://video.example/1",
                    index = 1,
                ),
            ),
        )

        val videos = dto.toVideoVariants()

        assertEquals(listOf(1L, 2L), videos.map { it.id })
        assertEquals(listOf(42L, 42L), videos.map { it.animeId })
        assertEquals("https://video.example/2", videos.last().url)
    }

    @Test
    fun totalSizeBytesCountsFilesRecursivelyAndIgnoresMissingPaths() {
        val root = Files.createTempDirectory("yummydroid-size-test").toFile()
        try {
            val child = root.resolve("child").also { it.mkdirs() }
            root.resolve("one.bin").writeBytes(ByteArray(5))
            child.resolve("two.bin").writeBytes(ByteArray(7))

            assertEquals(12L, root.totalSizeBytes())
            assertEquals(0L, root.resolve("missing").totalSizeBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}
