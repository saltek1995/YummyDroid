package me.yummyani.app

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
import me.yummyani.app.data.Anime
import me.yummyani.app.data.AnimeDetails
import me.yummyani.app.data.AnimeSort
import me.yummyani.app.data.AppSettings
import me.yummyani.app.data.AppSettingsStorage
import me.yummyani.app.data.AuthStorage
import me.yummyani.app.data.BrowseFilters
import me.yummyani.app.data.CaptchaRequiredException
import me.yummyani.app.data.FilterCatalog
import me.yummyani.app.data.FilterOption
import me.yummyani.app.data.ResolvedPlayback
import me.yummyani.app.data.ResolvedVideoStream
import me.yummyani.app.data.SiteDomainResolver
import me.yummyani.app.data.UserAnimeListMark
import me.yummyani.app.data.UserAnimeMark
import me.yummyani.app.data.UserProfile
import me.yummyani.app.data.VideoVariant
import me.yummyani.app.data.YummyAnimeRepository
import me.yummyani.app.data.normalized

private const val MAX_NAVIGATION_STACK = 40

class YummyAniViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val settingsStorage = AppSettingsStorage(application)
    private val initialSettings = settingsStorage.read()
    private val siteDomainResolver = SiteDomainResolver(candidates = initialSettings.siteDomains)
    private val repository = YummyAnimeRepository(
        context = application,
        siteDomainResolver = siteDomainResolver,
        authStorage = AuthStorage(application),
    )
    private val _uiState = MutableStateFlow(YummyAniUiState(settings = initialSettings))
    val uiState: StateFlow<YummyAniUiState> = _uiState

    private var searchDebounceJob: Job? = null
    private var featuredLoadJob: Job? = null
    private var searchLoadJob: Job? = null
    private var playerLoadJob: Job? = null
    private var animeMarkJob: Job? = null
    private var failedPlaybackSourceIds: Set<Long> = emptySet()
    private val playbackSourceCache = mutableMapOf<PlaybackCacheKey, PlaybackSourceCacheEntry>()
    private val autoAnimeMarkJobs = mutableMapOf<Long, Job>()

    init {
        loadHome()
        loadFilterCatalog()
        refreshSiteBaseUrl()
        restoreProfile()
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

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
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
        _uiState.update { state ->
            state.copy(
                filters = filters,
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
            )
        }
        reloadBrowse()
    }

    fun resetFilters() {
        _uiState.update { state ->
            state.copy(
                filters = BrowseFilters(),
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
            )
        }
        reloadBrowse()
    }

    fun updateSettings(settings: AppSettings) {
        val normalizedSettings = settings.normalized()
        settingsStorage.save(normalizedSettings)
        siteDomainResolver.updateCandidates(normalizedSettings.siteDomains)
        _uiState.update {
            it.copy(
                settings = normalizedSettings,
                siteBaseUrl = siteDomainResolver.cachedOrDefaultBaseUrl(),
            )
        }
        refreshSiteBaseUrl()
    }

    fun filterByGenre(genre: FilterOption) {
        applyDetailsFilter { it.copy(genres = setOf(genre.value)) }
    }

    fun filterByYear(year: Int) {
        applyDetailsFilter { it.copy(fromYear = year, toYear = year) }
    }

    fun filterByStudio(studio: FilterOption) {
        applyDetailsFilter { it.copy(studios = setOf(studio.value)) }
    }

    fun filterByCreator(creator: FilterOption) {
        applyDetailsFilter { it.copy(creators = setOf(creator.value)) }
    }

    fun openAnime(animeId: Long, pushCurrent: Boolean = true) {
        _uiState.update { state ->
            val targetRoute = AppRoute.Details(animeId)
            state.copy(
                navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                route = AppRoute.Details(animeId),
                details = LoadState.Loading,
                videos = LoadState.Loading,
                selectedVideoGroup = null,
                animeMark = LoadState.Loading,
            )
        }
        loadAnimeDetails(animeId)
    }

    private fun loadAnimeDetails(animeId: Long) {
        viewModelScope.launch {
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (animeDetails, videoVariants) ->
                    _uiState.update {
                        it.copy(
                            details = LoadState.Ready(animeDetails),
                            videos = LoadState.Ready(videoVariants),
                            selectedVideoGroup = videoVariants.firstOrNull()?.groupKey,
                        )
                    }
                    loadAnimeMark(animeId)
                }
                .onFailure { throwable ->
                    val message = throwable.userMessage()
                    _uiState.update {
                        it.copy(
                            details = LoadState.Error(message),
                            videos = LoadState.Error(message),
                            animeMark = LoadState.Ready(null),
                        )
                    }
                }
        }
    }

    fun selectVideoGroup(groupKey: String) {
        _uiState.update { it.copy(selectedVideoGroup = groupKey) }
    }

    private fun applyDetailsFilter(transform: (BrowseFilters) -> BrowseFilters) {
        val filters = transform(BrowseFilters())
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route != AppRoute.Home),
                filters = filters,
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
        if (state.searchQuery.isBlank()) {
            loadHome(reset = false)
        } else {
            searchNow(state.searchQuery, reset = false)
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

    private fun playVideoAt(video: VideoVariant, startPositionMs: Long, titleOverride: String) {
        failedPlaybackSourceIds = emptySet()
        playVideoFromCandidates(
            video = video,
            title = titleOverride,
            excludedSourceIds = emptySet(),
            startPositionMs = startPositionMs,
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
        )
    }

    private fun playVideoFromCandidates(
        video: VideoVariant,
        title: String,
        excludedSourceIds: Set<Long>,
        startPositionMs: Long,
    ) {
        playerLoadJob?.cancel()
        val safeStartPositionMs = startPositionMs.coerceAtLeast(0L)
        val allVideos = (_uiState.value.videos as? LoadState.Ready)?.data.orEmpty()
        val candidates = playbackCandidates(
            requested = video,
            allVideos = allVideos,
            excludedSourceIds = excludedSourceIds,
        )
        _uiState.update { state ->
            state.copy(
                route = AppRoute.Player(video, title, safeStartPositionMs),
                navigationBackStack = state.navigationStackAfterOptionalPush(state.route !is AppRoute.Player),
                playerStream = LoadState.Loading,
            )
        }

        playerLoadJob = viewModelScope.launch {
            runCatching { resolvePlaybackWithCache(video, candidates) }
                .onSuccess { playback ->
                    _uiState.update { state ->
                        if (state.route == AppRoute.Player(video, title, safeStartPositionMs)) {
                            state.copy(
                                route = AppRoute.Player(playback.video, title, safeStartPositionMs),
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
                        if (state.route == AppRoute.Player(video, title, safeStartPositionMs)) {
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

    private fun maybeAutoMarkWatching(video: VideoVariant) {
        val state = _uiState.value
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
                    AppLog.w("YummyAniMarks", "Failed to auto set anime mark", throwable)
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
                    _uiState.update { it.copy(auth = AuthUiState(profile = profile)) }
                    (_uiState.value.route as? AppRoute.Details)?.let { route ->
                        loadAnimeMark(route.animeId)
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
        repository.logout()
        _uiState.update {
            it.copy(
                auth = AuthUiState(),
                animeMark = LoadState.Ready(null),
                filters = it.filters.copy(userMarks = emptySet()),
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

        val current = (_uiState.value.animeMark as? LoadState.Ready)?.data
        _uiState.update { it.copy(animeMark = LoadState.Loading) }
        viewModelScope.launch {
            runCatching {
                if (current?.list == mark) {
                    repository.removeAnimeListMark(animeId)
                } else {
                    repository.setAnimeListMark(animeId, mark)
                }
            }
                .onSuccess { updatedMark ->
                    _uiState.update { it.copy(animeMark = LoadState.Ready(updatedMark)) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(animeMark = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    fun toggleFavorite() {
        val animeId = (_uiState.value.route as? AppRoute.Details)?.animeId ?: return
        if (_uiState.value.auth.profile == null) {
            _uiState.update { it.copy(auth = it.auth.copy(error = "Нужно войти в аккаунт")) }
            return
        }

        val current = (_uiState.value.animeMark as? LoadState.Ready)?.data ?: UserAnimeMark()
        _uiState.update { it.copy(animeMark = LoadState.Loading) }
        viewModelScope.launch {
            runCatching { repository.setFavorite(animeId, !current.isFavorite) }
                .onSuccess { updatedMark ->
                    _uiState.update { it.copy(animeMark = LoadState.Ready(updatedMark)) }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(animeMark = LoadState.Error(throwable.userMessage())) }
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
            AppRoute.Home -> Unit
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
                searchDebounceJob?.cancel()
                searchLoadJob?.cancel()
                _uiState.update {
                    it.copy(
                        route = AppRoute.Home,
                        navigationBackStack = remainingBackStack,
                        filters = entry.filters,
                        searchQuery = entry.searchQuery,
                        searchResults = if (entry.searchQuery.isBlank()) {
                            LoadState.Ready(emptyList())
                        } else {
                            LoadState.Loading
                        },
                        searchPaging = PagingUiState(canLoadMore = entry.searchQuery.isNotBlank()),
                        selectedVideoGroup = entry.selectedVideoGroup,
                    )
                }
                if (entry.searchQuery.isBlank()) {
                    loadHome(reset = true)
                } else {
                    searchNow(entry.searchQuery, reset = true)
                }
            }
            is AppRoute.Details -> {
                _uiState.update {
                    it.copy(
                        route = route,
                        navigationBackStack = remainingBackStack,
                        filters = entry.filters,
                        searchQuery = entry.searchQuery,
                        selectedVideoGroup = entry.selectedVideoGroup,
                        details = LoadState.Loading,
                        videos = LoadState.Loading,
                        animeMark = LoadState.Loading,
                    )
                }
                loadAnimeDetails(route.animeId)
            }
            is AppRoute.Player -> {
                _uiState.update {
                    it.copy(
                        route = route,
                        navigationBackStack = remainingBackStack,
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
                    _uiState.update { it.copy(auth = AuthUiState(profile = profile ?: cachedProfile)) }
                }
                .onFailure { throwable ->
                    if (cachedProfile == null) {
                        repository.logout()
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
        if (_uiState.value.searchQuery.isBlank()) {
            loadHome(reset = true)
        } else {
            searchNow(_uiState.value.searchQuery, reset = true)
        }
    }

    private fun playbackCandidates(
        requested: VideoVariant,
        allVideos: List<VideoVariant>,
        excludedSourceIds: Set<Long>,
    ): List<VideoVariant> {
        val pool = allVideos.ifEmpty { listOf(requested) }
        val sameEpisode = pool.filter { it.sameEpisodeSourceSlot(requested) }
            .ifEmpty { listOf(requested) }
        val sameVoice = sameEpisode.filter { it.hasSameVoiceAs(requested) }
        val otherVoices = sameEpisode.filterNot { candidate ->
            sameVoice.any { it.id == candidate.id }
        }

        return (sameVoice + otherVoices)
            .filterNot { it.id in excludedSourceIds }
            .sortedWith(
                compareBy<VideoVariant> { if (it.hasSameVoiceAs(requested)) 0 else 1 }
                    .thenByDescending { it.estimatedSourceMaxVideoHeight() }
                    .thenBy { it.index }
                    .thenBy { if (it.id == requested.id) 0 else 1 }
                    .thenBy { it.id },
            )
    }

    private suspend fun resolvePlaybackWithCache(
        requested: VideoVariant,
        candidates: List<VideoVariant>,
    ): ResolvedPlayback {
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
                runCatching { repository.resolveFirstPlaybackSource(cachedCandidates) }
                    .onSuccess { return it }
                playbackSourceCache.remove(cacheKey)
            } else {
                playbackSourceCache.remove(cacheKey)
            }
        }

        val primaryResult = runCatching { repository.resolveBestPlaybackSource(sameVoiceCandidates) }
        primaryResult.onSuccess { return it }

        val sameVoiceIds = sameVoiceCandidates.mapTo(mutableSetOf(), VideoVariant::id)
        val fallbackCandidates = candidates.filterNot { it.id in sameVoiceIds }
        if (fallbackCandidates.isNotEmpty()) {
            return repository.resolveBestPlaybackSource(fallbackCandidates)
        }

        throw primaryResult.exceptionOrNull() ?: IllegalStateException("Не удалось выбрать источник видео")
    }

    private fun removeCachedPlaybackSource(video: VideoVariant) {
        val cacheKey = video.playbackCacheKey()
        if (playbackSourceCache[cacheKey]?.providerKey == video.sourceProviderKey) {
            playbackSourceCache.remove(cacheKey)
        }
    }

    private companion object {
        const val PAGE_SIZE = 36
    }
}

data class YummyAniUiState(
    val route: AppRoute = AppRoute.Home,
    val navigationBackStack: List<NavigationEntry> = emptyList(),
    val siteBaseUrl: String = "https://old.yummyani.me/",
    val featured: LoadState<List<Anime>> = LoadState.Loading,
    val featuredPaging: PagingUiState = PagingUiState(),
    val searchQuery: String = "",
    val searchResults: LoadState<List<Anime>> = LoadState.Ready(emptyList()),
    val searchPaging: PagingUiState = PagingUiState(canLoadMore = false),
    val filters: BrowseFilters = BrowseFilters(),
    val filterCatalog: LoadState<FilterCatalog> = LoadState.Loading,
    val details: LoadState<AnimeDetails> = LoadState.Loading,
    val videos: LoadState<List<VideoVariant>> = LoadState.Loading,
    val selectedVideoGroup: String? = null,
    val playerStream: LoadState<ResolvedVideoStream> = LoadState.Loading,
    val auth: AuthUiState = AuthUiState(),
    val animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    val settings: AppSettings = AppSettings(),
) {
    val canNavigateBack: Boolean
        get() = route != AppRoute.Home || navigationBackStack.isNotEmpty()
}

data class NavigationEntry(
    val route: AppRoute,
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

sealed interface AppRoute {
    data object Home : AppRoute
    data class Details(val animeId: Long) : AppRoute
    data class Player(
        val video: VideoVariant,
        val animeTitle: String,
        val startPositionMs: Long = 0L,
    ) : AppRoute
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

private fun YummyAniUiState.navigationEntry(): NavigationEntry {
    return NavigationEntry(
        route = route,
        filters = filters,
        searchQuery = searchQuery,
        selectedVideoGroup = selectedVideoGroup,
    )
}

private fun YummyAniUiState.navigationStackAfterOptionalPush(push: Boolean): List<NavigationEntry> {
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

private fun AnimeDetails.isFullyReleased(): Boolean {
    val normalizedStatus = status
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')

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
    val currentOrder = episodeOrderForCompletion()
    val expectedEpisodeCount = details.episodeCount.takeIf { it > 0 }
    if (expectedEpisodeCount != null && currentOrder != null) {
        return currentOrder >= expectedEpisodeCount.toDouble()
    }

    val lastVideo = allVideos
        .filter { it.animeId == animeId }
        .ifEmpty { listOf(this) }
        .maxWithOrNull(
            compareBy<VideoVariant> { it.episodeOrderForCompletion() ?: 0.0 }
                .thenBy { it.index }
                .thenBy { it.id },
        )
    return lastVideo?.sameEpisodeSourceSlot(this) == true
}

private fun VideoVariant.episodeOrderForCompletion(): Double? {
    return episode
        .replace(',', '.')
        .toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
}

private val VideoVariant.sourceVoiceKey: String
    get() = dubbing.cleanSourceLabel("Озвучка")
        .ifBlank { player.cleanSourceLabel("Плеер") }
        .lowercase(Locale.ROOT)

private val VideoVariant.sourceEpisodeKey: String
    get() = episode.takeIf { it.isNotBlank() } ?: "index:$index"

private fun VideoVariant.sameEpisodeSourceSlot(other: VideoVariant): Boolean {
    return sourceEpisodeKey == other.sourceEpisodeKey
}

private fun VideoVariant.hasSameVoiceAs(other: VideoVariant): Boolean {
    return sourceVoiceKey == other.sourceVoiceKey ||
        dubbing.normalizedVoiceForMatching() == other.dubbing.normalizedVoiceForMatching()
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
    return PlaybackCacheKey(animeId = animeId, voiceKey = sourceVoiceKey)
}

private val VideoVariant.sourceProviderKey: String
    get() = listOf(
        player.cleanSourceLabel("Плеер").lowercase(Locale.ROOT),
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

private fun String.cleanSourceLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

private fun String.normalizedVoiceForMatching(): String {
    return lowercase(Locale.ROOT)
        .replace("озвучка", "")
        .replace("субтитры", "")
        .replace("плеер", "")
        .replace(Regex("""[\s./|•:_-]+"""), "")
        .trim()
}

