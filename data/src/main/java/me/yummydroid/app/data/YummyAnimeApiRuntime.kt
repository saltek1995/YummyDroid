package me.yummydroid.app.data

import okhttp3.OkHttpClient

open class YummyAnimeApiRuntime(
    client: OkHttpClient,
    initialContentLanguage: ContentLanguage = ContentLanguage.Russian,
) {
    private val transport = YummyAnimeApiTransport(client, initialContentLanguage)
    private val catalog = YummyAnimeCatalogApi(transport)
    private val account = YummyAnimeAccountApi(transport)
    private val community = YummyAnimeCommunityApi(transport)
    private val notifications = YummyAnimeNotificationApi(transport)

    fun updateContentLanguage(language: ContentLanguage) = transport.updateContentLanguage(language)

    fun submitCaptchaResponse(response: String) = transport.submitCaptchaResponse(response)

    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> = catalog.featuredAnime(limit, offset, filters, authToken, ids)

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> = catalog.search(query, limit, offset, filters, authToken, ids)

    suspend fun getFilterCatalog(): FilterCatalog = catalog.getFilterCatalog()

    suspend fun getAnime(animeId: Long, token: String? = null): AnimeDetails = catalog.getAnime(animeId, token)

    suspend fun getAnimeWithVideos(
        animeId: Long,
        token: String? = null,
    ): Pair<AnimeDetails, List<VideoVariant>> = catalog.getAnimeWithVideos(animeId, token)

    suspend fun getVideos(animeId: Long, token: String? = null): List<VideoVariant> =
        catalog.getVideos(animeId, token)

    suspend fun getUserListAnimeIds(userId: Long, listId: Int, token: String): Set<Long> =
        account.getUserListAnimeIds(userId, listId, token)

    suspend fun getUserFavoriteAnimeIds(userId: Long, token: String): Set<Long> =
        account.getUserFavoriteAnimeIds(userId, token)

    suspend fun login(login: String, password: String, captchaResponse: String? = null): String =
        account.login(login, password, captchaResponse)

    suspend fun refreshToken(token: String): String = account.refreshToken(token)

    suspend fun getProfile(token: String): UserProfile = account.getProfile(token)

    suspend fun getAnimeMark(animeId: Long, token: String): UserAnimeMark = account.getAnimeMark(animeId, token)

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark, token: String): UserAnimeMark =
        account.setAnimeListMark(animeId, mark, token)

    suspend fun removeAnimeListMark(animeId: Long, token: String): UserAnimeMark =
        account.removeAnimeListMark(animeId, token)

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean, token: String): UserAnimeMark =
        account.setFavorite(animeId, isFavorite, token)

    suspend fun getWatchHistory(token: String, limit: Int = 100, offset: Int = 0): List<PlaybackProgress> =
        account.getWatchHistory(token, limit, offset)

    suspend fun saveWatchProgress(progress: PlaybackProgress, token: String): Boolean =
        account.saveWatchProgress(progress, token)

    suspend fun deleteWatchProgress(videoIds: List<Long>, token: String): Boolean =
        account.deleteWatchProgress(videoIds, token)

    suspend fun getSchedule(): List<ScheduleAnime> = catalog.getSchedule()

    suspend fun getCollections(offset: Int = 0, limit: Int = 24): List<AnimeCollectionSummary> =
        community.getCollections(offset, limit)

    suspend fun getCollection(id: Long): AnimeCollectionSummary = community.getCollection(id)

    suspend fun getAnimeCollections(
        animeId: Long,
        offset: Int = 0,
        limit: Int = 12,
    ): List<AnimeCollectionSummary> = community.getAnimeCollections(animeId, offset, limit)

    suspend fun getAnimeComments(animeId: Long, offset: Int = 0, limit: Int = 20): List<AnimeComment> =
        community.getAnimeComments(animeId, offset, limit)

    suspend fun addAnimeComment(animeId: Long, text: String, token: String): AnimeComment? =
        community.addAnimeComment(animeId, text, token)

    suspend fun getAnimeRecommendations(animeId: Long, offset: Int = 0, limit: Int = 12): List<Anime> =
        community.getAnimeRecommendations(animeId, offset, limit)

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary =
        community.getAnimeRatingSummary(animeId)

    suspend fun setAnimeRating(animeId: Long, rating: Int, token: String): AnimeRatingSummary =
        community.setAnimeRating(animeId, rating, token)

    suspend fun deleteAnimeRating(animeId: Long, token: String): AnimeRatingSummary =
        community.deleteAnimeRating(animeId, token)

    suspend fun subscribeVideo(videoId: Long, token: String): Boolean =
        notifications.subscribeVideo(videoId, token)

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean =
        notifications.unsubscribeVideo(videoId, token)

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> =
        notifications.getVideoSubscriptions(userId, token)

    suspend fun getProfileNotifications(
        token: String,
        types: List<String> = emptyList(),
        subTypes: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 50,
    ): List<SiteNotification> = notifications.getProfileNotifications(token, types, subTypes, offset, limit)

    suspend fun markProfileNotificationsRead(token: String): Boolean =
        notifications.markProfileNotificationsRead(token)

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean =
        notifications.markProfileNotificationRead(notificationId, token)

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean =
        notifications.deleteProfileNotification(notificationId, token)
}
