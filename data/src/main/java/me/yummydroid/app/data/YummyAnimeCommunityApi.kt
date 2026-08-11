package me.yummydroid.app.data

import kotlinx.serialization.json.JsonElement

internal class YummyAnimeCommunityApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun getCollections(offset: Int, limit: Int): List<AnimeCollectionSummary> {
        return loadCollections(
            path = "/collection",
            offset = offset,
            limit = limit,
        )
    }

    suspend fun getCollection(id: Long): AnimeCollectionSummary {
        return transport.get<CollectionDto>(path = "/collection/$id").toAnimeCollectionSummary()
    }

    suspend fun getAnimeCollections(animeId: Long, offset: Int, limit: Int): List<AnimeCollectionSummary> {
        return loadCollections(
            path = "/anime/$animeId/collections",
            offset = offset,
            limit = limit,
        )
    }

    suspend fun getAnimeComments(animeId: Long, offset: Int, limit: Int): List<AnimeComment> {
        return transport.get<CommentsResponseDto>(
            path = "/comments/anime/$animeId",
            params = listOf(
                "limit" to limit.coerceIn(1, 50).toString(),
                "skip" to offset.coerceAtLeast(0).toString(),
                "sort" to "new",
            ),
        ).comments.map { it.toAnimeComment() }
    }

    suspend fun addAnimeComment(animeId: Long, text: String, token: String): AnimeComment? {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return null
        return transport.post<JsonElement, CommentRequestDto>(
            path = "/comments/anime/$animeId",
            body = CommentRequestDto(text = trimmedText),
            authToken = token,
        ).toAnimeCommentOrNull()
    }

    suspend fun getAnimeRecommendations(animeId: Long, offset: Int, limit: Int): List<Anime> {
        return transport.get<List<AnimeDto>>(
            path = "/anime/$animeId/recommendations",
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
                "from_ai" to "true",
            ),
        ).map { it.toAnime() }
    }

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary {
        val buckets = transport.get<List<RatingBucketDto>>(path = "/anime/$animeId/rates")
            .mapNotNull { it.toAnimeRatingBucket() }
        return AnimeRatingSummary(buckets = buckets)
    }

    suspend fun setAnimeRating(animeId: Long, rating: Int, token: String): AnimeRatingSummary {
        transport.put<JsonElement, RateRequestDto>(
            path = "/anime/$animeId/rate",
            body = RateRequestDto(rate = rating.coerceIn(1, 10)),
            authToken = token,
        )
        return getAnimeRatingSummary(animeId)
    }

    suspend fun deleteAnimeRating(animeId: Long, token: String): AnimeRatingSummary {
        transport.delete<JsonElement>(path = "/anime/$animeId/rate", authToken = token)
        return getAnimeRatingSummary(animeId)
    }

    private suspend fun loadCollections(
        path: String,
        offset: Int,
        limit: Int,
    ): List<AnimeCollectionSummary> {
        return transport.get<List<CollectionDto>>(
            path = path,
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ),
        ).map { it.toAnimeCollectionSummary() }
    }
}
