package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingBucket
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.RatingDetails

class AnimeDetailsExtrasCoordinatorTest {
    @Test
    fun loadPreservesOptionalSourceOrder() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            fetchComments = { _, offset, limit ->
                events += "comments:$offset:$limit"
                listOf(comment(1), comment(2))
            },
            fetchRecommendations = {
                events += "recommendations"
                listOf(anime(20))
            },
            resolveEffectiveRating = { animeId, remoteRating, trustRemote ->
                events += "effective:$animeId:$remoteRating:$trustRemote"
                8
            },
            fetchRatingSummary = {
                events += "rating"
                AnimeRatingSummary(buckets = listOf(AnimeRatingBucket(rating = 9, count = 3)))
            },
        )

        val result = coordinator.load(request(isAuthenticated = true))

        assertEquals(
            listOf(
                "comments:0:2",
                "recommendations",
                "effective:10:6:true",
                "rating",
            ),
            events,
        )
        assertEquals(listOf(1L, 2L), result.comments.map(AnimeComment::id))
        assertEquals(PagingUiState(isLoadingMore = false, canLoadMore = true), result.commentsPaging)
        assertEquals(listOf(20L), result.recommendations.map(Anime::id))
        assertEquals(8, result.rating.userRating)
    }

    @Test
    fun optionalSourceFailuresFallBackIndependently() = runBlocking {
        val coordinator = coordinator(
            fetchComments = { _, _, _ -> error("comments") },
            fetchRecommendations = { error("recommendations") },
            fetchRatingSummary = { error("rating") },
        )

        val result = coordinator.load(request(isAuthenticated = true))

        assertEquals(emptyList(), result.comments)
        assertEquals(PagingUiState(isLoadingMore = false, canLoadMore = false), result.commentsPaging)
        assertEquals(emptyList(), result.recommendations)
        assertEquals(AnimeRatingSummary(userRating = 6), result.rating)
    }

    @Test
    fun cancellationIsNeverConvertedIntoEmptyExtras() = runBlocking {
        val coordinator = coordinator(
            fetchRecommendations = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            coordinator.load(request())
        }
        Unit
    }

    @Test
    fun commentPagingReducersPreserveStateAndStopOnDuplicatePages() {
        val initial = AnimeDetailsExtras(
            comments = listOf(comment(1), comment(2)),
            commentsPaging = PagingUiState(canLoadMore = true),
        )

        val loading = initial.withAnimeCommentsLoading()
        val duplicatePage = loading.withLoadedAnimeComments(
            incoming = listOf(comment(1), comment(2)),
            pageSize = 2,
        )
        val failed = loading.withAnimeCommentsFailure("network")

        assertEquals(PagingUiState(isLoadingMore = true, canLoadMore = true), loading.commentsPaging)
        assertEquals(listOf(1L, 2L), duplicatePage.comments.map(AnimeComment::id))
        assertEquals(PagingUiState(isLoadingMore = false, canLoadMore = false), duplicatePage.commentsPaging)
        assertEquals(
            PagingUiState(isLoadingMore = false, canLoadMore = true, error = "network"),
            failed.commentsPaging,
        )
    }

    @Test
    fun addedCommentReplacesSameIdAtTheFront() {
        val existing = comment(id = 1, text = "old")
        val updated = comment(id = 1, text = "new")

        val result = AnimeDetailsExtras(comments = listOf(existing, comment(2)))
            .withAddedAnimeComment(updated)

        assertEquals(listOf(1L, 2L), result.comments.map(AnimeComment::id))
        assertEquals("new", result.comments.first().text)
    }

    private fun coordinator(
        fetchComments: suspend (Long, Int, Int) -> List<AnimeComment> = { _, _, _ -> emptyList() },
        fetchRecommendations: suspend (Long) -> List<Anime> = { emptyList() },
        fetchRatingSummary: suspend (Long) -> AnimeRatingSummary = { AnimeRatingSummary() },
        resolveEffectiveRating: suspend (Long, Int?, Boolean) -> Int? = { _, remote, _ -> remote },
        addComment: suspend (Long, String) -> AnimeComment? = { _, _ -> null },
    ): AnimeDetailsExtrasCoordinator {
        return AnimeDetailsExtrasCoordinator(
            fetchComments = fetchComments,
            fetchRecommendations = fetchRecommendations,
            fetchRatingSummary = fetchRatingSummary,
            resolveEffectiveRating = resolveEffectiveRating,
            addComment = addComment,
            commentsPageSize = 2,
        )
    }

    private fun request(
        isAuthenticated: Boolean = false,
    ): AnimeDetailsExtrasLoadRequest {
        return AnimeDetailsExtrasLoadRequest(
            animeId = 10,
            details = animeDetails(),
            isAuthenticated = isAuthenticated,
        )
    }

    private fun animeDetails(): AnimeDetails {
        return AnimeDetails(
            id = 10,
            title = "Anime 10",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "poster-10",
            backdropUrl = null,
            year = 2026,
            rating = null,
            userRating = 6,
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

    private fun comment(id: Long, text: String = "Comment $id"): AnimeComment {
        return AnimeComment(
            id = id,
            userId = 1,
            userName = "User",
            avatarUrl = "",
            text = text,
            createdAtSeconds = 0,
            likes = 0,
            dislikes = 0,
            childrenCount = 0,
        )
    }

}
