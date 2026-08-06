package me.yummydroid.app

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingStateStorage
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.distinctLatestByEpisode
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.GitHubUpdateChecker
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.isUnauthorizedApiError
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.normalized
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SearchHistoryStorage
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.siteDefaultVideo
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.toAnimeSummary
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoSubscriptionHintStorage
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.withVoiceSubscriptionState
import me.yummydroid.app.data.YummyAnimeRepository

private data class PlaybackResolution(
    val playback: ResolvedPlayback,
    val manualFallbackNotice: SourceFallbackNotice? = null,
)

private data class SourceFallbackNotice(
    val selectedVideo: VideoVariant,
    val reason: String,
)

private data class AnimeDetailsLoadResult(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val offlineMode: Boolean,
    val progress: PlaybackProgress?,
    val history: List<PlaybackProgress>,
    val selectedVideoGroup: String?,
)

class YummyDroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsStorage = AppSettingsStorage(application)
    private val playbackProgressStorage = PlaybackProgressStorage(application)
    private val historyAnimeCacheStorage = HistoryAnimeCacheStorage(application)
    private val animeRatingStateStorage = AnimeRatingStateStorage(application)
    private val videoSubscriptionHintStorage = VideoSubscriptionHintStorage(application)
    private val searchHistoryStorage = SearchHistoryStorage(application)
    private val initialSettings = settingsStorage.read()
    private val authStorage = AuthStorage(application)
    private val siteDomainResolver = SiteDomainResolver(candidates = initialSettings.siteDomains)
    private val repository = YummyAnimeRepository(
        context = application,
        siteDomainResolver = siteDomainResolver,
        authStorage = authStorage,
    )
    private val watchHistoryCoordinator = WatchHistoryCoordinator(
        readProgress = playbackProgressStorage::readAll,
        saveProgressIfNewer = playbackProgressStorage::saveIfNewer,
        readCachedAnime = historyAnimeCacheStorage::readMany,
        saveCachedAnime = historyAnimeCacheStorage::save,
        fetchHistoryPage = repository::getWatchHistory,
        uploadProgress = repository::saveWatchProgress,
        fetchAnimeSummary = { animeId -> repository.getAnime(animeId).toAnimeSummary() },
        monotonicClockMs = SystemClock::elapsedRealtime,
    )
    private val updateChecker = GitHubUpdateChecker()
    private val _uiState = MutableStateFlow(
        YummyDroidUiState(
            settings = initialSettings,
            filters = initialSettings.savedBrowseFilters,
        ),
    )
    val uiState: StateFlow<YummyDroidUiState> = _uiState

    private var searchDebounceJob: Job? = null
    private var featuredLoadJob: Job? = null
    private var searchLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var historyLoadJob: Job? = null
    private var offlineLoadJob: Job? = null
    private var downloadQueueJob: Job? = null
    private var detailsLoadJob: Job? = null
    private var detailsExtrasJob: Job? = null
    private var commentsLoadJob: Job? = null
    private var updateCheckJob: Job? = null
    private var playerLoadJob: Job? = null
    private var playbackMetadataJob: Job? = null
    private var playbackMetadataLoadId = 0L
    private var animeMarkJob: Job? = null
    private var subscriptionsSyncJob: Job? = null
    private var profileNotificationsSyncJob: Job? = null
    private var appContentCacheSizeJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var pendingCaptchaAction: (suspend () -> Unit)? = null
    private val videoSubscriptionHints = mutableListOf<VideoSubscriptionHint>()
    private var playbackHistorySyncJob: Job? = null
    private var offlineRecoveryJob: Job? = null
    private val playbackProgressWriteJobs = mutableMapOf<Long, Job>()
    private val playbackProgressSyncJobs = mutableMapOf<Long, Job>()
    private var failedPlaybackSourceKeys: Set<String> = emptySet()
    private val failedPlaybackSourceRetryAfterMs = mutableMapOf<String, Long>()
    private val playbackSourceCache = mutableMapOf<PlaybackCacheKey, PlaybackSourceCacheEntry>()
    private val manualPlaybackSourceOverrides = mutableMapOf<PlaybackCacheKey, String>()
    private var playerNoticeId = 0L
    private val animePlaybackQualityOverrides = mutableMapOf<Long, PreferredQuality>()
    private val autoAnimeMarkJobs = mutableMapOf<Long, Job>()
    private var completedDownloadTaskIds: Set<Long> = emptySet()
    private val knownAnimeRatings = mutableMapOf<Long, Int?>()
    private val detailsRouteCache = mutableMapOf<Long, DetailsRouteCache>()
    private val catalogPageCache = mutableMapOf<BrowseFilters, CatalogRouteCache>()
    private var catalogCacheInitialized = false
    private var scheduleCacheInitialized = false
    private var scheduleLastRemoteCheckAtMs = 0L

    init {
        DownloadCenter.initialize(application)
        repository.updateContentLanguage(initialSettings.contentLanguage)
        restoreSearchHistory()
        loadHome()
        loadFilterCatalog()
        loadSchedule()
        loadHistory(force = false)
        loadOfflineEntries()
        refreshAppContentCacheSize()
        observeDownloadQueue()
        refreshSiteBaseUrl()
        restoreProfile()
        startOfflineRecoveryMonitor()
        if (initialSettings.autoCheckUpdates) {
            checkForUpdates()
        }
    }

    fun refresh() {
        when (val route = _uiState.value.route) {
            AppRoute.Home -> reloadBrowse()
            is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
            is AppRoute.Player -> Unit
        }
    }

    private fun refreshSiteBaseUrl() {
        _uiState.update { it.copy(siteBaseUrl = repository.cachedSiteBaseUrl()) }
        viewModelScope.launch {
            runCatching { repository.activeSiteBaseUrl() }
                .onSuccess { baseUrl -> _uiState.update { it.copy(siteBaseUrl = baseUrl) } }
        }
    }

    private fun startOfflineRecoveryMonitor() {
        offlineRecoveryJob?.cancel()
        offlineRecoveryJob = viewModelScope.launch {
            while (true) {
                delay(OFFLINE_RECOVERY_CHECK_INTERVAL_MS)
                if (!_uiState.value.forcedOfflineMode) continue

                val reachableBaseUrl = runCatching { repository.checkReachableSiteBaseUrl() }.getOrNull()
                    ?: continue
                _uiState.update {
                    it.copy(
                        forcedOfflineMode = false,
                        siteBaseUrl = reachableBaseUrl,
                    )
                }
                when (val route = _uiState.value.route) {
                    AppRoute.Home -> reloadBrowse()
                    is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
                    is AppRoute.Player -> Unit
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        if (_uiState.value.forcedOfflineMode) {
            showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
            return
        }
        val shouldResetFilters = query.isNotBlank()
        val searchFilters = if (shouldResetFilters) BrowseFilters() else _uiState.value.filters
        val updatedSettings = if (shouldResetFilters && _uiState.value.filters != searchFilters) {
            saveBrowseFilters(searchFilters)
        } else {
            _uiState.value.settings
        }
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
                homeSection = BrowseSection.Catalog,
                filters = searchFilters,
                settings = updatedSettings,
                searchQuery = query,
                searchResults = if (query.isBlank()) LoadState.Ready(emptyList()) else LoadState.Loading,
                searchPaging = PagingUiState(canLoadMore = query.isNotBlank()),
            )
        }

        searchDebounceJob?.cancel()
        searchLoadJob?.cancel()
        if (query.isBlank()) return

        searchDebounceJob = viewModelScope.launch {
            delay(350)
            searchNow(query, reset = true)
        }
    }

    private fun restoreSearchHistory() {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { searchHistoryStorage.read() }
            _uiState.update { it.copy(searchHistory = history) }
        }
    }

    fun submitSearchQuery(query: String) {
        recordSearchHistory(query)
    }

    fun selectSearchHistoryQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        updateSearchQuery(normalizedQuery)
        recordSearchHistory(normalizedQuery)
    }

    private fun recordSearchHistory(query: String) {
        if (_uiState.value.forcedOfflineMode) return
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { searchHistoryStorage.add(query) }
            _uiState.update { it.copy(searchHistory = history) }
        }
    }

    fun updateFilters(filters: BrowseFilters) {
        if (_uiState.value.forcedOfflineMode) {
            showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
            return
        }
        applyBrowseFilters(filters)
    }

    fun resetFilters() {
        applyBrowseFilters(BrowseFilters())
    }

    private fun applyBrowseFilters(filters: BrowseFilters) {
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.copy(
                filters = filters,
                settings = updatedSettings,
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
                homeSection = BrowseSection.Catalog,
                homeFocusResetNonce = state.homeFocusResetNonce + 1L,
            )
        }
        reloadBrowse()
    }

    fun updateSettings(settings: AppSettings) {
        val previousSettings = _uiState.value.settings
        val normalizedSettings = settings.normalized()
        val languageChanged = previousSettings.contentLanguage != normalizedSettings.contentLanguage
        persistSettings(normalizedSettings)
        repository.updateContentLanguage(normalizedSettings.contentLanguage)
        siteDomainResolver.updateCandidates(normalizedSettings.siteDomains)
        _uiState.update {
            it.copy(
                settings = normalizedSettings,
                siteBaseUrl = siteDomainResolver.cachedOrDefaultBaseUrl(),
            )
        }
        refreshSiteBaseUrl()
        if (languageChanged) {
            when (val route = _uiState.value.route) {
                AppRoute.Home -> reloadBrowse()
                is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
                is AppRoute.Player -> {
                    route.video.animeId.takeIf { it > 0L }?.let { openAnime(it, pushCurrent = false, reload = true) }
                }
            }
        }
    }

    private fun saveBrowseFilters(filters: BrowseFilters): AppSettings {
        val updatedSettings = _uiState.value.settings.copy(savedBrowseFilters = filters).normalized()
        persistSettings(updatedSettings)
        return updatedSettings
    }

    fun checkForUpdates() {
        updateCheckJob?.cancel()
        _uiState.update { it.copy(updateState = LoadState.Loading) }
        updateCheckJob = viewModelScope.launch {
            runCatching { updateChecker.latestRelease() }
                .onSuccess { updateInfo ->
                    _uiState.update {
                        it.copy(
                            updateState = LoadState.Ready(
                                updateInfo.copy(
                                    title = if (updateInfo.isNewerThanVersion(BuildConfig.VERSION_NAME)) {
                                        updateInfo.title
                                    } else {
                                        uiString(R.string.ui_current_version_installed)
                                    },
                                ),
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(updateState = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    fun selectBrowseSection(section: BrowseSection) {
        val targetSection = if (_uiState.value.forcedOfflineMode) BrowseSection.Downloads else section
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
                homeSection = targetSection,
                searchQuery = if (targetSection == BrowseSection.Catalog) state.searchQuery else "",
                searchResults = if (targetSection == BrowseSection.Catalog) state.searchResults else LoadState.Ready(emptyList()),
                searchPaging = if (targetSection == BrowseSection.Catalog) state.searchPaging else PagingUiState(canLoadMore = false),
            )
        }
        ensureBrowseSectionLoaded(targetSection)
    }

    fun openLibraryFilter() {
        if (_uiState.value.forcedOfflineMode) {
            showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
            return
        }
        if (_uiState.value.auth.profile == null) return
        val filters = BrowseFilters(userMarks = ALL_USER_MARK_FILTERS)
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.withCatalogFilters(
                filters = filters,
                settings = updatedSettings,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
            )
        }
        loadHome(reset = true)
    }

    fun filterByGenre(animeId: Long, genre: FilterOption) {
        applyDetailsFilter(sourceAnimeId = animeId) { it.copy(genres = setOf(genre.value)) }
    }

    fun filterByYear(animeId: Long, year: Int) {
        applyDetailsFilter(sourceAnimeId = animeId) { it.copy(fromYear = year, toYear = year) }
    }

    fun filterByStudio(animeId: Long, studio: FilterOption) {
        applyDetailsFilter(sourceAnimeId = animeId) {
            it.copy(
                studios = setOf(studio.value),
                studioTitles = mapOf(studio.value to studio.title),
            )
        }
    }

    fun filterByCreator(animeId: Long, creator: FilterOption) {
        applyDetailsFilter(sourceAnimeId = animeId) {
            it.copy(
                creators = setOf(creator.value),
                creatorTitles = mapOf(creator.value to creator.title),
            )
        }
    }

    fun openAnime(animeId: Long, pushCurrent: Boolean = true, reload: Boolean = false) {
        if (_uiState.value.forcedOfflineMode) {
            val offlineEntries = _uiState.value.offlineEntries.readyDataOrNull()
            if (offlineEntries != null && offlineEntries.none { it.anime.id == animeId }) {
                showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
                return
            }
        }
        commentsLoadJob?.cancel()
        detailsLoadJob?.cancel()
        cacheCurrentDetailsRouteState()
        val cachedRoute = detailsRouteCache[animeId].takeUnless { reload }
        _uiState.update { state ->
            val targetRoute = AppRoute.Details(animeId)
            if (cachedRoute != null) {
                return@update state.withDetailsRouteCache(
                    cachedRoute = cachedRoute,
                    navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                    route = targetRoute,
                )
            }
            state.copy(
                navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                route = targetRoute,
                details = LoadState.Loading,
                videos = LoadState.Loading,
                detailsExtras = LoadState.Loading,
                selectedVideoGroup = null,
                animeMark = LoadState.Loading,
                playbackProgress = state.playbackProgress?.takeIf { it.animeId == animeId },
                playbackHistory = state.playbackHistory.takeIf { history ->
                    history.any { it.animeId == animeId }
                }.orEmpty(),
            )
        }
        if (cachedRoute != null) {
            refreshPlaybackProgressSnapshot(animeId)
            return
        }
        loadAnimeDetails(animeId)
    }

    private fun cacheCurrentDetailsRouteState() {
        val state = _uiState.value
        val animeId = (state.route as? AppRoute.Details)?.animeId
            ?: state.details.readyDataOrNull()?.id
            ?: (state.route as? AppRoute.Player)?.video?.animeId
                ?.takeIf { it > 0L }
            ?: return
        cacheDetailsRouteState(animeId, state)
    }

    private fun cacheDetailsRouteState(animeId: Long, state: YummyDroidUiState = _uiState.value) {
        val details = state.details as? LoadState.Ready ?: return
        if (details.data.id != animeId) return
        detailsRouteCache[animeId] = DetailsRouteCache(
            details = details,
            videos = state.videos,
            detailsExtras = state.detailsExtras,
            animeMark = state.animeMark,
            selectedVideoGroup = state.selectedVideoGroup,
            forcedOfflineMode = state.forcedOfflineMode,
            playbackProgress = state.playbackProgress,
            playbackHistory = state.playbackHistory,
        )
    }

    private fun updateCachedPlaybackProgress(progress: PlaybackProgress, history: List<PlaybackProgress>) {
        val cachedRoute = detailsRouteCache[progress.animeId] ?: return
        detailsRouteCache[progress.animeId] = cachedRoute.copy(
            selectedVideoGroup = progress.groupKey.takeIf { it.isNotBlank() } ?: cachedRoute.selectedVideoGroup,
            playbackProgress = progress,
            playbackHistory = history,
        )
    }

    private fun refreshPlaybackProgressSnapshot(animeId: Long) {
        if (animeId <= 0L) return
        viewModelScope.launch {
            val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
            val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
            if (progress != null) updateCachedPlaybackProgress(progress, history)
            _uiState.update { state ->
                val isCurrentDetails = (state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                if (!isCurrentDetails) return@update state
                val progressGroupKey = progress?.groupKey
                    ?.takeIf { groupKey -> state.videos.readyListOrEmpty().any { it.groupKey == groupKey } }
                state.copy(
                    selectedVideoGroup = progressGroupKey ?: state.selectedVideoGroup,
                    playbackProgress = progress,
                    playbackHistory = history,
                )
            }
        }
    }

    private fun clearCachedPlaybackProgress(animeId: Long) {
        val cachedRoute = detailsRouteCache[animeId] ?: return
        detailsRouteCache[animeId] = cachedRoute.copy(
            playbackProgress = null,
            playbackHistory = emptyList(),
        )
    }

    private fun loadAnimeDetails(animeId: Long) {
        detailsLoadJob?.cancel()
        detailsLoadJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val (animeDetails, videoVariants) = repository.getAnimeWithVideos(animeId)
                    val offlineMode = repository.isOfflineFallbackActive()
                    val playableVideos = if (offlineMode) {
                        videoVariants.filter { it.isOfflineAvailable }
                    } else {
                        videoVariants
                    }
                    val progress = playbackProgressStorage.read(animeId)
                    val history = playbackProgressStorage.readAnimeHistory(animeId)
                    val progressGroupKey = progress?.groupKey
                        ?.takeIf { groupKey -> playableVideos.any { it.groupKey == groupKey } }
                    AnimeDetailsLoadResult(
                        details = animeDetails,
                        videos = videoVariants,
                        offlineMode = offlineMode,
                        progress = progress,
                        history = history,
                        selectedVideoGroup = progressGroupKey
                            ?: playableVideos.siteDefaultVideo()?.groupKey
                            ?: videoVariants.siteDefaultVideo()?.groupKey,
                    )
                }
            }
                .onSuccess { loaded ->
                    val detailsWithRating = loaded.details.copy(
                        userRating = effectiveAnimeRating(
                            animeId = animeId,
                            remoteRating = loaded.details.userRating,
                            trustRemote = _uiState.value.auth.profile != null && !loaded.offlineMode,
                        ),
                    )
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            historyAnimeCacheStorage.save(detailsWithRating.toAnimeSummary())
                        }
                    }
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) {
                            return@update state
                        }
                        state.copy(
                            details = LoadState.Ready(detailsWithRating),
                            videos = LoadState.Ready(loaded.videos),
                            forcedOfflineMode = loaded.offlineMode,
                            selectedVideoGroup = loaded.selectedVideoGroup,
                            playbackProgress = loaded.progress,
                            playbackHistory = loaded.history,
                            detailsExtras = if (loaded.offlineMode) LoadState.Ready(AnimeDetailsExtras()) else state.detailsExtras,
                            animeMark = if (loaded.offlineMode) LoadState.Ready(null) else state.animeMark,
                        )
                    }
                    if ((_uiState.value.route as? AppRoute.Details)?.animeId != animeId) {
                        return@onSuccess
                    }
                    cacheDetailsRouteState(animeId)
                    if (loaded.offlineMode) {
                        animeMarkJob?.cancel()
                        detailsExtrasJob?.cancel()
                    } else {
                        refreshPlaybackProgressFromSite(animeId)
                        loadAnimeMark(animeId)
                        loadAnimeExtras(animeId)
                    }
                }
                .onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (_uiState.value.forcedOfflineMode || throwable.isOfflineConnectivityFailure()) {
                        showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
                        val failedState = _uiState.value
                        if ((failedState.route as? AppRoute.Details)?.animeId == animeId) {
                            val previous = failedState.navigationBackStack.lastOrNull()
                            if (previous != null) {
                                restoreNavigationEntry(
                                    entry = previous,
                                    remainingBackStack = failedState.navigationBackStack.dropLast(1),
                                    preserveHomeSection = true,
                                )
                            } else {
                                _uiState.update { state ->
                                    if ((state.route as? AppRoute.Details)?.animeId != animeId) {
                                        return@update state
                                    }
                                    state.copy(
                                        details = LoadState.Error(uiString(R.string.ui_offline_mode_unavailable)),
                                        videos = LoadState.Error(uiString(R.string.ui_offline_mode_unavailable)),
                                        detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
                                        animeMark = LoadState.Ready(null),
                                        playbackProgress = null,
                                    )
                                }
                            }
                        }
                        return@onFailure
                    }
                    val message = throwable.userMessage()
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) {
                            return@update state
                        }
                        state.copy(
                            details = LoadState.Error(message),
                            videos = LoadState.Error(message),
                            detailsExtras = LoadState.Error(message),
                            animeMark = LoadState.Ready(null),
                            forcedOfflineMode = false,
                            playbackProgress = null,
                        )
                    }
                }
        }
    }

    fun selectVideoGroup(groupKey: String) {
        _uiState.update { it.copy(selectedVideoGroup = groupKey) }
        cacheCurrentDetailsRouteState()
    }

    fun downloadVideoForOffline(video: VideoVariant, preferredQuality: PreferredQuality = PreferredQuality.Auto) {
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        videoId = video.id,
                        isRunning = false,
                        message = uiString(R.string.ui_download_unavailable_offline),
                    ),
                )
            }
            return
        }
        DownloadService.enqueueVideo(
            context = getApplication(),
            animeId = video.animeId,
            videoId = video.id,
            groupKey = video.groupKey,
            quality = preferredQuality,
        )
        _uiState.update {
            it.copy(
                offlineDownload = OfflineDownloadUiState(
                    videoId = video.id,
                    isRunning = true,
                    progress = 0f,
                    message = uiString(R.string.ui_added),
                ),
            )
        }
    }

    suspend fun resolveAvailableDownloadQualities(
        video: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> {
        if (_uiState.value.forcedOfflineMode) return emptyList()
        return repository.resolveAvailableDownloadQualities(video, videos, allEpisodes)
    }

    suspend fun resolveSampledDownloadQualities(
        selectedVoiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> {
        if (_uiState.value.forcedOfflineMode) return emptyMap()
        return repository.resolveSampledDownloadQualities(selectedVoiceKeys, videos)
    }

    fun downloadAllVideosForOffline(plan: DownloadPlan) {
        val state = _uiState.value
        if (state.forcedOfflineMode) {
            _uiState.update {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        isRunning = false,
                        message = uiString(R.string.ui_download_unavailable_offline),
                    ),
                )
            }
            return
        }
        if (plan.items.isEmpty()) return
        _uiState.update {
            it.copy(
                offlineDownload = OfflineDownloadUiState(
                    isRunning = true,
                    progress = 0f,
                    message = uiString(R.string.ui_added),
                ),
            )
        }
        viewModelScope.launch {
            val planId = withContext(Dispatchers.IO) { DownloadPlanStorage(getApplication()).save(plan) }
            DownloadService.enqueuePlan(getApplication(), planId)
        }
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        viewModelScope.launch {
            repository.deleteOfflineVideo(animeId, videoId, playbackUrl)
            refreshCurrentDetailsFromOfflineCache(animeId)
            loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun deleteOfflineAnime(animeId: Long) {
        viewModelScope.launch {
            repository.deleteOfflineAnime(animeId)
            refreshCurrentDetailsFromOfflineCache(animeId)
            loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun refreshAppContentCacheSize() {
        appContentCacheSizeJob?.cancel()
        appContentCacheSizeJob = viewModelScope.launch {
            val sizeBytes = withContext(Dispatchers.IO) {
                calculateAppContentCacheSize(getApplication())
            }
            _uiState.update { it.copy(appContentCacheSizeBytes = sizeBytes) }
        }
    }

    fun clearAppContentCache() {
        viewModelScope.launch {
            repository.clearAppContentCache(playbackProgressStorage)
            val sizeBytes = withContext(Dispatchers.IO) {
                val application = getApplication<Application>()
                historyAnimeCacheStorage.clear()
                application.clearRuntimeCacheDirectories()
                calculateAppContentCacheSize(application)
            }
            detailsRouteCache.clear()
            catalogPageCache.clear()
            catalogCacheInitialized = false
            scheduleCacheInitialized = false
            watchHistoryCoordinator.resetRefreshState()
            scheduleLastRemoteCheckAtMs = 0L
            DownloadCenter.clearAll()
            _uiState.update {
                it.copy(
                    playbackProgress = null,
                    playbackHistory = emptyList(),
                    historyAnime = if (it.homeSection == BrowseSection.History) LoadState.Loading else LoadState.Ready(emptyList()),
                    offlineEntries = LoadState.Ready(emptyList()),
                    downloadQueue = DownloadQueueSnapshot(),
                    offlineDownload = OfflineDownloadUiState(message = uiString(R.string.ui_cache_cleared)),
                    appContentCacheSizeBytes = sizeBytes,
                )
            }
            refresh()
        }
    }

    fun clearDownloadHistory() {
        DownloadCenter.clearHistory()
    }

    fun cancelDownload(taskId: Long) {
        DownloadCenter.requestCancel(taskId)
    }

    fun pauseDownload(taskId: Long) {
        DownloadCenter.requestPause(taskId)
    }

    fun resumeDownload(taskId: Long) {
        DownloadCenter.resumeTask(getApplication(), taskId)
    }

    private fun applyDetailsFilter(sourceAnimeId: Long? = null, transform: (BrowseFilters) -> BrowseFilters) {
        if (_uiState.value.forcedOfflineMode) {
            showTransientNotice(uiString(R.string.ui_offline_mode_unavailable))
            return
        }
        val filters = transform(BrowseFilters())
        val updatedSettings = saveBrowseFilters(filters)
        cacheCurrentDetailsRouteState()
        _uiState.update { state ->
            state.withCatalogFilters(
                filters = filters,
                settings = updatedSettings,
                navigationBackStack = state.navigationStackForDetailsFilter(sourceAnimeId),
            )
        }
        loadHome(reset = true)
    }

    fun loadMoreAnime() {
        val state = _uiState.value
        if (state.route != AppRoute.Home) return
        when (state.homeSection) {
            BrowseSection.Catalog -> {
                if (state.searchQuery.isBlank()) {
                    loadHome(reset = false)
                } else {
                    searchNow(state.searchQuery, reset = false)
                }
            }
            BrowseSection.Schedule -> Unit
            BrowseSection.History -> Unit
            BrowseSection.Downloads -> Unit
        }
    }

    fun playVideo(video: VideoVariant) {
        val title = _uiState.value.details.readyDataOrNull()?.title.orEmpty()
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideo(video: VideoVariant, animeTitle: String) {
        val title = animeTitle.ifBlank { _uiState.value.details.readyDataOrNull()?.title.orEmpty() }
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideoAt(video: VideoVariant, startPositionMs: Long) {
        val title = _uiState.value.details.readyDataOrNull()?.title
            ?: (_uiState.value.route as? AppRoute.Player)?.animeTitle
            ?: ""
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideoAtQuality(video: VideoVariant, startPositionMs: Long, preferredQuality: PreferredQuality) {
        val title = _uiState.value.details.readyDataOrNull()?.title
            ?: (_uiState.value.route as? AppRoute.Player)?.animeTitle
            ?: ""
        rememberPlaybackQualityOverride(video.animeId, preferredQuality)
        playVideoAt(video, startPositionMs, title, preferredQuality)
    }

    fun selectPlaybackSource(video: VideoVariant, startPositionMs: Long) {
        val route = _uiState.value.route as? AppRoute.Player
        val title = _uiState.value.details.readyDataOrNull()?.title
            ?: route?.animeTitle
            ?: ""
        val preferredQuality = route
            ?.takeIf { it.video.animeId == video.animeId && it.video.hasSameVoiceAs(video) }
            ?.preferredQuality
            ?: playbackQualityForAnime(video.animeId)
        rememberPlaybackSourceOverride(video)
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = title,
            preferredQuality = preferredQuality,
            clearPlaybackSourceState = true,
        )
    }

    fun playVideoWithResumeChoice(video: VideoVariant, resumePositionMs: Long) {
        val title = _uiState.value.details.readyDataOrNull()?.title.orEmpty()
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
            resumeChoicePositionMs = resumePositionMs.takeIf { it > 0L },
        )
    }

    fun choosePlayerResumePosition(startPositionMs: Long) {
        _uiState.update { state ->
            val route = state.route as? AppRoute.Player ?: return@update state
            if (route.resumeChoicePositionMs == null) return@update state
            state.copy(
                route = route.copy(
                    startPositionMs = startPositionMs.coerceAtLeast(0L),
                    resumeChoicePositionMs = null,
                ),
            )
        }
    }

    private fun playbackQualityForAnime(animeId: Long): PreferredQuality {
        return animePlaybackQualityOverrides[animeId] ?: _uiState.value.settings.defaultQuality
    }

    private fun rememberPlaybackQualityOverride(animeId: Long, preferredQuality: PreferredQuality) {
        if (animeId <= 0L) return
        if (preferredQuality == PreferredQuality.Auto) {
            animePlaybackQualityOverrides.remove(animeId)
            return
        }
        if (preferredQuality != _uiState.value.settings.defaultQuality ||
            animeId in animePlaybackQualityOverrides
        ) {
            animePlaybackQualityOverrides[animeId] = preferredQuality
        }
    }

    private fun rememberPlaybackSourceOverride(video: VideoVariant) {
        val sourceKey = video.sourceSelectionKey.takeIf { it.isNotBlank() } ?: return
        manualPlaybackSourceOverrides[video.playbackCacheKey()] = sourceKey
    }

    private fun manualPlaybackSourceKey(video: VideoVariant): String? {
        return manualPlaybackSourceOverrides[video.playbackCacheKey()]
            ?.takeIf { it.isNotBlank() }
    }

    fun consumePlayerNotice(id: Long) {
        _uiState.update { state ->
            if (state.playerNotice?.id == id) state.copy(playerNotice = null) else state
        }
    }

    private fun showTransientNotice(message: String) {
        _uiState.update {
            it.copy(
                playerNotice = PlayerNotice(
                    id = ++playerNoticeId,
                    message = message,
                ),
            )
        }
    }

    private fun uiString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val language = _uiState.value.settings.contentLanguage
        val context = getApplication<Application>()
        return if (formatArgs.isEmpty()) {
            context.localizedString(resId, language)
        } else {
            context.localizedString(resId, language, *formatArgs)
        }
    }

    private fun persistSettings(settings: AppSettings) {
        settingsSaveJob?.cancel()
        settingsSaveJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsStorage.save(settings)
            }
        }
    }

    private fun Throwable.isOfflineConnectivityFailure(): Boolean {
        return causalChain().any { it is UnknownHostException } ||
            userMessage().contains("Unable to resolve host", ignoreCase = true) ||
            userMessage().contains("No address associated with hostname", ignoreCase = true)
    }

    private fun Throwable.causalChain(): Sequence<Throwable> = sequence {
        val visited = mutableSetOf<Throwable>()
        var current: Throwable? = this@causalChain
        while (current != null && visited.add(current)) {
            yield(current)
            current = current.cause
        }
    }

    private fun resetPlaybackSourceRuntimeState(clearPlaybackSourceCache: Boolean) {
        failedPlaybackSourceKeys = emptySet()
        failedPlaybackSourceRetryAfterMs.clear()
        cancelPlaybackMetadataLoad()
        if (clearPlaybackSourceCache) {
            playbackSourceCache.clear()
        }
    }

    private fun playVideoAt(
        video: VideoVariant,
        startPositionMs: Long,
        titleOverride: String,
        preferredQuality: PreferredQuality = _uiState.value.settings.defaultQuality,
        resumeChoicePositionMs: Long? = null,
        clearPlaybackSourceState: Boolean = false,
    ) {
        resetPlaybackSourceRuntimeState(clearPlaybackSourceCache = clearPlaybackSourceState)
        playVideoFromCandidates(
            video = video,
            title = titleOverride,
            excludedSourceKeys = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = resumeChoicePositionMs,
        )
    }

    fun fallbackPlaybackSource(failedVideo: VideoVariant, playbackPositionMs: Long, failure: PlaybackFailure) {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        val manualSourceKey = manualPlaybackSourceKey(route.video)
        if (!shouldUseAutomaticPlaybackFallback(route.video, failedVideo, manualSourceKey, failure)) return
        val safePositionMs = playbackPositionMs.takeIf { it > 0L } ?: route.startPositionMs
        val fallbackNotice = if (route.video.isManualPlaybackSource(manualSourceKey)) {
            SourceFallbackNotice(
                selectedVideo = failedVideo,
                reason = failure.noticeReason(),
            )
        } else {
            null
        }
        markPlaybackSourceFailed(failedVideo)

        playVideoFromCandidates(
            video = route.video,
            title = route.animeTitle,
            excludedSourceKeys = blockedPlaybackSourceKeys(),
            startPositionMs = safePositionMs,
            preferredQuality = route.preferredQuality,
            sourceFallbackNotice = fallbackNotice,
        )
    }

    private fun playVideoFromCandidates(
        video: VideoVariant,
        title: String,
        excludedSourceKeys: Set<String>,
        startPositionMs: Long,
        preferredQuality: PreferredQuality,
        resumeChoicePositionMs: Long? = null,
        sourceFallbackNotice: SourceFallbackNotice? = null,
    ) {
        playerLoadJob?.cancel()
        cancelPlaybackMetadataLoad()
        val safeStartPositionMs = startPositionMs.coerceAtLeast(0L)
        val safeResumeChoicePositionMs = resumeChoicePositionMs?.takeIf { it > 0L }
        val forcedOfflineMode = _uiState.value.forcedOfflineMode
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Player(
                    video = video,
                    animeTitle = title,
                    startPositionMs = safeStartPositionMs,
                    preferredQuality = preferredQuality,
                    resumeChoicePositionMs = safeResumeChoicePositionMs,
                ),
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route !is AppRoute.Player),
                playerStream = LoadState.Loading,
                playbackMetadataLoading = false,
            )
        }

        playerLoadJob = viewModelScope.launch {
            val allVideos = playbackCandidatePool(video)
            val metadataCandidates = playbackCandidates(
                requested = video,
                allVideos = allVideos,
                excludedSourceKeys = emptySet(),
            ).let { sourceCandidates ->
                if (forcedOfflineMode) sourceCandidates.filter { it.isOfflineAvailable } else sourceCandidates
            }
            val candidates = metadataCandidates
                .filterNot { it.playbackSourceKey in excludedSourceKeys }
            if (forcedOfflineMode && candidates.isEmpty()) {
                _uiState.update {
                    it.copy(
                        offlineDownload = OfflineDownloadUiState(
                            isRunning = false,
                            message = uiString(R.string.ui_episode_unavailable_offline),
                        ),
                    )
                }
                return@launch
            }
            val routeVideo = if (forcedOfflineMode && !video.isOfflineAvailable) {
                candidates.first()
            } else {
                video
            }
            if (routeVideo != video) {
                _uiState.update { state ->
                    val currentRoute = state.route as? AppRoute.Player ?: return@update state
                    if (currentRoute.video == video && currentRoute.animeTitle == title) {
                        state.copy(route = currentRoute.copy(video = routeVideo))
                    } else {
                        state
                    }
                }
            }
            runCatching {
                resolvePlaybackWithCache(
                    requested = routeVideo,
                    candidates = candidates,
                    preferredQuality = preferredQuality,
                    metadataCandidates = metadataCandidates,
                    fastStart = true,
                )
            }
                .onSuccess { playback ->
                    val resolvedPlayback = playback.playback
                    val fallbackNotice = playback.manualFallbackNotice ?: sourceFallbackNotice
                    var acceptedPlayback = false
                    _uiState.update { state ->
                        val currentRoute = state.route as? AppRoute.Player
                        if (
                            currentRoute?.video == routeVideo &&
                            currentRoute.animeTitle == title &&
                            currentRoute.preferredQuality == preferredQuality
                        ) {
                            acceptedPlayback = true
                            state.copy(
                                route = currentRoute.copy(video = resolvedPlayback.video),
                                siteBaseUrl = repository.cachedSiteBaseUrl(),
                                selectedVideoGroup = resolvedPlayback.video.groupKey,
                                playerStream = LoadState.Ready(resolvedPlayback.stream),
                                playbackMetadataLoading = false,
                            )
                        } else {
                            state
                        }
                    }
                    if (acceptedPlayback) {
                        fallbackNotice?.let { showPlaybackSourceFallbackNotice(it, resolvedPlayback.video) }
                        startPlaybackMetadataLoad(
                            playback = resolvedPlayback,
                            title = title,
                            preferredQuality = preferredQuality,
                            metadataCandidates = metadataCandidates,
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        val currentRoute = state.route as? AppRoute.Player
                        if (
                            currentRoute?.video == routeVideo &&
                            currentRoute.animeTitle == title &&
                            currentRoute.preferredQuality == preferredQuality
                        ) {
                            state.copy(
                                playerStream = LoadState.Error(throwable.userMessage()),
                                playbackMetadataLoading = false,
                            )
                        } else {
                            state
                        }
                    }
                }
        }
    }

    private suspend fun playbackCandidatePool(video: VideoVariant): List<VideoVariant> {
        val stateVideos = _uiState.value.videos.readyListOrEmpty()
        val stateAnimeVideos = stateVideos.filter { it.animeId == video.animeId }
        val hasUsableStatePool = stateAnimeVideos.size > 1 &&
            stateAnimeVideos.any { it.isSameEpisodeAs(video) && it.hasSameVoiceAs(video) }
        if (hasUsableStatePool) {
            return stateAnimeVideos
        }
        if (video.animeId <= 0L || _uiState.value.forcedOfflineMode) {
            return stateAnimeVideos.ifEmpty { stateVideos.ifEmpty { listOf(video) } }
        }
        val loadedVideos = runCatching { repository.getVideos(video.animeId) }
            .getOrDefault(emptyList())
        if (loadedVideos.isNotEmpty()) {
            _uiState.update { state ->
                val route = state.route as? AppRoute.Player ?: return@update state
                if (route.video.animeId == video.animeId) {
                    state.copy(videos = LoadState.Ready(loadedVideos))
                } else {
                    state
                }
            }
            return loadedVideos
        }
        return stateAnimeVideos.ifEmpty { stateVideos.ifEmpty { listOf(video) } }
    }

    private fun startPlaybackMetadataLoad(
        playback: ResolvedPlayback,
        title: String,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant>,
    ) {
        val loadId = ++playbackMetadataLoadId
        playbackMetadataJob?.cancel()
        val playbackVideo = playback.video
        val playbackStreamUrl = playback.stream.url
        setPlaybackMetadataLoading(
            playbackVideo = playbackVideo,
            title = title,
            preferredQuality = preferredQuality,
            playbackStreamUrl = playbackStreamUrl,
            loading = true,
        )
        playbackMetadataJob = viewModelScope.launch {
            try {
                val enrichedPlayback = repository.resolvePlaybackMetadata(
                    playback = playback,
                    metadataCandidates = metadataCandidates,
                    preferredQuality = preferredQuality,
                )
                _uiState.update { state ->
                    val currentRoute = state.route as? AppRoute.Player ?: return@update state
                    val activeStream = state.playerStream.readyDataOrNull() ?: return@update state
                    if (
                        !currentRoute.matchesPlaybackMetadataRequest(
                            title = title,
                            preferredQuality = preferredQuality,
                            playbackVideo = playbackVideo,
                            activeStream = activeStream,
                            playbackStreamUrl = playbackStreamUrl,
                        )
                    ) {
                        return@update state
                    }
                    if (enrichedPlayback.video == currentRoute.video && enrichedPlayback.stream == activeStream) {
                        return@update state.copy(playbackMetadataLoading = false)
                    }
                    state.copy(
                        route = currentRoute.copy(video = enrichedPlayback.video),
                        siteBaseUrl = repository.cachedSiteBaseUrl(),
                        selectedVideoGroup = enrichedPlayback.video.groupKey,
                        playerStream = LoadState.Ready(enrichedPlayback.stream),
                        playbackMetadataLoading = false,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                AppLog.w("YummyDroidPlayer", "Playback metadata load failed", throwable)
            } finally {
                if (playbackMetadataLoadId == loadId) {
                    setPlaybackMetadataLoading(
                        playbackVideo = playbackVideo,
                        title = title,
                        preferredQuality = preferredQuality,
                        playbackStreamUrl = playbackStreamUrl,
                        loading = false,
                    )
                }
            }
        }
    }

    private fun cancelPlaybackMetadataLoad() {
        playbackMetadataLoadId += 1L
        playbackMetadataJob?.cancel()
        playbackMetadataJob = null
        _uiState.update { state ->
            if (state.playbackMetadataLoading) {
                state.copy(playbackMetadataLoading = false)
            } else {
                state
            }
        }
    }

    private fun setPlaybackMetadataLoading(
        playbackVideo: VideoVariant,
        title: String,
        preferredQuality: PreferredQuality,
        playbackStreamUrl: String,
        loading: Boolean,
    ) {
        _uiState.update { state ->
            val currentRoute = state.route as? AppRoute.Player ?: return@update state
            val activeStream = state.playerStream.readyDataOrNull() ?: return@update state
            if (
                !currentRoute.matchesPlaybackMetadataRequest(
                    title = title,
                    preferredQuality = preferredQuality,
                    playbackVideo = playbackVideo,
                    activeStream = activeStream,
                    playbackStreamUrl = playbackStreamUrl,
                )
            ) {
                return@update state
            }
            if (state.playbackMetadataLoading == loading) state else state.copy(playbackMetadataLoading = loading)
        }
    }

    private fun AppRoute.Player.matchesPlaybackMetadataRequest(
        title: String,
        preferredQuality: PreferredQuality,
        playbackVideo: VideoVariant,
        activeStream: ResolvedVideoStream,
        playbackStreamUrl: String,
    ): Boolean {
        return animeTitle == title &&
            this.preferredQuality == preferredQuality &&
            video.isSameEpisodeAs(playbackVideo) &&
            video.hasSameVoiceAs(playbackVideo) &&
            video.hasSamePlaybackSourceAs(playbackVideo) &&
            activeStream.url == playbackStreamUrl
    }

    fun confirmPlaybackSource(video: VideoVariant) {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        if (!route.video.hasSamePlaybackSourceAs(video)) return

        val stream = _uiState.value.playerStream.readyDataOrNull()
        val sourceKey = video.playbackSourceKey
        if (manualPlaybackSourceKey(video) == null) {
            playbackSourceCache[video.playbackCacheKey()] = PlaybackSourceCacheEntry(
                providerKey = video.sourceSelectionKey,
                maxVideoHeight = stream?.comparableVideoHeight()?.takeIf { it > 0 },
            )
        }
        if (sourceKey in failedPlaybackSourceKeys) {
            failedPlaybackSourceKeys = failedPlaybackSourceKeys - sourceKey
        }
        failedPlaybackSourceRetryAfterMs.remove(sourceKey)
        maybeAutoMarkWatching(video)
    }

    fun handlePlaybackEnded(video: VideoVariant) {
        val state = _uiState.value
        val details = state.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
            ?: return
        val videos = state.videos.readyListOrEmpty()

        if (
            state.settings.autoMarkWatchedOnCompletedFinalEpisode &&
            state.auth.profile != null &&
            details.isFullyReleased() &&
            video.isFinalEpisodeFor(details, videos)
        ) {
            scheduleAutoSetAnimeListMark(video.animeId, UserAnimeListMark.Watched)
        }

        if (!video.hasFollowingEpisodeIn(videos)) {
            openAnime(video.animeId, pushCurrent = false)
        }
    }

    fun savePlaybackProgress(video: VideoVariant, positionMs: Long, durationMs: Long) {
        if (video.animeId <= 0L || positionMs < 0L) return

        val currentDetails = _uiState.value.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
        val progress = PlaybackProgress(
            animeId = video.animeId,
            videoId = video.id,
            animeTitle = currentDetails?.title.orEmpty(),
            posterUrl = currentDetails?.posterUrl.orEmpty(),
            groupKey = video.groupKey,
            episode = video.episode.ifBlank { video.matchingEpisodeKey },
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = System.currentTimeMillis(),
        )
        var inMemoryHistory = listOf(progress)
        _uiState.update { state ->
            if (state.details.readyDataOrNull()?.id == video.animeId) {
                val history = (state.playbackHistory + progress).distinctLatestByEpisode()
                inMemoryHistory = history
                state.copy(
                    playbackProgress = progress,
                    playbackHistory = history,
                    historyAnime = state.historyAnime.updatedWithLocalHistorySnapshot(
                        progress = progress,
                        anime = currentDetails?.toAnimeSummary(),
                    ),
                )
            } else {
                state.copy(
                    historyAnime = state.historyAnime.updatedWithLocalHistorySnapshot(
                        progress = progress,
                        anime = currentDetails?.toAnimeSummary(),
                    ),
                )
            }
        }
        updateCachedPlaybackProgress(progress, inMemoryHistory)
        playbackProgressWriteJobs[video.animeId]?.cancel()
        playbackProgressWriteJobs[video.animeId] = viewModelScope.launch {
            delay(250)
            val storedHistory = withContext(Dispatchers.IO) {
                currentDetails?.toAnimeSummary()?.let(historyAnimeCacheStorage::save)
                playbackProgressStorage.save(progress)
                playbackProgressStorage.readAnimeHistory(video.animeId)
            }
            updateCachedPlaybackProgress(progress, storedHistory)
            _uiState.update { state ->
                if (state.details.readyDataOrNull()?.id == video.animeId) {
                    state.copy(playbackHistory = storedHistory)
                } else {
                    state
                }
            }
            playbackProgressWriteJobs.remove(video.animeId)
        }
        playbackProgressSiteMirrors(progress, video).forEach(::syncPlaybackProgressToSite)
    }

    private fun playbackProgressSiteMirrors(
        progress: PlaybackProgress,
        video: VideoVariant,
    ): List<PlaybackProgress> {
        val sameEpisodeVoiceVideos = _uiState.value.videos.readyListOrEmpty()
            .asSequence()
            .filter { candidate ->
                candidate.animeId == video.animeId &&
                    candidate.id > 0L &&
                    candidate.isSameEpisodeAs(video) &&
                    candidate.hasSameVoiceAs(video)
            }
            .distinctBy { it.id }
            .toList()
            .ifEmpty { listOf(video).filter { it.id > 0L } }

        return sameEpisodeVoiceVideos.map { candidate ->
            progress.copy(
                videoId = candidate.id,
                groupKey = candidate.groupKey,
                episode = candidate.episode.ifBlank { progress.episode },
            )
        }
    }

    fun resetAnimeWatchProgress(animeId: Long) {
        if (animeId <= 0L) return
        val state = _uiState.value
        viewModelScope.launch {
            val storedVideoIds = withContext(Dispatchers.IO) {
                playbackProgressStorage.readAnimeHistory(animeId).map { it.videoId }
            }
            val videoIds = (
                state.videos.readyListOrEmpty()
                    .filter { it.animeId == animeId }
                    .map { it.id } +
                    state.playbackHistory
                        .filter { it.animeId == animeId }
                        .map { it.videoId } +
                    storedVideoIds
                )
                .filter { it > 0L }
                .distinct()

            clearAnimeWatchProgressLocally(animeId, videoIds)
            if (state.forcedOfflineMode || state.auth.profile == null || videoIds.isEmpty()) return@launch
            deleteAnimeWatchProgressFromSite(animeId, videoIds)
        }
    }

    private suspend fun deleteAnimeWatchProgressFromSite(animeId: Long, videoIds: List<Long>) {
        runCatching { repository.deleteWatchProgress(videoIds) }
            .onSuccess {
                clearAnimeWatchProgressLocally(animeId, videoIds)
                if (_uiState.value.homeSection == BrowseSection.History) {
                    loadHistory(force = true)
                }
            }
            .onFailure { throwable ->
                if (!requestCaptchaRetry(throwable) { deleteAnimeWatchProgressFromSite(animeId, videoIds) }) {
                    AppLog.w("YummyDroidHistory", "Failed to reset anime watch progress", throwable)
                }
            }
    }

    private suspend fun clearAnimeWatchProgressLocally(animeId: Long, videoIds: Collection<Long>) {
        videoIds
            .filter { it > 0L }
            .distinct()
            .forEach { videoId ->
                playbackProgressSyncJobs.remove(videoId)?.cancel()
            }
        playbackProgressWriteJobs.remove(animeId)?.cancel()
        withContext(Dispatchers.IO) {
            playbackProgressStorage.clearAnime(animeId)
        }
        clearCachedPlaybackProgress(animeId)
        _uiState.update { state ->
            val isCurrentDetails = state.details.readyDataOrNull()?.id == animeId
            state.copy(
                playbackProgress = if (isCurrentDetails) null else state.playbackProgress,
                playbackHistory = if (isCurrentDetails) emptyList() else state.playbackHistory,
                historyAnime = state.historyAnime.withoutAnime(animeId),
            )
        }
    }

    private fun maybeAutoMarkWatching(video: VideoVariant) {
        val state = _uiState.value
        if (state.forcedOfflineMode) return
        if (!state.settings.autoMarkWatchingOnPlayback || state.auth.profile == null) return

        val currentMark = state.animeMark.readyDataOrNull()
            ?.takeIf { state.details.readyDataOrNull()?.id == video.animeId }
        if (currentMark?.list == UserAnimeListMark.Watching || currentMark?.list == UserAnimeListMark.Watched) {
            return
        }

        scheduleAutoSetAnimeListMark(
            animeId = video.animeId,
            mark = UserAnimeListMark.Watching,
            preserveWatched = true,
        )
    }

    private fun scheduleAutoSetAnimeListMark(
        animeId: Long,
        mark: UserAnimeListMark,
        preserveWatched: Boolean = false,
    ) {
        autoAnimeMarkJobs[animeId]?.cancel()
        val job = viewModelScope.launch {
            runCatching {
                val state = _uiState.value
                if (state.forcedOfflineMode) return@launch
                if (state.auth.profile == null) return@launch

                val stateMark = state.animeMark.readyDataOrNull()
                    ?.takeIf { state.details.readyDataOrNull()?.id == animeId }
                if (stateMark?.list == mark || (preserveWatched && stateMark?.list == UserAnimeListMark.Watched)) {
                    return@launch
                }

                val currentMark = stateMark ?: repository.getAnimeMark(animeId)
                if (currentMark?.list == mark || (preserveWatched && currentMark?.list == UserAnimeListMark.Watched)) {
                    return@launch
                }

                repository.setAnimeListMark(animeId, mark)
            }
                .onSuccess { updatedMark ->
                    _uiState.update { state ->
                        if (state.details.readyDataOrNull()?.id == animeId) {
                            state.copy(animeMark = LoadState.Ready(updatedMark))
                        } else {
                            state
                        }
                    }
                }
                .onFailure { throwable ->
                    AppLog.w("YummyDroidMarks", "Failed to auto set anime mark", throwable)
                }
        }
        autoAnimeMarkJobs[animeId] = job
        job.invokeOnCompletion {
            if (autoAnimeMarkJobs[animeId] == job) {
                autoAnimeMarkJobs.remove(animeId)
            }
        }
    }

    fun retryVideo() {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        playVideoAt(route.video, route.startPositionMs)
    }

    fun submitCaptchaResponse(captchaResponse: String) {
        val action = pendingCaptchaAction ?: return
        if (captchaResponse.isBlank()) return
        pendingCaptchaAction = null
        repository.submitCaptchaResponse(captchaResponse)
        viewModelScope.launch { action() }
    }

    fun cancelCaptchaChallenge(error: String?) {
        pendingCaptchaAction = null
        _uiState.update {
            it.copy(
                auth = it.auth.copy(
                    loading = false,
                    error = error?.takeIf { message -> message.isNotBlank() },
                ),
            )
        }
    }

    private fun requestCaptchaRetry(throwable: Throwable, action: suspend () -> Unit): Boolean {
        if (throwable !is CaptchaRequiredException) return false
        pendingCaptchaAction = action
        _uiState.update {
            it.copy(
                auth = it.auth.copy(
                    loading = false,
                    error = throwable.userMessage(),
                    captchaRequestNonce = it.auth.captchaRequestNonce + 1,
                ),
            )
        }
        return true
    }

    fun login(login: String, password: String, captchaResponse: String? = null) {
        if (login.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(auth = it.auth.copy(error = uiString(R.string.ui_enter_login_and_password))) }
            return
        }

        val normalizedLogin = login.trim()
        _uiState.update { it.copy(auth = it.auth.copy(loading = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.login(normalizedLogin, password, captchaResponse) }
                .onSuccess { profile ->
                    restoreKnownAnimeRatings(profile)
                    restoreVideoSubscriptionHints(profile)
                    _uiState.update { it.copy(auth = AuthUiState(profile = profile)) }
                    syncPlaybackHistoryFromSite()
                    syncVideoSubscriptionsFromSite()
                    (_uiState.value.route as? AppRoute.Details)?.let { route ->
                        loadAnimeMark(route.animeId)
                        loadAnimeExtras(route.animeId)
                    }
                }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { login(normalizedLogin, password) }) {
                        _uiState.update {
                            it.copy(auth = AuthUiState(error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun logout() {
        autoAnimeMarkJobs.values.forEach { it.cancel() }
        autoAnimeMarkJobs.clear()
        playbackHistorySyncJob?.cancel()
        playbackProgressSyncJobs.values.forEach { it.cancel() }
        playbackProgressSyncJobs.clear()
        knownAnimeRatings.clear()
        detailsRouteCache.clear()
        videoSubscriptionHints.clear()
        subscriptionsSyncJob?.cancel()
        profileNotificationsSyncJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.logout() }
        }
        SubscriptionNotificationScheduler.cancel(getApplication())
        val filters = _uiState.value.filters.copy(userMarks = emptySet())
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update {
            it.copy(
                auth = AuthUiState(),
                animeMark = LoadState.Ready(null),
                globalSubscriptions = LoadState.Ready(emptyList()),
                profileNotifications = LoadState.Ready(emptyList()),
                filters = filters,
                settings = updatedSettings,
            )
        }
        reloadBrowse()
    }

    private fun authenticatedDetailsAnimeIdOrNull(): Long? {
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return null
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return null
        }
        return animeId
    }

    fun selectAnimeListMark(mark: UserAnimeListMark) {
        val animeId = authenticatedDetailsAnimeIdOrNull() ?: return

        val previousMarkState = _uiState.value.animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        val optimisticMark = if (current.list == mark) {
            current.copy(list = null)
        } else {
            current.copy(list = mark)
        }
        setAnimeMarkState(animeId, LoadState.Ready(optimisticMark))
        viewModelScope.launch {
            runCatching {
                if (current.list == mark) {
                    repository.removeAnimeListMark(animeId)
                } else {
                    repository.setAnimeListMark(animeId, mark)
                }
            }
                .onSuccess { updatedMark ->
                    setAnimeMarkState(animeId, LoadState.Ready(updatedMark))
                }
                .onFailure { throwable ->
                    handleAnimeMarkMutationFailure(
                        animeId = animeId,
                        previousMarkState = previousMarkState,
                        throwable = throwable,
                    ) {
                        selectAnimeListMark(mark)
                    }
                }
        }
    }

    fun toggleFavorite() {
        val animeId = authenticatedDetailsAnimeIdOrNull() ?: return

        val previousMarkState = _uiState.value.animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        val optimisticMark = current.copy(isFavorite = !current.isFavorite)
        setAnimeMarkState(animeId, LoadState.Ready(optimisticMark))
        viewModelScope.launch {
            runCatching { repository.setFavorite(animeId, !current.isFavorite) }
                .onSuccess { updatedMark ->
                    setAnimeMarkState(animeId, LoadState.Ready(updatedMark))
                }
                .onFailure { throwable ->
                    handleAnimeMarkMutationFailure(
                        animeId = animeId,
                        previousMarkState = previousMarkState,
                        throwable = throwable,
                    ) {
                        toggleFavorite()
                    }
                }
        }
    }

    private fun setAnimeMarkState(animeId: Long, animeMark: LoadState<UserAnimeMark?>) {
        _uiState.update { it.copy(animeMark = animeMark) }
        cacheDetailsRouteState(animeId)
    }

    private fun handleAnimeMarkMutationFailure(
        animeId: Long,
        previousMarkState: LoadState<UserAnimeMark?>,
        throwable: Throwable,
        retry: () -> Unit,
    ) {
        if (throwable is CaptchaRequiredException) {
            setAnimeMarkState(animeId, previousMarkState)
            requestCaptchaRetry(throwable, retry)
            return
        }
        _uiState.update {
            it.copy(
                animeMark = previousMarkState,
                auth = it.auth.copy(error = throwable.userMessage()),
            )
        }
        cacheDetailsRouteState(animeId)
    }

    fun navigateBack() {
        cacheCurrentDetailsRouteState()
        applyNavigationTransition { state ->
            backNavigationTransition(
                state = state,
                catalogCacheForFilters = catalogPageCache::get,
                detailsCacheForAnime = detailsRouteCache::get,
            )
        }
    }

    private fun restoreNavigationEntry(
        entry: NavigationEntry,
        remainingBackStack: List<NavigationEntry>,
        preserveHomeSection: Boolean = false,
    ) {
        applyNavigationTransition { state ->
            restoreNavigationEntryTransition(
                state = state,
                entry = entry,
                remainingBackStack = remainingBackStack,
                cachedCatalogForEntry = catalogPageCache[entry.filters],
                cachedDetailsForEntry = (entry.route as? AppRoute.Details)
                    ?.animeId
                    ?.let(detailsRouteCache::get),
                preserveHomeSection = preserveHomeSection,
            )
        }
    }

    private fun applyNavigationTransition(
        transitionFor: (YummyDroidUiState) -> NavigationTransition,
    ) {
        val transition = transitionFor(_uiState.value)
        if (transition.cancelSearchRequests) {
            searchDebounceJob?.cancel()
            searchLoadJob?.cancel()
        }
        _uiState.value = transition.state
        transition.effects.forEach(::applyNavigationEffect)
    }

    private fun applyNavigationEffect(effect: NavigationEffect) {
        when (effect) {
            NavigationEffect.LoadCatalog -> loadHome(reset = true)
            is NavigationEffect.SearchCatalog -> searchNow(effect.query, reset = true)
            is NavigationEffect.EnsureBrowseSection -> ensureBrowseSectionLoaded(effect.section)
            is NavigationEffect.RefreshPlaybackProgress -> refreshPlaybackProgressSnapshot(effect.animeId)
            is NavigationEffect.LoadAnimeDetails -> loadAnimeDetails(effect.animeId)
            is NavigationEffect.OpenAnime -> openAnime(effect.animeId, pushCurrent = false)
            is NavigationEffect.PlayVideo -> effect.route.run {
                playVideoAt(video, startPositionMs, animeTitle, preferredQuality)
            }
        }
    }

    private fun loadHome(reset: Boolean = true) {
        val currentState = _uiState.value
        val request = animePageRequest(
            items = currentState.featured,
            paging = currentState.featuredPaging,
            reset = reset,
        ) ?: return

        if (reset) {
            catalogCacheInitialized = true
            featuredLoadJob?.cancel()
        }
        _uiState.update { it.withCatalogPageLoading(reset = reset, request = request) }

        featuredLoadJob = viewModelScope.launch {
            val filters = _uiState.value.filters
            runCatching { repository.getFeatured(filters, offset = request.offset, limit = PAGE_SIZE) }
                .onSuccess { animes ->
                    val forcedOfflineMode = repository.isOfflineFallbackActive()
                    var cacheUpdate: CatalogRouteCache? = null
                    _uiState.update { state ->
                        val update = reduceCatalogPageSuccess(
                            state = state,
                            requestedFilters = filters,
                            incoming = animes,
                            reset = reset,
                            pageSize = PAGE_SIZE,
                            forcedOfflineMode = forcedOfflineMode,
                        )
                        cacheUpdate = update?.cache
                        update?.state ?: state
                    }
                    cacheUpdate?.let { catalogPageCache[filters] = it }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    val offlineFailure = throwable.isOfflineConnectivityFailure()
                    val errorMessage = throwable.userMessage()
                    _uiState.update { state ->
                        reduceCatalogPageFailure(
                            state = state,
                            requestedFilters = filters,
                            reset = reset,
                            offlineFailure = offlineFailure,
                            error = errorMessage,
                        )
                    }
                    if (offlineFailure) loadOfflineEntries()
                }
        }
    }

    private fun loadSchedule(force: Boolean = true) {
        val state = _uiState.value
        if (state.forcedOfflineMode) {
            scheduleLoadJob?.cancel()
            _uiState.update { it.copy(schedule = LoadState.Ready(emptyList())) }
            return
        }
        val hasReadySchedule = state.schedule is LoadState.Ready
        val shouldRefresh = force ||
            !scheduleCacheInitialized ||
            !hasReadySchedule ||
            remoteRefreshDue(scheduleLastRemoteCheckAtMs)
        if (!shouldRefresh) return
        if (!force && scheduleLoadJob?.isActive == true) return
        val shouldShowLoading = force || !scheduleCacheInitialized || !hasReadySchedule
        scheduleCacheInitialized = true
        scheduleLoadJob?.cancel()
        if (shouldShowLoading) {
            _uiState.update { it.copy(schedule = LoadState.Loading) }
        }
        scheduleLoadJob = viewModelScope.launch {
            scheduleLastRemoteCheckAtMs = SystemClock.elapsedRealtime()
            runCatching { repository.getSchedule() }
                .onSuccess { schedule -> _uiState.update { it.copy(schedule = LoadState.Ready(schedule)) } }
                .onFailure { throwable ->
                    _uiState.update { current ->
                        if (!shouldShowLoading && current.schedule is LoadState.Ready) {
                            current
                        } else {
                            current.copy(schedule = LoadState.Error(throwable.userMessage()))
                        }
                    }
                }
        }
    }

    private fun loadHistory(force: Boolean = true) {
        val state = _uiState.value
        val plan = watchHistoryCoordinator.beginRefresh(
            force = force,
            hasReadyHistory = state.historyAnime is LoadState.Ready,
            canUseRemote = !state.forcedOfflineMode && state.auth.profile != null,
            loadActive = historyLoadJob?.isActive == true,
        ) ?: return
        historyLoadJob?.cancel()
        if (plan.showCachedSnapshot) {
            _uiState.update { it.copy(historyAnime = LoadState.Loading) }
        }
        historyLoadJob = viewModelScope.launch {
            val resolution = watchHistoryCoordinator.load(
                plan = plan,
                canUseRemote = {
                    !_uiState.value.forcedOfflineMode && _uiState.value.auth.profile != null
                },
                onCachedSnapshot = { anime ->
                    _uiState.update { it.copy(historyAnime = LoadState.Ready(anime)) }
                },
                shouldRetryRemoteFailure = { throwable ->
                    requestCaptchaRetry(throwable) { loadHistory(force = true) }.also { retrying ->
                        if (retrying) {
                            _uiState.update { it.copy(historyAnime = LoadState.Loading) }
                        }
                    }
                },
            ) ?: return@launch
            when (resolution) {
                is WatchHistoryResolution.Failed -> {
                    val errorMessage = resolution.cause
                        .userMessage()
                        .ifBlank { uiString(R.string.ui_history_temporarily_unavailable) }
                    _uiState.update { it.copy(historyAnime = LoadState.Error(errorMessage)) }
                }
                is WatchHistoryResolution.Ready -> {
                    _uiState.update { it.copy(historyAnime = LoadState.Ready(resolution.anime)) }
                }
            }
        }
    }

    private fun remoteRefreshDue(lastCheckAtMs: Long): Boolean {
        return lastCheckAtMs == 0L ||
            SystemClock.elapsedRealtime() - lastCheckAtMs >= BROWSE_REMOTE_REFRESH_INTERVAL_MS
    }

    private fun ensureBrowseSectionLoaded(section: BrowseSection) {
        if (_uiState.value.forcedOfflineMode && section != BrowseSection.Downloads) {
            loadOfflineEntries()
            return
        }
        when (section) {
            BrowseSection.Catalog -> {
                if (!catalogCacheInitialized) {
                    loadHome(reset = true)
                }
            }
            BrowseSection.Schedule -> loadSchedule(force = false)
            BrowseSection.History -> loadHistory(force = false)
            BrowseSection.Downloads -> loadOfflineEntries()
        }
    }

    private fun LoadState<List<Anime>>.updatedWithLocalHistorySnapshot(
        progress: PlaybackProgress,
        anime: Anime?,
    ): LoadState<List<Anime>> {
        val summary = anime ?: progress.toAnimeSummary()
        return when (this) {
            is LoadState.Ready -> LoadState.Ready(
                (listOf(summary) + data.filterNot { it.id == progress.animeId })
                    .distinctBy { it.id },
            )
            else -> this
        }
    }

    private fun LoadState<List<Anime>>.withoutAnime(animeId: Long): LoadState<List<Anime>> {
        return when (this) {
            is LoadState.Ready -> LoadState.Ready(data.filterNot { it.id == animeId })
            else -> this
        }
    }

    private fun loadOfflineEntries() {
        offlineLoadJob?.cancel()
        _uiState.update { it.copy(offlineEntries = LoadState.Loading) }
        offlineLoadJob = viewModelScope.launch {
            runCatching { repository.offlineAnime() }
                .onSuccess { entries -> _uiState.update { it.copy(offlineEntries = LoadState.Ready(entries)) } }
                .onFailure { throwable -> _uiState.update { it.copy(offlineEntries = LoadState.Error(throwable.userMessage())) } }
        }
    }

    private fun observeDownloadQueue() {
        downloadQueueJob?.cancel()
        downloadQueueJob = viewModelScope.launch {
            DownloadCenter.state.collect { snapshot ->
                val active = snapshot.activeTasks.firstOrNull()
                val latest = snapshot.tasks.firstOrNull()
                val completedIds = snapshot.tasks
                    .filter { it.state == DownloadTaskState.Completed }
                    .map { it.id }
                    .toSet()
                val hasNewCompletion = completedIds.any { it !in completedDownloadTaskIds }
                completedDownloadTaskIds = completedIds

                _uiState.update { state ->
                    state.copy(
                        downloadQueue = snapshot,
                        offlineDownload = when {
                            active != null -> OfflineDownloadUiState(
                                videoId = active.videoId,
                                isRunning = true,
                                progress = active.progress,
                                message = active.message.ifBlank { uiString(R.string.ui_loading) },
                            )
                            latest != null -> OfflineDownloadUiState(
                                videoId = latest.videoId,
                                isRunning = false,
                                progress = latest.progress,
                                message = latest.message.ifBlank { latest.state.title },
                            )
                            else -> state.offlineDownload.copy(isRunning = false)
                        },
                    )
                }

                if (hasNewCompletion) {
                    loadOfflineEntries()
                    val currentAnimeId = _uiState.value.details.readyDataOrNull()?.id
                    if (currentAnimeId != null) {
                        refreshCurrentDetailsFromOfflineCache(currentAnimeId)
                    }
                }
            }
        }
    }

    private fun refreshCurrentDetailsFromOfflineCache(animeId: Long) {
        val currentDetails = _uiState.value.details.readyDataOrNull()
            ?.takeIf { it.id == animeId }
            ?: return
        viewModelScope.launch {
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (details, videos) ->
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(details),
                            videos = LoadState.Ready(videos),
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure {
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(currentDetails),
                            videos = LoadState.Ready(emptyList()),
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    cacheDetailsRouteState(animeId)
                }
        }
    }

    private fun loadFilterCatalog() {
        _uiState.update { it.copy(filterCatalog = LoadState.Loading) }
        viewModelScope.launch {
            runCatching { repository.getFilterCatalog() }
                .onSuccess { catalog ->
                    _uiState.update { it.copy(filterCatalog = LoadState.Ready(catalog)) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(filterCatalog = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    private fun restoreProfile() {
        _uiState.update { it.copy(auth = it.auth.copy(loading = true)) }
        viewModelScope.launch {
            val cachedProfile = withContext(Dispatchers.IO) { repository.cachedProfile() }
            _uiState.update { it.copy(auth = AuthUiState(profile = cachedProfile, loading = true)) }
            runCatching { repository.restoreProfile() }
            .onSuccess { profile ->
                val activeProfile = profile
                restoreKnownAnimeRatings(activeProfile)
                restoreVideoSubscriptionHints(activeProfile)
                _uiState.update { it.copy(auth = AuthUiState(profile = activeProfile)) }
                if (activeProfile != null) {
                    syncPlaybackHistoryFromSite()
                    syncVideoSubscriptionsFromSite()
                }
            }
                .onFailure { throwable ->
                    if (throwable.isUnauthorizedApiError()) {
                        withContext(Dispatchers.IO) { repository.logout() }
                        knownAnimeRatings.clear()
                        detailsRouteCache.clear()
                        videoSubscriptionHints.clear()
                        _uiState.update { it.copy(auth = AuthUiState()) }
                    } else {
                        _uiState.update {
                            it.copy(auth = AuthUiState(profile = cachedProfile, error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    private fun loadAnimeMark(animeId: Long) {
        animeMarkJob?.cancel()
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update { it.copy(animeMark = LoadState.Ready(null)) }
            return
        }
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(animeMark = LoadState.Ready(null)) }
            return
        }

        _uiState.update { it.copy(animeMark = LoadState.Loading) }
        animeMarkJob = viewModelScope.launch {
            runCatching { repository.getAnimeMark(animeId) }
                .onSuccess { mark ->
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                        state.copy(animeMark = LoadState.Ready(mark))
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                        state.copy(animeMark = LoadState.Error(throwable.userMessage()))
                    }
                    cacheDetailsRouteState(animeId)
                }
        }
    }

    private fun loadAnimeExtras(animeId: Long) {
        detailsExtrasJob?.cancel()
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update { it.copy(detailsExtras = LoadState.Ready(AnimeDetailsExtras())) }
            return
        }
        _uiState.update { it.copy(detailsExtras = LoadState.Loading) }
        detailsExtrasJob = viewModelScope.launch {
            val comments = runCatching {
                repository.getAnimeComments(animeId, offset = 0, limit = COMMENTS_PAGE_SIZE)
            }.getOrDefault(emptyList())
            val recommendations = runCatching { repository.getAnimeRecommendations(animeId) }.getOrDefault(emptyList())
            val currentUserRating = _uiState.value.details.readyDataOrNull()
                ?.takeIf { it.id == animeId }
                ?.let {
                    effectiveAnimeRating(
                        animeId = animeId,
                        remoteRating = it.userRating,
                        trustRemote = _uiState.value.auth.profile != null && !_uiState.value.forcedOfflineMode,
                    )
                }
                ?.takeIf { it in 1..10 }
            val rating = runCatching { repository.getAnimeRatingSummary(animeId) }
                .getOrDefault(AnimeRatingSummary())
                .copy(userRating = currentUserRating)
            val subscriptionResult = if (_uiState.value.auth.profile != null) {
                runCatching { loadResolvedVideoSubscriptions() }
            } else {
                null
            }
            val serverSubscriptions = when {
                subscriptionResult == null -> emptyList()
                subscriptionResult.isSuccess -> {
                    val loadedSubscriptions = subscriptionResult.getOrThrow()
                    updateGlobalSubscriptions(loadedSubscriptions)
                    loadedSubscriptions
                }
                else -> emptyList()
            }
            val details = _uiState.value.details.readyDataOrNull()
            val videos = _uiState.value.videos.readyListOrEmpty()
            val subscriptions = canonicalizeVideoSubscriptionsForVideos(
                subscriptions = serverSubscriptions,
                videos = videos.filter { it.animeId == animeId },
                hints = videoSubscriptionHints,
                title = details?.title.orEmpty(),
                posterUrl = details?.posterUrl.orEmpty(),
            )
            _uiState.update { state ->
                if ((state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                ) {
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            AnimeDetailsExtras(
                                comments = comments,
                                commentsPaging = PagingUiState(
                                    isLoadingMore = false,
                                    canLoadMore = comments.size >= COMMENTS_PAGE_SIZE,
                                ),
                                recommendations = recommendations,
                                rating = rating,
                                subscriptions = subscriptions,
                            ),
                        ),
                    )
                } else {
                    state
                }
            }
            cacheDetailsRouteState(animeId)
        }
    }

    fun loadMoreAnimeComments() {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        val extras = _uiState.value.detailsExtras.readyDataOrNull() ?: return
        if (extras.commentsPaging.isLoadingMore || !extras.commentsPaging.canLoadMore) return

        val offset = extras.comments.size
        commentsLoadJob?.cancel()
        _uiState.update { state ->
            val current = state.detailsExtras.readyDataOrNull() ?: return@update state
            state.copy(
                detailsExtras = LoadState.Ready(
                    current.copy(
                        commentsPaging = current.commentsPaging.copy(
                            isLoadingMore = true,
                            error = null,
                        ),
                    ),
                ),
            )
        }

        commentsLoadJob = viewModelScope.launch {
            runCatching {
                repository.getAnimeComments(animeId, offset = offset, limit = COMMENTS_PAGE_SIZE)
            }.onSuccess { comments ->
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@update state
                    val merged = (current.comments + comments).distinctBy { it.id }
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            current.copy(
                                comments = merged,
                                commentsPaging = PagingUiState(
                                    isLoadingMore = false,
                                    canLoadMore = comments.size >= COMMENTS_PAGE_SIZE && merged.size > current.comments.size,
                                ),
                            ),
                        ),
                    )
                }
                cacheDetailsRouteState(animeId)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@update state
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            current.copy(
                                commentsPaging = current.commentsPaging.copy(
                                    isLoadingMore = false,
                                    error = throwable.userMessage(),
                                ),
                            ),
                        ),
                    )
                }
                cacheDetailsRouteState(animeId)
            }
        }
    }

    fun setAnimeRating(rating: Int?) {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeIdOrNull() ?: return
        val previousDetails = _uiState.value.details
        val previousExtras = _uiState.value.detailsExtras
        val hadPreviousKnownRating = knownAnimeRatings.containsKey(animeId)
        val previousKnownRating = knownAnimeRatings[animeId]
        val optimisticRating = rating?.takeIf { it in 1..10 }
        knownAnimeRatings[animeId] = optimisticRating
        _uiState.update { state ->
            val details = when (val detailsState = state.details) {
                is LoadState.Ready -> LoadState.Ready(detailsState.data.copy(userRating = optimisticRating))
                else -> detailsState
            }
            val extras = state.detailsExtras.readyDataOrNull()
            state.copy(
                details = details,
                detailsExtras = if (extras != null) {
                    LoadState.Ready(extras.copy(rating = extras.rating.copy(userRating = optimisticRating)))
                } else {
                    state.detailsExtras
                },
            )
        }
        cacheDetailsRouteState(animeId)
        viewModelScope.launch {
            runCatching {
                val updatedRating = if (rating == null) {
                    repository.deleteAnimeRating(animeId)
                } else {
                    repository.setAnimeRating(animeId, rating)
                }
                val confirmedUserRating = runCatching {
                    repository.getAnime(animeId).userRating?.takeIf { it in 1..10 }
                }.getOrNull()
                updatedRating to confirmedUserRating
            }
                .onSuccess { (updatedRating, confirmedUserRating) ->
                    _uiState.update { state ->
                        val extras = state.detailsExtras.readyDataOrNull()
                        val selectedRating = if (rating == null) {
                            null
                        } else {
                            confirmedUserRating ?: rating.takeIf { it in 1..10 }
                        }
                        knownAnimeRatings[animeId] = selectedRating
                        persistKnownAnimeRatings()
                        val details = when (val detailsState = state.details) {
                            is LoadState.Ready -> LoadState.Ready(detailsState.data.copy(userRating = selectedRating))
                            else -> detailsState
                        }
                        state.copy(
                            details = details,
                            detailsExtras = if (extras != null) {
                                LoadState.Ready(extras.copy(rating = updatedRating.copy(userRating = selectedRating)))
                            } else {
                                LoadState.Ready(AnimeDetailsExtras(rating = updatedRating.copy(userRating = selectedRating)))
                            },
                        )
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (hadPreviousKnownRating) {
                        knownAnimeRatings[animeId] = previousKnownRating
                    } else {
                        knownAnimeRatings.remove(animeId)
                    }
                    if (throwable is CaptchaRequiredException) {
                        _uiState.update { state ->
                            state.copy(
                                details = previousDetails,
                                detailsExtras = previousExtras,
                            )
                        }
                        cacheDetailsRouteState(animeId)
                        requestCaptchaRetry(throwable) { setAnimeRating(rating) }
                        return@onFailure
                    }
                    _uiState.update { state ->
                        state.copy(
                            details = previousDetails,
                            detailsExtras = previousExtras,
                            auth = state.auth.copy(error = throwable.userMessage()),
                        )
                    }
                    cacheDetailsRouteState(animeId)
                }
        }
    }

    private fun effectiveAnimeRating(
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean = false,
    ): Int? {
        val normalized = remoteRating?.takeIf { it in 1..10 }
        if (trustRemote) {
            if (normalized != null) {
                knownAnimeRatings[animeId] = normalized
            } else {
                knownAnimeRatings.remove(animeId)
            }
            persistKnownAnimeRatings()
            return normalized
        }

        return normalized ?: knownAnimeRatings[animeId]
    }

    private fun restoreKnownAnimeRatings(profile: UserProfile?) {
        knownAnimeRatings.clear()
        val userId = profile?.id?.takeIf { it > 0L } ?: return
        viewModelScope.launch {
            val ratings = withContext(Dispatchers.IO) { animeRatingStateStorage.read(userId) }
            knownAnimeRatings.clear()
            knownAnimeRatings.putAll(ratings)
        }
    }

    private fun persistKnownAnimeRatings() {
        val currentUserId = _uiState.value.auth.profile?.id?.takeIf { it > 0L }
        val snapshot = knownAnimeRatings.toMap()
        viewModelScope.launch {
            val userId = currentUserId
                ?: withContext(Dispatchers.IO) { authStorage.readProfile()?.id?.takeIf { it > 0L } }
                ?: return@launch
            withContext(Dispatchers.IO) { animeRatingStateStorage.save(userId, snapshot) }
        }
    }

    private fun restoreVideoSubscriptionHints(profile: UserProfile?) {
        videoSubscriptionHints.clear()
        val userId = profile?.id?.takeIf { it > 0L } ?: return
        viewModelScope.launch {
            val hints = withContext(Dispatchers.IO) { videoSubscriptionHintStorage.read(userId) }
            videoSubscriptionHints.clear()
            videoSubscriptionHints += hints
        }
    }

    private fun persistVideoSubscriptionHints() {
        val currentUserId = _uiState.value.auth.profile?.id?.takeIf { it > 0L }
        val snapshot = videoSubscriptionHints.toList()
        viewModelScope.launch {
            val userId = currentUserId
                ?: withContext(Dispatchers.IO) { authStorage.readProfile()?.id?.takeIf { it > 0L } }
                ?: return@launch
            withContext(Dispatchers.IO) { videoSubscriptionHintStorage.save(userId, snapshot) }
        }
    }

    private fun rememberVideoSubscriptionHints(
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) {
        val hints = videos
            .mapNotNull { video ->
                val voiceKey = video.matchingVoiceKey
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                VideoSubscriptionHint(
                    animeId = video.animeId,
                    playerId = video.playerId,
                    playerKey = video.matchingPlayerKey,
                    voiceKey = voiceKey,
                    voiceTitle = video.matchingDubbingTitle.ifBlank { video.matchingVoiceTitle },
                    title = title,
                    posterUrl = posterUrl,
                )
            }
        if (hints.isEmpty()) return
        videoSubscriptionHints.removeAll { existing ->
            hints.any { hint ->
                existing.animeId == hint.animeId &&
                    existing.voiceKey == hint.voiceKey &&
                    (
                        (hint.playerId > 0L && existing.playerId == hint.playerId) ||
                            (hint.playerKey.isNotBlank() && existing.playerKey == hint.playerKey)
                    )
            }
        }
        videoSubscriptionHints += hints
        persistVideoSubscriptionHints()
    }

    private fun forgetVideoSubscriptionHints(animeId: Long, voiceKey: String) {
        val normalizedVoiceKey = voiceKey.takeIf { it.isNotBlank() } ?: return
        val removed = videoSubscriptionHints.removeAll { hint ->
            hint.animeId == animeId && hint.voiceKey == normalizedVoiceKey
        }
        if (removed) persistVideoSubscriptionHints()
    }

    private fun syncVideoSubscriptionsFromSite() {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) {
            _uiState.update { it.copy(globalSubscriptions = LoadState.Ready(emptyList())) }
            return
        }
        subscriptionsSyncJob?.cancel()
        _uiState.update { it.copy(globalSubscriptions = LoadState.Loading) }
        subscriptionsSyncJob = viewModelScope.launch {
            runCatching { loadResolvedVideoSubscriptions() }
                .onSuccess { subscriptions ->
                    val activeSubscriptions = unsubscribeCompletedAnimeSubscriptions(subscriptions)
                    updateGlobalSubscriptions(activeSubscriptions)
                }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { syncVideoSubscriptionsFromSite() }) {
                        _uiState.update { it.copy(globalSubscriptions = LoadState.Error(throwable.userMessage())) }
                    }
                }
        }
    }

    fun refreshVideoSubscriptions() {
        syncVideoSubscriptionsFromSite()
    }

    fun refreshProfileNotifications() {
        syncProfileNotificationsFromSite()
    }

    private fun syncProfileNotificationsFromSite() {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) {
            _uiState.update { it.copy(profileNotifications = LoadState.Ready(emptyList())) }
            return
        }
        profileNotificationsSyncJob?.cancel()
        _uiState.update { it.copy(profileNotifications = LoadState.Loading) }
        profileNotificationsSyncJob = viewModelScope.launch {
            runCatching {
                val notifications = repository.getProfileNotifications(limit = PROFILE_NOTIFICATIONS_LIMIT)
                    .sortedByDescending { it.dateSeconds }
                notifications
            }
                .onSuccess { notifications ->
                    val unreadCount = notifications.unreadCount()
                    _uiState.update { state ->
                        state.copy(
                            profileNotifications = LoadState.Ready(notifications),
                            auth = state.auth.withUnreadNotifications(unreadCount),
                        )
                    }
                    syncUnreadNotifications(notifications)
                }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { syncProfileNotificationsFromSite() }) {
                        _uiState.update { it.copy(profileNotifications = LoadState.Error(throwable.userMessage())) }
                    }
                }
        }
    }

    fun markProfileNotificationRead(notification: SiteNotification) {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null || notification.viewed) return
        updateProfileNotificationReadState(notification.id, viewed = true)
        SubscriptionNotificationBadge.cancelNotification(getApplication(), notification.id)
        viewModelScope.launch {
            runCatching { repository.markProfileNotificationRead(notification.id) }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { markProfileNotificationRead(notification) }) {
                        syncProfileNotificationsFromSite()
                        _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                    }
                }
        }
    }

    fun markAllProfileNotificationsRead() {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) return
        val loadedNotifications = _uiState.value.profileNotifications.readyDataOrNull().orEmpty()
        _uiState.update { state ->
            val notifications = state.profileNotifications.readyDataOrNull()
            state.copy(
                profileNotifications = if (notifications != null) {
                    LoadState.Ready(notifications.map { it.copy(viewed = true) })
                } else {
                    state.profileNotifications
                },
                auth = state.auth.withUnreadNotifications(0),
            )
        }
        loadedNotifications.forEach { notification ->
            SubscriptionNotificationBadge.cancelNotification(getApplication(), notification.id)
        }
        syncUnreadNotificationCount(0)
        viewModelScope.launch {
            runCatching { repository.markProfileNotificationsRead() }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { markAllProfileNotificationsRead() }) {
                        syncProfileNotificationsFromSite()
                        _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                    }
                }
        }
    }

    fun deleteProfileNotification(notification: SiteNotification) {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) return
        _uiState.update { state ->
            val notifications = state.profileNotifications.readyDataOrNull()
            state.copy(
                profileNotifications = if (notifications != null) {
                    LoadState.Ready(notifications.filterNot { it.id == notification.id })
                } else {
                    state.profileNotifications
                },
                auth = state.auth.withUnreadNotificationDelta(if (notification.viewed) 0 else -1),
            )
        }
        SubscriptionNotificationBadge.cancelNotification(getApplication(), notification.id)
        syncUnreadNotificationCountFromState()
        viewModelScope.launch {
            runCatching { repository.deleteProfileNotification(notification.id) }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { deleteProfileNotification(notification) }) {
                        syncProfileNotificationsFromSite()
                        _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                    }
                }
        }
    }

    private fun updateProfileNotificationReadState(notificationId: Long, viewed: Boolean) {
        _uiState.update { state ->
            val notifications = state.profileNotifications.readyDataOrNull() ?: return@update state
            val previous = notifications.firstOrNull { it.id == notificationId }
            val updatedNotifications = notifications.map { notification ->
                if (notification.id == notificationId) notification.copy(viewed = viewed) else notification
            }
            state.copy(
                profileNotifications = LoadState.Ready(updatedNotifications),
                auth = if (previous != null && previous.viewed != viewed) {
                    state.auth.withUnreadNotifications(updatedNotifications.unreadCount())
                } else {
                    state.auth
                },
            )
        }
        syncUnreadNotificationCountFromState()
    }

    private suspend fun loadResolvedVideoSubscriptions(): List<VideoSubscription> {
        return resolveVideoSubscriptionVoices(repository.getVideoSubscriptions())
    }

    private suspend fun resolveVideoSubscriptionVoices(
        subscriptions: List<VideoSubscription>,
    ): List<VideoSubscription> {
        if (subscriptions.isEmpty()) return subscriptions
        val videoCache = mutableMapOf<Long, List<VideoVariant>>()
        return subscriptions
            .flatMap { subscription ->
                if (subscription.animeId <= 0L) {
                    return@flatMap listOf(subscription)
                }

                val videos = videoCache.getOrPut(subscription.animeId) {
                    runCatching { repository.getVideos(subscription.animeId) }.getOrDefault(emptyList())
                }
                val subscribedVideos = videos.filter { it.subscribed }
                if (subscribedVideos.isNotEmpty()) {
                    return@flatMap subscribedVideos.map { video -> subscription.withResolvedVoice(video) }
                }

                val directVideo = videos.firstOrNull { it.id == subscription.videoId }
                if (directVideo != null) {
                    return@flatMap listOf(subscription.withResolvedVoice(directVideo))
                }

                val hintedSubscriptions = subscription.resolveVoiceHints(videoSubscriptionHints)
                    .map { hint -> subscription.withResolvedHint(hint) }
                if (hintedSubscriptions.isNotEmpty()) {
                    return@flatMap hintedSubscriptions
                }

                val singlePlayerVoice = subscription.resolveSinglePlayerVoice(videos)
                if (singlePlayerVoice != null) {
                    listOf(subscription.withResolvedVoice(singlePlayerVoice))
                } else {
                    listOf(subscription)
                }
            }
            .distinctBy { subscription ->
                listOf(
                    subscription.animeId,
                    subscription.matchingVoiceKey,
                    subscription.videoId,
                    subscription.playerId,
                    subscription.matchingPlayerKey,
                ).joinToString("|")
            }
    }

    private suspend fun unsubscribeCompletedAnimeSubscriptions(
        subscriptions: List<VideoSubscription>,
    ): List<VideoSubscription> {
        if (subscriptions.isEmpty()) return subscriptions
        val subscriptionsByAnime = subscriptions
            .filter { it.animeId > 0L }
            .groupBy { it.animeId }
        if (subscriptionsByAnime.isEmpty()) return subscriptions

        val removedAnimeIds = mutableSetOf<Long>()
        subscriptionsByAnime.forEach { (animeId, animeSubscriptions) ->
            val details = runCatching { repository.getAnimeOnline(animeId) }.getOrNull()
                ?: return@forEach
            if (!details.isFullyReleased()) return@forEach

            val removed = unsubscribeCompletedAnimeSubscriptionGroup(animeId, animeSubscriptions)
            if (removed) {
                removedAnimeIds += animeId
            }
        }

        if (removedAnimeIds.isEmpty()) return subscriptions
        val removedHints = videoSubscriptionHints.removeAll { it.animeId in removedAnimeIds }
        if (removedHints) persistVideoSubscriptionHints()
        return subscriptions.filterNot { it.animeId in removedAnimeIds }
    }

    private suspend fun unsubscribeCompletedAnimeSubscriptionGroup(
        animeId: Long,
        subscriptions: List<VideoSubscription>,
    ): Boolean {
        val directVideoIds = subscriptions
            .mapNotNull { it.videoId.takeIf { videoId -> videoId > 0L } }
            .distinct()
        if (directVideoIds.unsubscribeByVideoIds()) return true

        val targetVoiceKeys = subscriptions
            .map { it.matchingVoiceKey }
            .filter { it.isNotBlank() }
            .toSet()
        if (targetVoiceKeys.isEmpty()) return false

        val videos = runCatching { repository.getVideos(animeId) }.getOrDefault(emptyList())
        val targetVideoIds = videos
            .filter { video -> video.matchingVoiceKey in targetVoiceKeys }
            .distinctBy { it.matchingSourceKey }
            .map { it.id }
            .filter { it > 0L }
        return targetVideoIds.unsubscribeByVideoIds()
    }

    private suspend fun List<Long>.unsubscribeByVideoIds(): Boolean {
        if (isEmpty()) return false
        return map { videoId ->
            runCatching { repository.unsubscribeVideo(videoId) }.getOrDefault(false)
        }.any { it }
    }

    private fun updateGlobalSubscriptions(subscriptions: List<VideoSubscription>) {
        _uiState.update { state ->
            val detailsAnimeId = (state.route as? AppRoute.Details)?.animeId
                ?: state.details.readyDataOrNull()?.id
            val detailsExtras = state.detailsExtras.readyDataOrNull()
            val details = state.details.readyDataOrNull()
            val detailsVideos = state.videos.readyDataOrNull()
                .orEmpty()
                .filter { it.animeId == detailsAnimeId }
            val detailsSubscriptions = canonicalizeVideoSubscriptionsForVideos(
                subscriptions = subscriptions,
                videos = detailsVideos,
                hints = videoSubscriptionHints,
                title = details?.title.orEmpty(),
                posterUrl = details?.posterUrl.orEmpty(),
            )
            state.copy(
                globalSubscriptions = LoadState.Ready(subscriptions),
                detailsExtras = if (detailsAnimeId != null && detailsExtras != null) {
                    LoadState.Ready(
                        detailsExtras.copy(
                            subscriptions = detailsSubscriptions,
                        ),
                    )
                } else {
                    state.detailsExtras
                },
            )
        }
        cacheCurrentDetailsRouteState()
    }

    fun addAnimeComment(text: String) {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeIdOrNull() ?: return
        viewModelScope.launch {
            runCatching { repository.addAnimeComment(animeId, text) }
                .onSuccess { comment ->
                    if (comment == null) return@onSuccess
                    _uiState.update { state ->
                        val extras = state.detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
                        state.copy(
                            detailsExtras = LoadState.Ready(
                                extras.copy(
                                    comments = (listOf(comment) + extras.comments).distinctBy { it.id },
                                ),
                            ),
                        )
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (!requestCaptchaRetry(throwable) { addAnimeComment(text) }) {
                        _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                    }
                }
        }
    }

    fun toggleVideoSubscription(video: VideoVariant) {
        toggleVideoSubscription(video, showNotice = false)
    }

    fun togglePlayerVideoSubscription(video: VideoVariant) {
        toggleVideoSubscription(video, showNotice = true)
    }

    private fun toggleVideoSubscription(video: VideoVariant, showNotice: Boolean) {
        if (_uiState.value.forcedOfflineMode) return
        val details = _uiState.value.details.readyDataOrNull()
        if (details?.isFullyReleased() == true) return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return
        }
        viewModelScope.launch {
            val current = _uiState.value.detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
            val allVideos = _uiState.value.videos.readyListOrEmpty()
            val targetVoiceKey = video.matchingVoiceKey
            val sameVoiceVideos = loadSubscriptionTargets(video.animeId, targetVoiceKey, allVideos)
                .ifEmpty { listOf(video).filter { it.id > 0L } }
            if (sameVoiceVideos.isEmpty()) return@launch

            val shouldSubscribe = !current.subscriptions.hasSubscriptionForVoice(video.animeId, targetVoiceKey)
            val title = details?.title.orEmpty()
            val posterUrl = details?.posterUrl.orEmpty()

            val optimisticSubscriptions = current.subscriptions.withVoiceSubscriptionState(
                animeId = video.animeId,
                voiceKey = targetVoiceKey,
                videos = sameVoiceVideos,
                subscribed = shouldSubscribe,
                title = title,
                posterUrl = posterUrl,
            )
            _uiState.update { state ->
                val extras = state.detailsExtras.readyDataOrNull() ?: current
                state.copy(detailsExtras = LoadState.Ready(extras.copy(subscriptions = optimisticSubscriptions)))
            }
            cacheDetailsRouteState(video.animeId)

            runCatching {
                applySubscriptionStateToVideos(sameVoiceVideos, shouldSubscribe)
                if (shouldSubscribe) {
                    rememberVideoSubscriptionHints(sameVoiceVideos, title, posterUrl)
                } else {
                    forgetVideoSubscriptionHints(video.animeId, targetVoiceKey)
                }

                loadResolvedVideoSubscriptions().withVoiceSubscriptionState(
                    animeId = video.animeId,
                    voiceKey = targetVoiceKey,
                    videos = sameVoiceVideos,
                    subscribed = shouldSubscribe,
                    title = title,
                    posterUrl = posterUrl,
                )
            }
                .onSuccess { subscriptions ->
                    updateGlobalSubscriptions(subscriptions)
                    if (showNotice) {
                        showTransientNotice(
                            uiString(
                                if (shouldSubscribe) {
                                    R.string.ui_subscription_enabled
                                } else {
                                    R.string.ui_subscription_disabled
                                },
                            ),
                        )
                    }
                    cacheDetailsRouteState(video.animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CaptchaRequiredException) {
                        if (shouldSubscribe) {
                            forgetVideoSubscriptionHints(video.animeId, targetVoiceKey)
                        } else {
                            rememberVideoSubscriptionHints(sameVoiceVideos, title, posterUrl)
                        }
                        _uiState.update { state ->
                            val extras = state.detailsExtras.readyDataOrNull() ?: current
                            state.copy(detailsExtras = LoadState.Ready(extras.copy(subscriptions = current.subscriptions)))
                        }
                        cacheDetailsRouteState(video.animeId)
                        requestCaptchaRetry(throwable) { toggleVideoSubscription(video, showNotice) }
                        return@onFailure
                    }
                    if (shouldSubscribe) {
                        forgetVideoSubscriptionHints(video.animeId, targetVoiceKey)
                    } else {
                        rememberVideoSubscriptionHints(sameVoiceVideos, title, posterUrl)
                    }
                    _uiState.update { state ->
                        val extras = state.detailsExtras.readyDataOrNull() ?: current
                        state.copy(
                            detailsExtras = LoadState.Ready(extras.copy(subscriptions = current.subscriptions)),
                            auth = state.auth.copy(error = throwable.userMessage()),
                        )
                    }
                    cacheDetailsRouteState(video.animeId)
                }
        }
    }

    fun unsubscribeVideoSubscription(subscription: VideoSubscription) {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) return
        val currentSubscriptions = _uiState.value.globalSubscriptions.readyListOrEmpty()
        val target = subscription.unsubscribeTarget(currentSubscriptions) ?: return
        if (target.voiceKey.isNotBlank()) {
            forgetVideoSubscriptionHints(target.animeId, target.voiceKey)
        }

        updateGlobalSubscriptions(currentSubscriptions.withoutUnsubscribeTarget(target))

        viewModelScope.launch {
            runCatching {
                val loadedVideos = if (target.requiresVideoLookup) {
                    _uiState.value.videos.readyListOrEmpty()
                        .takeIf { videos -> videos.any { it.animeId == target.animeId } }
                        ?: repository.getVideos(target.animeId)
                } else {
                    emptyList()
                }
                val resolvedTarget = target.withResolvedVideoIds(loadedVideos)
                if (resolvedTarget.videoIds.isEmpty()) throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)

                applySubscriptionStateToVideoIds(resolvedTarget.videoIds, subscribed = false)

                loadResolvedVideoSubscriptions().withoutUnsubscribeTarget(resolvedTarget)
            }
                .onSuccess { subscriptions ->
                    updateGlobalSubscriptions(subscriptions)
                }
                .onFailure { throwable ->
                    if (throwable is CaptchaRequiredException) {
                        if (target.voiceKey.isNotBlank()) {
                            rememberVideoSubscriptionHints(
                                videos = target.hintVideos(_uiState.value.videos.readyListOrEmpty()),
                                title = subscription.title,
                                posterUrl = subscription.posterUrl,
                            )
                        }
                        syncVideoSubscriptionsFromSite()
                        requestCaptchaRetry(throwable) { unsubscribeVideoSubscription(subscription) }
                        return@onFailure
                    }
                    if (target.voiceKey.isNotBlank()) {
                        rememberVideoSubscriptionHints(
                            videos = target.hintVideos(_uiState.value.videos.readyListOrEmpty()),
                            title = subscription.title,
                            posterUrl = subscription.posterUrl,
                        )
                    }
                    syncVideoSubscriptionsFromSite()
                    _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                }
        }
    }

    private suspend fun applySubscriptionStateToVideos(videos: List<VideoVariant>, subscribed: Boolean) {
        applySubscriptionStateToVideoIds(
            videoIds = videos
                .map { it.id }
                .filter { it > 0L }
                .distinct(),
            subscribed = subscribed,
        )
    }

    private suspend fun applySubscriptionStateToVideoIds(videoIds: List<Long>, subscribed: Boolean) {
        if (videoIds.isEmpty()) throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
        val operationResults = videoIds.map { videoId ->
            runCatching {
                if (subscribed) {
                    repository.subscribeVideo(videoId)
                } else {
                    repository.unsubscribeVideo(videoId)
                    true
                }
            }
        }
        val hasSuccess = operationResults.any { it.getOrDefault(false) }
        if (!hasSuccess) {
            throw operationResults.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException(
                    if (subscribed) SUBSCRIPTION_ENABLE_FAILED_KEY else SUBSCRIPTION_DISABLE_FAILED_KEY,
                )
        }
    }

    private suspend fun loadSubscriptionTargets(
        animeId: Long,
        voiceKey: String,
        fallbackVideos: List<VideoVariant>,
    ): List<VideoVariant> {
        val loadedVideos = fallbackVideos
            .takeIf { videos -> videos.any { it.animeId == animeId && it.matchingVoiceKey == voiceKey } }
            ?: repository.getVideos(animeId)
        return loadedVideos
            .filter { it.animeId == animeId && it.matchingVoiceKey == voiceKey && it.id > 0L }
            .distinctBy { it.matchingSourceKey }
    }

    private suspend fun syncPlaybackProgressForAnime(animeId: Long): PlaybackProgress? {
        var local = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
        if (_uiState.value.forcedOfflineMode) return local
        if (_uiState.value.auth.profile == null) return local

        val remoteHistoryResult = watchHistoryCoordinator.fetchRemoteHistory()
        remoteHistoryResult.exceptionOrNull()?.let { throwable ->
            if (requestCaptchaRetry(throwable) { syncPlaybackProgressForAnime(animeId); Unit }) {
                return local
            }
        }
        val remoteEntries = remoteHistoryResult
            .getOrDefault(emptyList())
            .filter { it.animeId == animeId }
        watchHistoryCoordinator.storeRemoteHistory(remoteEntries)
        val remote = remoteEntries.maxByOrNull { it.updatedAtMs }
        local = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
        if (local != null && local.isNewerThan(remote) && local.videoId > 0L) {
            syncPlaybackProgressToSite(local)
        }
        return local
    }

    private fun refreshPlaybackProgressFromSite(animeId: Long) {
        if (animeId <= 0L) return
        viewModelScope.launch {
            val progress = syncPlaybackProgressForAnime(animeId)
            val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
            if (progress != null) updateCachedPlaybackProgress(progress, history)
            _uiState.update { state ->
                val isCurrentDetails = (state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                if (!isCurrentDetails) return@update state
                val progressGroupKey = progress?.groupKey
                    ?.takeIf { groupKey -> state.videos.readyListOrEmpty().any { it.groupKey == groupKey } }
                state.copy(
                    selectedVideoGroup = progressGroupKey ?: state.selectedVideoGroup,
                    playbackProgress = progress ?: state.playbackProgress,
                    playbackHistory = history,
                )
            }
        }
    }

    private fun syncPlaybackHistoryFromSite() {
        if (_uiState.value.forcedOfflineMode) return
        if (_uiState.value.auth.profile == null) return
        playbackHistorySyncJob?.cancel()
        playbackHistorySyncJob = viewModelScope.launch {
            val localEntries = withContext(Dispatchers.IO) { playbackProgressStorage.readAll() }
            val remoteHistoryResult = watchHistoryCoordinator.fetchRemoteHistory()
            remoteHistoryResult.exceptionOrNull()?.let { throwable ->
                if (requestCaptchaRetry(throwable) { syncPlaybackHistoryFromSite() }) {
                    return@launch
                }
            }
            val remoteEntries = remoteHistoryResult.getOrDefault(emptyList())
            watchHistoryCoordinator.storeRemoteHistory(remoteEntries)
            watchHistoryCoordinator.uploadNewerLocalProgress(localEntries, remoteEntries)

            val currentAnimeId = _uiState.value.details.readyDataOrNull()?.id
            if (currentAnimeId != null) {
                val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(currentAnimeId) }
                val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(currentAnimeId) }
                _uiState.update {
                    it.copy(
                        playbackProgress = progress,
                        playbackHistory = history,
                    )
                }
            }
            watchHistoryCoordinator.markRemoteSynchronized()
            val history = watchHistoryCoordinator.readLatestLocalProgress()
            val animes = watchHistoryCoordinator.resolveAnimeSummaries(history)
            _uiState.update { it.copy(historyAnime = LoadState.Ready(animes)) }
        }
    }

    private fun syncPlaybackProgressToSite(progress: PlaybackProgress) {
        if (_uiState.value.forcedOfflineMode) return
        if (_uiState.value.auth.profile == null || progress.videoId <= 0L) return
        playbackProgressSyncJobs[progress.videoId]?.cancel()
        playbackProgressSyncJobs[progress.videoId] = viewModelScope.launch {
            runCatching { repository.saveWatchProgress(progress) }
                .onFailure { throwable ->
                    requestCaptchaRetry(throwable) { syncPlaybackProgressToSite(progress) }
                }
            playbackProgressSyncJobs.remove(progress.videoId)
        }
    }

    private fun searchNow(query: String, reset: Boolean = true) {
        val currentState = _uiState.value
        val request = animePageRequest(
            items = currentState.searchResults,
            paging = currentState.searchPaging,
            reset = reset,
            canLoadMoreOnReset = query.isNotBlank(),
        ) ?: return

        if (reset) {
            searchLoadJob?.cancel()
        }
        _uiState.update { it.withSearchPageLoading(reset = reset, request = request) }

        searchLoadJob = viewModelScope.launch {
            val filters = _uiState.value.filters
            runCatching { repository.search(query, filters, offset = request.offset, limit = PAGE_SIZE) }
                .onSuccess { animes ->
                    val forcedOfflineMode = repository.isOfflineFallbackActive()
                    _uiState.update { state ->
                        reduceSearchPageSuccess(
                            state = state,
                            query = query,
                            requestedFilters = filters,
                            incoming = animes,
                            reset = reset,
                            pageSize = PAGE_SIZE,
                            forcedOfflineMode = forcedOfflineMode,
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) return@onFailure
                    val errorMessage = throwable.userMessage()
                    _uiState.update { state ->
                        reduceSearchPageFailure(
                            state = state,
                            query = query,
                            requestedFilters = filters,
                            reset = reset,
                            error = errorMessage,
                        )
                    }
                }
        }
    }

    private fun reloadBrowse() {
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update {
                it.copy(
                    route = AppRoute.Home,
                    homeSection = BrowseSection.Downloads,
                    searchQuery = "",
                    searchResults = LoadState.Ready(emptyList()),
                    searchPaging = PagingUiState(canLoadMore = false),
                )
            }
            loadOfflineEntries()
            return
        }
        when (_uiState.value.homeSection) {
            BrowseSection.Catalog -> {
                if (_uiState.value.searchQuery.isBlank()) {
                    loadHome(reset = true)
                } else {
                    searchNow(_uiState.value.searchQuery, reset = true)
                }
            }
            BrowseSection.Schedule -> loadSchedule()
            BrowseSection.History -> loadHistory()
            BrowseSection.Downloads -> loadOfflineEntries()
        }
    }

    private fun playbackCandidates(
        requested: VideoVariant,
        allVideos: List<VideoVariant>,
        excludedSourceKeys: Set<String>,
    ): List<VideoVariant> {
        val pool = allVideos.ifEmpty { listOf(requested) }
        val sameEpisode = pool.filter { it.isSameEpisodeAs(requested) }
            .ifEmpty { listOf(requested) }
        val sameVoice = sameEpisode.filter { it.hasSameVoiceAs(requested) }
        val manualSourceKey = manualPlaybackSourceKey(requested)

        return sameVoice
            .ifEmpty { listOf(requested) }
            .filterNot { it.playbackSourceKey in excludedSourceKeys }
            .sortedForPlaybackSource(
                requested = requested,
                manualSourceKey = manualSourceKey,
            )
    }

    private suspend fun resolvePlaybackWithCache(
        requested: VideoVariant,
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant> = candidates,
        useCachedSource: Boolean = true,
        fastStart: Boolean = false,
    ): PlaybackResolution {
        if (requested.localPlaybackUrl.isNotBlank()) {
            return PlaybackResolution(
                playback = ResolvedPlayback(
                    video = requested,
                    stream = repository.resolveVideoStream(requested),
                ),
            )
        }

        val sameVoiceCandidates = candidates
            .filter { it.hasSameVoiceAs(requested) }
        if (sameVoiceCandidates.isEmpty()) {
            throw IllegalStateException("No playback sources for selected voice")
        }
        val sameVoiceMetadataCandidates = metadataCandidates
            .filter { it.hasSameVoiceAs(requested) }
            .ifEmpty { sameVoiceCandidates }
        val cacheKey = requested.playbackCacheKey()
        val manualSourceKey = manualPlaybackSourceKey(requested)
            ?.takeIf { sourceKey -> sameVoiceCandidates.any { it.matchesSourceSelectionKey(sourceKey) } }
        val cachedSource = playbackSourceCache[cacheKey].takeIf { useCachedSource && manualSourceKey == null }
        val manualCandidates = manualSourceKey
            ?.let { sourceKey -> sameVoiceCandidates.filter { it.matchesSourceSelectionKey(sourceKey) } }
            .orEmpty()

        val orderedCandidates = sameVoiceCandidates.sortedForPlaybackSource(
            requested = requested,
            manualSourceKey = manualSourceKey,
            cachedSourceKey = cachedSource?.providerKey
                ?.takeIf { providerKey -> sameVoiceCandidates.any { it.matchesSourceSelectionKey(providerKey) } },
        )

        if (fastStart) {
            val failures = mutableListOf<Throwable>()
            var manualFailure: Throwable? = null
            val selectedManualVideo = manualCandidates.firstOrNull() ?: requested
            orderedCandidates.fastStartResolutionGroups(
                manualSourceKey = manualSourceKey,
            ).forEach { candidateGroup ->
                if (candidateGroup.isEmpty()) return@forEach
                val isManualGroup = manualSourceKey != null &&
                    candidateGroup.any { it.matchesSourceSelectionKey(manualSourceKey) }
                var playback: ResolvedPlayback? = null
                for (candidate in candidateGroup) {
                    if (playback != null) break
                    val candidatePlayback = runCatching {
                        repository.resolveBestPlaybackSource(
                            candidates = listOf(candidate),
                            preferredQuality = preferredQuality,
                            metadataCandidates = emptyList(),
                            waitForRuntimeSubtitles = false,
                        )
                    }.getOrElse { throwable ->
                        failures += throwable
                        if (isManualGroup) manualFailure = throwable
                        null
                    }
                    if (candidatePlayback != null) playback = candidatePlayback
                }
                val resolvedPlayback = playback ?: return@forEach

                if (cachedSource != null && !resolvedPlayback.video.matchesSourceSelectionKey(cachedSource.providerKey)) {
                    playbackSourceCache.remove(cacheKey)
                }
                return PlaybackResolution(
                    playback = resolvedPlayback,
                    manualFallbackNotice = manualFailure
                        ?.takeIf { manualSourceKey != null && !resolvedPlayback.video.matchesSourceSelectionKey(manualSourceKey) }
                        ?.let { throwable ->
                            SourceFallbackNotice(
                                selectedVideo = selectedManualVideo,
                                reason = throwable.userMessage(),
                            )
                        },
                )
            }
            playbackSourceCache.remove(cacheKey)
            throw failures.firstOrNull()
                ?: IllegalStateException(uiString(R.string.ui_could_not_select_video_source))
        }

        val manualResult = if (manualCandidates.isNotEmpty()) {
            runCatching {
                repository.resolveBestPlaybackSource(
                    candidates = manualCandidates,
                    preferredQuality = preferredQuality,
                    metadataCandidates = sameVoiceMetadataCandidates,
                )
            }.onSuccess { playback ->
                return PlaybackResolution(playback = playback)
            }
        } else {
            null
        }

        val automaticCandidates = orderedCandidates
            .filterNot { candidate ->
                manualSourceKey != null && candidate.matchesSourceSelectionKey(manualSourceKey)
            }
        if (automaticCandidates.isEmpty()) {
            throw manualResult?.exceptionOrNull()
                ?: IllegalStateException(uiString(R.string.ui_no_fallback_video_sources_after_manual_selection))
        }
        val primaryResult = runCatching {
            repository.resolveBestPlaybackSource(
                candidates = automaticCandidates,
                preferredQuality = preferredQuality,
                metadataCandidates = sameVoiceMetadataCandidates,
            )
        }
        primaryResult.onSuccess { playback ->
            if (cachedSource != null && !playback.video.matchesSourceSelectionKey(cachedSource.providerKey)) {
                playbackSourceCache.remove(cacheKey)
            }
            return PlaybackResolution(
                playback = playback,
                manualFallbackNotice = manualResult
                    ?.exceptionOrNull()
                    ?.takeIf { manualSourceKey != null && !playback.video.matchesSourceSelectionKey(manualSourceKey) }
                    ?.let { throwable ->
                        SourceFallbackNotice(
                            selectedVideo = manualCandidates.firstOrNull() ?: requested,
                            reason = throwable.userMessage(),
                        )
                    },
            )
        }
        playbackSourceCache.remove(cacheKey)

        throw primaryResult.exceptionOrNull() ?: IllegalStateException(uiString(R.string.ui_could_not_select_video_source))
    }

    private fun List<VideoVariant>.fastStartResolutionGroups(
        manualSourceKey: String?,
    ): List<List<VideoVariant>> {
        val uniqueCandidates = distinctBy { it.playbackSourceKey }
        val manualCandidates = manualSourceKey
            ?.let { sourceKey -> uniqueCandidates.filter { it.matchesSourceSelectionKey(sourceKey) } }
            .orEmpty()
        val automaticCandidates = if (manualCandidates.isNotEmpty()) {
            uniqueCandidates.filterNot { candidate -> candidate.matchesSourceSelectionKey(manualSourceKey) }
        } else {
            uniqueCandidates
        }
        val automaticGroups = automaticCandidates.groupByEstimatedQuality()
        return if (manualCandidates.isEmpty()) automaticGroups else listOf(manualCandidates) + automaticGroups
    }

    private fun List<VideoVariant>.groupByEstimatedQuality(): List<List<VideoVariant>> {
        val groups = mutableListOf<MutableList<VideoVariant>>()
        var activeHeight: Int? = null
        forEach { candidate ->
            val height = candidate.estimatedSourceMaxVideoHeight()
            val group = groups.lastOrNull()
            if (group == null || activeHeight != height) {
                groups += mutableListOf(candidate)
                activeHeight = height
            } else {
                group += candidate
            }
        }
        return groups
    }

    private fun markPlaybackSourceFailed(video: VideoVariant) {
        val sourceKey = video.playbackSourceKey
        failedPlaybackSourceKeys = failedPlaybackSourceKeys + sourceKey
        failedPlaybackSourceRetryAfterMs[sourceKey] = System.currentTimeMillis() + PLAYBACK_FAILED_SOURCE_RETRY_COOLDOWN_MS
        removeCachedPlaybackSource(video)
        cancelPlaybackMetadataLoad()
    }

    private fun blockedPlaybackSourceKeys(nowMs: Long = System.currentTimeMillis()): Set<String> {
        val expiredSourceKeys = failedPlaybackSourceKeys.filter { sourceKey ->
            val retryAfterMs = failedPlaybackSourceRetryAfterMs[sourceKey]
            retryAfterMs == null || nowMs >= retryAfterMs
        }
        if (expiredSourceKeys.isNotEmpty()) {
            failedPlaybackSourceKeys = failedPlaybackSourceKeys - expiredSourceKeys.toSet()
            expiredSourceKeys.forEach(failedPlaybackSourceRetryAfterMs::remove)
        }
        return failedPlaybackSourceKeys
    }

    private fun showPlaybackSourceFallbackNotice(notice: SourceFallbackNotice, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSamePlaybackSourceAs(notice.selectedVideo)) return
        val selectedLabel = notice.selectedVideo.playbackNoticeSourceLabel()
        val fallbackLabel = fallbackVideo.playbackNoticeSourceLabel()
        _uiState.update { state ->
            state.copy(
                playerNotice = PlayerNotice(
                    id = ++playerNoticeId,
                    message = uiString(R.string.ui_source_fallback_notice, selectedLabel, notice.reason, fallbackLabel),
                ),
            )
        }
    }

    private fun PlaybackFailure.noticeReason(): String {
        return message
            ?.takeIf { it.isNotBlank() }
            ?: when (kind) {
                PlaybackFailureKind.PlayerError -> uiString(R.string.ui_playback_player_error)
                PlaybackFailureKind.BufferingTimeout -> uiString(R.string.ui_playback_buffer_not_filling)
            }
    }

    private fun VideoVariant.playbackNoticeSourceLabel(): String {
        return player.cleanVideoSourceLabel()
            .ifBlank { player }
            .ifBlank { uiString(R.string.ui_source) }
    }

    private fun removeCachedPlaybackSource(video: VideoVariant) {
        val cacheKey = video.playbackCacheKey()
        if (video.matchesSourceSelectionKey(playbackSourceCache[cacheKey]?.providerKey)) {
            playbackSourceCache.remove(cacheKey)
        }
    }

    private fun syncUnreadNotificationCountFromState() {
        val notifications = _uiState.value.profileNotifications.readyDataOrNull()
        if (notifications != null) {
            syncUnreadNotifications(notifications)
        } else {
            val count = _uiState.value.auth.profile?.unreadNotifications ?: 0
            syncUnreadNotificationCount(count)
        }
    }

    private fun syncUnreadNotifications(notifications: List<SiteNotification>) {
        val unreadCount = notifications.unreadCount()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                authStorage.readProfile()?.let { profile ->
                    authStorage.saveProfile(profile.copy(unreadNotifications = unreadCount))
                }
            }
        }
        SubscriptionNotificationBadge.update(getApplication(), notifications)
    }

    private fun syncUnreadNotificationCount(count: Int) {
        val normalizedCount = count.coerceAtLeast(0)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                authStorage.readProfile()?.let { profile ->
                    authStorage.saveProfile(profile.copy(unreadNotifications = normalizedCount))
                }
            }
        }
        SubscriptionNotificationBadge.update(getApplication(), normalizedCount)
    }

    private companion object {
        const val PAGE_SIZE = 36
        const val COMMENTS_PAGE_SIZE = 20
        const val PROFILE_NOTIFICATIONS_LIMIT = 80
        const val OFFLINE_RECOVERY_CHECK_INTERVAL_MS = 30_000L
        val ALL_USER_MARK_FILTERS = setOf("0", "1", "2", "3", "4", "5")
    }
}
