package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingBucket
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

class AnimeDetailsExtrasCoordinatorTest {
    @Test
    fun loadPreservesSourceOrderAndBuildsCanonicalExtras() = runBlocking {
        val events = mutableListOf<String>()
        val sourceSubscription = subscription(player = "CVH")
        val canonicalSubscription = subscription(player = "Canonical")
        var canonicalizedVideos = emptyList<VideoVariant>()
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
            loadSubscriptions = {
                events += "subscriptions"
                listOf(sourceSubscription)
            },
            canonicalizeSubscriptions = { subscriptions, videos, title, posterUrl ->
                events += "canonical:$title:$posterUrl:${subscriptions.size}"
                canonicalizedVideos = videos
                listOf(canonicalSubscription)
            },
        )

        val result = coordinator.load(
            request(
                videos = listOf(video(animeId = 10), video(animeId = 99)),
                isAuthenticated = true,
            ),
        )

        assertEquals(
            listOf(
                "comments:0:2",
                "recommendations",
                "effective:10:6:true",
                "rating",
                "subscriptions",
                "canonical:Anime 10:poster-10:1",
            ),
            events,
        )
        assertEquals(listOf(10L), canonicalizedVideos.map(VideoVariant::animeId))
        assertEquals(listOf(1L, 2L), result.extras.comments.map(AnimeComment::id))
        assertEquals(PagingUiState(isLoadingMore = false, canLoadMore = true), result.extras.commentsPaging)
        assertEquals(listOf(20L), result.extras.recommendations.map(Anime::id))
        assertEquals(8, result.extras.rating.userRating)
        assertEquals(listOf(canonicalSubscription), result.extras.subscriptions)
        assertEquals(listOf(sourceSubscription), result.synchronizedSubscriptions)
    }

    @Test
    fun optionalSourceFailuresFallBackIndependently() = runBlocking {
        val coordinator = coordinator(
            fetchComments = { _, _, _ -> error("comments") },
            fetchRecommendations = { error("recommendations") },
            fetchRatingSummary = { error("rating") },
            loadSubscriptions = { error("subscriptions") },
        )

        val result = coordinator.load(request(isAuthenticated = true))

        assertEquals(emptyList(), result.extras.comments)
        assertEquals(PagingUiState(isLoadingMore = false, canLoadMore = false), result.extras.commentsPaging)
        assertEquals(emptyList(), result.extras.recommendations)
        assertEquals(AnimeRatingSummary(userRating = 6), result.extras.rating)
        assertEquals(emptyList(), result.extras.subscriptions)
        assertNull(result.synchronizedSubscriptions)
    }

    @Test
    fun successfulEmptySubscriptionLoadRemainsDistinguishableFromFailure() = runBlocking {
        val result = coordinator(loadSubscriptions = { emptyList() })
            .load(request(isAuthenticated = true))

        assertEquals(emptyList(), result.synchronizedSubscriptions)
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
        loadSubscriptions: suspend () -> List<VideoSubscription> = { emptyList() },
        canonicalizeSubscriptions: (
            List<VideoSubscription>,
            List<VideoVariant>,
            String,
            String,
        ) -> List<VideoSubscription> = { subscriptions, _, _, _ -> subscriptions },
        addComment: suspend (Long, String) -> AnimeComment? = { _, _ -> null },
    ): AnimeDetailsExtrasCoordinator {
        return AnimeDetailsExtrasCoordinator(
            fetchComments = fetchComments,
            fetchRecommendations = fetchRecommendations,
            fetchRatingSummary = fetchRatingSummary,
            resolveEffectiveRating = resolveEffectiveRating,
            loadSubscriptions = loadSubscriptions,
            canonicalizeSubscriptions = canonicalizeSubscriptions,
            addComment = addComment,
            commentsPageSize = 2,
        )
    }

    private fun request(
        videos: List<VideoVariant> = emptyList(),
        isAuthenticated: Boolean = false,
    ): AnimeDetailsExtrasLoadRequest {
        return AnimeDetailsExtrasLoadRequest(
            animeId = 10,
            details = animeDetails(),
            videos = videos,
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

    private fun subscription(player: String): VideoSubscription {
        return VideoSubscription(
            animeId = 10,
            title = "Anime 10",
            posterUrl = "poster-10",
            player = player,
            dubbing = "Voice",
        )
    }

    private fun video(animeId: Long): VideoVariant {
        return VideoVariant(
            id = animeId,
            animeId = animeId,
            player = "CVH",
            dubbing = "Voice",
            episode = "1",
            url = "https://example.test/$animeId",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }
}
