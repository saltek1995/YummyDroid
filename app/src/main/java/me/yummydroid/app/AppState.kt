package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.util.DisplayMetrics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.InterfaceScale
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

// AppStateModels
data class NavigationEntry(
    val route: AppRoute,
    val homeSection: BrowseSection,
    val filters: BrowseFilters,
    val searchQuery: String,
    val selectedVideoGroup: String?,
)

internal data class DetailsRouteCache(
    val details: LoadState.Ready<AnimeDetails>,
    val videos: LoadState<List<VideoVariant>>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val animeMark: LoadState<UserAnimeMark?>,
    val selectedVideoGroup: String?,
    val forcedOfflineMode: Boolean,
    val playbackProgress: PlaybackProgress?,
    val playbackHistory: List<PlaybackProgress>,
)

internal data class CatalogRouteCache(
    val animes: List<Anime>,
    val paging: PagingUiState,
    val forcedOfflineMode: Boolean,
)

data class PlayerNotice(
    val id: Long,
    val message: String,
)

enum class PlaybackFailureKind {
    PlayerError,
    BufferingTimeout,
    SourceUnavailable,
}

data class PlaybackFailure(
    val kind: PlaybackFailureKind,
    val message: String? = null,
)

data class AuthUiState(
    val profile: UserProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val captchaRequestNonce: Long = 0L,
)

data class LocalWatchHistoryMergePrompt(
    val profileId: Long,
    val entryCount: Int,
    val entries: List<PlaybackProgress>,
)

data class OfflineDownloadUiState(
    val videoId: Long? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)

internal val DownloadTaskState.title: String
    get() = when (this) {
        DownloadTaskState.Queued -> "Queued"
        DownloadTaskState.Running -> "Downloading"
        DownloadTaskState.Paused -> "Paused"
        DownloadTaskState.Added -> "Added"
        DownloadTaskState.Completed -> "Downloaded"
        DownloadTaskState.Failed -> "Error"
        DownloadTaskState.Cancelled -> "Cancelled"
    }

enum class BrowseSection {
    Catalog,
    Schedule,
    History,
    Downloads,
}

data class AnimeDetailsExtras(
    val comments: List<AnimeComment> = emptyList(),
    val commentsPaging: PagingUiState = PagingUiState(),
    val recommendations: List<Anime> = emptyList(),
    val rating: AnimeRatingSummary = AnimeRatingSummary(),
    val subscriptions: List<VideoSubscription> = emptyList(),
)

sealed interface AppRoute {
    data object Home : AppRoute
    data class Details(val animeId: Long) : AppRoute
    data class Player(
        val video: VideoVariant,
        val animeTitle: String,
        val startPositionMs: Long = 0L,
        val preferredQuality: PreferredQuality = PreferredQuality.Auto,
        val resumeChoicePositionMs: Long? = null,
        val playWhenReady: Boolean = true,
    ) : AppRoute
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

// AppUiConfiguration
private const val ReferenceTelevisionWidthDp = 960
private const val ReferenceTelevisionHeightDp = 540
private const val DensityDefaultDpi = 160
internal const val AppFontScale = 1f

internal data class AppUiConfiguration(
    val densityDpi: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val fontScale: Float,
)

internal fun resolveBaseUiDensityDpi(
    isTelevision: Boolean,
    widthPixels: Int,
    heightPixels: Int,
    stableDensityDpi: Int,
): Int? {
    if (widthPixels <= 0 || heightPixels <= 0 || stableDensityDpi <= 0) return null
    if (!isTelevision) return stableDensityDpi

    val referenceScale = min(
        widthPixels.toFloat() / ReferenceTelevisionWidthDp,
        heightPixels.toFloat() / ReferenceTelevisionHeightDp,
    )
    return max(stableDensityDpi, (DensityDefaultDpi * referenceScale).roundToInt())
}

internal fun resolveAppUiConfiguration(
    isTelevision: Boolean,
    widthPixels: Int,
    heightPixels: Int,
    currentDensityDpi: Int,
    stableDensityDpi: Int,
    currentFontScale: Float,
    interfaceScale: InterfaceScale = InterfaceScale.Default,
): AppUiConfiguration? {
    if (!hasValidAppUiConfigurationInput(
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            currentDensityDpi = currentDensityDpi,
            stableDensityDpi = stableDensityDpi,
        )
    ) return null

    val standardDensityDpi = resolveBaseUiDensityDpi(
        isTelevision = isTelevision,
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        stableDensityDpi = stableDensityDpi,
    ) ?: return null
    val normalizedDensityDpi = (standardDensityDpi * interfaceScale.multiplier).roundToInt()
    if (normalizedDensityDpi == currentDensityDpi && currentFontScale == AppFontScale) return null

    return AppUiConfiguration(
        densityDpi = normalizedDensityDpi,
        screenWidthDp = (widthPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        screenHeightDp = (heightPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        fontScale = AppFontScale,
    )
}

private fun hasValidAppUiConfigurationInput(
    widthPixels: Int,
    heightPixels: Int,
    currentDensityDpi: Int,
    stableDensityDpi: Int,
): Boolean {
    if (widthPixels <= 0) return false
    if (heightPixels <= 0) return false
    if (currentDensityDpi <= 0) return false
    return stableDensityDpi > 0
}

internal fun Context.isTelevisionDevice(): Boolean {
    val configuration = resources.configuration
    return (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun Context.baseUiDensityDpi(): Int {
    val metrics = resources.displayMetrics
    return resolveBaseUiDensityDpi(
        isTelevision = isTelevisionDevice(),
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
    ) ?: resources.configuration.densityDpi.coerceAtLeast(DensityDefaultDpi)
}

internal fun Context.withAppUiConfiguration(
    interfaceScale: InterfaceScale,
    contentLanguage: ContentLanguage,
): Context {
    val configuration = resources.configuration
    val metrics = resources.displayMetrics
    val desiredLocale = contentLanguage.locale
    val localeChanged = configuration.locales.isEmpty ||
        configuration.locales[0].language != desiredLocale.language
    val normalized = resolveAppUiConfiguration(
        isTelevision = isTelevisionDevice(),
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        currentDensityDpi = configuration.densityDpi,
        stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
        currentFontScale = configuration.fontScale,
        interfaceScale = interfaceScale,
    )
    if (normalized == null && !localeChanged) return this

    val overrideConfiguration = Configuration(configuration).apply {
        normalized?.let {
            densityDpi = it.densityDpi
            screenWidthDp = it.screenWidthDp
            screenHeightDp = it.screenHeightDp
            smallestScreenWidthDp = min(it.screenWidthDp, it.screenHeightDp)
            fontScale = it.fontScale
        }
        setLocales(LocaleList(desiredLocale))
    }
    return createConfigurationContext(overrideConfiguration)
}

// LoadStates
internal fun <T> LoadState<T>.readyDataOrNull(): T? = (this as? LoadState.Ready)?.data

internal fun <T> LoadState<List<T>>.readyListOrEmpty(): List<T> = readyDataOrNull().orEmpty()

// YummyDroidDetailsRouteState
internal fun DetailsRouteCache.validProgressVideoGroup(): String? {
    val progressGroupKey = playbackProgress?.groupKey?.takeIf { it.isNotBlank() } ?: return null
    return progressGroupKey.takeIf { groupKey ->
        videos.readyListOrEmpty().any { it.groupKey == groupKey }
    }
}

internal fun shouldAwaitPlaybackHistoryForDetails(
    animeId: Long,
    isAuthenticated: Boolean,
    forcedOfflineMode: Boolean,
    playbackProgress: PlaybackProgress?,
    playbackHistory: List<PlaybackProgress>,
): Boolean {
    return isAuthenticated &&
        !forcedOfflineMode &&
        playbackProgress?.animeId != animeId &&
        playbackHistory.none { progress -> progress.animeId == animeId }
}

internal fun YummyDroidUiState.withDetailsRouteCache(
    route: AppRoute.Details,
    navigationBackStack: List<NavigationEntry>,
    cachedRoute: DetailsRouteCache,
    homeSection: BrowseSection = this.homeSection,
    filters: BrowseFilters = this.filters,
    searchQuery: String = this.searchQuery,
): YummyDroidUiState {
    val cachedProgress = cachedRoute.playbackProgress
    val cachedHistory = cachedRoute.playbackHistory
    return copy(
        route = route,
        navigationBackStack = navigationBackStack,
        homeSection = homeSection,
        filters = filters,
        searchQuery = searchQuery,
        details = cachedRoute.details,
        videos = cachedRoute.videos,
        detailsExtras = cachedRoute.detailsExtras,
        animeMark = cachedRoute.animeMark,
        forcedOfflineMode = cachedRoute.forcedOfflineMode,
        selectedVideoGroup = cachedRoute.validProgressVideoGroup() ?: cachedRoute.selectedVideoGroup,
        playbackProgress = cachedProgress,
        playbackHistory = cachedHistory,
        playbackHistoryLoading = shouldAwaitPlaybackHistoryForDetails(
            animeId = route.animeId,
            isAuthenticated = auth.profile != null,
            forcedOfflineMode = cachedRoute.forcedOfflineMode,
            playbackProgress = cachedProgress,
            playbackHistory = cachedHistory,
        ),
    )
}

// YummyDroidHomeRestoreState
internal data class HomeRouteRestorePlan(
    val restoredHomeSection: BrowseSection,
    val restoredSearchQuery: String,
    val cachedCatalog: CatalogRouteCache?,
    val canReuseCatalog: Boolean,
    val canReuseSearch: Boolean,
) {
    val shouldLoadCatalog: Boolean
        get() = restoredHomeSection == BrowseSection.Catalog &&
            restoredSearchQuery.isBlank() &&
            !canReuseCatalog &&
            cachedCatalog == null

    val shouldSearchNow: Boolean
        get() = restoredHomeSection == BrowseSection.Catalog &&
            restoredSearchQuery.isNotBlank() &&
            !canReuseSearch
}

internal fun homeRouteRestorePlan(
    entry: NavigationEntry,
    currentState: YummyDroidUiState,
    cachedCatalogForEntry: CatalogRouteCache?,
    preserveHomeSection: Boolean,
): HomeRouteRestorePlan {
    val restoredHomeSection = restoredHomeSection(entry, currentState, preserveHomeSection)
    val restoredSearchQuery = if (restoredHomeSection == BrowseSection.Catalog) entry.searchQuery else ""
    val restoreCatalog = restoredHomeSection == BrowseSection.Catalog && restoredSearchQuery.isBlank()
    val restoreSearch = restoredHomeSection == BrowseSection.Catalog && restoredSearchQuery.isNotBlank()
    val cachedCatalog = cachedCatalogForEntry.takeIf { restoreCatalog }
    return HomeRouteRestorePlan(
        restoredHomeSection = restoredHomeSection,
        restoredSearchQuery = restoredSearchQuery,
        cachedCatalog = cachedCatalog,
        canReuseCatalog = currentState.canReuseRestoredCatalog(entry, restoreCatalog),
        canReuseSearch = currentState.canReuseRestoredSearch(entry, restoredSearchQuery, restoreSearch),
    )
}

private fun restoredHomeSection(
    entry: NavigationEntry,
    currentState: YummyDroidUiState,
    preserveHomeSection: Boolean,
): BrowseSection {
    if (preserveHomeSection) return entry.homeSection
    if (currentState.forcedOfflineMode) return BrowseSection.Downloads
    return entry.homeSection
}

private fun YummyDroidUiState.canReuseRestoredCatalog(
    entry: NavigationEntry,
    restoreCatalog: Boolean,
): Boolean {
    if (!restoreCatalog) return false
    if (filters != entry.filters) return false
    return featured is LoadState.Ready
}

private fun YummyDroidUiState.canReuseRestoredSearch(
    entry: NavigationEntry,
    restoredSearchQuery: String,
    restoreSearch: Boolean,
): Boolean {
    if (!restoreSearch) return false
    if (filters != entry.filters) return false
    if (searchQuery != restoredSearchQuery) return false
    return searchResults is LoadState.Ready
}

internal fun YummyDroidUiState.withRestoredHomeRoute(
    entry: NavigationEntry,
    remainingBackStack: List<NavigationEntry>,
    plan: HomeRouteRestorePlan,
): YummyDroidUiState {
    return copy(
        route = AppRoute.Home,
        navigationBackStack = remainingBackStack,
        homeSection = plan.restoredHomeSection,
        filters = entry.filters,
        searchQuery = plan.restoredSearchQuery,
        searchResults = when {
            plan.restoredHomeSection != BrowseSection.Catalog || plan.restoredSearchQuery.isBlank() -> {
                LoadState.Ready(emptyList())
            }
            plan.canReuseSearch -> searchResults
            else -> LoadState.Loading
        },
        searchPaging = when {
            plan.restoredHomeSection != BrowseSection.Catalog || plan.restoredSearchQuery.isBlank() -> {
                PagingUiState(canLoadMore = false)
            }
            plan.canReuseSearch -> searchPaging
            else -> PagingUiState(canLoadMore = true)
        },
        featured = when {
            plan.canReuseCatalog -> featured
            plan.cachedCatalog != null -> LoadState.Ready(plan.cachedCatalog.animes)
            else -> featured
        },
        featuredPaging = when {
            plan.canReuseCatalog -> featuredPaging
            plan.cachedCatalog != null -> plan.cachedCatalog.paging
            else -> featuredPaging
        },
        forcedOfflineMode = if (forcedOfflineMode) true else plan.cachedCatalog?.forcedOfflineMode ?: false,
        selectedVideoGroup = entry.selectedVideoGroup,
    )
}

// YummyDroidUiStateModel
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
    val localWatchHistoryMergePrompt: LocalWatchHistoryMergePrompt? = null,
    val settings: AppSettings = AppSettings(),
    val playbackProgress: PlaybackProgress? = null,
    val playbackHistory: List<PlaybackProgress> = emptyList(),
    val playbackHistoryLoading: Boolean = false,
    val updateState: LoadState<AppUpdateInfo?> = LoadState.Ready(null),
) {
    val canNavigateBack: Boolean
        get() = route != AppRoute.Home || navigationBackStack.isNotEmpty()
            || (!forcedOfflineMode && homeSection == BrowseSection.Downloads) || searchQuery.isNotBlank()
}
