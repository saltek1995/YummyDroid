package me.yummydroid.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun YummyAnimeRepository.repositoryGetCollections(
    offset: Int,
    limit: Int,
): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
    api.getCollections(offset = offset, limit = limit)
}

internal suspend fun YummyAnimeRepository.repositoryGetCollection(
    id: Long,
): AnimeCollectionSummary = withContext(Dispatchers.IO) {
    api.getCollection(id)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeCollections(
    animeId: Long,
): List<AnimeCollectionSummary> = withContext(Dispatchers.IO) {
    api.getAnimeCollections(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeComments(
    animeId: Long,
    offset: Int,
    limit: Int,
): List<AnimeComment> = withContext(Dispatchers.IO) {
    api.getAnimeComments(animeId, offset = offset, limit = limit)
}

internal suspend fun YummyAnimeRepository.repositoryAddAnimeComment(
    animeId: Long,
    text: String,
): AnimeComment? = withContext(Dispatchers.IO) {
    api.addAnimeComment(animeId, text, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeRecommendations(
    animeId: Long,
): List<Anime> = withContext(Dispatchers.IO) {
    api.getAnimeRecommendations(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryGetAnimeRatingSummary(
    animeId: Long,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.getAnimeRatingSummary(animeId)
}

internal suspend fun YummyAnimeRepository.repositorySetAnimeRating(
    animeId: Long,
    rating: Int,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.setAnimeRating(animeId, rating, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryDeleteAnimeRating(
    animeId: Long,
): AnimeRatingSummary = withContext(Dispatchers.IO) {
    api.deleteAnimeRating(animeId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositorySubscribeVideo(
    videoId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.subscribeVideo(videoId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryUnsubscribeVideo(
    videoId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.unsubscribeVideo(videoId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryGetVideoSubscriptions(): List<VideoSubscription> =
    withContext(Dispatchers.IO) {
        val token = authStorage?.readToken() ?: return@withContext emptyList()
        val userId = authStorage.readProfile()?.id ?: return@withContext emptyList()
        api.getVideoSubscriptions(userId, token)
    }

internal suspend fun YummyAnimeRepository.repositoryGetNewEpisodeNotifications(
    limit: Int,
): List<SiteNotification> {
    return repositoryGetProfileNotifications(
        types = listOf("anime_episode"),
        subTypes = listOf("new_episode"),
        offset = 0,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositoryGetProfileNotifications(
    types: List<String>,
    subTypes: List<String>,
    offset: Int,
    limit: Int,
): List<SiteNotification> = withContext(Dispatchers.IO) {
    api.getProfileNotifications(
        token = requireToken(),
        types = types,
        subTypes = subTypes,
        offset = offset,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositoryMarkProfileNotificationsRead(): Boolean =
    withContext(Dispatchers.IO) {
        api.markProfileNotificationsRead(requireToken())
    }

internal suspend fun YummyAnimeRepository.repositoryMarkProfileNotificationRead(
    notificationId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.markProfileNotificationRead(notificationId, requireToken())
}

internal suspend fun YummyAnimeRepository.repositoryDeleteProfileNotification(
    notificationId: Long,
): Boolean = withContext(Dispatchers.IO) {
    api.deleteProfileNotification(notificationId, requireToken())
}
