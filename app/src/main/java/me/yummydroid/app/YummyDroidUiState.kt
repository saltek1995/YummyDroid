package me.yummydroid.app

import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

data class YummyDroidUiState(
    val route: AppRoute = AppRoute.Home,
    val navigationBackStack: List<NavigationEntry> = emptyList(),
    val siteBaseUrl: String = DEFAULT_SITE_BASE_URL,
    val homeSection: BrowseSection = BrowseSection.Catalog,
    val featured: LoadState<List<Anime>> = LoadState.Loading,
    val featuredPaging: PagingUiState = PagingUiState(),
    val schedule: LoadState<List<ScheduleAnime>> = LoadState.Loading,
    val historyAnime: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val offlineEntries: LoadState<List<OfflineAnimeEntry>> = LoadState.Ready(emptyList()),
    val appContentCacheSizeBytes: Long = 0L,
    val downloadQueue: DownloadQueueSnapshot = DownloadQueueSnapshot(),
    val offlineDownload: OfflineDownloadUiState = OfflineDownloadUiState(),
    val forcedOfflineMode: Boolean = false,
    val homeFocusResetNonce: Long = 0L,
    val searchQuery: String = "",
    val searchHistory: List<String> = emptyList(),
    val searchResults: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val searchPaging: PagingUiState = PagingUiState(canLoadMore = false),
    val filters: BrowseFilters = BrowseFilters(),
    val filterCatalog: LoadState<FilterCatalog> = LoadState.Loading,
    val details: LoadState<AnimeDetails> = LoadState.Loading,
    val detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Loading,
    val globalSubscriptions: LoadState<List<VideoSubscription>> = LoadState.Ready(emptyList()),
    val profileNotifications: LoadState<List<SiteNotification>> = LoadState.Ready(emptyList()),
    val videos: LoadState<List<VideoVariant>> = LoadState.Loading,
    val selectedVideoGroup: String? = null,
    val playerStream: LoadState<ResolvedVideoStream> = LoadState.Loading,
    val playbackMetadataLoading: Boolean = false,
    val playerNotice: PlayerNotice? = null,
    val auth: AuthUiState = AuthUiState(),
    val animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    val settings: AppSettings = AppSettings(),
    val playbackProgress: PlaybackProgress? = null,
    val playbackHistory: List<PlaybackProgress> = emptyList(),
    val updateState: LoadState<AppUpdateInfo?> = LoadState.Ready(null),
) {
    val canNavigateBack: Boolean
        get() = route != AppRoute.Home || navigationBackStack.isNotEmpty()
            || (!forcedOfflineMode && homeSection == BrowseSection.Downloads) || searchQuery.isNotBlank()
}
