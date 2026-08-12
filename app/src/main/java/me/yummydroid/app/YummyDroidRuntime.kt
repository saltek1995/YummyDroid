package me.yummydroid.app

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import me.yummydroid.app.data.AnimeRatingStateStorage
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
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.isUnauthorizedApiError
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.normalized
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SearchHistoryStorage
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.toAnimeSummary
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHintStorage
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

internal class YummyDroidRuntime(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    private val settingsStorage = AppSettingsStorage(application)
    private val playbackProgressStorage = PlaybackProgressStorage(application)
    private val historyAnimeCacheStorage = HistoryAnimeCacheStorage(application)
    private val searchHistoryStorage = SearchHistoryStorage(application)
    private val initialSettings = settingsStorage.read()
    private val authStorage = AuthStorage(application)
    private val siteDomainResolver = SiteDomainResolver(candidates = initialSettings.siteDomains)
    private val repository = YummyAnimeRepository(
        context = application,
        siteDomainResolver = siteDomainResolver,
        authStorage = authStorage,
    )
    private val profileNotificationCoordinator = ProfileNotificationCoordinator(
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
    private val animeRatingCoordinator = AnimeRatingStateStorage(application).let { ratingStorage ->
        AnimeRatingCoordinator(
            readRatings = ratingStorage::read,
            saveRatings = ratingStorage::save,
            setRating = repository::setAnimeRating,
            deleteRating = repository::deleteAnimeRating,
            fetchUserRating = { animeId -> repository.getAnime(animeId).userRating },
        )
    }
    private val videoSubscriptionCoordinator = VideoSubscriptionHintStorage(application).let { hintStorage ->
        VideoSubscriptionCoordinator(
            readHints = hintStorage::read,
            saveHints = hintStorage::save,
            fetchSubscriptions = repository::getVideoSubscriptions,
            fetchVideos = repository::getVideos,
            fetchAnime = repository::getAnimeOnline,
            subscribeVideo = repository::subscribeVideo,
            unsubscribeVideo = repository::unsubscribeVideo,
        )
    }
    private val animeDetailsLoadCoordinator = AnimeDetailsLoadCoordinator(
        fetchAnimeWithVideos = repository::getAnimeWithVideos,
        isOfflineFallbackActive = repository::isOfflineFallbackActive,
        readProgress = playbackProgressStorage::read,
        readHistory = playbackProgressStorage::readAnimeHistory,
        resolveEffectiveRating = animeRatingCoordinator::effectiveRating,
        saveAnimeSummary = historyAnimeCacheStorage::save,
    )
    private val animeDetailsExtrasCoordinator = AnimeDetailsExtrasCoordinator(
        fetchComments = repository::getAnimeComments,
        fetchRecommendations = repository::getAnimeRecommendations,
        fetchRatingSummary = repository::getAnimeRatingSummary,
        resolveEffectiveRating = animeRatingCoordinator::effectiveRating,
        loadSubscriptions = videoSubscriptionCoordinator::loadResolvedSubscriptions,
        canonicalizeSubscriptions = videoSubscriptionCoordinator::canonicalizeForVideos,
        addComment = repository::addAnimeComment,
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
    private val videoSubscriptionStateCoordinator = VideoSubscriptionStateCoordinator(
        scope = scope,
        subscriptions = videoSubscriptionCoordinator,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        cacheCurrentDetailsRouteState = ::cacheCurrentDetailsRouteState,
        showToggleNotice = { subscribed ->
            showTransientNotice(
                uiString(
                    if (subscribed) {
                        R.string.ui_subscription_enabled
                    } else {
                        R.string.ui_subscription_disabled
                    },
                ),
            )
        },
        showErrorNotice = ::showTransientNotice,
    )
    private val animeMarkCoordinator = AnimeMarkCoordinator(
        scope = scope,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        getAnimeMark = repository::getAnimeMark,
        setAnimeListMark = repository::setAnimeListMark,
        removeAnimeListMark = repository::removeAnimeListMark,
        setFavorite = repository::setFavorite,
        authenticatedDetailsAnimeId = ::authenticatedDetailsAnimeIdOrNull,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        onMutationFailure = ::showTransientNotice,
        onAutoMarkFailure = { throwable ->
            AppLog.w("YummyDroidMarks", "Failed to auto set anime mark", throwable)
        },
    )
    private val playbackSessionCoordinator = PlaybackSessionCoordinator(
        scope = scope,
        sourceCoordinator = PlaybackSourceCoordinator(
            resolveLocalStream = repository::resolveVideoStream,
            resolveBestPlayback = { candidates, preferredQuality, metadataCandidates, waitForRuntimeSubtitles ->
                repository.resolveBestPlaybackSource(
                    candidates = candidates,
                    preferredQuality = preferredQuality,
                    metadataCandidates = metadataCandidates,
                    waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                )
            },
            couldNotSelectSourceMessage = { uiString(R.string.ui_could_not_select_video_source) },
            noFallbackAfterManualMessage = {
                uiString(R.string.ui_no_fallback_video_sources_after_manual_selection)
            },
        ),
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        fetchVideos = repository::getVideos,
        resolvePlaybackMetadata = repository::resolvePlaybackMetadata,
        cachedSiteBaseUrl = repository::cachedSiteBaseUrl,
        offlineUnavailableMessage = { uiString(R.string.ui_episode_unavailable_offline) },
        onFallbackNotice = ::showPlaybackSourceFallbackNotice,
        onMetadataFailure = { throwable ->
            AppLog.w("YummyDroidPlayer", "Playback metadata load failed", throwable)
        },
    )
    private val browseContentCoordinator = BrowseContentCoordinator(
        scope = scope,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        fetchCatalog = { filters, offset, limit -> repository.getFeatured(filters, offset, limit) },
        searchCatalog = { query, filters, offset, limit -> repository.search(query, filters, offset, limit) },
        fetchSchedule = repository::getSchedule,
        fetchOfflineEntries = repository::offlineAnime,
        isOfflineFallbackActive = repository::isOfflineFallbackActive,
        isOfflineConnectivityFailure = { throwable -> throwable.isOfflineConnectivityFailure() },
        watchHistoryCoordinator = watchHistoryCoordinator,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        historyUnavailableMessage = { uiString(R.string.ui_history_temporarily_unavailable) },
        monotonicClockMs = SystemClock::elapsedRealtime,
    )

    private var searchDebounceJob: Job? = null
    private var downloadQueueJob: Job? = null
    private var detailsLoadJob: Job? = null
    private var detailsExtrasJob: Job? = null
    private var commentsLoadJob: Job? = null
    private var updateCheckJob: Job? = null
    private var profileNotificationsSyncJob: Job? = null
    private var filterCatalogLoadJob: Job? = null
    private var profileNotificationsRequestId = 0L
    private var filterCatalogRequestId = 0L
    private var appContentCacheSizeJob: Job? = null
    private var settingsSaveJob: Job? = null
    private var pendingCaptchaAction: (suspend () -> Unit)? = null
    private var playbackHistorySyncJob: Job? = null
    private var offlineRecoveryJob: Job? = null
    private val playbackProgressWriteJobs = mutableMapOf<Long, Job>()
    private val playbackProgressSyncJobs = mutableMapOf<Long, Job>()
    private var playerNoticeId = 0L
    private val animePlaybackQualityOverrides = mutableMapOf<Long, PreferredQuality>()
    private var completedDownloadTaskIds: Set<Long> = emptySet()
    private val detailsRouteCache = mutableMapOf<Long, DetailsRouteCache>()

    init {
        DownloadCenter.initialize(application)
        repository.updateContentLanguage(initialSettings.contentLanguage)
        restoreSearchHistory()
        browseContentCoordinator.loadCatalog()
        loadFilterCatalog()
        browseContentCoordinator.loadSchedule()
        browseContentCoordinator.loadHistory(force = false)
        browseContentCoordinator.loadOfflineEntries()
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
            AppRoute.Home -> browseContentCoordinator.reload()
            is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
            is AppRoute.Player -> Unit
        }
    }

    private fun refreshSiteBaseUrl() {
        _uiState.update { it.copy(siteBaseUrl = repository.cachedSiteBaseUrl()) }
        scope.launch {
            runCatching { repository.activeSiteBaseUrl() }
                .onSuccess { baseUrl -> _uiState.update { it.copy(siteBaseUrl = baseUrl) } }
        }
    }

    private fun startOfflineRecoveryMonitor() {
        offlineRecoveryJob?.cancel()
        offlineRecoveryJob = scope.launch {
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
                    AppRoute.Home -> browseContentCoordinator.reload()
                    is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
                    is AppRoute.Player -> Unit
                }
            }
        }
    }

    fun refreshOfflineDownloads() {
        browseContentCoordinator.loadOfflineEntries()
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
        browseContentCoordinator.cancelSearch()
        if (query.isBlank()) return

        searchDebounceJob = scope.launch {
            delay(350)
            browseContentCoordinator.search(query, reset = true)
        }
    }

    private fun restoreSearchHistory() {
        scope.launch {
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
        scope.launch {
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
        browseContentCoordinator.reload()
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
                AppRoute.Home -> browseContentCoordinator.reload()
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
        updateCheckJob = scope.launch {
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
                    if (throwable is CancellationException) throw throwable
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
        browseContentCoordinator.ensureLoaded(targetSection)
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
        browseContentCoordinator.loadCatalog(reset = true)
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
        scope.launch {
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
        detailsLoadJob = scope.launch {
            try {
                val loaded = animeDetailsLoadCoordinator.load(animeId) {
                    _uiState.value.auth.profile != null
                }
                scope.launch { animeDetailsLoadCoordinator.cache(loaded.details) }
                _uiState.update { state -> state.withLoadedAnimeDetails(animeId, loaded) }
                if ((_uiState.value.route as? AppRoute.Details)?.animeId != animeId) return@launch

                cacheDetailsRouteState(animeId)
                if (loaded.offlineMode) {
                    animeMarkCoordinator.cancelLoad()
                    detailsExtrasJob?.cancel()
                } else {
                    refreshPlaybackProgressFromSite(animeId)
                    animeMarkCoordinator.load(animeId)
                    loadAnimeExtras(animeId)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                applyAnimeDetailsLoadFailure(animeId, throwable)
            }
        }
    }

    private fun applyAnimeDetailsLoadFailure(animeId: Long, throwable: Throwable) {
        val failedState = _uiState.value
        val offlineUnavailable = failedState.forcedOfflineMode || throwable.isOfflineConnectivityFailure()
        val offlineMessage = uiString(R.string.ui_offline_mode_unavailable)
        if (offlineUnavailable) showTransientNotice(offlineMessage)
        val errorMessage = if (offlineUnavailable) offlineMessage else throwable.userMessage()
        when (val plan = animeDetailsLoadFailurePlan(
            state = failedState,
            animeId = animeId,
            offlineUnavailable = offlineUnavailable,
            offlineMessage = offlineMessage,
            errorMessage = errorMessage,
        )) {
            AnimeDetailsLoadFailurePlan.Ignore -> Unit
            is AnimeDetailsLoadFailurePlan.RestorePrevious -> restoreNavigationEntry(
                entry = plan.entry,
                remainingBackStack = plan.remainingBackStack,
                preserveHomeSection = true,
            )

            is AnimeDetailsLoadFailurePlan.Publish -> _uiState.update { current ->
                val currentPlan = animeDetailsLoadFailurePlan(
                    state = current,
                    animeId = animeId,
                    offlineUnavailable = offlineUnavailable,
                    offlineMessage = offlineMessage,
                    errorMessage = errorMessage,
                )
                (currentPlan as? AnimeDetailsLoadFailurePlan.Publish)?.state ?: current
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
            context = application,
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
        scope.launch {
            val planId = withContext(Dispatchers.IO) { DownloadPlanStorage(application).save(plan) }
            DownloadService.enqueuePlan(application, planId)
        }
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        scope.launch {
            repository.deleteOfflineVideo(animeId, videoId, playbackUrl)
            refreshCurrentDetailsFromOfflineCache(animeId)
            browseContentCoordinator.loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun deleteOfflineAnime(animeId: Long) {
        scope.launch {
            repository.deleteOfflineAnime(animeId)
            refreshCurrentDetailsFromOfflineCache(animeId)
            browseContentCoordinator.loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun refreshAppContentCacheSize() {
        appContentCacheSizeJob?.cancel()
        appContentCacheSizeJob = scope.launch {
            val sizeBytes = withContext(Dispatchers.IO) {
                calculateAppContentCacheSize(application)
            }
            _uiState.update { it.copy(appContentCacheSizeBytes = sizeBytes) }
        }
    }

    fun clearAppContentCache() {
        scope.launch {
            repository.clearAppContentCache(playbackProgressStorage)
            val sizeBytes = withContext(Dispatchers.IO) {
                historyAnimeCacheStorage.clear()
                application.clearRuntimeCacheDirectories()
                calculateAppContentCacheSize(application)
            }
            detailsRouteCache.clear()
            browseContentCoordinator.clearCaches()
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
        DownloadCenter.resumeTask(application, taskId)
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
        browseContentCoordinator.loadCatalog(reset = true)
    }

    fun loadMoreAnime() {
        browseContentCoordinator.loadMore()
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
        playbackSessionCoordinator.rememberManualSource(video)
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
        val context = application
        return if (formatArgs.isEmpty()) {
            context.localizedString(resId, language)
        } else {
            context.localizedString(resId, language, *formatArgs)
        }
    }

    private fun persistSettings(settings: AppSettings) {
        settingsSaveJob?.cancel()
        settingsSaveJob = scope.launch {
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
        playbackSessionCoordinator.resetRuntime(clearSourceCache = clearPlaybackSourceCache)
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
        val fallbackPlan = playbackSessionCoordinator.fallbackPlan(
            currentVideo = route.video,
            failedVideo = failedVideo,
            failure = failure,
            reason = failure.noticeReason(),
        ) ?: return
        val safePositionMs = playbackPositionMs.takeIf { it > 0L } ?: route.startPositionMs
        playbackSessionCoordinator.cancelMetadataLoad()

        playVideoFromCandidates(
            video = route.video,
            title = route.animeTitle,
            excludedSourceKeys = fallbackPlan.excludedSourceKeys,
            startPositionMs = safePositionMs,
            preferredQuality = route.preferredQuality,
            sourceFallbackNotice = fallbackPlan.notice,
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
        playbackSessionCoordinator.play(
            PlaybackSessionRequest(
                video = video,
                title = title,
                excludedSourceKeys = excludedSourceKeys,
                startPositionMs = startPositionMs,
                preferredQuality = preferredQuality,
                resumeChoicePositionMs = resumeChoicePositionMs,
                sourceFallbackNotice = sourceFallbackNotice,
            ),
        )
    }

    fun confirmPlaybackSource(video: VideoVariant) {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        if (!playbackSessionCoordinator.confirm(route.video, video)) return
        animeMarkCoordinator.maybeMarkWatching(video)
    }

    fun handlePlaybackEnded(video: VideoVariant) {
        val state = _uiState.value
        val details = state.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
            ?: return
        val videos = state.videos.readyListOrEmpty()
        animeMarkCoordinator.maybeMarkWatchedOnCompletion(video, state)

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
        playbackProgressWriteJobs[video.animeId] = scope.launch {
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
        scope.launch {
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
                    browseContentCoordinator.loadHistory(force = true)
                }
            }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                if (!requestCaptchaRetry(throwable) { deleteAnimeWatchProgressFromSite(animeId, videoIds) }) {
                    AppLog.w("YummyDroidHistory", "Failed to reset anime watch progress", throwable)
                    showTransientNotice(throwable.userMessage())
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

    fun retryVideo() {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        playVideoAt(route.video, route.startPositionMs)
    }

    fun submitCaptchaResponse(captchaResponse: String) {
        val action = pendingCaptchaAction ?: return
        if (captchaResponse.isBlank()) return
        pendingCaptchaAction = null
        repository.submitCaptchaResponse(captchaResponse)
        scope.launch { action() }
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
        scope.launch {
            runCatching { repository.login(normalizedLogin, password, captchaResponse) }
                .onSuccess { profile ->
                    _uiState.update { it.copy(auth = AuthUiState(profile = profile)) }
                    animeRatingCoordinator.restore(profile.id)
                    videoSubscriptionStateCoordinator.restoreHints(profile.id)
                    syncPlaybackHistoryFromSite()
                    videoSubscriptionStateCoordinator.synchronize()
                    (_uiState.value.route as? AppRoute.Details)?.let { route ->
                        animeMarkCoordinator.load(route.animeId)
                        loadAnimeExtras(route.animeId)
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!requestCaptchaRetry(throwable) { login(normalizedLogin, password) }) {
                        _uiState.update {
                            it.copy(auth = AuthUiState(error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun logout() {
        pendingCaptchaAction = null
        animeMarkCoordinator.clear()
        playbackHistorySyncJob?.cancel()
        playbackProgressSyncJobs.values.forEach { it.cancel() }
        playbackProgressSyncJobs.clear()
        animeRatingCoordinator.clear()
        detailsRouteCache.clear()
        videoSubscriptionStateCoordinator.clear()
        profileNotificationsSyncJob?.cancel()
        profileNotificationsRequestId += 1L
        detailsLoadJob?.cancel()
        detailsExtrasJob?.cancel()
        scope.launch {
            withContext(Dispatchers.IO) { repository.logout() }
        }
        SubscriptionNotificationScheduler.cancel(application)
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
        browseContentCoordinator.reload()
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
        animeMarkCoordinator.toggleListMark(mark)
    }

    fun toggleFavorite() {
        animeMarkCoordinator.toggleFavorite()
    }

    fun navigateBack() {
        cacheCurrentDetailsRouteState()
        applyNavigationTransition { state ->
            backNavigationTransition(
                state = state,
                catalogCacheForFilters = browseContentCoordinator::catalogCache,
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
                cachedCatalogForEntry = browseContentCoordinator.catalogCache(entry.filters),
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
            browseContentCoordinator.cancelSearch()
        }
        _uiState.value = transition.state
        transition.effects.forEach(::applyNavigationEffect)
    }

    private fun applyNavigationEffect(effect: NavigationEffect) {
        when (effect) {
            NavigationEffect.LoadCatalog -> browseContentCoordinator.loadCatalog(reset = true)
            is NavigationEffect.SearchCatalog -> browseContentCoordinator.search(effect.query, reset = true)
            is NavigationEffect.EnsureBrowseSection -> browseContentCoordinator.ensureLoaded(effect.section)
            is NavigationEffect.RefreshPlaybackProgress -> refreshPlaybackProgressSnapshot(effect.animeId)
            is NavigationEffect.LoadAnimeDetails -> loadAnimeDetails(effect.animeId)
            is NavigationEffect.OpenAnime -> openAnime(effect.animeId, pushCurrent = false)
            is NavigationEffect.PlayVideo -> effect.route.run {
                playVideoAt(video, startPositionMs, animeTitle, preferredQuality)
            }
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

    private fun observeDownloadQueue() {
        downloadQueueJob?.cancel()
        downloadQueueJob = scope.launch {
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
                    browseContentCoordinator.loadOfflineEntries()
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
        scope.launch {
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (details, videos) ->
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    var accepted = false
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                        accepted = true
                        state.copy(
                            details = LoadState.Ready(details),
                            videos = LoadState.Ready(videos),
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    if (accepted) cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    var accepted = false
                    _uiState.update { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                        accepted = true
                        state.copy(
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    if (accepted) {
                        cacheDetailsRouteState(animeId)
                        showTransientNotice(throwable.userMessage())
                    }
                }
        }
    }

    fun refreshFilterCatalog() {
        loadFilterCatalog()
    }

    private fun loadFilterCatalog() {
        val requestId = ++filterCatalogRequestId
        filterCatalogLoadJob?.cancel()
        _uiState.update { it.copy(filterCatalog = LoadState.Loading) }
        filterCatalogLoadJob = scope.launch {
            runCatching { repository.getFilterCatalog() }
                .onSuccess { catalog ->
                    if (requestId != filterCatalogRequestId) return@onSuccess
                    _uiState.update { it.copy(filterCatalog = LoadState.Ready(catalog)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (requestId != filterCatalogRequestId) return@onFailure
                    _uiState.update { it.copy(filterCatalog = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    private fun restoreProfile() {
        _uiState.update { it.copy(auth = it.auth.copy(loading = true)) }
        scope.launch {
            val cachedProfile = withContext(Dispatchers.IO) { repository.cachedProfile() }
            _uiState.update { it.copy(auth = AuthUiState(profile = cachedProfile, loading = true)) }
            runCatching { repository.restoreProfile() }
            .onSuccess { profile ->
                val activeProfile = profile
                _uiState.update { it.copy(auth = AuthUiState(profile = activeProfile)) }
                animeRatingCoordinator.restore(activeProfile?.id)
                videoSubscriptionStateCoordinator.restoreHints(activeProfile?.id)
                if (activeProfile != null) {
                    syncPlaybackHistoryFromSite()
                    videoSubscriptionStateCoordinator.synchronize()
                }
            }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (throwable.isUnauthorizedApiError()) {
                        withContext(Dispatchers.IO) { repository.logout() }
                        animeRatingCoordinator.clear()
                        detailsRouteCache.clear()
                        videoSubscriptionStateCoordinator.clear()
                        _uiState.update { it.copy(auth = AuthUiState()) }
                    } else {
                        _uiState.update {
                            it.copy(auth = AuthUiState(profile = cachedProfile, error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    private fun loadAnimeExtras(animeId: Long) {
        detailsExtrasJob?.cancel()
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update { it.copy(detailsExtras = LoadState.Ready(AnimeDetailsExtras())) }
            return
        }
        val stateSnapshot = _uiState.value
        val request = AnimeDetailsExtrasLoadRequest(
            animeId = animeId,
            details = stateSnapshot.details.readyDataOrNull(),
            videos = stateSnapshot.videos.readyListOrEmpty(),
            isAuthenticated = stateSnapshot.auth.profile != null,
        )
        _uiState.update { it.copy(detailsExtras = LoadState.Loading) }
        detailsExtrasJob = scope.launch {
            val loaded = animeDetailsExtrasCoordinator.load(request)
            loaded.synchronizedSubscriptions?.let(videoSubscriptionStateCoordinator::publish)
            _uiState.update { state ->
                if ((state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                ) {
                    state.copy(detailsExtras = LoadState.Ready(loaded.extras))
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
            state.copy(detailsExtras = LoadState.Ready(current.withAnimeCommentsLoading()))
        }

        commentsLoadJob = scope.launch {
            try {
                val comments = animeDetailsExtrasCoordinator.loadCommentsPage(animeId, offset)
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@update state
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            animeDetailsExtrasCoordinator.mergeCommentsPage(current, comments),
                        ),
                    )
                }
                cacheDetailsRouteState(animeId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@update state
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            current.withAnimeCommentsFailure(throwable.userMessage()),
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
        val operationState = _uiState.value
        val profileId = operationState.auth.profile?.id ?: return
        val previousDetails = operationState.details
        val previousExtras = operationState.detailsExtras
        val stagedRating = animeRatingCoordinator.stage(animeId, rating)
        _uiState.update { state ->
            state.withOptimisticAnimeRating(animeId, stagedRating.optimisticRating)
        }
        cacheDetailsRouteState(animeId)
        scope.launch {
            runCatching { animeRatingCoordinator.submit(stagedRating) }
                .onSuccess { update ->
                    if (!update.accepted || !acceptsAnimeRatingResult(animeId, profileId, stagedRating)) {
                        return@onSuccess
                    }
                    _uiState.update { state ->
                        state.withConfirmedAnimeRating(animeId, update)
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!acceptsAnimeRatingResult(animeId, profileId, stagedRating)) {
                        return@onFailure
                    }
                    _uiState.update { state ->
                        state.withRestoredAnimeRating(
                            animeId = animeId,
                            previousDetails = previousDetails,
                            previousExtras = previousExtras,
                        )
                    }
                    cacheDetailsRouteState(animeId)
                    if (throwable is CaptchaRequiredException) {
                        requestCaptchaRetry(throwable) { setAnimeRating(rating) }
                    } else {
                        showTransientNotice(throwable.userMessage())
                    }
                }
        }
    }

    private fun acceptsAnimeRatingResult(
        animeId: Long,
        profileId: Long,
        stagedRating: StagedAnimeRating,
    ): Boolean {
        val current = _uiState.value
        return animeRatingCoordinator.isCurrent(stagedRating) &&
            current.auth.profile?.id == profileId &&
            (current.route as? AppRoute.Details)?.animeId == animeId
    }

    fun refreshVideoSubscriptions() {
        videoSubscriptionStateCoordinator.synchronize()
    }

    fun refreshProfileNotifications() {
        syncProfileNotificationsFromSite()
    }

    private fun syncProfileNotificationsFromSite() {
        val requestId = ++profileNotificationsRequestId
        val profile = _uiState.value.auth.profile
        if (_uiState.value.forcedOfflineMode || profile == null) {
            _uiState.update { it.copy(profileNotifications = LoadState.Ready(emptyList())) }
            return
        }
        profileNotificationsSyncJob?.cancel()
        _uiState.update { it.copy(profileNotifications = LoadState.Loading) }
        profileNotificationsSyncJob = scope.launch {
            try {
                val notifications = profileNotificationCoordinator.load(profile.id)
                if (requestId != profileNotificationsRequestId || !isActiveProfile(profile.id)) return@launch
                _uiState.update { state -> state.withProfileNotifications(notifications) }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (requestId != profileNotificationsRequestId || !isActiveProfile(profile.id)) return@launch
                if (!requestCaptchaRetry(throwable) { syncProfileNotificationsFromSite() }) {
                    _uiState.update { it.copy(profileNotifications = LoadState.Error(throwable.userMessage())) }
                }
            }
        }
    }

    fun markProfileNotificationRead(notification: SiteNotification) {
        val profile = _uiState.value.auth.profile
        if (_uiState.value.forcedOfflineMode || profile == null || notification.viewed) return
        _uiState.update { state -> state.withProfileNotificationRead(notification.id) }
        val notifications = _uiState.value.profileNotifications.readyDataOrNull().orEmpty()
        launchProfileNotificationMutation(
            profileId = profile.id,
            retryAction = { markProfileNotificationRead(notification) },
        ) {
            profileNotificationCoordinator.markRead(
                profileId = profile.id,
                notificationId = notification.id,
                notifications = notifications,
            )
        }
    }

    fun markAllProfileNotificationsRead() {
        val profile = _uiState.value.auth.profile
        if (_uiState.value.forcedOfflineMode || profile == null) return
        _uiState.update(YummyDroidUiState::withAllProfileNotificationsRead)
        val notifications = _uiState.value.profileNotifications.readyDataOrNull().orEmpty()
        launchProfileNotificationMutation(
            profileId = profile.id,
            retryAction = { markAllProfileNotificationsRead() },
        ) {
            profileNotificationCoordinator.markAllRead(
                profileId = profile.id,
                notifications = notifications,
            )
        }
    }

    fun deleteProfileNotification(notification: SiteNotification) {
        val profile = _uiState.value.auth.profile
        if (_uiState.value.forcedOfflineMode || profile == null) return
        _uiState.update { state -> state.withoutProfileNotification(notification) }
        val notifications = _uiState.value.profileNotifications.readyDataOrNull().orEmpty()
        launchProfileNotificationMutation(
            profileId = profile.id,
            retryAction = { deleteProfileNotification(notification) },
        ) {
            profileNotificationCoordinator.delete(
                profileId = profile.id,
                notificationId = notification.id,
                notifications = notifications,
            )
        }
    }

    private fun launchProfileNotificationMutation(
        profileId: Long,
        retryAction: suspend () -> Unit,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            try {
                action()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!isActiveProfile(profileId)) return@launch
                if (!requestCaptchaRetry(throwable, retryAction)) {
                    syncProfileNotificationsFromSite()
                    showTransientNotice(throwable.userMessage())
                }
            }
        }
    }

    private fun isActiveProfile(profileId: Long): Boolean {
        val current = _uiState.value
        return !current.forcedOfflineMode && current.auth.profile?.id == profileId
    }

    fun addAnimeComment(text: String) {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeIdOrNull() ?: return
        scope.launch {
            try {
                val comment = animeDetailsExtrasCoordinator.submitComment(animeId, text) ?: return@launch
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val extras = state.detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
                    state.copy(
                        detailsExtras = LoadState.Ready(extras.withAddedAnimeComment(comment)),
                    )
                }
                cacheDetailsRouteState(animeId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!requestCaptchaRetry(throwable) { addAnimeComment(text) }) {
                    showTransientNotice(throwable.userMessage())
                }
            }
        }
    }

    fun toggleVideoSubscription(video: VideoVariant) {
        videoSubscriptionStateCoordinator.toggle(video, showNotice = false)
    }

    fun togglePlayerVideoSubscription(video: VideoVariant) {
        videoSubscriptionStateCoordinator.toggle(video, showNotice = true)
    }

    fun unsubscribeVideoSubscription(subscription: VideoSubscription) {
        videoSubscriptionStateCoordinator.unsubscribe(subscription)
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
        scope.launch {
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
        playbackHistorySyncJob = scope.launch {
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
        playbackProgressSyncJobs[progress.videoId] = scope.launch {
            runCatching { repository.saveWatchProgress(progress) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    requestCaptchaRetry(throwable) { syncPlaybackProgressToSite(progress) }
                }
            playbackProgressSyncJobs.remove(progress.videoId, coroutineContext[Job])
        }
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

    private companion object {
        const val OFFLINE_RECOVERY_CHECK_INTERVAL_MS = 30_000L
        val ALL_USER_MARK_FILTERS = setOf("0", "1", "2", "3", "4", "5")
    }
}
