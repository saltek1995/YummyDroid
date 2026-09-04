package me.yummydroid.app

import android.app.Application
import android.os.SystemClock
import me.yummydroid.app.data.AnimeRatingStateStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.toAnimeSummary

internal fun createProfileNotificationCoordinator(
    application: Application,
    authStorage: AuthStorage,
    repository: YummyAnimeRepository,
): ProfileNotificationCoordinator {
    return ProfileNotificationCoordinator(
        runtime = AndroidProfileNotificationRuntime(application, authStorage),
        fetchNotifications = { limit -> repository.getProfileNotifications(limit = limit) },
        markNotificationRead = { notificationId ->
            repository.markProfileNotificationRead(notificationId)
        },
        markAllNotificationsRead = {
            repository.markProfileNotificationsRead()
        },
        deleteNotification = { notificationId ->
            repository.deleteProfileNotification(notificationId)
        },
    )
}

internal fun createAnimeRatingCoordinator(
    application: Application,
    repository: YummyAnimeRepository,
): AnimeRatingCoordinator {
    val ratingStorage = AnimeRatingStateStorage(application)
    return AnimeRatingCoordinator(
        readRatings = ratingStorage::read,
        saveRatings = ratingStorage::save,
        setRating = repository::setAnimeRating,
        deleteRating = repository::deleteAnimeRating,
        fetchUserRating = { animeId -> repository.getAnime(animeId).userRating },
    )
}

internal fun createVideoSubscriptionCoordinator(
    repository: YummyAnimeRepository,
): VideoSubscriptionCoordinator {
    return VideoSubscriptionCoordinator(
        fetchSubscriptions = repository::getVideoSubscriptions,
        fetchVideos = repository::getVideos,
        subscribeVideo = repository::subscribeVideo,
        unsubscribeVideo = repository::unsubscribeVideo,
    )
}

internal fun createAnimeDetailsLoadCoordinator(
    repository: YummyAnimeRepository,
    animeRatingCoordinator: AnimeRatingCoordinator,
    historyAnimeCacheStorage: HistoryAnimeCacheStorage,
    playbackProgressStorage: PlaybackProgressStorage,
): AnimeDetailsLoadCoordinator {
    return AnimeDetailsLoadCoordinator(
        fetchAnimeWithVideos = repository::getAnimeWithVideos,
        fetchAnimeWithVideosByAlias = repository::getAnimeWithVideos,
        isOfflineFallbackActive = repository::isOfflineFallbackActive,
        resolveEffectiveRating = animeRatingCoordinator::effectiveRating,
        saveAnimeSummary = historyAnimeCacheStorage::save,
        readPlaybackSelection = playbackProgressStorage::readSelection,
    )
}

internal fun createAnimeDetailsExtrasCoordinator(
    repository: YummyAnimeRepository,
    animeRatingCoordinator: AnimeRatingCoordinator,
): AnimeDetailsExtrasCoordinator {
    return AnimeDetailsExtrasCoordinator(
        fetchComments = repository::getAnimeComments,
        fetchRecommendations = repository::getAnimeRecommendations,
        fetchRatingSummary = repository::getAnimeRatingSummary,
        resolveEffectiveRating = animeRatingCoordinator::effectiveRating,
        addComment = repository::addAnimeComment,
    )
}

internal fun createWatchHistoryCoordinator(
    playbackProgressStorage: PlaybackProgressStorage,
    historyAnimeCacheStorage: HistoryAnimeCacheStorage,
    repository: YummyAnimeRepository,
): WatchHistoryCoordinator {
    return WatchHistoryCoordinator(
        readProgress = playbackProgressStorage::readAll,
        saveProgressIfNewer = playbackProgressStorage::saveIfNewer,
        replaceProgressHistory = playbackProgressStorage::replaceAll,
        replaceAnimeProgressHistory = playbackProgressStorage::replaceAnime,
        readCachedAnime = historyAnimeCacheStorage::readMany,
        saveCachedAnime = historyAnimeCacheStorage::save,
        fetchHistoryPage = repository::getWatchHistory,
        uploadProgress = repository::saveWatchProgress,
        fetchAnimeSummary = { animeId -> repository.getAnime(animeId).toAnimeSummary() },
        monotonicClockMs = SystemClock::elapsedRealtime,
    )
}
