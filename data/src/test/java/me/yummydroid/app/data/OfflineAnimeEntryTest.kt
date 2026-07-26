package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineAnimeEntryTest {
    @Test
    fun countsDownloadedEpisodesUniquelyAndKeepsFileVariantsSeparate() {
        val entry = offlineEntry(
            videos = listOf(
                video(
                    id = 11,
                    episode = "1",
                    player = "CVH",
                    localFiles = listOf(offlineFile("file:///episode-1-cvh-1080.mp4", bytes = 320_000)),
                ),
                video(
                    id = 12,
                    episode = "1",
                    player = "Kodik",
                    localFiles = listOf(offlineFile("file:///episode-1-kodik-720.mp4", bytes = 310_000)),
                ),
                video(
                    id = 21,
                    episode = "2",
                    player = "CVH",
                    localFiles = listOf(offlineFile("file:///episode-2-cvh-partial.mp4", bytes = 32_000)),
                ),
            ),
        )

        assertEquals(2, entry.downloadedVariants.size)
        assertEquals(1, entry.downloadedVideos.size)
        assertEquals(630_000, entry.totalBytes)
    }

    private fun offlineEntry(videos: List<VideoVariant>): OfflineAnimeEntry {
        return OfflineAnimeEntry(
            anime = animeSummary(),
            details = animeDetails(),
            videos = videos,
            updatedAtMs = 0L,
        )
    }

    private fun animeSummary(): Anime {
        return Anime(
            id = 1,
            title = "Test",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = null,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun animeDetails(): AnimeDetails {
        return AnimeDetails(
            id = 1,
            title = "Test",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "",
            backdropUrl = null,
            year = null,
            rating = null,
            views = 0,
            status = "",
            type = "",
            minAge = "",
            genreTags = emptyList(),
            genres = emptyList(),
            episodeSummary = "",
            episodeAired = 0,
            episodeCount = 0,
            nextEpisodeText = "",
            durationSeconds = 0,
            ratingDetails = RatingDetails(),
            studios = emptyList(),
            creators = emptyList(),
            original = "",
            commentsCount = 0,
            listsCount = 0,
            translations = emptyList(),
            relatedAnime = emptyList(),
            screenshots = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun video(
        id: Long,
        episode: String,
        player: String,
        localFiles: List<OfflineVideoFile>,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 1,
            player = player,
            dubbing = "AniLibria",
            episode = episode,
            url = "https://example.test/$id",
            index = episode.toIntOrNull() ?: id.toInt(),
            durationSeconds = null,
            views = 0,
            localFiles = localFiles,
        )
    }

    private fun offlineFile(url: String, bytes: Long): OfflineVideoFile {
        return OfflineVideoFile(
            playbackUrl = url,
            mimeType = "video/mp4",
            bytes = bytes,
            qualityTitle = "1080p",
            voiceTitle = "AniLibria",
            player = "CVH",
        )
    }
}
