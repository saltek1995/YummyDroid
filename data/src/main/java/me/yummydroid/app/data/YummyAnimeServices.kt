package me.yummydroid.app.data

import kotlinx.serialization.json.JsonElement

// YummyAnimeCatalogApi
internal class YummyAnimeCatalogApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toAnimeQueryParams(
                query = null,
                limit = limit,
                offset = offset,
                ids = ids,
            ),
            authToken,
        )
    }

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toAnimeQueryParams(
                query = query,
                limit = limit,
                offset = offset,
                ids = ids,
            ),
            authToken,
        )
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return transport.get<CatalogDto>(path = "/anime/catalog")
            .toFilterCatalog(transport.locale)
    }

    suspend fun getAnime(animeId: Long, token: String?): AnimeDetails {
        return transport.get<AnimeDto>(path = "/anime/$animeId", authToken = token)
            .toDetails(transport.locale)
    }

    suspend fun getAnimeWithVideos(
        animeId: Long,
        token: String?,
    ): Pair<AnimeDetails, List<VideoVariant>> {
        return loadAnimeWithVideos(animeId.toString(), token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getAnimeWithVideos(
        animeAlias: String,
        token: String?,
    ): Pair<AnimeDetails, List<VideoVariant>> {
        return loadAnimeWithVideos(animeAlias, token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getVideos(animeId: Long, token: String?): List<VideoVariant> {
        return loadAnimeWithVideos(animeId.toString(), token)
            .toVideoVariants()
    }

    suspend fun getSchedule(): List<ScheduleAnime> {
        return transport.get<List<ScheduleAnimeDto>>(path = "/anime/schedule")
            .mapNotNull { it.toScheduleAnime() }
    }

    private suspend fun loadAnime(params: List<Pair<String, String>>, authToken: String?): List<Anime> {
        return transport.get<List<AnimeDto>>(
            path = "/anime",
            params = params,
            authToken = authToken,
        ).map { it.toAnime() }
    }

    private suspend fun loadAnimeWithVideos(animeReference: String, token: String?): AnimeDto {
        return transport.get(
            path = "/anime/$animeReference",
            params = listOf("need_videos" to "true"),
            authToken = token,
        )
    }
}
// YummyAnimeCommunityApi
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
// YummyAnimeNotificationApi
internal class YummyAnimeNotificationApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun subscribeVideo(videoId: Long, token: String): Boolean {
        return transport.putEmptySuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean {
        return transport.deleteSuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> {
        return transport.get<List<SubscriptionDto>>(
            path = "/users/$userId/lists/subs",
            authToken = token,
        ).mapNotNull(SubscriptionDto::toVideoSubscription)
    }

    suspend fun getProfileNotifications(
        token: String,
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<SiteNotification> {
        return transport.get<List<NotificationDto>>(
            path = "/profile/notifications",
            params = notificationParams(types, subTypes, offset, limit),
            authToken = token,
        ).mapNotNull { it.toSiteNotification() }
    }

    suspend fun markProfileNotificationsRead(token: String): Boolean {
        return transport.postEmptySuccess(
            path = "/profile/notifications/read",
            authToken = token,
        )
    }

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.postEmptySuccess(
            path = "/profile/notifications/$notificationId/read",
            authToken = token,
        )
    }

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.deleteSuccess(
            path = "/profile/notifications/$notificationId",
            authToken = token,
        )
    }

    private fun notificationParams(
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<Pair<String, String>> = buildList {
        types.forEach { add("type" to it) }
        subTypes.forEach { add("sub_type" to it) }
        add("offset" to offset.coerceAtLeast(0).toString())
        add("limit" to limit.coerceIn(1, 100).toString())
    }
}
