package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

internal data class AnimeDetailsExtrasLoadRequest(
    val animeId: Long,
    val details: AnimeDetails?,
    val videos: List<VideoVariant>,
    val isAuthenticated: Boolean,
)

internal data class AnimeDetailsExtrasLoadResult(
    val extras: AnimeDetailsExtras,
    val synchronizedSubscriptions: List<VideoSubscription>?,
)

internal class AnimeDetailsExtrasCoordinator(
    private val fetchComments: suspend (animeId: Long, offset: Int, limit: Int) -> List<AnimeComment>,
    private val fetchRecommendations: suspend (animeId: Long) -> List<Anime>,
    private val fetchRatingSummary: suspend (animeId: Long) -> AnimeRatingSummary,
    private val resolveEffectiveRating: suspend (
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ) -> Int?,
    private val loadSubscriptions: suspend () -> List<VideoSubscription>,
    private val canonicalizeSubscriptions: (
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) -> List<VideoSubscription>,
    private val addComment: suspend (animeId: Long, text: String) -> AnimeComment?,
    private val commentsPageSize: Int = DEFAULT_COMMENTS_PAGE_SIZE,
) {
    suspend fun load(request: AnimeDetailsExtrasLoadRequest): AnimeDetailsExtrasLoadResult {
        val comments = bestEffort(emptyList<AnimeComment>()) {
            fetchComments(request.animeId, 0, commentsPageSize)
        }
        val recommendations = bestEffort(emptyList<Anime>()) {
            fetchRecommendations(request.animeId)
        }
        val matchingDetails = request.details?.takeIf { it.id == request.animeId }
        val currentUserRating = matchingDetails
            ?.let { details ->
                resolveEffectiveRating(
                    request.animeId,
                    details.userRating,
                    request.isAuthenticated,
                )
            }
            ?.takeIf { it in 1..10 }
        val rating = bestEffort(AnimeRatingSummary()) {
            fetchRatingSummary(request.animeId)
        }.copy(userRating = currentUserRating)
        val synchronizedSubscriptions = if (request.isAuthenticated) {
            bestEffortOrNull(loadSubscriptions)
        } else {
            null
        }
        val subscriptions = canonicalizeSubscriptions(
            synchronizedSubscriptions.orEmpty(),
            request.videos.filter { it.animeId == request.animeId },
            request.details?.title.orEmpty(),
            request.details?.posterUrl.orEmpty(),
        )
        val extras = AnimeDetailsExtras(
            recommendations = recommendations,
            rating = rating,
            subscriptions = subscriptions,
        ).withLoadedAnimeComments(comments, commentsPageSize)
        return AnimeDetailsExtrasLoadResult(
            extras = extras,
            synchronizedSubscriptions = synchronizedSubscriptions,
        )
    }

    suspend fun loadCommentsPage(animeId: Long, offset: Int): List<AnimeComment> {
        return fetchComments(animeId, offset, commentsPageSize)
    }

    suspend fun submitComment(animeId: Long, text: String): AnimeComment? {
        return addComment(animeId, text)
    }

    fun mergeCommentsPage(
        current: AnimeDetailsExtras,
        incoming: List<AnimeComment>,
    ): AnimeDetailsExtras {
        return current.withLoadedAnimeComments(incoming, commentsPageSize)
    }

    private suspend fun <T> bestEffort(default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            default
        }
    }

    private suspend fun <T> bestEffortOrNull(block: suspend () -> T): T? {
        return try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private companion object {
        const val DEFAULT_COMMENTS_PAGE_SIZE = 20
    }
}
