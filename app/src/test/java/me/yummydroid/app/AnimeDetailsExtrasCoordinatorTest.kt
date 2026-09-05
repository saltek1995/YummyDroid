package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingBucket
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserProfile

class AnimeDetailsExtrasCoordinatorTest {
    @Test
    fun completedCommentAcknowledgesItsDraftAfterNavigationWithoutUpdatingAnotherAnime() = runBlocking {
        val state = MutableStateFlow(YummyDroidUiState(route = AppRoute.Details(10), auth = AuthUiState(profile = UserProfile(1, "User", ""))))
        val response = CompletableDeferred<AnimeComment?>()
        val coordinator = AnimeCommentSubmissionCoordinator(
            this, SerialStateOperationCoordinator(), { state.value }, { state.update(it) },
            send = { _, _ -> response.await() },
            onSent = { _, _ -> error("Must not insert into the newly opened anime") },
            onFailure = { _, _ -> error("Must not show an error on another anime") },
        )
        coordinator.submit(10, 1, "Draft")
        yield()
        state.update { it.copy(route = AppRoute.Details(20)) }
        response.complete(comment(1))
        withTimeout(2_000) { state.first { it.commentSubmission?.status == CommentSubmissionStatus.Sent } }
        assertEquals(10, state.value.commentSubmission!!.animeId)
        assertEquals("", state.value.commentSubmission!!.confirmedDraft("Draft"))
    }

    @Test
    fun commentDraftSurvivesFailureAndDuplicatePressUntilSuccessfulRetry() = runBlocking {
        val state = MutableStateFlow(YummyDroidUiState(route = AppRoute.Details(10), auth = AuthUiState(profile = UserProfile(1, "User", ""))))
        val response = CompletableDeferred<AnimeComment?>()
        var attempts = 0
        var successes = 0
        var failures = 0
        val coordinator = AnimeCommentSubmissionCoordinator(
            this, SerialStateOperationCoordinator(), { state.value }, { state.update(it) },
            send = { _, _ -> attempts++; if (attempts == 1) response.await() else comment(2) },
            onSent = { _, _ -> successes++ },
            onFailure = { _, _ -> failures++ },
        )
        coordinator.submit(10, 1, " Draft ")
        coordinator.submit(10, 1, " Draft ")
        yield()
        assertEquals(1, attempts)
        assertEquals(" Draft ", state.value.commentSubmission!!.confirmedDraft(" Draft "))
        response.completeExceptionally(IllegalStateException("network"))
        withTimeout(2_000) { state.first { it.commentSubmission?.status == CommentSubmissionStatus.Failed } }
        assertEquals(" Draft ", state.value.commentSubmission!!.confirmedDraft(" Draft "))
        assertEquals(1, failures)

        coordinator.submit(10, 1, " Draft ")
        withTimeout(2_000) { state.first { it.commentSubmission?.status == CommentSubmissionStatus.Sent } }
        assertEquals("", state.value.commentSubmission!!.confirmedDraft(" Draft "))
        assertEquals("New text", state.value.commentSubmission!!.confirmedDraft("New text"))
        assertEquals(2, attempts)
        assertEquals(1, successes)
    }

    @Test
    fun oldAccountCompletionCannotClearNewSubmissionAndStaleCaptchaCannotSendToAnotherAnime() = runBlocking {
        val state = MutableStateFlow(YummyDroidUiState(route = AppRoute.Details(10), auth = AuthUiState(profile = UserProfile(1, "First", ""))))
        val operations = SerialStateOperationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val successes = mutableListOf<Long>()
        var attempts = 0
        val coordinator = AnimeCommentSubmissionCoordinator(
            this, operations, { state.value }, { state.update(it) },
            send = { _, _ ->
                attempts++
                if (attempts == 1) {
                    firstStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirst.await() }
                }
                comment(attempts.toLong())
            },
            onSent = { _, sent -> successes += sent!!.id },
            onFailure = { _, failure -> throw AssertionError(failure) },
        )
        coordinator.submit(10, 1, "First")
        firstStarted.await()
        operations.cancel()
        state.update { it.withEndedProfileSession(it.settings).copy(auth = AuthUiState(profile = UserProfile(2, "Second", ""))) }
        coordinator.submit(10, 2, "Second")
        releaseFirst.complete(Unit)
        withTimeout(2_000) { state.first { it.commentSubmission?.status == CommentSubmissionStatus.Sent } }
        assertEquals(listOf(2L), successes)
        assertEquals("Second", state.value.commentSubmission!!.text)
        state.update { it.copy(route = AppRoute.Details(20)) }
        coordinator.submit(10, 2, "Stale captcha retry")
        coordinator.submit(20, 1, "Old account retry")
        yield()
        assertEquals(2, attempts)
        assertTrue(state.value.commentSubmission?.status != CommentSubmissionStatus.Sending)
    }

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
