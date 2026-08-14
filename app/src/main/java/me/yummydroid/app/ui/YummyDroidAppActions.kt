package me.yummydroid.app.ui

import androidx.compose.runtime.Stable
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

// YummyDroidAppActions
@Stable
class YummyDroidAppActions(
    val onQueryChange: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onSearchHistorySelected: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshFilterCatalog: () -> Unit,
    val onLoadMoreAnime: () -> Unit,
    val onBrowseSectionChange: (BrowseSection) -> Unit,
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onResetFilters: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onOpenAnime: (Long) -> Unit,
    val onFilterByGenre: (Long, FilterOption) -> Unit,
    val onFilterByYear: (Long, Int) -> Unit,
    val onFilterByStudio: (Long, FilterOption) -> Unit,
    val onFilterByCreator: (Long, FilterOption) -> Unit,
    val onSelectVideoGroup: (String) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    val onChoosePlayerResumePosition: (Long) -> Unit,
    val onRetryVideo: () -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val onResetAnimeWatchProgress: (Long) -> Unit,
    val onEnterPictureInPicture: () -> Unit,
    val onLogin: (String, String, String?) -> Unit,
    val onCaptchaSolved: (String) -> Unit,
    val onCaptchaCanceled: (String?) -> Unit,
    val onLogout: () -> Unit,
    val onConfirmLocalWatchHistoryMerge: () -> Unit,
    val onDismissLocalWatchHistoryMerge: () -> Unit,
    val onOpenLibraryFilter: () -> Unit,
    val onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onAddAnimeComment: (String) -> Unit,
    val onLoadMoreAnimeComments: () -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onTogglePlayerVideoSubscription: (VideoVariant) -> Unit,
    val onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    val onRefreshVideoSubscriptions: () -> Unit,
    val onRefreshProfileNotifications: () -> Unit,
    val onMarkProfileNotificationRead: (SiteNotification) -> Unit,
    val onMarkAllProfileNotificationsRead: () -> Unit,
    val onDeleteProfileNotification: (SiteNotification) -> Unit,
    val onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    val onDeleteOfflineAnime: (Long) -> Unit,
    val onClearAppContentCache: () -> Unit,
    val onRefreshAppContentCacheSize: () -> Unit,
    val onRefreshOfflineDownloads: () -> Unit,
    val onClearDownloadHistory: () -> Unit,
    val onCancelDownload: (Long) -> Unit,
    val onPauseDownload: (Long) -> Unit,
    val onResumeDownload: (Long) -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onConsumePlayerNotice: (Long) -> Unit,
    val onBack: () -> Unit,
    val onExitApp: () -> Unit,
    val onProfileNotificationsRequestConsumed: () -> Unit,
    val registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
)
