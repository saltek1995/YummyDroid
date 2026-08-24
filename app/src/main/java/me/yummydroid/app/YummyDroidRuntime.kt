package me.yummydroid.app

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.UnknownHostException
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SearchHistoryStorage
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
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
    private val profileNotificationCoordinator = createProfileNotificationCoordinator(
        application = application,
        authStorage = authStorage,
        repository = repository,
    )
    private val animeRatingCoordinator = createAnimeRatingCoordinator(
        application = application,
        repository = repository,
    )
    private val videoSubscriptionCoordinator = createVideoSubscriptionCoordinator(
        repository = repository,
    )
    private val animeDetailsLoadCoordinator = createAnimeDetailsLoadCoordinator(
        repository = repository,
        animeRatingCoordinator = animeRatingCoordinator,
        historyAnimeCacheStorage = historyAnimeCacheStorage,
    )
    private val animeDetailsExtrasCoordinator = createAnimeDetailsExtrasCoordinator(
        repository = repository,
        animeRatingCoordinator = animeRatingCoordinator,
    )
    private val watchHistoryCoordinator = createWatchHistoryCoordinator(
        playbackProgressStorage = playbackProgressStorage,
        historyAnimeCacheStorage = historyAnimeCacheStorage,
        repository = repository,
    )
    private val playbackProgressOperations = KeyedLatestStateOperationCoordinator<Long>()
    private val playbackHistoryOperations = LatestStateOperationCoordinator()
    private val _uiState = MutableStateFlow(
        YummyDroidUiState(
            settings = initialSettings,
            filters = initialSettings.savedBrowseFilters,
        ),
    )
    val uiState: StateFlow<YummyDroidUiState> = _uiState
    private val currentUiState: () -> YummyDroidUiState = { _uiState.value }
    private val updateUiState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit = { transform ->
        _uiState.update(transform)
    }
    private val profilePlaybackHistoryCache = ProfilePlaybackHistoryCache()
    private val playerNoticeRuntime = PlayerNoticeRuntime(
        updateState = updateUiState,
        sourceFallbackMessage = { selectedLabel, reason, fallbackLabel ->
            uiString(R.string.ui_source_fallback_notice, selectedLabel, reason, fallbackLabel)
        },
        voiceFallbackMessage = { previousVoice, fallbackEpisode, fallbackVoice ->
            uiString(R.string.ui_voice_fallback_toast, previousVoice, fallbackEpisode, fallbackVoice)
        },
        playbackPlayerErrorMessage = { uiString(R.string.ui_playback_player_error) },
        playbackBufferingTimeoutMessage = { uiString(R.string.ui_playback_buffer_not_filling) },
        genericSourceLabel = { uiString(R.string.ui_source) },
    )
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
        currentState = currentUiState,
        updateState = updateUiState,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        showErrorNotice = playerNoticeRuntime::showTransientNotice,
    )
    private val animeRatingStateRuntime = AnimeRatingStateRuntime(
        scope = scope,
        coordinator = animeRatingCoordinator,
        currentState = currentUiState,
        updateState = updateUiState,
        authenticatedDetailsAnimeId = ::authenticatedDetailsAnimeIdOrNull,
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        showErrorNotice = playerNoticeRuntime::showTransientNotice,
    )
    private val videoSubscriptionStateCoordinator = VideoSubscriptionStateCoordinator(
        scope = scope,
        subscriptions = videoSubscriptionCoordinator,
        currentState = currentUiState,
        updateState = updateUiState,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        cacheCurrentDetailsRouteState = ::cacheCurrentDetailsRouteState,
        showToggleNotice = { subscribed ->
            playerNoticeRuntime.showTransientNotice(
                uiString(
                    if (subscribed) {
                        R.string.ui_subscription_enabled
                    } else {
                        R.string.ui_subscription_disabled
                    },
                ),
            )
        },
        showErrorNotice = playerNoticeRuntime::showTransientNotice,
    )
    private val animeMarkCoordinator = AnimeMarkCoordinator(
        scope = scope,
        currentState = currentUiState,
        updateState = updateUiState,
        getAnimeMark = repository::getAnimeMark,
        setAnimeListMark = repository::setAnimeListMark,
        removeAnimeListMark = repository::removeAnimeListMark,
        setFavorite = repository::setFavorite,
        authenticatedDetailsAnimeId = ::authenticatedDetailsAnimeIdOrNull,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        onMutationFailure = playerNoticeRuntime::showTransientNotice,
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
        currentState = currentUiState,
        updateState = updateUiState,
        resolvePlaybackMetadata = repository::resolvePlaybackMetadata,
        cachedSiteBaseUrl = repository::cachedSiteBaseUrl,
        offlineUnavailableMessage = { uiString(R.string.ui_episode_unavailable_offline) },
        sourceResolveTimeoutMessage = { uiString(R.string.ui_some_sources_did_not_respond) },
        onFallbackNotice = playerNoticeRuntime::showPlaybackSourceFallbackNotice,
        onVoiceFallbackNotice = playerNoticeRuntime::showPlaybackVoiceFallbackNotice,
        onMetadataFailure = { throwable ->
            AppLog.w("YummyDroidPlayer", "Playback metadata load failed", throwable)
        },
    )
    private val browseContentCoordinator = BrowseContentCoordinator(
        scope = scope,
        currentState = currentUiState,
        updateState = updateUiState,
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
    private val appSettingsRuntime = AppSettingsRuntime(
        scope = scope,
        settingsStorage = settingsStorage,
        repository = repository,
        siteDomainResolver = siteDomainResolver,
        currentState = currentUiState,
        updateState = updateUiState,
        reloadCurrentRoute = { route ->
            when (route) {
                AppRoute.Home -> browseContentCoordinator.reload()
                is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
                is AppRoute.Player -> {
                    route.video.animeId.takeIf { it > 0L }?.let { animeId ->
                        openAnime(animeId, pushCurrent = false, reload = true)
                    }
                }
            }
        },
        currentVersionInstalledMessage = { uiString(R.string.ui_current_version_installed) },
    )
    private val browseActionRuntime = BrowseActionRuntime(
        scope = scope,
        searchHistoryStorage = searchHistoryStorage,
        currentState = currentUiState,
        updateState = updateUiState,
        browseContentCoordinator = browseContentCoordinator,
        saveBrowseFilters = appSettingsRuntime::saveBrowseFilters,
        offlineUnavailableMessage = { uiString(R.string.ui_offline_mode_unavailable) },
        showNotice = playerNoticeRuntime::showTransientNotice,
    )

    private val detailsLoadOperations = LatestStateOperationCoordinator()
    private val detailsExtrasOperations = LatestStateOperationCoordinator()
    private val commentsOperations = LatestStateOperationCoordinator()
    private val commentMutations = SerialStateOperationCoordinator()
    private val cacheMaintenanceOperations = SerialStateOperationCoordinator()
    private val authOperations = LatestStateOperationCoordinator()
    private val offlineDetailsRefreshOperations = KeyedLatestStateOperationCoordinator<Long>()
    private val detailsRouteCache = mutableMapOf<Long, DetailsRouteCache>()
    private val appContentRefreshRuntime = AppContentRefreshRuntime(
        scope = scope,
        repository = repository,
        currentState = currentUiState,
        updateState = updateUiState,
        reloadCurrentRoute = { route ->
            when (route) {
                AppRoute.Home -> browseContentCoordinator.reload()
                is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false, reload = true)
                is AppRoute.Player -> Unit
            }
        },
    )
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
        currentState = currentUiState,
        updateState = updateUiState,
        cacheDetailsRouteState = ::cacheDetailsRouteState,
        clearDetailsRouteCache = detailsRouteCache::clear,
        refresh = ::refresh,
        showNotice = playerNoticeRuntime::showTransientNotice,
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
        currentState = currentUiState,
        updateState = updateUiState,
        saveBrowseFilters = appSettingsRuntime::saveBrowseFilters,
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
        currentState = currentUiState,
        updateState = updateUiState,
        updateCachedPlaybackProgress = ::updateCachedPlaybackProgress,
        clearCachedPlaybackProgress = ::clearCachedPlaybackProgress,
        isActiveProfile = ::isActiveProfile,
        requestCaptchaRetry = { throwable, action -> requestCaptchaRetry(throwable, action) },
        playbackFailureReason = playerNoticeRuntime::playbackFailureReason,
        openAnime = { animeId, pushCurrent -> openAnime(animeId, pushCurrent = pushCurrent) },
        showNotice = playerNoticeRuntime::showTransientNotice,
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
        currentState = currentUiState,
        updateState = updateUiState,
        saveBrowseFilters = appSettingsRuntime::saveBrowseFilters,
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
        showNotice = playerNoticeRuntime::showTransientNotice,
    )
    private val navigationStateRuntime = NavigationStateRuntime(
        currentState = currentUiState,
        publishState = { state -> _uiState.value = state },
        updateState = updateUiState,
        browseActionRuntime = browseActionRuntime,
        browseContentCoordinator = browseContentCoordinator,
        cachedDetailsRoute = detailsRouteCache::get,
        cacheCurrentDetailsRouteState = ::cacheCurrentDetailsRouteState,
        refreshPlaybackProgressSnapshot = ::refreshPlaybackProgressSnapshot,
        loadAnimeDetails = ::loadAnimeDetails,
        openAnime = { animeId, pushCurrent -> openAnime(animeId, pushCurrent = pushCurrent) },
        playRouteVideo = { route ->
            playbackActionRuntime.playVideoAt(
                video = route.video,
                startPositionMs = route.startPositionMs,
                titleOverride = route.animeTitle,
                preferredQuality = route.preferredQuality,
            )
        },
    )

    init {
        DownloadCenter.initialize(application)
        repository.updateContentLanguage(initialSettings.contentLanguage)
        browseActionRuntime.restoreSearchHistory()
        restoreProfile()
        browseContentCoordinator.loadCatalog()
        appContentRefreshRuntime.loadFilterCatalog()
        browseContentCoordinator.loadSchedule()
        browseContentCoordinator.loadOfflineEntries()
        refreshAppContentCacheSize()
        offlineContentRuntime.observeDownloadQueue()
        appSettingsRuntime.refreshSiteBaseUrl()
        appContentRefreshRuntime.startOfflineRecoveryMonitor()
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
        appSettingsRuntime.updateSettings(settings)
    }

    fun checkForUpdates() {
        appSettingsRuntime.checkForUpdates()
    }

    fun selectBrowseSection(section: BrowseSection) {
        navigationStateRuntime.selectBrowseSection(section)
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

    fun openAnime(target: AnimeOpenTarget, pushCurrent: Boolean = true, reload: Boolean = false) {
        animeDetailsStateRuntime.openAnime(target, pushCurrent, reload)
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

    private fun uiString(@StringRes resId: Int, vararg formatArgs: Any): String {
        val language = _uiState.value.settings.contentLanguage
        val context = application
        return if (formatArgs.isEmpty()) {
            context.localizedString(resId, language)
        } else {
            context.localizedString(resId, language, *formatArgs)
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
        navigationStateRuntime.navigateBack()
    }

    private fun restoreNavigationEntry(
        entry: NavigationEntry,
        remainingBackStack: List<NavigationEntry>,
        preserveHomeSection: Boolean = false,
    ) {
        navigationStateRuntime.restoreNavigationEntry(entry, remainingBackStack, preserveHomeSection)
    }
    fun refreshFilterCatalog() {
        appContentRefreshRuntime.loadFilterCatalog()
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
}
