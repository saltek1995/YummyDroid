package me.yummydroid.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

// ViewModelConstants
internal const val MAX_NAVIGATION_STACK = 40
internal const val AUTH_REQUIRED_ERROR_KEY = "auth_required"
internal const val SUBSCRIPTION_ENABLE_FAILED_KEY = "subscription_enable_failed"
internal const val SUBSCRIPTION_DISABLE_FAILED_KEY = "subscription_disable_failed"
internal const val SUBSCRIPTION_TARGET_NOT_FOUND_KEY = "subscription_target_not_found"
internal const val WATCH_HISTORY_MAX_OFFSET = 100_000
internal const val PLAYBACK_FAILED_SOURCE_RETRY_COOLDOWN_MS = 5L * 60L * 1000L
internal const val BROWSE_REMOTE_REFRESH_INTERVAL_MS = 60_000L

// YummyDroidViewModelFacade
class YummyDroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val runtime = YummyDroidRuntime(application, viewModelScope)

    val uiState: StateFlow<YummyDroidUiState> = runtime.uiState

    fun refresh() = runtime.refresh()

    fun updateSearchQuery(query: String) = runtime.updateSearchQuery(query)

    fun submitSearchQuery(query: String) = runtime.submitSearchQuery(query)

    fun selectSearchHistoryQuery(query: String) = runtime.selectSearchHistoryQuery(query)

    fun updateFilters(filters: BrowseFilters) = runtime.updateFilters(filters)

    fun resetFilters() = runtime.resetFilters()

    fun updateSettings(settings: AppSettings) = runtime.updateSettings(settings)

    fun checkForUpdates() = runtime.checkForUpdates()

    fun selectBrowseSection(section: BrowseSection) = runtime.selectBrowseSection(section)

    fun openLibraryFilter() = runtime.openLibraryFilter()

    fun filterByGenre(animeId: Long, genre: FilterOption) = runtime.filterByGenre(animeId, genre)

    fun filterByYear(animeId: Long, year: Int) = runtime.filterByYear(animeId, year)

    fun filterByStudio(animeId: Long, studio: FilterOption) = runtime.filterByStudio(animeId, studio)

    fun filterByCreator(animeId: Long, creator: FilterOption) = runtime.filterByCreator(animeId, creator)

    fun openAnime(animeId: Long, pushCurrent: Boolean = true, reload: Boolean = false) {
        runtime.openAnime(animeId, pushCurrent, reload)
    }

    fun selectVideoGroup(groupKey: String) = runtime.selectVideoGroup(groupKey)

    fun downloadVideoForOffline(
        video: VideoVariant,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
    ) = runtime.downloadVideoForOffline(video, preferredQuality)

    suspend fun resolveAvailableDownloadQualities(
        video: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> = runtime.resolveAvailableDownloadQualities(video, videos, allEpisodes)

    suspend fun resolveSampledDownloadQualities(
        selectedVoiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> = runtime.resolveSampledDownloadQualities(selectedVoiceKeys, videos)

    fun downloadAllVideosForOffline(plan: DownloadPlan) = runtime.downloadAllVideosForOffline(plan)

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        runtime.deleteOfflineVideo(animeId, videoId, playbackUrl)
    }

    fun deleteOfflineAnime(animeId: Long) = runtime.deleteOfflineAnime(animeId)

    fun refreshAppContentCacheSize() = runtime.refreshAppContentCacheSize()

    fun clearAppContentCache() = runtime.clearAppContentCache()

    fun clearDownloadHistory() = runtime.clearDownloadHistory()

    fun cancelDownload(taskId: Long) = runtime.cancelDownload(taskId)

    fun pauseDownload(taskId: Long) = runtime.pauseDownload(taskId)

    fun resumeDownload(taskId: Long) = runtime.resumeDownload(taskId)

    fun loadMoreAnime() = runtime.loadMoreAnime()

    fun playVideo(video: VideoVariant) = runtime.playVideo(video)

    fun playVideo(video: VideoVariant, animeTitle: String) = runtime.playVideo(video, animeTitle)

    fun playVideoAt(video: VideoVariant, startPositionMs: Long) = runtime.playVideoAt(video, startPositionMs)

    fun playVideoAtQuality(
        video: VideoVariant,
        startPositionMs: Long,
        preferredQuality: PreferredQuality,
    ) = runtime.playVideoAtQuality(video, startPositionMs, preferredQuality)

    fun selectPlaybackSource(video: VideoVariant, startPositionMs: Long) {
        runtime.selectPlaybackSource(video, startPositionMs)
    }

    fun playVideoWithResumeChoice(video: VideoVariant, resumePositionMs: Long) {
        runtime.playVideoWithResumeChoice(video, resumePositionMs)
    }

    fun choosePlayerResumePosition(startPositionMs: Long) = runtime.choosePlayerResumePosition(startPositionMs)

    fun consumePlayerNotice(id: Long) = runtime.consumePlayerNotice(id)

    fun fallbackPlaybackSource(
        failedVideo: VideoVariant,
        playbackPositionMs: Long,
        failure: PlaybackFailure,
    ) = runtime.fallbackPlaybackSource(failedVideo, playbackPositionMs, failure)

    fun confirmPlaybackSource(video: VideoVariant) = runtime.confirmPlaybackSource(video)

    fun handlePlaybackEnded(video: VideoVariant) = runtime.handlePlaybackEnded(video)

    fun savePlaybackProgress(video: VideoVariant, positionMs: Long, durationMs: Long) {
        runtime.savePlaybackProgress(video, positionMs, durationMs)
    }

    fun resetAnimeWatchProgress(animeId: Long) = runtime.resetAnimeWatchProgress(animeId)

    fun retryVideo() = runtime.retryVideo()

    fun submitCaptchaResponse(captchaResponse: String) = runtime.submitCaptchaResponse(captchaResponse)

    fun cancelCaptchaChallenge(error: String?) = runtime.cancelCaptchaChallenge(error)

    fun login(login: String, password: String, captchaResponse: String? = null) {
        runtime.login(login, password, captchaResponse)
    }

    fun logout() = runtime.logout()

    fun selectAnimeListMark(mark: UserAnimeListMark) = runtime.selectAnimeListMark(mark)

    fun toggleFavorite() = runtime.toggleFavorite()

    fun navigateBack() = runtime.navigateBack()

    fun loadMoreAnimeComments() = runtime.loadMoreAnimeComments()

    fun setAnimeRating(rating: Int?) = runtime.setAnimeRating(rating)

    fun refreshVideoSubscriptions() = runtime.refreshVideoSubscriptions()

    fun refreshProfileNotifications() = runtime.refreshProfileNotifications()

    fun markProfileNotificationRead(notification: SiteNotification) {
        runtime.markProfileNotificationRead(notification)
    }

    fun markAllProfileNotificationsRead() = runtime.markAllProfileNotificationsRead()

    fun deleteProfileNotification(notification: SiteNotification) {
        runtime.deleteProfileNotification(notification)
    }

    fun addAnimeComment(text: String) = runtime.addAnimeComment(text)

    fun toggleVideoSubscription(video: VideoVariant) = runtime.toggleVideoSubscription(video)

    fun togglePlayerVideoSubscription(video: VideoVariant) = runtime.togglePlayerVideoSubscription(video)

    fun unsubscribeVideoSubscription(subscription: VideoSubscription) {
        runtime.unsubscribeVideoSubscription(subscription)
    }
}
