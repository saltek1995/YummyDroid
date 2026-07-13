package me.yummydroid.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeRatingStateStorage
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.GitHubUpdateChecker
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoSubscriptionHintStorage
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.isUnauthorizedApiError
import me.yummydroid.app.data.normalized
import me.yummydroid.app.data.withVoiceSubscriptionState

private const val MAX_NAVIGATION_STACK = 40
private const val AUTH_REQUIRED_ERROR_KEY = "auth_required"
private const val SUBSCRIPTION_ENABLE_FAILED_KEY = "subscription_enable_failed"
private const val SUBSCRIPTION_DISABLE_FAILED_KEY = "subscription_disable_failed"
private const val SUBSCRIPTION_TARGET_NOT_FOUND_KEY = "subscription_target_not_found"

class YummyDroidViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsStorage = AppSettingsStorage(application)
    private val playbackProgressStorage = PlaybackProgressStorage(application)
    private val animeRatingStateStorage = AnimeRatingStateStorage(application)
    private val videoSubscriptionHintStorage = VideoSubscriptionHintStorage(application)
    private val initialSettings = settingsStorage.read()
    private val authStorage = AuthStorage(application)
    private val siteDomainResolver = SiteDomainResolver(candidates = initialSettings.siteDomains)
    private val repository = YummyAnimeRepository(
        context = application,
        siteDomainResolver = siteDomainResolver,
        authStorage = authStorage,
    )
    private val updateChecker = GitHubUpdateChecker()
    private val _uiState = MutableStateFlow(
        YummyDroidUiState(
            settings = initialSettings,
            filters = initialSettings.savedBrowseFilters,
            auth = AuthUiState(profile = authStorage.readProfile()),
        ),
    )
    val uiState: StateFlow<YummyDroidUiState> = _uiState

    private var searchDebounceJob: Job? = null
    private var featuredLoadJob: Job? = null
    private var searchLoadJob: Job? = null
    private var topLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var historyLoadJob: Job? = null
    private var libraryLoadJob: Job? = null
    private var offlineLoadJob: Job? = null
    private var downloadQueueJob: Job? = null
    private var detailsExtrasJob: Job? = null
    private var commentsLoadJob: Job? = null
    private var updateCheckJob: Job? = null
    private var playerLoadJob: Job? = null
    private var animeMarkJob: Job? = null
    private var subscriptionsSyncJob: Job? = null
    private val videoSubscriptionHints = mutableListOf<VideoSubscriptionHint>()
    private var playbackHistorySyncJob: Job? = null
    private var offlineRecoveryJob: Job? = null
    private val playbackProgressSyncJobs = mutableMapOf<Long, Job>()
    private var failedPlaybackSourceIds: Set<Long> = emptySet()
    private val playbackSourceCache = mutableMapOf<PlaybackCacheKey, PlaybackSourceCacheEntry>()
    private val autoAnimeMarkJobs = mutableMapOf<Long, Job>()
    private var completedDownloadTaskIds: Set<Long> = emptySet()
    private val knownAnimeRatings = mutableMapOf<Long, Int?>()

    init {
        DownloadCenter.initialize(application)
        repository.updateContentLanguage(initialSettings.contentLanguage)
        val cachedProfile = authStorage.readProfile()
        restoreKnownAnimeRatings(cachedProfile)
        restoreVideoSubscriptionHints(cachedProfile)
        loadHome()
        loadFilterCatalog()
        loadSchedule()
        loadOfflineEntries()
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
            is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false)
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
                    is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false)
                    is AppRoute.Player -> Unit
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
                homeSection = BrowseSection.Catalog,
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

    fun updateFilters(filters: BrowseFilters) {
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.copy(
                filters = filters,
                settings = updatedSettings,
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
            )
        }
        reloadBrowse()
    }

    fun resetFilters() {
        val filters = BrowseFilters()
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.copy(
                filters = filters,
                settings = updatedSettings,
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
            )
        }
        reloadBrowse()
    }

    fun updateSettings(settings: AppSettings) {
        val previousSettings = _uiState.value.settings
        val normalizedSettings = settings.normalized()
        val languageChanged = previousSettings.contentLanguage != normalizedSettings.contentLanguage
        settingsStorage.save(normalizedSettings)
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
                is AppRoute.Details -> openAnime(route.animeId, pushCurrent = false)
                is AppRoute.Player -> {
                    route.video.animeId.takeIf { it > 0L }?.let { openAnime(it, pushCurrent = false) }
                }
            }
        }
    }

    private fun saveBrowseFilters(filters: BrowseFilters): AppSettings {
        val updatedSettings = _uiState.value.settings.copy(savedBrowseFilters = filters).normalized()
        settingsStorage.save(updatedSettings)
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
                                    title = if (updateInfo.isNewerThan(BuildConfig.VERSION_NAME)) {
                                        updateInfo.title
                                    } else {
                                        "Установлена актуальная версия"
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
        _uiState.update { state ->
            val shouldPushCurrent = state.route != AppRoute.Home || state.homeSection != section
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(shouldPushCurrent),
                homeSection = section,
                searchQuery = if (section == BrowseSection.Catalog) state.searchQuery else "",
                searchResults = if (section == BrowseSection.Catalog) state.searchResults else LoadState.Ready(emptyList()),
                searchPaging = if (section == BrowseSection.Catalog) state.searchPaging else PagingUiState(canLoadMore = false),
            )
        }
        when (section) {
            BrowseSection.Catalog -> reloadBrowse()
            BrowseSection.Schedule -> loadSchedule()
            BrowseSection.History -> loadHistory()
            BrowseSection.Downloads -> loadOfflineEntries()
        }
    }

    fun openLibraryFilter() {
        if (_uiState.value.auth.profile == null) return
        val filters = BrowseFilters(userMarks = ALL_USER_MARK_FILTERS)
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
                homeSection = BrowseSection.Catalog,
                filters = filters,
                settings = updatedSettings,
                searchQuery = "",
                searchResults = LoadState.Ready(emptyList()),
                searchPaging = PagingUiState(canLoadMore = false),
            )
        }
        loadHome(reset = true)
    }

    fun filterByGenre(genre: FilterOption) {
        applyDetailsFilter { it.copy(genres = setOf(genre.value)) }
    }

    fun filterByYear(year: Int) {
        applyDetailsFilter { it.copy(fromYear = year, toYear = year) }
    }

    fun filterByStudio(studio: FilterOption) {
        applyDetailsFilter {
            it.copy(
                studios = setOf(studio.value),
                studioTitles = mapOf(studio.value to studio.title),
            )
        }
    }

    fun filterByCreator(creator: FilterOption) {
        applyDetailsFilter {
            it.copy(
                creators = setOf(creator.value),
                creatorTitles = mapOf(creator.value to creator.title),
            )
        }
    }

    fun openAnime(animeId: Long, pushCurrent: Boolean = true) {
        commentsLoadJob?.cancel()
        _uiState.update { state ->
            val targetRoute = AppRoute.Details(animeId)
            state.copy(
                navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                route = AppRoute.Details(animeId),
                details = LoadState.Loading,
                videos = LoadState.Loading,
                detailsExtras = LoadState.Loading,
                selectedVideoGroup = null,
                animeMark = LoadState.Loading,
                playbackProgress = playbackProgressStorage.read(animeId),
                playbackHistory = playbackProgressStorage.readAnimeHistory(animeId),
            )
        }
        loadAnimeDetails(animeId)
    }

    private fun loadAnimeDetails(animeId: Long) {
        viewModelScope.launch {
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (animeDetails, videoVariants) ->
                    val offlineMode = repository.isOfflineFallbackActive()
                    val detailsWithRating = animeDetails.copy(
                        userRating = effectiveAnimeRating(
                            animeId = animeId,
                            remoteRating = animeDetails.userRating,
                            trustRemote = _uiState.value.auth.profile != null && !offlineMode,
                        ),
                    )
                    val playableVideos = if (offlineMode) {
                        videoVariants.filter { it.isOfflineAvailable }
                    } else {
                        videoVariants
                    }
                    val progress = if (offlineMode) {
                        playbackProgressStorage.read(animeId)
                    } else {
                        syncPlaybackProgressForAnime(animeId)
                    }
                    val progressGroupKey = progress?.groupKey
                        ?.takeIf { groupKey -> playableVideos.any { it.groupKey == groupKey } }
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(detailsWithRating),
                            videos = LoadState.Ready(videoVariants),
                            forcedOfflineMode = offlineMode,
                            selectedVideoGroup = progressGroupKey
                                ?: playableVideos.firstOrNull()?.groupKey
                                ?: videoVariants.firstOrNull()?.groupKey,
                            playbackProgress = progress,
                            playbackHistory = playbackProgressStorage.readAnimeHistory(animeId),
                            detailsExtras = if (offlineMode) LoadState.Ready(AnimeDetailsExtras()) else it.detailsExtras,
                            animeMark = if (offlineMode) LoadState.Ready(null) else it.animeMark,
                        )
                    }
                    if (offlineMode) {
                        animeMarkJob?.cancel()
                        detailsExtrasJob?.cancel()
                    } else {
                        loadAnimeMark(animeId)
                        loadAnimeExtras(animeId)
                    }
                }
                .onFailure { throwable ->
                    val message = throwable.userMessage()
                    _uiState.update {
                        it.copy(
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
    }

    fun downloadVideoForOffline(video: VideoVariant, preferredQuality: PreferredQuality = PreferredQuality.Auto) {
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        videoId = video.id,
                        isRunning = false,
                        message = "Скачивание недоступно в оффлайн-режиме",
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
                    message = "Добавлено в очередь",
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

    fun downloadAllVideosForOffline(groupKey: String?, preferredQuality: PreferredQuality = PreferredQuality.Auto) {
        val state = _uiState.value
        if (state.forcedOfflineMode) {
            _uiState.update {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        isRunning = false,
                        message = "Скачивание недоступно в оффлайн-режиме",
                    ),
                )
            }
            return
        }
        val details = (state.details as? LoadState.Ready)?.data ?: return
        val videos = (state.videos as? LoadState.Ready)?.data.orEmpty()
        if (videos.isEmpty()) return

        DownloadService.enqueueAnime(
            context = getApplication(),
            animeId = details.id,
            groupKey = groupKey ?: state.selectedVideoGroup,
            quality = preferredQuality,
        )
        _uiState.update {
            it.copy(
                offlineDownload = OfflineDownloadUiState(
                    isRunning = true,
                    progress = 0f,
                    message = "Добавлено в очередь",
                ),
            )
        }
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        repository.deleteOfflineVideo(animeId, videoId, playbackUrl)
        refreshCurrentDetailsFromOfflineCache(animeId)
        loadOfflineEntries()
    }

    fun deleteOfflineAnime(animeId: Long) {
        repository.deleteOfflineAnime(animeId)
        refreshCurrentDetailsFromOfflineCache(animeId)
        loadOfflineEntries()
    }

    fun clearAppContentCache() {
        repository.clearAppContentCache(playbackProgressStorage)
        DownloadCenter.clearAll()
        _uiState.update {
            it.copy(
                playbackProgress = null,
                playbackHistory = emptyList(),
                offlineEntries = LoadState.Ready(emptyList()),
                downloadQueue = DownloadQueueSnapshot(),
                offlineDownload = OfflineDownloadUiState(message = "Кэш очищен"),
            )
        }
        refresh()
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

    private fun applyDetailsFilter(transform: (BrowseFilters) -> BrowseFilters) {
        val filters = transform(BrowseFilters())
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
                homeSection = BrowseSection.Catalog,
                filters = filters,
                settings = updatedSettings,
                searchQuery = "",
                searchResults = LoadState.Ready(emptyList()),
                searchPaging = PagingUiState(canLoadMore = false),
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
        val title = (_uiState.value.details as? LoadState.Ready)?.data?.title.orEmpty()
        playVideoAt(video, startPositionMs = 0L, titleOverride = title)
    }

    fun playVideo(video: VideoVariant, animeTitle: String) {
        val title = animeTitle.ifBlank { (_uiState.value.details as? LoadState.Ready)?.data?.title.orEmpty() }
        playVideoAt(video, startPositionMs = 0L, titleOverride = title)
    }

    fun playVideoAt(video: VideoVariant, startPositionMs: Long) {
        val title = (_uiState.value.details as? LoadState.Ready)?.data?.title
            ?: (_uiState.value.route as? AppRoute.Player)?.animeTitle
            ?: ""
        playVideoAt(video, startPositionMs, title)
    }

    fun playVideoAtQuality(video: VideoVariant, startPositionMs: Long, preferredQuality: PreferredQuality) {
        val title = (_uiState.value.details as? LoadState.Ready)?.data?.title
            ?: (_uiState.value.route as? AppRoute.Player)?.animeTitle
            ?: ""
        playVideoAt(video, startPositionMs, title, preferredQuality)
    }

    private fun playVideoAt(
        video: VideoVariant,
        startPositionMs: Long,
        titleOverride: String,
        preferredQuality: PreferredQuality = _uiState.value.settings.defaultQuality,
    ) {
        failedPlaybackSourceIds = emptySet()
        playVideoFromCandidates(
            video = video,
            title = titleOverride,
            excludedSourceIds = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
        )
    }

    fun fallbackPlaybackSource(failedVideo: VideoVariant, playbackPositionMs: Long) {
        val route = _uiState.value.route as? AppRoute.Player ?: return
        failedPlaybackSourceIds = failedPlaybackSourceIds + failedVideo.id
        removeCachedPlaybackSource(failedVideo)
        playVideoFromCandidates(
            video = route.video,
            title = route.animeTitle,
            excludedSourceIds = failedPlaybackSourceIds,
            startPositionMs = playbackPositionMs.takeIf { it > 0L } ?: route.startPositionMs,
            preferredQuality = route.preferredQuality,
        )
    }

    private fun playVideoFromCandidates(
        video: VideoVariant,
        title: String,
        excludedSourceIds: Set<Long>,
        startPositionMs: Long,
        preferredQuality: PreferredQuality,
    ) {
        playerLoadJob?.cancel()
        val safeStartPositionMs = startPositionMs.coerceAtLeast(0L)
        val allVideos = (_uiState.value.videos as? LoadState.Ready)?.data.orEmpty()
        val forcedOfflineMode = _uiState.value.forcedOfflineMode
        val candidates = playbackCandidates(
            requested = video,
            allVideos = allVideos,
            excludedSourceIds = excludedSourceIds,
        ).let { candidates ->
            if (forcedOfflineMode) candidates.filter { it.isOfflineAvailable } else candidates
        }
        if (forcedOfflineMode && candidates.isEmpty()) {
            _uiState.update {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        isRunning = false,
                        message = "Эта серия недоступна офлайн",
                    ),
                )
            }
            return
        }
        val routeVideo = if (forcedOfflineMode && !video.isOfflineAvailable) {
            candidates.first()
        } else {
            video
        }
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Player(routeVideo, title, safeStartPositionMs, preferredQuality),
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route !is AppRoute.Player),
                playerStream = LoadState.Loading,
            )
        }

        playerLoadJob = viewModelScope.launch {
            runCatching { resolvePlaybackWithCache(routeVideo, candidates, preferredQuality) }
                .onSuccess { playback ->
                    _uiState.update { state ->
                        if (state.route == AppRoute.Player(routeVideo, title, safeStartPositionMs, preferredQuality)) {
                            state.copy(
                                route = AppRoute.Player(playback.video, title, safeStartPositionMs, preferredQuality),
                                siteBaseUrl = repository.cachedSiteBaseUrl(),
                                selectedVideoGroup = playback.video.groupKey,
                                playerStream = LoadState.Ready(playback.stream),
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        if (state.route == AppRoute.Player(routeVideo, title, safeStartPositionMs, preferredQuality)) {
                            state.copy(playerStream = LoadState.Error(throwable.userMessage()))
                        } else {
                            state
                        }
                    }
                }
        }
    }

    fun confirmPlaybackSource(video: VideoVariant) {
        val stream = (_uiState.value.playerStream as? LoadState.Ready)?.data
        playbackSourceCache[video.playbackCacheKey()] = PlaybackSourceCacheEntry(
            providerKey = video.sourceProviderKey,
            maxVideoHeight = stream?.maxVideoHeight,
        )
        maybeAutoMarkWatching(video)
    }

    fun handlePlaybackEnded(video: VideoVariant) {
        val state = _uiState.value
        if (!state.settings.autoMarkWatchedOnCompletedFinalEpisode || state.auth.profile == null) return

        val details = (state.details as? LoadState.Ready)?.data
            ?.takeIf { it.id == video.animeId }
            ?: return
        if (!details.isFullyReleased()) return

        val videos = (state.videos as? LoadState.Ready)?.data.orEmpty()
        if (video.isFinalEpisodeFor(details, videos)) {
            scheduleAutoSetAnimeListMark(video.animeId, UserAnimeListMark.Watched)
        }
    }

    fun savePlaybackProgress(video: VideoVariant, positionMs: Long, durationMs: Long) {
        if (video.animeId <= 0L || video.id <= 0L || positionMs < 0L) return

        val animeTitle = (_uiState.value.details as? LoadState.Ready)
            ?.data
            ?.takeIf { it.id == video.animeId }
            ?.title
            .orEmpty()
        val progress = PlaybackProgress(
            animeId = video.animeId,
            videoId = video.id,
            animeTitle = animeTitle,
            groupKey = video.groupKey,
            episode = video.episode,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = System.currentTimeMillis(),
        )
        playbackProgressStorage.save(progress)
        _uiState.update { state ->
            if ((state.details as? LoadState.Ready)?.data?.id == video.animeId) {
                state.copy(
                    playbackProgress = progress,
                    playbackHistory = playbackProgressStorage.readAnimeHistory(video.animeId),
                    historyAnime = state.historyAnime.updatedWithLocalHistory(),
                )
            } else {
                state.copy(historyAnime = state.historyAnime.updatedWithLocalHistory())
            }
        }
        syncPlaybackProgressToSite(progress)
    }

    private fun maybeAutoMarkWatching(video: VideoVariant) {
        val state = _uiState.value
        if (state.forcedOfflineMode) return
        if (!state.settings.autoMarkWatchingOnPlayback || state.auth.profile == null) return

        val currentMark = (state.animeMark as? LoadState.Ready)?.data
            ?.takeIf { (state.details as? LoadState.Ready)?.data?.id == video.animeId }
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

                val stateMark = (state.animeMark as? LoadState.Ready)?.data
                    ?.takeIf { (state.details as? LoadState.Ready)?.data?.id == animeId }
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
                        if ((state.details as? LoadState.Ready)?.data?.id == animeId) {
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

    fun login(login: String, password: String, captchaResponse: String? = null) {
        if (login.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Введите логин и пароль")) }
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
                    if (throwable is CaptchaRequiredException) {
                        _uiState.update {
                            it.copy(
                                auth = it.auth.copy(
                                    loading = false,
                                    error = throwable.userMessage(),
                                    captchaRequestNonce = it.auth.captchaRequestNonce + 1,
                                ),
                            )
                        }
                    } else {
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
        videoSubscriptionHints.clear()
        subscriptionsSyncJob?.cancel()
        repository.logout()
        val filters = _uiState.value.filters.copy(userMarks = emptySet())
        val updatedSettings = saveBrowseFilters(filters)
        _uiState.update {
            it.copy(
                auth = AuthUiState(),
                animeMark = LoadState.Ready(null),
                libraryAnime = LoadState.Ready(emptyList()),
                globalSubscriptions = LoadState.Ready(emptyList()),
                filters = filters,
                settings = updatedSettings,
            )
        }
        reloadBrowse()
    }

    fun selectAnimeListMark(mark: UserAnimeListMark) {
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Нужно войти в аккаунт")) }
            return
        }

        val previousMarkState = _uiState.value.animeMark
        val current = (previousMarkState as? LoadState.Ready)?.data ?: UserAnimeMark()
        val optimisticMark = if (current.list == mark) {
            current.copy(list = null)
        } else {
            current.copy(list = mark)
        }
        _uiState.update { it.copy(animeMark = LoadState.Ready(optimisticMark)) }
        viewModelScope.launch {
            runCatching {
                if (current.list == mark) {
                    repository.removeAnimeListMark(animeId)
                } else {
                    repository.setAnimeListMark(animeId, mark)
                }
            }
                .onSuccess { updatedMark ->
                    _uiState.update { it.copy(animeMark = LoadState.Ready(updatedMark)) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            animeMark = previousMarkState,
                            auth = it.auth.copy(error = throwable.userMessage()),
                        )
                    }
                }
        }
    }

    fun toggleFavorite() {
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Нужно войти в аккаунт")) }
            return
        }

        val previousMarkState = _uiState.value.animeMark
        val current = (previousMarkState as? LoadState.Ready)?.data ?: UserAnimeMark()
        val optimisticMark = current.copy(isFavorite = !current.isFavorite)
        _uiState.update { it.copy(animeMark = LoadState.Ready(optimisticMark)) }
        viewModelScope.launch {
            runCatching { repository.setFavorite(animeId, !current.isFavorite) }
                .onSuccess { updatedMark ->
                    _uiState.update { it.copy(animeMark = LoadState.Ready(updatedMark)) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            animeMark = previousMarkState,
                            auth = it.auth.copy(error = throwable.userMessage()),
                        )
                    }
                }
        }
    }

    fun navigateBack() {
        val state = _uiState.value
        val previous = state.navigationBackStack.lastOrNull()
        if (previous != null) {
            restoreNavigationEntry(previous, state.navigationBackStack.dropLast(1))
            return
        }

        when (val route = state.route) {
            AppRoute.Home -> {
                when {
                    state.searchQuery.isNotBlank() -> {
                        searchDebounceJob?.cancel()
                        searchLoadJob?.cancel()
                        _uiState.update {
                            it.copy(
                                searchQuery = "",
                                searchResults = LoadState.Ready(emptyList()),
                                searchPaging = PagingUiState(canLoadMore = false),
                            )
                        }
                        loadHome(reset = true)
                    }
                    state.homeSection != BrowseSection.Catalog -> {
                        _uiState.update {
                            it.copy(
                                homeSection = BrowseSection.Catalog,
                                searchQuery = "",
                                searchResults = LoadState.Ready(emptyList()),
                                searchPaging = PagingUiState(canLoadMore = false),
                            )
                        }
                        loadHome(reset = true)
                    }
                    else -> Unit
                }
            }
            is AppRoute.Details -> _uiState.update { it.copy(route = AppRoute.Home) }
            is AppRoute.Player -> openAnime(route.video.animeId, pushCurrent = false)
        }
    }

    private fun restoreNavigationEntry(
        entry: NavigationEntry,
        remainingBackStack: List<NavigationEntry>,
    ) {
        when (val route = entry.route) {
            AppRoute.Home -> {
                val currentState = _uiState.value
                val restoreCatalog = entry.homeSection == BrowseSection.Catalog && entry.searchQuery.isBlank()
                val restoreSearch = entry.homeSection == BrowseSection.Catalog && entry.searchQuery.isNotBlank()
                val canReuseCatalog = restoreCatalog &&
                    currentState.filters == entry.filters &&
                    currentState.featured is LoadState.Ready
                val canReuseSearch = restoreSearch &&
                    currentState.filters == entry.filters &&
                    currentState.searchQuery == entry.searchQuery &&
                    currentState.searchResults is LoadState.Ready
                searchDebounceJob?.cancel()
                searchLoadJob?.cancel()
                _uiState.update {
                    it.copy(
                        route = AppRoute.Home,
                        navigationBackStack = remainingBackStack,
                        homeSection = entry.homeSection,
                        filters = entry.filters,
                        searchQuery = entry.searchQuery,
                        searchResults = when {
                            entry.homeSection != BrowseSection.Catalog || entry.searchQuery.isBlank() -> {
                                LoadState.Ready(emptyList())
                            }
                            canReuseSearch -> it.searchResults
                            else -> LoadState.Loading
                        },
                        searchPaging = when {
                            entry.homeSection != BrowseSection.Catalog || entry.searchQuery.isBlank() -> {
                                PagingUiState(canLoadMore = false)
                            }
                            canReuseSearch -> it.searchPaging
                            else -> PagingUiState(canLoadMore = true)
                        },
                        selectedVideoGroup = entry.selectedVideoGroup,
                    )
                }
                when (entry.homeSection) {
                    BrowseSection.Catalog -> {
                        if (entry.searchQuery.isBlank()) {
                            if (!canReuseCatalog) loadHome(reset = true)
                        } else {
                            if (!canReuseSearch) searchNow(entry.searchQuery, reset = true)
                        }
                    }
                    BrowseSection.Schedule -> loadSchedule()
                    BrowseSection.History -> loadHistory()
                    BrowseSection.Downloads -> loadOfflineEntries()
                }
            }
            is AppRoute.Details -> {
                _uiState.update {
                    it.copy(
                        route = route,
                        navigationBackStack = remainingBackStack,
                        homeSection = entry.homeSection,
                        filters = entry.filters,
                        searchQuery = entry.searchQuery,
                        selectedVideoGroup = entry.selectedVideoGroup,
                        details = LoadState.Loading,
                        videos = LoadState.Loading,
                        detailsExtras = LoadState.Loading,
                        animeMark = LoadState.Loading,
                        playbackProgress = playbackProgressStorage.read(route.animeId),
                        playbackHistory = playbackProgressStorage.readAnimeHistory(route.animeId),
                    )
                }
                loadAnimeDetails(route.animeId)
            }
            is AppRoute.Player -> {
                _uiState.update {
                    it.copy(
                        route = route,
                        navigationBackStack = remainingBackStack,
                        homeSection = entry.homeSection,
                        filters = entry.filters,
                        searchQuery = entry.searchQuery,
                        selectedVideoGroup = entry.selectedVideoGroup,
                    )
                }
                playVideoAt(route.video, route.startPositionMs, route.animeTitle)
            }
        }
    }

    private fun loadHome(reset: Boolean = true) {
        val currentState = _uiState.value
        val paging = currentState.featuredPaging
        if (!reset && (paging.isLoadingMore || !paging.canLoadMore)) return

        if (reset) {
            featuredLoadJob?.cancel()
            _uiState.update {
                it.copy(
                    featured = LoadState.Loading,
                    featuredPaging = PagingUiState(),
                )
            }
        } else {
            _uiState.update {
                it.copy(featuredPaging = it.featuredPaging.copy(isLoadingMore = true, error = null))
            }
        }

        val offset = if (reset) {
            0
        } else {
            (currentState.featured as? LoadState.Ready)?.data.orEmpty().size
        }

        featuredLoadJob = viewModelScope.launch {
            val filters = _uiState.value.filters
            runCatching { repository.getFeatured(filters, offset = offset, limit = PAGE_SIZE) }
                .onSuccess { animes ->
                    _uiState.update { state ->
                        if (state.filters != filters || state.searchQuery.isNotBlank()) {
                            state
                        } else {
                            val existing = if (reset) emptyList() else (state.featured as? LoadState.Ready)?.data.orEmpty()
                            val merged = (existing + animes).distinctBy { it.id }
                            state.copy(
                                featured = LoadState.Ready(merged),
                                forcedOfflineMode = repository.isOfflineFallbackActive(),
                                featuredPaging = PagingUiState(
                                    isLoadingMore = false,
                                    canLoadMore = animes.size >= PAGE_SIZE && merged.size > existing.size,
                                ),
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        if (reset) {
                            state.copy(
                                featured = LoadState.Error(throwable.userMessage()),
                                forcedOfflineMode = false,
                                featuredPaging = PagingUiState(canLoadMore = true),
                            )
                        } else {
                            state.copy(
                                featuredPaging = state.featuredPaging.copy(
                                    isLoadingMore = false,
                                    canLoadMore = true,
                                    error = throwable.userMessage(),
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun loadTop(reset: Boolean = true) {
        val currentState = _uiState.value
        val paging = currentState.topPaging
        if (!reset && (paging.isLoadingMore || !paging.canLoadMore)) return

        if (reset) {
            topLoadJob?.cancel()
            _uiState.update { it.copy(topAnime = LoadState.Loading, topPaging = PagingUiState()) }
        } else {
            _uiState.update { it.copy(topPaging = it.topPaging.copy(isLoadingMore = true, error = null)) }
        }

        val offset = if (reset) 0 else (currentState.topAnime as? LoadState.Ready)?.data.orEmpty().size
        topLoadJob = viewModelScope.launch {
            runCatching { repository.getTopAnime(offset = offset, limit = PAGE_SIZE) }
                .onSuccess { animes ->
                    _uiState.update { state ->
                        val existing = if (reset) emptyList() else (state.topAnime as? LoadState.Ready)?.data.orEmpty()
                        val merged = (existing + animes).distinctBy { it.id }
                        state.copy(
                            topAnime = LoadState.Ready(merged),
                            topPaging = PagingUiState(
                                isLoadingMore = false,
                                canLoadMore = animes.size >= PAGE_SIZE && merged.size > existing.size,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        if (reset) {
                            state.copy(topAnime = LoadState.Error(throwable.userMessage()), topPaging = PagingUiState())
                        } else {
                            state.copy(
                                topPaging = state.topPaging.copy(
                                    isLoadingMore = false,
                                    canLoadMore = true,
                                    error = throwable.userMessage(),
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun loadSchedule() {
        scheduleLoadJob?.cancel()
        _uiState.update { it.copy(schedule = LoadState.Loading) }
        scheduleLoadJob = viewModelScope.launch {
            runCatching { repository.getSchedule() }
                .onSuccess { schedule -> _uiState.update { it.copy(schedule = LoadState.Ready(schedule)) } }
                .onFailure { throwable -> _uiState.update { it.copy(schedule = LoadState.Error(throwable.userMessage())) } }
        }
    }

    private fun loadHistory() {
        historyLoadJob?.cancel()
        _uiState.update { it.copy(historyAnime = LoadState.Loading) }
        historyLoadJob = viewModelScope.launch {
            val canUseRemoteHistory = !_uiState.value.forcedOfflineMode && _uiState.value.auth.profile != null
            val remoteHistoryResult = if (canUseRemoteHistory) {
                runCatching { fetchWatchHistoryPages() }
            } else {
                Result.failure(IllegalStateException("Remote history is not available"))
            }
            val remoteHistory = remoteHistoryResult.getOrDefault(emptyList())
            remoteHistory.forEach { remote ->
                playbackProgressStorage.saveIfNewer(remote)
            }
            val localHistory = playbackProgressStorage.readAll()
            val remoteByEpisode = remoteHistory.associateBy { it.syncEpisodeKey() }
            if (canUseRemoteHistory) {
                localHistory
                    .filter { local -> local.videoId > 0L && local.isNewerThan(remoteByEpisode[local.syncEpisodeKey()]) }
                    .forEach { local -> runCatching { repository.saveWatchProgress(local) } }
            }

            val history = if (remoteHistoryResult.isSuccess) {
                remoteHistory.latestByAnime()
            } else {
                latestPlaybackProgressByAnime()
            }
            val animes = history.map { progress ->
                runCatching { repository.getAnime(progress.animeId).toAnimeSummary() }
                    .getOrElse { progress.toAnimeSummary() }
            }
            _uiState.update { it.copy(historyAnime = LoadState.Ready(animes.distinctBy { anime -> anime.id })) }
        }
    }

    private fun LoadState<List<Anime>>.updatedWithLocalHistory(): LoadState<List<Anime>> {
        val latestHistory = latestPlaybackProgressByAnime()
        if (latestHistory.isEmpty()) return LoadState.Ready(emptyList())

        val existingById = (this as? LoadState.Ready)?.data.orEmpty().associateBy { it.id }
        val animes = latestHistory.map { progress ->
            existingById[progress.animeId] ?: progress.toAnimeSummary()
        }
        return LoadState.Ready(animes.distinctBy { it.id })
    }

    private fun latestPlaybackProgressByAnime(): List<PlaybackProgress> {
        return playbackProgressStorage.readAll().latestByAnime()
    }

    private fun List<PlaybackProgress>.latestByAnime(): List<PlaybackProgress> {
        return this
            .groupBy { it.animeId }
            .values
            .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
            .sortedByDescending { it.updatedAtMs }
    }

    private suspend fun fetchWatchHistoryPages(): List<PlaybackProgress> {
        val pageSize = 100
        val maxPages = 10
        val history = mutableListOf<PlaybackProgress>()
        repeat(maxPages) { page ->
            val offset = page * pageSize
            val pageEntries = repository.getWatchHistory(limit = pageSize, offset = offset)
            history += pageEntries
            if (pageEntries.size < pageSize) return history
        }
        return history
    }

    private fun loadLibrary() {
        libraryLoadJob?.cancel()
        if (_uiState.value.forcedOfflineMode) {
            _uiState.update { it.copy(libraryAnime = LoadState.Ready(emptyList())) }
            return
        }
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(libraryAnime = LoadState.Ready(emptyList())) }
            return
        }

        _uiState.update { it.copy(libraryAnime = LoadState.Loading) }
        libraryLoadJob = viewModelScope.launch {
            runCatching { repository.getLibraryAnime() }
                .onSuccess { animes -> _uiState.update { it.copy(libraryAnime = LoadState.Ready(animes)) } }
                .onFailure { throwable -> _uiState.update { it.copy(libraryAnime = LoadState.Error(throwable.userMessage())) } }
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
                                message = active.message.ifBlank { "Загрузка" },
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
                    val currentAnimeId = (_uiState.value.details as? LoadState.Ready)?.data?.id
                    if (currentAnimeId != null) {
                        refreshCurrentDetailsFromOfflineCache(currentAnimeId)
                    }
                }
            }
        }
    }

    private fun refreshCurrentDetailsFromOfflineCache(animeId: Long) {
        val currentDetails = (_uiState.value.details as? LoadState.Ready)?.data
            ?.takeIf { it.id == animeId }
            ?: return
        viewModelScope.launch {
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (details, videos) ->
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(details),
                            videos = LoadState.Ready(videos),
                            playbackProgress = playbackProgressStorage.read(animeId),
                            playbackHistory = playbackProgressStorage.readAnimeHistory(animeId),
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(currentDetails),
                            videos = LoadState.Ready(emptyList()),
                            playbackProgress = playbackProgressStorage.read(animeId),
                            playbackHistory = playbackProgressStorage.readAnimeHistory(animeId),
                        )
                    }
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
        val cachedProfile = repository.cachedProfile()
        _uiState.update { it.copy(auth = AuthUiState(profile = cachedProfile, loading = true)) }
        viewModelScope.launch {
            runCatching { repository.restoreProfile() }
            .onSuccess { profile ->
                val activeProfile = profile ?: cachedProfile
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
                        repository.logout()
                        knownAnimeRatings.clear()
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
                    _uiState.update { it.copy(animeMark = LoadState.Ready(mark)) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(animeMark = LoadState.Error(throwable.userMessage())) }
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
            val trailers = runCatching { repository.getAnimeTrailers(animeId) }.getOrDefault(emptyList())
            val recommendations = runCatching { repository.getAnimeRecommendations(animeId) }.getOrDefault(emptyList())
            val currentUserRating = (_uiState.value.details as? LoadState.Ready)
                ?.data
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
            val details = (_uiState.value.details as? LoadState.Ready)?.data
            val videos = (_uiState.value.videos as? LoadState.Ready)?.data.orEmpty()
            val subscriptions = canonicalizeVideoSubscriptionsForVideos(
                subscriptions = serverSubscriptions,
                videos = videos.filter { it.animeId == animeId },
                title = details?.title.orEmpty(),
                posterUrl = details?.posterUrl.orEmpty(),
            )
            _uiState.update { state ->
                if ((state.route as? AppRoute.Details)?.animeId == animeId ||
                    (state.details as? LoadState.Ready)?.data?.id == animeId
                ) {
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            AnimeDetailsExtras(
                                comments = comments,
                                commentsPaging = PagingUiState(
                                    isLoadingMore = false,
                                    canLoadMore = comments.size >= COMMENTS_PAGE_SIZE,
                                ),
                                trailers = trailers,
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
        }
    }

    fun loadMoreAnimeComments() {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        val extras = (_uiState.value.detailsExtras as? LoadState.Ready)?.data ?: return
        if (extras.commentsPaging.isLoadingMore || !extras.commentsPaging.canLoadMore) return

        val offset = extras.comments.size
        commentsLoadJob?.cancel()
        _uiState.update { state ->
            val current = (state.detailsExtras as? LoadState.Ready)?.data ?: return@update state
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
                    val current = (state.detailsExtras as? LoadState.Ready)?.data ?: return@update state
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
            }.onFailure { throwable ->
                _uiState.update { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@update state
                    val current = (state.detailsExtras as? LoadState.Ready)?.data ?: return@update state
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
            }
        }
    }

    fun setAnimeRating(rating: Int?) {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Нужно войти в аккаунт")) }
            return
        }
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
            val extras = (state.detailsExtras as? LoadState.Ready)?.data
            state.copy(
                details = details,
                detailsExtras = if (extras != null) {
                    LoadState.Ready(extras.copy(rating = extras.rating.copy(userRating = optimisticRating)))
                } else {
                    state.detailsExtras
                },
            )
        }
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
                        val extras = (state.detailsExtras as? LoadState.Ready)?.data
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
                }
                .onFailure { throwable ->
                    if (hadPreviousKnownRating) {
                        knownAnimeRatings[animeId] = previousKnownRating
                    } else {
                        knownAnimeRatings.remove(animeId)
                    }
                    _uiState.update { state ->
                        state.copy(
                            details = previousDetails,
                            detailsExtras = previousExtras,
                            auth = state.auth.copy(error = throwable.userMessage()),
                        )
                    }
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
        knownAnimeRatings.putAll(animeRatingStateStorage.read(userId))
    }

    private fun persistKnownAnimeRatings() {
        val userId = _uiState.value.auth.profile?.id?.takeIf { it > 0L }
            ?: authStorage.readProfile()?.id?.takeIf { it > 0L }
            ?: return
        animeRatingStateStorage.save(userId, knownAnimeRatings)
    }

    private fun restoreVideoSubscriptionHints(profile: UserProfile?) {
        videoSubscriptionHints.clear()
        val userId = profile?.id?.takeIf { it > 0L } ?: return
        videoSubscriptionHints += videoSubscriptionHintStorage.read(userId)
    }

    private fun persistVideoSubscriptionHints() {
        val userId = _uiState.value.auth.profile?.id?.takeIf { it > 0L }
            ?: authStorage.readProfile()?.id?.takeIf { it > 0L }
            ?: return
        videoSubscriptionHintStorage.save(userId, videoSubscriptionHints)
    }

    private fun rememberVideoSubscriptionHints(
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) {
        val hints = videos
            .mapNotNull { video ->
                val voiceKey = video.matchingDubbingKey
                    .ifBlank { video.matchingVoiceKey }
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
                    _uiState.update { it.copy(globalSubscriptions = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    fun refreshVideoSubscriptions() {
        syncVideoSubscriptionsFromSite()
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

                val hintedSubscriptions = subscription.resolveVoiceHints()
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

    private fun VideoSubscription.resolveSinglePlayerVoice(videos: List<VideoVariant>): VideoVariant? {
        val candidates = videos.filter { video ->
            matchesVideoPlayer(video)
        }.let { playerVideos ->
            val voiceKey = matchingVoiceKey
            if (voiceKey.isBlank()) {
                playerVideos
            } else {
                playerVideos.filter { it.matchingVoiceKey == voiceKey }
            }
        }
        return candidates
            .distinctBy { it.matchingVoiceKey }
            .singleOrNull()
    }

    private fun VideoSubscription.resolveVoiceHints(): List<VideoSubscriptionHint> {
        if (animeId <= 0L) return emptyList()
        val explicitVoiceKey = matchingVoiceKey
        return videoSubscriptionHints
            .filter { hint ->
                hint.animeId == animeId &&
                    (explicitVoiceKey.isBlank() || hint.voiceKey == explicitVoiceKey) &&
                    (
                        (playerId > 0L && hint.playerId == playerId) ||
                            (matchingPlayerKey.isNotBlank() && hint.playerKey == matchingPlayerKey)
                    )
            }
            .distinctBy { it.voiceKey }
    }

    private fun VideoSubscription.withResolvedVoice(video: VideoVariant): VideoSubscription {
        return copy(
            player = video.player.ifBlank { player },
            dubbing = video.dubbing.ifBlank { dubbing },
            playerId = video.playerId.takeIf { it > 0L } ?: playerId,
            videoId = video.id.takeIf { it > 0L } ?: videoId,
        )
    }

    private fun VideoSubscription.withResolvedHint(hint: VideoSubscriptionHint): VideoSubscription {
        return copy(
            title = title.ifBlank { hint.title },
            posterUrl = posterUrl.ifBlank { hint.posterUrl },
            dubbing = hint.voiceTitle.ifBlank { dubbing },
            playerId = playerId.takeIf { it > 0L } ?: hint.playerId,
        )
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

    private fun canonicalizeVideoSubscriptionsForVideos(
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ): List<VideoSubscription> {
        val animeId = videos.firstOrNull()?.animeId?.takeIf { it > 0L } ?: return subscriptions
        val videoById = videos
            .filter { it.id > 0L }
            .associateBy { it.id }
        val availableVoiceKeys = videos
            .map { it.matchingDubbingKey.ifBlank { it.matchingVoiceKey } }
            .filter { it.isNotBlank() }
            .toSet()
        if (availableVoiceKeys.isEmpty()) return subscriptions

        val activeVoiceKeys = linkedSetOf<String>()
        subscriptions
            .filter { it.animeId == animeId }
            .forEach { subscription ->
                val directVideoVoiceKey = videoById[subscription.videoId]
                    ?.let { video -> video.matchingDubbingKey.ifBlank { video.matchingVoiceKey } }
                    .orEmpty()
                val singlePlayerVoiceKey = videos
                    .filter { subscription.matchesVideoPlayer(it) }
                    .distinctBy { video -> video.matchingDubbingKey.ifBlank { video.matchingVoiceKey } }
                    .singleOrNull()
                    ?.let { video -> video.matchingDubbingKey.ifBlank { video.matchingVoiceKey } }
                    .orEmpty()
                val hintedVoiceKeys = subscription.resolveVoiceHints()
                    .map { it.voiceKey }
                    .filter { it in availableVoiceKeys }
                when {
                    directVideoVoiceKey in availableVoiceKeys -> activeVoiceKeys += directVideoVoiceKey
                    subscription.matchingVoiceKey in availableVoiceKeys -> activeVoiceKeys += subscription.matchingVoiceKey
                    singlePlayerVoiceKey in availableVoiceKeys -> activeVoiceKeys += singlePlayerVoiceKey
                }
                activeVoiceKeys += hintedVoiceKeys
            }

        if (activeVoiceKeys.isEmpty()) return subscriptions
        var result = subscriptions
        activeVoiceKeys.forEach { voiceKey ->
            val targets = videos
                .filter { video -> video.matchingDubbingKey.ifBlank { video.matchingVoiceKey } == voiceKey && video.id > 0L }
                .distinctBy { it.matchingSourceKey }
            if (targets.isNotEmpty()) {
                result = result.withVoiceSubscriptionState(
                    animeId = animeId,
                    voiceKey = voiceKey,
                    videos = targets,
                    subscribed = true,
                    title = title,
                    posterUrl = posterUrl,
                )
            }
        }
        return result
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
            .filter { video -> video.matchingDubbingKey.ifBlank { video.matchingVoiceKey } in targetVoiceKeys }
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
                ?: (state.details as? LoadState.Ready)?.data?.id
            val detailsExtras = (state.detailsExtras as? LoadState.Ready)?.data
            val details = (state.details as? LoadState.Ready)?.data
            val detailsVideos = (state.videos as? LoadState.Ready)?.data
                .orEmpty()
                .filter { it.animeId == detailsAnimeId }
            val detailsSubscriptions = canonicalizeVideoSubscriptionsForVideos(
                subscriptions = subscriptions,
                videos = detailsVideos,
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
    }

    fun addAnimeComment(text: String) {
        if (_uiState.value.forcedOfflineMode) return
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Нужно войти в аккаунт")) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.addAnimeComment(animeId, text) }
                .onSuccess { comment ->
                    if (comment == null) return@onSuccess
                    _uiState.update { state ->
                        val extras = (state.detailsExtras as? LoadState.Ready)?.data ?: AnimeDetailsExtras()
                        state.copy(
                            detailsExtras = LoadState.Ready(
                                extras.copy(
                                    comments = (listOf(comment) + extras.comments).distinctBy { it.id },
                                ),
                            ),
                        )
                    }
                }
                .onFailure { throwable -> _uiState.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) } }
        }
    }

    fun toggleVideoSubscription(video: VideoVariant) {
        if (_uiState.value.forcedOfflineMode) return
        val details = (_uiState.value.details as? LoadState.Ready)?.data
        if (details?.isFullyReleased() == true) return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return
        }
        viewModelScope.launch {
            val current = (_uiState.value.detailsExtras as? LoadState.Ready)?.data ?: AnimeDetailsExtras()
            val allVideos = (_uiState.value.videos as? LoadState.Ready)?.data.orEmpty()
                val targetVoiceKey = video.matchingDubbingKey.ifBlank { video.matchingVoiceKey }
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
                val extras = (state.detailsExtras as? LoadState.Ready)?.data ?: current
                state.copy(detailsExtras = LoadState.Ready(extras.copy(subscriptions = optimisticSubscriptions)))
            }

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
                }
                .onFailure { throwable ->
                    if (shouldSubscribe) {
                        forgetVideoSubscriptionHints(video.animeId, targetVoiceKey)
                    } else {
                        rememberVideoSubscriptionHints(sameVoiceVideos, title, posterUrl)
                    }
                    _uiState.update { state ->
                        val extras = (state.detailsExtras as? LoadState.Ready)?.data ?: current
                        state.copy(
                            detailsExtras = LoadState.Ready(extras.copy(subscriptions = current.subscriptions)),
                            auth = state.auth.copy(error = throwable.userMessage()),
                        )
                    }
                }
        }
    }

    fun unsubscribeVideoSubscription(subscription: VideoSubscription) {
        if (_uiState.value.forcedOfflineMode || _uiState.value.auth.profile == null) return
        val animeId = subscription.animeId.takeIf { it > 0L } ?: return
        val currentSubscriptions = (_uiState.value.globalSubscriptions as? LoadState.Ready)?.data.orEmpty()
        val targetVoiceKey = subscription.matchingVoiceKey.ifBlank {
            currentSubscriptions.firstOrNull { it.animeId == animeId && it.videoId == subscription.videoId }?.matchingVoiceKey.orEmpty()
        }
        val targetPlayerId = subscription.playerId.takeIf { it > 0L }
            ?: currentSubscriptions.firstOrNull { it.animeId == animeId && it.videoId == subscription.videoId }?.playerId?.takeIf { it > 0L }
        val targetPlayerKey = subscription.player.cleanVideoSourceLabel()
        if (targetVoiceKey.isBlank() && subscription.videoId <= 0L && targetPlayerId == null && targetPlayerKey.isBlank()) return
        if (targetVoiceKey.isNotBlank()) {
            forgetVideoSubscriptionHints(animeId, targetVoiceKey)
        }

        val directVideoIds = currentSubscriptions
            .filter { currentSubscription ->
                currentSubscription.videoId > 0L &&
                    currentSubscription.animeId == animeId &&
                    (
                        currentSubscription.videoId == subscription.videoId ||
                            (targetVoiceKey.isNotBlank() && currentSubscription.matchesAnimeVoice(animeId, targetVoiceKey))
                    )
            }
            .map { it.videoId }
            .ifEmpty { listOf(subscription.videoId).filter { it > 0L } }
            .distinct()

        updateGlobalSubscriptions(
            currentSubscriptions.filterNot { currentSubscription ->
                currentSubscription.videoId in directVideoIds ||
                    (targetVoiceKey.isNotBlank() && currentSubscription.matchesAnimeVoice(animeId, targetVoiceKey)) ||
                    (
                        targetVoiceKey.isBlank() &&
                            currentSubscription.animeId == animeId &&
                            (
                                (targetPlayerId != null && currentSubscription.playerId == targetPlayerId) ||
                                    (
                                        targetPlayerId == null &&
                                            targetPlayerKey.isNotBlank() &&
                                            currentSubscription.player.cleanVideoSourceLabel()
                                                .equals(targetPlayerKey, ignoreCase = true)
                                    )
                            )
                    )
            },
        )

        viewModelScope.launch {
            runCatching {
                val loadedVideos = if (
                    targetVoiceKey.isNotBlank() ||
                    targetPlayerId != null ||
                    targetPlayerKey.isNotBlank()
                ) {
                    (_uiState.value.videos as? LoadState.Ready)
                        ?.data
                        ?.takeIf { videos -> videos.any { it.animeId == animeId } }
                        ?: repository.getVideos(animeId)
                } else {
                    emptyList()
                }
                val targetVideoIds = (
                    directVideoIds + loadedVideos
                        .filter {
                            it.animeId == animeId &&
                                when {
                                    targetVoiceKey.isNotBlank() -> it.matchingDubbingKey.ifBlank { it.matchingVoiceKey } == targetVoiceKey
                                    targetPlayerId != null -> it.playerId == targetPlayerId
                                    targetPlayerKey.isNotBlank() -> it.player.cleanVideoSourceLabel()
                                        .equals(targetPlayerKey, ignoreCase = true)
                                    else -> false
                                }
                        }
                        .distinctBy { it.matchingSourceKey }
                        .map { it.id }
                        .filter { it > 0L }
                    ).distinct()
                if (targetVideoIds.isEmpty()) throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)

                applySubscriptionStateToVideoIds(targetVideoIds, subscribed = false)

                loadResolvedVideoSubscriptions().filterNot { currentSubscription ->
                    currentSubscription.videoId in targetVideoIds ||
                        (targetVoiceKey.isNotBlank() && currentSubscription.matchesAnimeVoice(animeId, targetVoiceKey)) ||
                        (
                            targetVoiceKey.isBlank() &&
                                currentSubscription.animeId == animeId &&
                                (
                                    (targetPlayerId != null && currentSubscription.playerId == targetPlayerId) ||
                                        (
                                            targetPlayerId == null &&
                                                targetPlayerKey.isNotBlank() &&
                                                currentSubscription.player.cleanVideoSourceLabel()
                                                    .equals(targetPlayerKey, ignoreCase = true)
                                        )
                                )
                        )
                }
            }
                .onSuccess { subscriptions ->
                    updateGlobalSubscriptions(subscriptions)
                }
                .onFailure { throwable ->
                    if (targetVoiceKey.isNotBlank()) {
                        val videosForHint = (_uiState.value.videos as? LoadState.Ready)
                            ?.data
                            ?.filter { it.animeId == animeId && it.matchingDubbingKey.ifBlank { it.matchingVoiceKey } == targetVoiceKey }
                            .orEmpty()
                        rememberVideoSubscriptionHints(
                            videos = videosForHint,
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
            .takeIf { videos -> videos.any { it.animeId == animeId && it.matchingDubbingKey.ifBlank { it.matchingVoiceKey } == voiceKey } }
            ?: repository.getVideos(animeId)
        return loadedVideos
            .filter { it.animeId == animeId && it.matchingDubbingKey.ifBlank { it.matchingVoiceKey } == voiceKey && it.id > 0L }
            .distinctBy { it.matchingSourceKey }
    }

    private suspend fun syncPlaybackProgressForAnime(animeId: Long): PlaybackProgress? {
        var local = playbackProgressStorage.read(animeId)
        if (_uiState.value.forcedOfflineMode) return local
        if (_uiState.value.auth.profile == null) return local

        val remoteEntries = runCatching { fetchWatchHistoryPages() }
            .getOrDefault(emptyList())
            .filter { it.animeId == animeId }
        remoteEntries.forEach { remote ->
            playbackProgressStorage.saveIfNewer(remote)
        }
        val remote = remoteEntries.maxByOrNull { it.updatedAtMs }
        local = playbackProgressStorage.read(animeId)
        if (local != null && local.isNewerThan(remote) && local.videoId > 0L) {
            syncPlaybackProgressToSite(local)
        }
        return local
    }

    private fun syncPlaybackHistoryFromSite() {
        if (_uiState.value.forcedOfflineMode) return
        if (_uiState.value.auth.profile == null) return
        playbackHistorySyncJob?.cancel()
        playbackHistorySyncJob = viewModelScope.launch {
            val localEntries = playbackProgressStorage.readAll()
            val remoteEntries = runCatching { fetchWatchHistoryPages() }
                .getOrDefault(emptyList())

            remoteEntries.forEach { remote ->
                playbackProgressStorage.saveIfNewer(remote)
            }
            val remoteByEpisode = remoteEntries.associateBy { it.syncEpisodeKey() }
            localEntries
                .filter { local -> local.videoId > 0L && local.isNewerThan(remoteByEpisode[local.syncEpisodeKey()]) }
                .forEach { local -> runCatching { repository.saveWatchProgress(local) } }

            val currentAnimeId = (_uiState.value.details as? LoadState.Ready)?.data?.id
            if (currentAnimeId != null) {
                _uiState.update {
                    it.copy(
                        playbackProgress = playbackProgressStorage.read(currentAnimeId),
                        playbackHistory = playbackProgressStorage.readAnimeHistory(currentAnimeId),
                    )
                }
            }
            if (_uiState.value.homeSection == BrowseSection.History) {
                loadHistory()
            }
        }
    }

    private fun syncPlaybackProgressToSite(progress: PlaybackProgress) {
        if (_uiState.value.forcedOfflineMode) return
        if (_uiState.value.auth.profile == null || progress.videoId <= 0L) return
        playbackProgressSyncJobs[progress.videoId]?.cancel()
        playbackProgressSyncJobs[progress.videoId] = viewModelScope.launch {
            runCatching { repository.saveWatchProgress(progress) }
            playbackProgressSyncJobs.remove(progress.videoId)
        }
    }

    private fun searchNow(query: String, reset: Boolean = true) {
        val currentState = _uiState.value
        val paging = currentState.searchPaging
        if (!reset && (paging.isLoadingMore || !paging.canLoadMore)) return

        if (reset) {
            searchLoadJob?.cancel()
            _uiState.update {
                it.copy(
                    searchResults = LoadState.Loading,
                    searchPaging = PagingUiState(canLoadMore = query.isNotBlank()),
                )
            }
        } else {
            _uiState.update {
                it.copy(searchPaging = it.searchPaging.copy(isLoadingMore = true, error = null))
            }
        }

        val offset = if (reset) {
            0
        } else {
            (currentState.searchResults as? LoadState.Ready)?.data.orEmpty().size
        }

        searchLoadJob = viewModelScope.launch {

            val filters = _uiState.value.filters
            runCatching { repository.search(query, filters, offset = offset, limit = PAGE_SIZE) }
                .onSuccess { animes ->
                    _uiState.update { state ->
                        if (state.searchQuery == query && state.filters == filters) {
                            val existing = if (reset) emptyList() else (state.searchResults as? LoadState.Ready)?.data.orEmpty()
                            val merged = (existing + animes).distinctBy { it.id }
                            state.copy(
                                searchResults = LoadState.Ready(merged),
                                forcedOfflineMode = repository.isOfflineFallbackActive(),
                                searchPaging = PagingUiState(
                                    isLoadingMore = false,
                                    canLoadMore = animes.size >= PAGE_SIZE && merged.size > existing.size,
                                ),
                            )
                        } else {
                            state
                        }
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        if (reset) {
                            state.copy(
                                searchResults = LoadState.Error(throwable.userMessage()),
                                forcedOfflineMode = false,
                                searchPaging = PagingUiState(canLoadMore = true),
                            )
                        } else {
                            state.copy(
                                searchPaging = state.searchPaging.copy(
                                    isLoadingMore = false,
                                    canLoadMore = true,
                                    error = throwable.userMessage(),
                                ),
                            )
                        }
                    }
                }
        }
    }

    private fun reloadBrowse() {
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
        excludedSourceIds: Set<Long>,
    ): List<VideoVariant> {
        val pool = allVideos.ifEmpty { listOf(requested) }
        val sameEpisode = pool.filter { it.isSameEpisodeAs(requested) }
            .ifEmpty { listOf(requested) }
        val sameVoice = sameEpisode.filter { it.hasSameVoiceAs(requested) }
        val otherVoices = sameEpisode.filterNot { candidate ->
            sameVoice.any { it.id == candidate.id }
        }

        return (sameVoice + otherVoices)
            .filterNot { it.id in excludedSourceIds }
            .sortedWith(
                compareBy<VideoVariant> { if (it.hasSameVoiceAs(requested)) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenByDescending { it.estimatedSourceMaxVideoHeight() }
                    .thenBy { it.index }
                    .thenBy { if (it.id == requested.id) 0 else 1 }
                    .thenBy { it.id },
            )
    }

    private suspend fun resolvePlaybackWithCache(
        requested: VideoVariant,
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
    ): ResolvedPlayback {
        if (requested.localPlaybackUrl.isNotBlank()) {
            return ResolvedPlayback(
                video = requested,
                stream = repository.resolveVideoStream(requested),
            )
        }

        val sameVoiceCandidates = candidates
            .filter { it.hasSameVoiceAs(requested) }
            .ifEmpty { candidates }
        val cacheKey = requested.playbackCacheKey()
        val cachedSource = playbackSourceCache[cacheKey]

        if (cachedSource != null) {
            val shouldTrustCache = cachedSource.maxVideoHeight != null &&
                sameVoiceCandidates.none { candidate ->
                    candidate.sourceProviderKey != cachedSource.providerKey &&
                        candidate.estimatedSourceMaxVideoHeight() > cachedSource.maxVideoHeight
                }
            val cachedCandidates = sameVoiceCandidates.filter { it.sourceProviderKey == cachedSource.providerKey }
            if (shouldTrustCache && cachedCandidates.isNotEmpty()) {
                runCatching { repository.resolveBestPlaybackSource(cachedCandidates, preferredQuality) }
                    .onSuccess { return it }
                playbackSourceCache.remove(cacheKey)
            } else {
                playbackSourceCache.remove(cacheKey)
            }
        }

        val primaryResult = runCatching { repository.resolveBestPlaybackSource(sameVoiceCandidates, preferredQuality) }
        primaryResult.onSuccess { return it }

        val sameVoiceIds = sameVoiceCandidates.mapTo(mutableSetOf(), VideoVariant::id)
        val fallbackCandidates = candidates.filterNot { it.id in sameVoiceIds }
        if (fallbackCandidates.isNotEmpty()) {
            return repository.resolveBestPlaybackSource(fallbackCandidates, preferredQuality)
        }

        throw primaryResult.exceptionOrNull() ?: IllegalStateException("Не удалось выбрать источник видео")
    }

    private fun removeCachedPlaybackSource(video: VideoVariant) {
        val cacheKey = video.playbackCacheKey()
        if (playbackSourceCache[cacheKey]?.providerKey == video.sourceProviderKey) {
            playbackSourceCache.remove(cacheKey)
        }
    }

    private fun AnimeDetails.toAnimeSummary(): Anime {
        return Anime(
            id = id,
            title = title,
            description = description,
            posterUrl = posterUrl,
            animeUrl = "",
            year = year,
            rating = rating,
            userRating = userRating,
            views = views,
            status = status,
            type = type,
            genres = genres,
            blockedIn = blockedIn,
        )
    }

    private fun PlaybackProgress.toAnimeSummary(): Anime {
        return Anime(
            id = animeId,
            title = animeTitle.ifBlank { "Anime #$animeId" },
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = null,
            rating = null,
            views = 0L,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private companion object {
        const val PAGE_SIZE = 36
        const val COMMENTS_PAGE_SIZE = 20
        const val OFFLINE_RECOVERY_CHECK_INTERVAL_MS = 30_000L
        val ALL_USER_MARK_FILTERS = setOf("0", "1", "2", "3", "4", "5")
    }
}
data class YummyDroidUiState(
    val route: AppRoute = AppRoute.Home,
    val navigationBackStack: List<NavigationEntry> = emptyList(),
    val siteBaseUrl: String = "https://old.yummyani.me/",
    val homeSection: BrowseSection = BrowseSection.Catalog,
    val featured: LoadState<List<Anime>> = LoadState.Loading,
    val featuredPaging: PagingUiState = PagingUiState(),
    val topAnime: LoadState<List<Anime>> = LoadState.Loading,
    val topPaging: PagingUiState = PagingUiState(),
    val schedule: LoadState<List<ScheduleAnime>> = LoadState.Loading,
    val historyAnime: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val libraryAnime: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val offlineEntries: LoadState<List<OfflineAnimeEntry>> = LoadState.Ready(emptyList()),
    val downloadQueue: DownloadQueueSnapshot = DownloadQueueSnapshot(),
    val offlineDownload: OfflineDownloadUiState = OfflineDownloadUiState(),
    val forcedOfflineMode: Boolean = false,
    val searchQuery: String = "",
    val searchResults: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val searchPaging: PagingUiState = PagingUiState(canLoadMore = false),
    val filters: BrowseFilters = BrowseFilters(),
    val filterCatalog: LoadState<FilterCatalog> = LoadState.Loading,
    val details: LoadState<AnimeDetails> = LoadState.Loading,
    val detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Loading,
    val globalSubscriptions: LoadState<List<VideoSubscription>> = LoadState.Ready(emptyList()),
    val videos: LoadState<List<VideoVariant>> = LoadState.Loading,
    val selectedVideoGroup: String? = null,
    val playerStream: LoadState<ResolvedVideoStream> = LoadState.Loading,
    val auth: AuthUiState = AuthUiState(),
    val animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    val settings: AppSettings = AppSettings(),
    val playbackProgress: PlaybackProgress? = null,
    val playbackHistory: List<PlaybackProgress> = emptyList(),
    val updateState: LoadState<AppUpdateInfo?> = LoadState.Ready(null),
) {
    val canNavigateBack: Boolean
        get() = route != AppRoute.Home || navigationBackStack.isNotEmpty()
            || homeSection != BrowseSection.Catalog || searchQuery.isNotBlank()
}

data class NavigationEntry(
    val route: AppRoute,
    val homeSection: BrowseSection,
    val filters: BrowseFilters,
    val searchQuery: String,
    val selectedVideoGroup: String?,
)

data class PagingUiState(
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

data class AuthUiState(
    val profile: UserProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val captchaRequestNonce: Long = 0L,
)

data class OfflineDownloadUiState(
    val videoId: Long? = null,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
)

private val DownloadTaskState.title: String
    get() = when (this) {
        DownloadTaskState.Queued -> "В очереди"
        DownloadTaskState.Running -> "Загрузка"
        DownloadTaskState.Paused -> "Пауза"
        DownloadTaskState.Added -> "Добавлено"
        DownloadTaskState.Completed -> "Скачано"
        DownloadTaskState.Failed -> "Ошибка"
        DownloadTaskState.Cancelled -> "Отменено"
    }

enum class BrowseSection(
    val title: String,
) {
    Catalog("Каталог"),
    Schedule("Расписание"),
    History("История"),
    Downloads("Загрузки"),
}

data class AnimeDetailsExtras(
    val comments: List<AnimeComment> = emptyList(),
    val commentsPaging: PagingUiState = PagingUiState(),
    val trailers: List<AnimeTrailer> = emptyList(),
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
    ) : AppRoute
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

private fun YummyDroidUiState.navigationEntry(): NavigationEntry {
    return NavigationEntry(
        route = route,
        homeSection = homeSection,
        filters = filters,
        searchQuery = searchQuery,
        selectedVideoGroup = selectedVideoGroup,
    )
}

private fun YummyDroidUiState.navigationStackAfterOptionalPush(push: Boolean): List<NavigationEntry> {
    return if (push) {
        navigationBackStack.withNavigationEntry(navigationEntry())
    } else {
        navigationBackStack
    }
}

private fun List<NavigationEntry>.withNavigationEntry(entry: NavigationEntry): List<NavigationEntry> {
    return if (lastOrNull() == entry) {
        this
    } else {
        (this + entry).takeLast(MAX_NAVIGATION_STACK)
    }
}

private fun Throwable.userMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "Не удалось загрузить данные"
}

private fun AppUpdateInfo.isNewerThan(currentVersion: String): Boolean {
    val latest = normalizedVersion.versionParts()
    val current = currentVersion.versionParts()
    val maxSize = maxOf(latest.size, current.size)
    repeat(maxSize) { index ->
        val left = latest.getOrElse(index) { 0 }
        val right = current.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun String.versionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
        .ifEmpty { listOf(0) }
}

private fun AnimeDetails.isFullyReleased(): Boolean {
    val normalizedStatus = status
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')

    if (
        normalizedStatus.contains("онго") ||
        normalizedStatus.contains("ongoing") ||
        normalizedStatus.contains("анонс") ||
        normalizedStatus.contains("не выш")
    ) {
        return false
    }

    return listOf(
        "вышел",
        "вышло",
        "заверш",
        "released",
        "completed",
        "complete",
        "finished",
    ).any(normalizedStatus::contains)
}

private fun VideoVariant.isFinalEpisodeFor(details: AnimeDetails, allVideos: List<VideoVariant>): Boolean {
    val currentOrder = episodeOrderValue()
    val expectedEpisodeCount = details.episodeCount.takeIf { it > 0 }
    if (expectedEpisodeCount != null && currentOrder != null) {
        return currentOrder >= expectedEpisodeCount.toDouble()
    }

    val lastVideo = allVideos
        .filter { it.animeId == animeId }
        .ifEmpty { listOf(this) }
        .maxWithOrNull(
            compareBy<VideoVariant> { it.episodeOrderValue() ?: 0.0 }
                .thenBy { it.index }
                .thenBy { it.id },
        )
    return lastVideo?.isSameEpisodeAs(this) == true
}

private fun PlaybackProgress.isNewerThan(other: PlaybackProgress?): Boolean {
    return updatedAtMs > (other?.updatedAtMs ?: Long.MIN_VALUE)
}

private fun PlaybackProgress.syncEpisodeKey(): String {
    return when {
        videoId > 0L -> "video:$videoId"
        episode.isNotBlank() -> "episode:${episode.trim()}"
        groupKey.isNotBlank() -> "group:$groupKey"
        else -> "anime:$animeId"
    }
}

private data class PlaybackCacheKey(
    val animeId: Long,
    val voiceKey: String,
)

private data class PlaybackSourceCacheEntry(
    val providerKey: String,
    val maxVideoHeight: Int?,
)

private fun VideoVariant.playbackCacheKey(): PlaybackCacheKey {
    return PlaybackCacheKey(animeId = animeId, voiceKey = matchingVoiceKey)
}

private val VideoVariant.sourceProviderKey: String
    get() = listOf(
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceProviderFingerprint(),
    ).filter { it.isNotBlank() }.joinToString("|")

private fun String.sourceProviderFingerprint(): String {
    val value = trim().lowercase(Locale.ROOT)
    val host = Regex("""^https?://([^/?#]+)""").find(value)?.groupValues?.getOrNull(1).orEmpty()
    val path = Regex("""^https?://[^/]+/([^?#]+)""").find(value)?.groupValues?.getOrNull(1)
        ?.substringBefore('/')
        .orEmpty()
    return listOf(host, path).filter { it.isNotBlank() }.joinToString("/")
}

private fun VideoVariant.estimatedSourceMaxVideoHeight(): Int {
    val lowerPlayer = player.lowercase(Locale.ROOT)
    val lowerUrl = url.lowercase(Locale.ROOT)
    return when {
        "cvh" in lowerPlayer || "iframecvh" in lowerUrl -> 1080
        "alloha" in lowerPlayer || "alloha" in lowerUrl -> 1080
        "aksor" in lowerPlayer || "aksor" in lowerUrl -> 1080
        else -> Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}
