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
import me.yummydroid.app.data.AnimeRatingStateStorage
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.GitHubUpdateChecker
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.matchingVoiceTitle
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
        replaceProgressHistory = playbackProgressStorage::replaceAll,
        replaceAnimeProgressHistory = playbackProgressStorage::replaceAnime,
        readCachedAnime = historyAnimeCacheStorage::readMany,
        saveCachedAnime = historyAnimeCacheStorage::save,
        fetchHistoryPage = repository::getWatchHistory,
        uploadProgress = repository::saveWatchProgress,
        fetchAnimeSummary = { animeId -> repository.getAnime(animeId).toAnimeSummary() },
        monotonicClockMs = SystemClock::elapsedRealtime,
    )
    private val playbackProgressOperations = KeyedLatestStateOperationCoordinator<Long>()
    private val playbackHistoryOperations = LatestStateOperationCoordinator()
    private val updateChecker = GitHubUpdateChecker()
    private val _uiState = MutableStateFlow(
        YummyDroidUiState(
            settings = initialSettings,
            filters = initialSettings.savedBrowseFilters,
        ),
    )
    val uiState: StateFlow<YummyDroidUiState> = _uiState
    private val profilePlaybackHistoryCache = ProfilePlaybackHistoryCache()
    private val playbackHistoryStateRuntime = PlaybackHistoryStateRuntime(
        scope = scope,
        uiState = _uiState,
        playbackProgressStorage = playbackProgressStorage,
        watchHistoryCoordinator = watchHistoryCoordinator,
        playbackProgressOperations = playbackProgressOperations,
        playbackHistoryOperations = playbackHistoryOperations,
        profilePlaybackHistoryCache = profilePlaybackHistoryCache,
        saveProgressToSite = repository::saveWatchProgress,
        updateCachedPlaybackProgress = ::updateCachedPlaybackProgress,
        clearCachedPlaybackProgress = ::clearCachedPlaybackProgress,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        isActiveProfile = ::isActiveProfile,
    )
    private val profileNotificationStateRuntime = ProfileNotificationStateRuntime(
        scope = scope,
        coordinator = profileNotificationCoordinator,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        showErrorNotice = ::showTransientNotice,
    )
    private val animeRatingStateRuntime = AnimeRatingStateRuntime(
        scope = scope,
        coordinator = animeRatingCoordinator,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        authenticatedDetailsAnimeId = ::authenticatedDetailsAnimeIdOrNull,
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        showErrorNotice = ::showTransientNotice,
    )
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
        onVoiceFallbackNotice = ::showPlaybackVoiceFallbackNotice,
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
        historyOperations = playbackHistoryOperations,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        historyUnavailableMessage = { uiString(R.string.ui_history_temporarily_unavailable) },
        monotonicClockMs = SystemClock::elapsedRealtime,
    )
    private val browseActionRuntime = BrowseActionRuntime(
        scope = scope,
        searchHistoryStorage = searchHistoryStorage,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        browseContentCoordinator = browseContentCoordinator,
        saveBrowseFilters = ::saveBrowseFilters,
        offlineUnavailableMessage = { uiString(R.string.ui_offline_mode_unavailable) },
        showNotice = ::showTransientNotice,
    )

    private val detailsLoadOperations = LatestStateOperationCoordinator()
    private val detailsExtrasOperations = LatestStateOperationCoordinator()
    private val commentsOperations = LatestStateOperationCoordinator()
    private val filterCatalogOperations = LatestStateOperationCoordinator()
    private val commentMutations = SerialStateOperationCoordinator()
    private val siteBaseUrlOperations = LatestStateOperationCoordinator()
    private val cacheMaintenanceOperations = SerialStateOperationCoordinator()
    private val settingsSaveOperations = LatestStateOperationCoordinator()
    private val authOperations = LatestStateOperationCoordinator()
    private val updateCheckOperations = LatestStateOperationCoordinator()
    private var offlineRecoveryJob: Job? = null
    private val offlineDetailsRefreshOperations = KeyedLatestStateOperationCoordinator<Long>()
    private var playerNoticeId = 0L
    private val detailsRouteCache = mutableMapOf<Long, DetailsRouteCache>()
    private val offlineContentRuntime = OfflineContentRuntime(
        application = application,
        scope = scope,
        repository = repository,
        playbackProgressStorage = playbackProgressStorage,
        historyAnimeCacheStorage = historyAnimeCacheStorage,
        cacheMaintenanceOperations = cacheMaintenanceOperations,
        detailsLoadOperations = detailsLoadOperations,
        offlineDetailsRefreshOperations = offlineDetailsRefreshOperations,
        playbackProgressOperations = playbackProgressOperations,
        playbackHistoryOperations = playbackHistoryOperations,
        browseContentCoordinator = browseContentCoordinator,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        clearDetailsRouteCache = detailsRouteCache::clear,
        refresh = ::refresh,
        showNotice = ::showTransientNotice,
        stringResource = { resId -> uiString(resId) },
    )
    private val authStateRuntime = AuthStateRuntime(
        application = application,
        scope = scope,
        repository = repository,
        authOperations = authOperations,
        playbackProgressOperations = playbackProgressOperations,
        playbackHistoryOperations = playbackHistoryOperations,
        animeRatingCoordinator = animeRatingCoordinator,
        animeRatingStateRuntime = animeRatingStateRuntime,
        videoSubscriptionStateCoordinator = videoSubscriptionStateCoordinator,
        animeMarkCoordinator = animeMarkCoordinator,
        playbackHistoryStateRuntime = playbackHistoryStateRuntime,
        profileNotificationStateRuntime = profileNotificationStateRuntime,
        browseContentCoordinator = browseContentCoordinator,
        detailsLoadOperations = detailsLoadOperations,
        detailsExtrasOperations = detailsExtrasOperations,
        commentsOperations = commentsOperations,
        commentMutations = commentMutations,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        saveBrowseFilters = ::saveBrowseFilters,
        clearDetailsRouteCache = detailsRouteCache::clear,
        loadAnimeExtras = ::loadAnimeExtras,
        syncPlaybackHistoryFromSite = { mergeLocalHistory, mergeCandidates, allowLocalHistoryMergePrompt ->
            syncPlaybackHistoryFromSite(
                mergeLocalHistory = mergeLocalHistory,
                mergeCandidates = mergeCandidates,
                allowLocalHistoryMergePrompt = allowLocalHistoryMergePrompt,
            )
        },
        enterLoginAndPasswordMessage = { uiString(R.string.ui_enter_login_and_password) },
    )
    private val playbackActionRuntime = PlaybackActionRuntime(
        scope = scope,
        repository = repository,
        playbackSessionCoordinator = playbackSessionCoordinator,
        animeMarkCoordinator = animeMarkCoordinator,
        playbackHistoryStateRuntime = playbackHistoryStateRuntime,
        playbackProgressStorage = playbackProgressStorage,
        historyAnimeCacheStorage = historyAnimeCacheStorage,
        playbackProgressOperations = playbackProgressOperations,
        profilePlaybackHistoryCache = profilePlaybackHistoryCache,
        browseContentCoordinator = browseContentCoordinator,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        updateCachedPlaybackProgress = ::updateCachedPlaybackProgress,
        clearCachedPlaybackProgress = ::clearCachedPlaybackProgress,
        isActiveProfile = ::isActiveProfile,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        playbackFailureReason = { failure -> failure.noticeReason() },
        openAnime = { animeId, pushCurrent -> openAnime(animeId, pushCurrent = pushCurrent) },
        showNotice = ::showTransientNotice,
    )
    private val animeDetailsStateRuntime = AnimeDetailsStateRuntime(
        scope = scope,
        playbackProgressStorage = playbackProgressStorage,
        profilePlaybackHistoryCache = profilePlaybackHistoryCache,
        animeDetailsLoadCoordinator = animeDetailsLoadCoordinator,
        animeDetailsExtrasCoordinator = animeDetailsExtrasCoordinator,
        animeMarkCoordinator = animeMarkCoordinator,
        videoSubscriptionStateCoordinator = videoSubscriptionStateCoordinator,
        browseContentCoordinator = browseContentCoordinator,
        detailsLoadOperations = detailsLoadOperations,
        detailsExtrasOperations = detailsExtrasOperations,
        commentsOperations = commentsOperations,
        commentMutations = commentMutations,
        cacheMaintenanceOperations = cacheMaintenanceOperations,
        playbackProgressOperations = playbackProgressOperations,
        currentState = { _uiState.value },
        updateState = { transform -> _uiState.update(transform) },
        saveBrowseFilters = ::saveBrowseFilters,
        cachedDetailsRoute = detailsRouteCache::get,
        cacheCurrentDetailsRouteState = ::cacheCurrentDetailsRouteState,
        cacheDetailsRouteState = { animeId -> cacheDetailsRouteState(animeId) },
        updateCachedPlaybackProgress = ::updateCachedPlaybackProgress,
        refreshPlaybackProgressFromSite = ::refreshPlaybackProgressFromSite,
        restoreNavigationEntry = { entry, remainingBackStack, preserveHomeSection ->
            restoreNavigationEntry(
                entry = entry,
                remainingBackStack = remainingBackStack,
                preserveHomeSection = preserveHomeSection,
            )
        },
        authenticatedDetailsAnimeId = ::authenticatedDetailsAnimeIdOrNull,
        isActiveProfile = ::isActiveProfile,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        isOfflineConnectivityFailure = { throwable -> throwable.isOfflineConnectivityFailure() },
        offlineUnavailableMessage = { uiString(R.string.ui_offline_mode_unavailable) },
        showNotice = ::showTransientNotice,
    )

    init {
        DownloadCenter.initialize(application)
        repository.updateContentLanguage(initialSettings.contentLanguage)
        browseActionRuntime.restoreSearchHistory()
        restoreProfile()
        browseContentCoordinator.loadCatalog()
        loadFilterCatalog()
        browseContentCoordinator.loadSchedule()
        browseContentCoordinator.loadOfflineEntries()
        refreshAppContentCacheSize()
        offlineContentRuntime.observeDownloadQueue()
        refreshSiteBaseUrl()
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
        siteBaseUrlOperations.launchLatest(scope) { lease ->
            runCatching { repository.activeSiteBaseUrl() }
                .onSuccess { baseUrl ->
                    if (lease.isCurrent) {
                        _uiState.update { it.copy(siteBaseUrl = baseUrl) }
                    }
                }
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
        browseActionRuntime.updateSearchQuery(query)
    }

    fun submitSearchQuery(query: String) {
        browseActionRuntime.submitSearchQuery(query)
    }

    fun selectSearchHistoryQuery(query: String) {
        browseActionRuntime.selectSearchHistoryQuery(query)
    }

    fun updateFilters(filters: BrowseFilters) {
        browseActionRuntime.updateFilters(filters)
    }

    fun resetFilters() {
        browseActionRuntime.resetFilters()
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
        _uiState.update { it.copy(updateState = LoadState.Loading) }
        updateCheckOperations.launchLatest(scope) { lease ->
            runCatching { updateChecker.latestRelease() }
                .onSuccess { updateInfo ->
                    if (!lease.isCurrent) return@onSuccess
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
                    if (lease.isCurrent) {
                        _uiState.update { it.copy(updateState = LoadState.Error(throwable.userMessage())) }
                    }
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
        browseActionRuntime.openLibraryFilter()
    }

    fun filterByGenre(animeId: Long, genre: FilterOption) {
        animeDetailsStateRuntime.filterByGenre(animeId, genre)
    }

    fun filterByYear(animeId: Long, year: Int) {
        animeDetailsStateRuntime.filterByYear(animeId, year)
    }

    fun filterByStudio(animeId: Long, studio: FilterOption) {
        animeDetailsStateRuntime.filterByStudio(animeId, studio)
    }

    fun filterByCreator(animeId: Long, creator: FilterOption) {
        animeDetailsStateRuntime.filterByCreator(animeId, creator)
    }

    fun openAnime(animeId: Long, pushCurrent: Boolean = true, reload: Boolean = false) {
        animeDetailsStateRuntime.openAnime(animeId, pushCurrent, reload)
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
        animeDetailsStateRuntime.refreshPlaybackProgressSnapshot(animeId)
    }

    private fun clearCachedPlaybackProgress(animeId: Long) {
        val cachedRoute = detailsRouteCache[animeId] ?: return
        detailsRouteCache[animeId] = cachedRoute.copy(
            playbackProgress = null,
            playbackHistory = emptyList(),
        )
    }

    private fun loadAnimeDetails(animeId: Long) {
        animeDetailsStateRuntime.loadAnimeDetails(animeId)
    }

    fun selectVideoGroup(groupKey: String) {
        animeDetailsStateRuntime.selectVideoGroup(groupKey)
    }

    fun downloadVideoForOffline(video: VideoVariant, preferredQuality: PreferredQuality = PreferredQuality.Auto) {
        offlineContentRuntime.downloadVideoForOffline(video, preferredQuality)
    }

    suspend fun resolveAvailableDownloadQualities(
        video: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> {
        return offlineContentRuntime.resolveAvailableDownloadQualities(video, videos, allEpisodes)
    }

    suspend fun resolveSampledDownloadQualities(
        selectedVoiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> {
        return offlineContentRuntime.resolveSampledDownloadQualities(selectedVoiceKeys, videos)
    }

    fun downloadAllVideosForOffline(plan: DownloadPlan) {
        offlineContentRuntime.downloadAllVideosForOffline(plan)
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        offlineContentRuntime.deleteOfflineVideo(animeId, videoId, playbackUrl)
    }

    fun deleteOfflineAnime(animeId: Long) {
        offlineContentRuntime.deleteOfflineAnime(animeId)
    }

    fun refreshAppContentCacheSize() {
        offlineContentRuntime.refreshAppContentCacheSize()
    }

    fun clearAppContentCache() {
        offlineContentRuntime.clearAppContentCache()
    }

    fun clearDownloadHistory() {
        offlineContentRuntime.clearDownloadHistory()
    }

    fun cancelDownload(taskId: Long) {
        offlineContentRuntime.cancelDownload(taskId)
    }

    fun pauseDownload(taskId: Long) {
        offlineContentRuntime.pauseDownload(taskId)
    }

    fun resumeDownload(taskId: Long) {
        offlineContentRuntime.resumeDownload(taskId)
    }

    fun loadMoreAnime() {
        browseContentCoordinator.loadMore()
    }

    fun playVideo(video: VideoVariant) {
        playbackActionRuntime.playVideo(video)
    }

    fun playVideo(video: VideoVariant, animeTitle: String) {
        playbackActionRuntime.playVideo(video, animeTitle)
    }

    fun playVideoAt(video: VideoVariant, startPositionMs: Long) {
        playbackActionRuntime.playVideoAt(video, startPositionMs)
    }

    fun playVideoAtQuality(video: VideoVariant, startPositionMs: Long, preferredQuality: PreferredQuality) {
        playbackActionRuntime.playVideoAtQuality(video, startPositionMs, preferredQuality)
    }

    fun selectPlaybackSource(video: VideoVariant, startPositionMs: Long) {
        playbackActionRuntime.selectPlaybackSource(video, startPositionMs)
    }

    fun playVideoWithResumeChoice(video: VideoVariant, resumePositionMs: Long) {
        playbackActionRuntime.playVideoWithResumeChoice(video, resumePositionMs)
    }

    fun choosePlayerResumePosition(startPositionMs: Long) {
        playbackActionRuntime.choosePlayerResumePosition(startPositionMs)
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
        settingsSaveOperations.launchLatest(scope) {
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

    fun fallbackPlaybackSource(failedVideo: VideoVariant, playbackPositionMs: Long, failure: PlaybackFailure) {
        playbackActionRuntime.fallbackPlaybackSource(failedVideo, playbackPositionMs, failure)
    }

    fun confirmPlaybackSource(video: VideoVariant) {
        playbackActionRuntime.confirmPlaybackSource(video)
    }

    fun handlePlaybackEnded(video: VideoVariant) {
        playbackActionRuntime.handlePlaybackEnded(video)
    }

    fun savePlaybackProgress(video: VideoVariant, positionMs: Long, durationMs: Long) {
        playbackActionRuntime.savePlaybackProgress(video, positionMs, durationMs)
    }

    fun resetAnimeWatchProgress(animeId: Long) {
        playbackActionRuntime.resetAnimeWatchProgress(animeId)
    }

    fun retryVideo() {
        playbackActionRuntime.retryVideo()
    }
    fun submitCaptchaResponse(captchaResponse: String) {
        authStateRuntime.submitCaptchaResponse(captchaResponse)
    }

    fun cancelCaptchaChallenge(error: String?) {
        authStateRuntime.cancelCaptchaChallenge(error)
    }

    private fun requestCaptchaRetry(throwable: Throwable, action: suspend () -> Unit): Boolean {
        return authStateRuntime.requestCaptchaRetry(throwable, action)
    }

    fun login(login: String, password: String, captchaResponse: String? = null) {
        authStateRuntime.login(login, password, captchaResponse)
    }

    fun logout() {
        authStateRuntime.logout()
    }

    private fun authenticatedDetailsAnimeIdOrNull(): Long? {
        return authStateRuntime.authenticatedDetailsAnimeIdOrNull()
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
            browseActionRuntime.cancelSearchRequests()
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
                playbackActionRuntime.playVideoAt(video, startPositionMs, animeTitle, preferredQuality)
            }
        }
    }

    fun refreshFilterCatalog() {
        loadFilterCatalog()
    }

    private fun loadFilterCatalog() {
        _uiState.update { it.copy(filterCatalog = LoadState.Loading) }
        filterCatalogOperations.launchLatest(scope) { lease ->
            runCatching { repository.getFilterCatalog() }
                .onSuccess { catalog ->
                    if (!lease.isCurrent) return@onSuccess
                    _uiState.update { it.copy(filterCatalog = LoadState.Ready(catalog)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    _uiState.update { it.copy(filterCatalog = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    private fun restoreProfile() {
        authStateRuntime.restoreProfile()
    }

    private fun loadAnimeExtras(animeId: Long) {
        animeDetailsStateRuntime.loadAnimeExtras(animeId)
    }

    fun loadMoreAnimeComments() {
        animeDetailsStateRuntime.loadMoreAnimeComments()
    }
    fun setAnimeRating(rating: Int?) {
        animeRatingStateRuntime.setRating(rating)
    }

    fun refreshVideoSubscriptions() {
        videoSubscriptionStateCoordinator.synchronize()
    }

    fun refreshProfileNotifications() {
        profileNotificationStateRuntime.refresh()
    }

    fun markProfileNotificationRead(notification: SiteNotification) {
        profileNotificationStateRuntime.markRead(notification)
    }

    fun markAllProfileNotificationsRead() {
        profileNotificationStateRuntime.markAllRead()
    }

    fun deleteProfileNotification(notification: SiteNotification) {
        profileNotificationStateRuntime.delete(notification)
    }

    private fun isActiveProfile(profileId: Long): Boolean {
        return authStateRuntime.isActiveProfile(profileId)
    }

    fun addAnimeComment(text: String) {
        animeDetailsStateRuntime.addAnimeComment(text)
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

    private fun refreshPlaybackProgressFromSite(animeId: Long) {
        playbackHistoryStateRuntime.refreshPlaybackProgressFromSite(animeId)
    }

    private fun syncPlaybackHistoryFromSite(
        mergeLocalHistory: Boolean = false,
        mergeCandidates: List<PlaybackProgress>? = null,
        allowLocalHistoryMergePrompt: Boolean = false,
    ) {
        playbackHistoryStateRuntime.syncPlaybackHistoryFromSite(
            mergeLocalHistory = mergeLocalHistory,
            mergeCandidates = mergeCandidates,
            allowLocalHistoryMergePrompt = allowLocalHistoryMergePrompt,
        )
    }

    fun confirmLocalWatchHistoryMerge() {
        playbackHistoryStateRuntime.confirmLocalWatchHistoryMerge()
    }

    fun dismissLocalWatchHistoryMerge() {
        playbackHistoryStateRuntime.dismissLocalWatchHistoryMerge()
    }

    private fun syncPlaybackProgressToSite(progress: PlaybackProgress) {
        playbackHistoryStateRuntime.syncPlaybackProgressToSite(progress)
    }

    private fun syncPlaybackProgressToSite(progressEntries: List<PlaybackProgress>) {
        playbackHistoryStateRuntime.syncPlaybackProgressToSite(progressEntries)
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

    private fun showPlaybackVoiceFallbackNotice(previousVideo: VideoVariant, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSameVoiceAs(previousVideo)) return
        _uiState.update { state ->
            state.copy(
                playerNotice = PlayerNotice(
                    id = ++playerNoticeId,
                    message = uiString(
                        R.string.ui_voice_fallback_toast,
                        previousVideo.matchingVoiceTitle,
                        fallbackVideo.episodeTitle,
                        fallbackVideo.matchingVoiceTitle,
                    ),
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
    }
}
