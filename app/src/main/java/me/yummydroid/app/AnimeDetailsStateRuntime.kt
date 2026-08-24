package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage

// AnimeDetailsStateRuntime
internal class AnimeDetailsStateRuntime(
    private val scope: CoroutineScope,
    private val playbackProgressStorage: PlaybackProgressStorage,
    private val profilePlaybackHistoryCache: ProfilePlaybackHistoryCache,
    private val animeDetailsLoadCoordinator: AnimeDetailsLoadCoordinator,
    private val animeDetailsExtrasCoordinator: AnimeDetailsExtrasCoordinator,
    private val animeMarkCoordinator: AnimeMarkCoordinator,
    private val videoSubscriptionStateCoordinator: VideoSubscriptionStateCoordinator,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val detailsLoadOperations: LatestStateOperationCoordinator,
    private val detailsExtrasOperations: LatestStateOperationCoordinator,
    private val commentsOperations: LatestStateOperationCoordinator,
    private val commentMutations: SerialStateOperationCoordinator,
    private val cacheMaintenanceOperations: SerialStateOperationCoordinator,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val saveBrowseFilters: (BrowseFilters) -> AppSettings,
    private val cachedDetailsRoute: (Long) -> DetailsRouteCache?,
    private val cacheCurrentDetailsRouteState: () -> Unit,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val updateCachedPlaybackProgress: (PlaybackProgress, List<PlaybackProgress>) -> Unit,
    private val refreshPlaybackProgressFromSite: (Long) -> Unit,
    private val restoreNavigationEntry: (NavigationEntry, List<NavigationEntry>, Boolean) -> Unit,
    private val authenticatedDetailsAnimeId: () -> Long?,
    private val isActiveProfile: (Long) -> Boolean,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val isOfflineConnectivityFailure: (Throwable) -> Boolean,
    private val offlineUnavailableMessage: () -> String,
    private val showNotice: (String) -> Unit,
) {
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
        openAnime(
            target = AnimeOpenTarget(animeId = animeId),
            pushCurrent = pushCurrent,
            reload = reload,
        )
    }

    fun openAnime(target: AnimeOpenTarget, pushCurrent: Boolean = true, reload: Boolean = false) {
        val animeId = target.animeId
        if (currentState().forcedOfflineMode) {
            val offlineEntries = currentState().offlineEntries.readyDataOrNull()
            if (offlineEntries != null && offlineEntries.none { it.anime.id == animeId }) {
                showNotice(offlineUnavailableMessage())
                return
            }
        }
        commentsOperations.cancel()
        detailsLoadOperations.cancel()
        cacheCurrentDetailsRouteState()
        val cachedRoute = cachedDetailsRoute(animeId)
            .takeIf { target.animeAlias == null }
            .takeUnless { reload }
        updateState { state ->
            val targetRoute = AppRoute.Details(animeId)
            if (cachedRoute != null) {
                return@updateState state.withDetailsRouteCache(
                    cachedRoute = cachedRoute,
                    navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                    route = targetRoute,
                ).withProfilePlaybackHistorySnapshot(animeId)
            }
            val retainedProgress = state.playbackProgress?.takeIf { it.animeId == animeId }
            val retainedHistory = state.playbackHistory.takeIf { history ->
                history.any { it.animeId == animeId }
            }.orEmpty()
            state.copy(
                navigationBackStack = state.navigationStackAfterOptionalPush(pushCurrent && state.route != targetRoute),
                route = targetRoute,
                details = LoadState.Loading,
                videos = LoadState.Loading,
                detailsExtras = LoadState.Loading,
                selectedVideoGroup = null,
                animeMark = LoadState.Loading,
                playbackProgress = retainedProgress,
                playbackHistory = retainedHistory,
                playbackHistoryLoading = shouldAwaitPlaybackHistoryForDetails(
                    animeId = animeId,
                    isAuthenticated = state.auth.profile != null,
                    forcedOfflineMode = state.forcedOfflineMode,
                    playbackProgress = retainedProgress,
                    playbackHistory = retainedHistory,
                ),
            ).withProfilePlaybackHistorySnapshot(animeId)
        }
        if (cachedRoute != null) {
            refreshPlaybackProgressSnapshot(animeId)
            return
        }
        loadAnimeDetails(animeId, target.animeAlias)
    }

    fun refreshPlaybackProgressSnapshot(animeId: Long) {
        if (!currentState().forcedOfflineMode && currentState().auth.profile?.id != null) {
            refreshPlaybackProgressFromSite(animeId)
            return
        }
        refreshLocalPlaybackProgressSnapshot(animeId)
    }

    fun loadAnimeDetails(animeId: Long) {
        loadAnimeDetails(animeId, animeAlias = null)
    }

    private fun loadAnimeDetails(animeId: Long, animeAlias: String?) {
        detailsLoadOperations.launchLatest(scope) { lease ->
            try {
                val loaded = animeDetailsLoadCoordinator.load(animeId, animeAlias) {
                    currentState().auth.profile != null
                }
                if (!lease.isCurrent) return@launchLatest
                val canonicalAnimeId = loaded.details.id
                cacheMaintenanceOperations.launch(scope) {
                    animeDetailsLoadCoordinator.cache(loaded.details)
                }
                updateState { state -> state.withLoadedAnimeDetails(animeId, loaded) }
                if ((currentState().route as? AppRoute.Details)?.animeId != canonicalAnimeId) {
                    return@launchLatest
                }

                cacheDetailsRouteState(canonicalAnimeId)
                if (loaded.offlineMode) {
                    refreshPlaybackProgressSnapshot(canonicalAnimeId)
                    animeMarkCoordinator.cancelLoad()
                    detailsExtrasOperations.cancel()
                } else {
                    refreshPlaybackProgressFromSite(canonicalAnimeId)
                    animeMarkCoordinator.load(canonicalAnimeId)
                    loadAnimeExtras(canonicalAnimeId)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (lease.isCurrent) applyAnimeDetailsLoadFailure(animeId, throwable)
            }
        }
    }

    fun selectVideoGroup(groupKey: String) {
        updateState { it.copy(selectedVideoGroup = groupKey) }
        cacheCurrentDetailsRouteState()
    }

    fun loadAnimeExtras(animeId: Long) {
        if (currentState().forcedOfflineMode) {
            detailsExtrasOperations.cancel()
            updateState { it.copy(detailsExtras = LoadState.Ready(AnimeDetailsExtras())) }
            return
        }
        val stateSnapshot = currentState()
        val request = AnimeDetailsExtrasLoadRequest(
            animeId = animeId,
            details = stateSnapshot.details.readyDataOrNull(),
            isAuthenticated = stateSnapshot.auth.profile != null,
        )
        updateState { it.copy(detailsExtras = LoadState.Loading) }
        detailsExtrasOperations.launchLatest(scope) { lease ->
            try {
                val loaded = animeDetailsExtrasCoordinator.load(request)
                if (!lease.isCurrent || !isCurrentDetailsAnime(animeId)) return@launchLatest
                updateState { state -> state.withLoadedAnimeDetailsExtras(animeId, loaded) }
                cacheDetailsRouteState(animeId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!lease.isCurrent || !isCurrentDetailsAnime(animeId)) return@launchLatest
                updateState { state ->
                    if (state.isShowingDetailsAnime(animeId)) {
                        state.copy(detailsExtras = LoadState.Error(throwable.userMessage()))
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun loadMoreAnimeComments() {
        if (currentState().forcedOfflineMode) return
        val animeId = (currentState().route as? AppRoute.Details)?.animeId ?: return
        val extras = currentState().detailsExtras.readyDataOrNull() ?: return
        if (extras.commentsPaging.isLoadingMore || !extras.commentsPaging.canLoadMore) return

        val offset = extras.comments.size
        updateState { state ->
            val current = state.detailsExtras.readyDataOrNull() ?: return@updateState state
            state.copy(detailsExtras = LoadState.Ready(current.withAnimeCommentsLoading()))
        }

        commentsOperations.launchLatest(scope) { lease ->
            try {
                val comments = animeDetailsExtrasCoordinator.loadCommentsPage(animeId, offset)
                if (!lease.isCurrent) return@launchLatest
                updateState { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@updateState state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@updateState state
                    state.copy(
                        detailsExtras = LoadState.Ready(
                            animeDetailsExtrasCoordinator.mergeCommentsPage(current, comments),
                        ),
                    )
                }
                cacheDetailsRouteState(animeId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!lease.isCurrent) return@launchLatest
                updateState { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@updateState state
                    val current = state.detailsExtras.readyDataOrNull() ?: return@updateState state
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

    fun addAnimeComment(text: String) {
        if (currentState().forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeId() ?: return
        val profileId = currentState().auth.profile?.id ?: return
        commentMutations.launch(scope) {
            try {
                val comment = animeDetailsExtrasCoordinator.submitComment(animeId, text) ?: return@launch
                if (!isActiveProfile(profileId)) return@launch
                updateState { state ->
                    if ((state.route as? AppRoute.Details)?.animeId != animeId) return@updateState state
                    val extras = state.detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
                    state.copy(
                        detailsExtras = LoadState.Ready(extras.withAddedAnimeComment(comment)),
                    )
                }
                cacheDetailsRouteState(animeId)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!isActiveProfile(profileId) || !isCurrentDetailsAnime(animeId)) return@launch
                if (!requestCaptchaRetry(throwable) { addAnimeComment(text) }) {
                    showNotice(throwable.userMessage())
                }
            }
        }
    }

    private fun applyDetailsFilter(sourceAnimeId: Long? = null, transform: (BrowseFilters) -> BrowseFilters) {
        if (currentState().forcedOfflineMode) {
            showNotice(offlineUnavailableMessage())
            return
        }
        val filters = transform(BrowseFilters())
        val updatedSettings = saveBrowseFilters(filters)
        cacheCurrentDetailsRouteState()
        updateState { state ->
            state.withCatalogFilters(
                filters = filters,
                settings = updatedSettings,
                navigationBackStack = state.navigationStackForDetailsFilter(sourceAnimeId),
            )
        }
        browseContentCoordinator.loadCatalog(reset = true)
    }

    private fun YummyDroidUiState.withProfilePlaybackHistorySnapshot(animeId: Long): YummyDroidUiState {
        if (playbackProgress?.animeId == animeId || playbackHistory.any { it.animeId == animeId }) return this
        val history = profilePlaybackHistoryCache.historyForAnime(auth.profile?.id, animeId)
        if (history.isEmpty()) return this
        val progress = history.maxByOrNull { it.updatedAtMs }
        val progressGroupKey = progress?.groupKey
            ?.takeIf { groupKey -> videos.readyListOrEmpty().any { it.groupKey == groupKey } }
        return copy(
            selectedVideoGroup = progressGroupKey ?: selectedVideoGroup,
            playbackProgress = progress,
            playbackHistory = history,
            playbackHistoryLoading = shouldAwaitPlaybackHistoryForDetails(
                animeId = animeId,
                isAuthenticated = auth.profile != null,
                forcedOfflineMode = forcedOfflineMode,
                playbackProgress = progress,
                playbackHistory = history,
            ),
        )
    }

    private fun refreshLocalPlaybackProgressSnapshot(animeId: Long) {
        if (animeId <= 0L) return
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
            val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
            if (!lease.isCurrent) return@launchLatest
            if (progress != null) updateCachedPlaybackProgress(progress, history)
            updateState { state ->
                val isCurrentDetails = (state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                if (!isCurrentDetails) return@updateState state
                val progressGroupKey = progress?.groupKey
                    ?.takeIf { groupKey -> state.videos.readyListOrEmpty().any { it.groupKey == groupKey } }
                state.copy(
                    selectedVideoGroup = progressGroupKey ?: state.selectedVideoGroup,
                    playbackProgress = progress,
                    playbackHistory = history,
                    playbackHistoryLoading = false,
                )
            }
        }
    }

    private fun applyAnimeDetailsLoadFailure(animeId: Long, throwable: Throwable) {
        val failedState = currentState()
        val offlineUnavailable = failedState.forcedOfflineMode || isOfflineConnectivityFailure(throwable)
        val offlineMessage = offlineUnavailableMessage()
        if (offlineUnavailable) showNotice(offlineMessage)
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
                plan.entry,
                plan.remainingBackStack,
                true,
            )

            is AnimeDetailsLoadFailurePlan.Publish -> updateState { current ->
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

    private fun isCurrentDetailsAnime(animeId: Long): Boolean {
        return currentState().isShowingDetailsAnime(animeId)
    }

    private fun YummyDroidUiState.isShowingDetailsAnime(animeId: Long): Boolean {
        return when (val currentRoute = route) {
            is AppRoute.Details -> currentRoute.animeId == animeId
            is AppRoute.Player -> currentRoute.video.animeId == animeId
            AppRoute.Home -> false
        }
    }
}
