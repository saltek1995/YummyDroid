package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant

class EpisodeProgressionTest {
    @Test
    fun finalEpisodeUsesActualVideosBeforeDeclaredCount() {
        val details = details(episodeAired = 12, episodeCount = 40)
        val videos = (1..12).map { episode ->
            video(episode = episode)
        }

        assertFalse(videos.first { it.episode == "11" }.isFinalEpisodeFor(details, videos))
        assertTrue(videos.first { it.episode == "12" }.isFinalEpisodeFor(details, videos))
    }

    private fun details(
        episodeAired: Int,
        episodeCount: Int,
    ): AnimeDetails {
        return AnimeDetails(
            id = 1,
            title = "Anime",
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
            episodeAired = episodeAired,
            episodeCount = episodeCount,
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

    private fun video(episode: Int): VideoVariant {
        return VideoVariant(
            id = episode.toLong(),
            animeId = 1,
            player = "CVH",
            dubbing = "AniLibria",
            episode = episode.toString(),
            url = "",
            index = episode,
            durationSeconds = 1_440,
            views = 0,
        )
    }
}
