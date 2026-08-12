package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.siteDefaultVideo
import me.yummydroid.app.data.toAnimeSummary

// AnimeCommentsState
internal fun AnimeDetailsExtras.withAnimeCommentsLoading(): AnimeDetailsExtras {
    return copy(
        commentsPaging = commentsPaging.copy(
            isLoadingMore = true,
            error = null,
        ),
    )
}

internal fun AnimeDetailsExtras.withLoadedAnimeComments(
    incoming: List<AnimeComment>,
    pageSize: Int,
): AnimeDetailsExtras {
    val previousComments = comments
    val mergedComments = (previousComments + incoming).distinctBy(AnimeComment::id)
    return copy(
        comments = mergedComments,
        commentsPaging = PagingUiState(
            isLoadingMore = false,
            canLoadMore = incoming.size >= pageSize && mergedComments.size > previousComments.size,
        ),
    )
}

internal fun AnimeDetailsExtras.withAnimeCommentsFailure(error: String): AnimeDetailsExtras {
    return copy(
        commentsPaging = commentsPaging.copy(
            isLoadingMore = false,
            error = error,
        ),
    )
}

internal fun AnimeDetailsExtras.withAddedAnimeComment(comment: AnimeComment): AnimeDetailsExtras {
    return copy(comments = (listOf(comment) + comments).distinctBy(AnimeComment::id))
}

// AnimeDetailsExtrasCoordinator
internal data class AnimeDetailsExtrasLoadRequest(
    val animeId: Long,
    val details: AnimeDetails?,
    val videos: List<VideoVariant>,
    val isAuthenticated: Boolean,
)

internal data class AnimeDetailsExtrasLoadResult(
    val extras: AnimeDetailsExtras,
    val synchronizedSubscriptions: List<VideoSubscription>?,
)

internal class AnimeDetailsExtrasCoordinator(
    private val fetchComments: suspend (animeId: Long, offset: Int, limit: Int) -> List<AnimeComment>,
    private val fetchRecommendations: suspend (animeId: Long) -> List<Anime>,
    private val fetchRatingSummary: suspend (animeId: Long) -> AnimeRatingSummary,
    private val resolveEffectiveRating: suspend (
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ) -> Int?,
    private val loadSubscriptions: suspend () -> List<VideoSubscription>,
    private val canonicalizeSubscriptions: (
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) -> List<VideoSubscription>,
    private val addComment: suspend (animeId: Long, text: String) -> AnimeComment?,
    private val commentsPageSize: Int = DEFAULT_COMMENTS_PAGE_SIZE,
) {
    suspend fun load(request: AnimeDetailsExtrasLoadRequest): AnimeDetailsExtrasLoadResult {
        val comments = bestEffort(emptyList<AnimeComment>()) {
            fetchComments(request.animeId, 0, commentsPageSize)
        }
        val recommendations = bestEffort(emptyList<Anime>()) {
            fetchRecommendations(request.animeId)
        }
        val matchingDetails = request.details?.takeIf { it.id == request.animeId }
        val currentUserRating = matchingDetails
            ?.let { details ->
                resolveEffectiveRating(
                    request.animeId,
                    details.userRating,
                    request.isAuthenticated,
                )
            }
            ?.takeIf { it in 1..10 }
        val rating = bestEffort(AnimeRatingSummary()) {
            fetchRatingSummary(request.animeId)
        }.copy(userRating = currentUserRating)
        val synchronizedSubscriptions = if (request.isAuthenticated) {
            bestEffortOrNull(loadSubscriptions)
        } else {
            null
        }
        val subscriptions = canonicalizeSubscriptions(
            synchronizedSubscriptions.orEmpty(),
            request.videos.filter { it.animeId == request.animeId },
            request.details?.title.orEmpty(),
            request.details?.posterUrl.orEmpty(),
        )
        val extras = AnimeDetailsExtras(
            recommendations = recommendations,
            rating = rating,
            subscriptions = subscriptions,
        ).withLoadedAnimeComments(comments, commentsPageSize)
        return AnimeDetailsExtrasLoadResult(
            extras = extras,
            synchronizedSubscriptions = synchronizedSubscriptions,
        )
    }

    suspend fun loadCommentsPage(animeId: Long, offset: Int): List<AnimeComment> {
        return fetchComments(animeId, offset, commentsPageSize)
    }

    suspend fun submitComment(animeId: Long, text: String): AnimeComment? {
        return addComment(animeId, text)
    }

    fun mergeCommentsPage(
        current: AnimeDetailsExtras,
        incoming: List<AnimeComment>,
    ): AnimeDetailsExtras {
        return current.withLoadedAnimeComments(incoming, commentsPageSize)
    }

    private suspend fun <T> bestEffort(default: T, block: suspend () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            default
        }
    }

    private suspend fun <T> bestEffortOrNull(block: suspend () -> T): T? {
        return try {
            block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private companion object {
        const val DEFAULT_COMMENTS_PAGE_SIZE = 20
    }
}

// AnimeDetailsLoadCoordinator
internal data class LoadedAnimeDetails(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val offlineMode: Boolean,
    val progress: PlaybackProgress?,
    val history: List<PlaybackProgress>,
    val selectedVideoGroup: String?,
)

internal class AnimeDetailsLoadCoordinator(
    private val fetchAnimeWithVideos: suspend (Long) -> Pair<AnimeDetails, List<VideoVariant>>,
    private val isOfflineFallbackActive: () -> Boolean,
    private val readProgress: (Long) -> PlaybackProgress?,
    private val readHistory: (Long) -> List<PlaybackProgress>,
    private val resolveEffectiveRating: suspend (
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ) -> Int?,
    private val saveAnimeSummary: (Anime) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(
        animeId: Long,
        isAuthenticated: () -> Boolean,
    ): LoadedAnimeDetails {
        val loaded = withContext(ioDispatcher) {
            val (details, videos) = fetchAnimeWithVideos(animeId)
            val offlineMode = isOfflineFallbackActive()
            val progress = readProgress(animeId)
            LoadedAnimeDetails(
                details = details,
                videos = videos,
                offlineMode = offlineMode,
                progress = progress,
                history = readHistory(animeId),
                selectedVideoGroup = selectInitialVideoGroup(
                    videos = videos,
                    progress = progress,
                    offlineMode = offlineMode,
                ),
            )
        }
        val effectiveRating = resolveEffectiveRating(
            animeId,
            loaded.details.userRating,
            isAuthenticated() && !loaded.offlineMode,
        )
        return loaded.copy(details = loaded.details.copy(userRating = effectiveRating))
    }

    suspend fun cache(details: AnimeDetails) {
        withContext(ioDispatcher) {
            saveAnimeSummary(details.toAnimeSummary())
        }
    }
}

internal fun selectInitialVideoGroup(
    videos: List<VideoVariant>,
    progress: PlaybackProgress?,
    offlineMode: Boolean,
): String? {
    val playableVideos = if (offlineMode) {
        videos.filter(VideoVariant::isOfflineAvailable)
    } else {
        videos
    }
    val progressGroup = progress?.groupKey
        ?.takeIf { groupKey -> playableVideos.any { it.groupKey == groupKey } }
    return progressGroup
        ?: playableVideos.siteDefaultVideo()?.groupKey
        ?: videos.siteDefaultVideo()?.groupKey
}

// AnimeDetailsLoadState
internal sealed interface AnimeDetailsLoadFailurePlan {
    data object Ignore : AnimeDetailsLoadFailurePlan

    data class RestorePrevious(
        val entry: NavigationEntry,
        val remainingBackStack: List<NavigationEntry>,
    ) : AnimeDetailsLoadFailurePlan

    data class Publish(val state: YummyDroidUiState) : AnimeDetailsLoadFailurePlan
}

internal fun YummyDroidUiState.withLoadedAnimeDetails(
    animeId: Long,
    loaded: LoadedAnimeDetails,
): YummyDroidUiState {
    if ((route as? AppRoute.Details)?.animeId != animeId) return this
    return copy(
        details = LoadState.Ready(loaded.details),
        videos = LoadState.Ready(loaded.videos),
        forcedOfflineMode = loaded.offlineMode,
        selectedVideoGroup = loaded.selectedVideoGroup,
        playbackProgress = loaded.progress,
        playbackHistory = loaded.history,
        detailsExtras = if (loaded.offlineMode) LoadState.Ready(AnimeDetailsExtras()) else detailsExtras,
        animeMark = if (loaded.offlineMode) LoadState.Ready(null) else animeMark,
    )
}

internal fun animeDetailsLoadFailurePlan(
    state: YummyDroidUiState,
    animeId: Long,
    offlineUnavailable: Boolean,
    offlineMessage: String,
    errorMessage: String,
): AnimeDetailsLoadFailurePlan {
    if ((state.route as? AppRoute.Details)?.animeId != animeId) {
        return AnimeDetailsLoadFailurePlan.Ignore
    }
    if (offlineUnavailable) {
        val previous = state.navigationBackStack.lastOrNull()
        if (previous != null) {
            return AnimeDetailsLoadFailurePlan.RestorePrevious(
                entry = previous,
                remainingBackStack = state.navigationBackStack.dropLast(1),
            )
        }
        return AnimeDetailsLoadFailurePlan.Publish(
            state.copy(
                details = LoadState.Error(offlineMessage),
                videos = LoadState.Error(offlineMessage),
                detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
                animeMark = LoadState.Ready(null),
                playbackProgress = null,
            ),
        )
    }
    return AnimeDetailsLoadFailurePlan.Publish(
        state.copy(
            details = LoadState.Error(errorMessage),
            videos = LoadState.Error(errorMessage),
            detailsExtras = LoadState.Error(errorMessage),
            animeMark = LoadState.Ready(null),
            forcedOfflineMode = false,
            playbackProgress = null,
        ),
    )
}

// AnimeMarkCoordinator
internal class AnimeMarkCoordinator(
    private val scope: CoroutineScope,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val getAnimeMark: suspend (Long) -> UserAnimeMark?,
    private val setAnimeListMark: suspend (Long, UserAnimeListMark) -> UserAnimeMark,
    private val removeAnimeListMark: suspend (Long) -> UserAnimeMark,
    private val setFavorite: suspend (Long, Boolean) -> UserAnimeMark,
    private val authenticatedDetailsAnimeId: () -> Long?,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val onAutoMarkFailure: (Throwable) -> Unit,
) {
    private var loadJob: Job? = null
    private val autoMarkJobs = mutableMapOf<Long, Job>()

    fun load(animeId: Long) {
        cancelLoad()
        val state = currentState()
        if (state.forcedOfflineMode || state.auth.profile == null) {
            updateState { it.copy(animeMark = LoadState.Ready(null)) }
            return
        }

        updateState { it.copy(animeMark = LoadState.Loading) }
        val job = scope.launch {
            runCatching { getAnimeMark(animeId) }
                .onSuccess { mark ->
                    updateState { current ->
                        if ((current.route as? AppRoute.Details)?.animeId == animeId) {
                            current.copy(animeMark = LoadState.Ready(mark))
                        } else {
                            current
                        }
                    }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    updateState { current ->
                        if ((current.route as? AppRoute.Details)?.animeId == animeId) {
                            current.copy(animeMark = LoadState.Error(throwable.userMessage()))
                        } else {
                            current
                        }
                    }
                    cacheDetailsRouteState(animeId)
                }
        }
        loadJob = job
        job.invokeOnCompletion {
            if (loadJob == job) loadJob = null
        }
    }

    fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
    }

    fun clear() {
        cancelLoad()
        autoMarkJobs.values.forEach(Job::cancel)
        autoMarkJobs.clear()
    }

    fun toggleListMark(mark: UserAnimeListMark) {
        val animeId = authenticatedDetailsAnimeId() ?: return
        val previousMarkState = currentState().animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        val optimisticMark = if (current.list == mark) {
            current.copy(list = null)
        } else {
            current.copy(list = mark)
        }
        setMarkState(animeId, LoadState.Ready(optimisticMark))
        scope.launch {
            runCatching {
                if (current.list == mark) {
                    removeAnimeListMark(animeId)
                } else {
                    setAnimeListMark(animeId, mark)
                }
            }
                .onSuccess { updatedMark ->
                    setMarkState(animeId, LoadState.Ready(updatedMark))
                }
                .onFailure { throwable ->
                    handleMutationFailure(
                        animeId = animeId,
                        previousMarkState = previousMarkState,
                        throwable = throwable,
                    ) {
                        toggleListMark(mark)
                    }
                }
        }
    }

    fun toggleFavorite() {
        val animeId = authenticatedDetailsAnimeId() ?: return
        val previousMarkState = currentState().animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        setMarkState(animeId, LoadState.Ready(current.copy(isFavorite = !current.isFavorite)))
        scope.launch {
            runCatching { setFavorite(animeId, !current.isFavorite) }
                .onSuccess { updatedMark ->
                    setMarkState(animeId, LoadState.Ready(updatedMark))
                }
                .onFailure { throwable ->
                    handleMutationFailure(
                        animeId = animeId,
                        previousMarkState = previousMarkState,
                        throwable = throwable,
                    ) {
                        toggleFavorite()
                    }
                }
        }
    }

    fun maybeMarkWatching(video: VideoVariant) {
        val state = currentState()
        if (state.forcedOfflineMode) return
        if (!state.settings.autoMarkWatchingOnPlayback || state.auth.profile == null) return

        val currentMark = state.animeMark.readyDataOrNull()
            ?.takeIf { state.details.readyDataOrNull()?.id == video.animeId }
        if (currentMark?.list == UserAnimeListMark.Watching || currentMark?.list == UserAnimeListMark.Watched) {
            return
        }
        scheduleAutoSetListMark(
            animeId = video.animeId,
            mark = UserAnimeListMark.Watching,
            preserveWatched = true,
        )
    }

    fun maybeMarkWatchedOnCompletion(video: VideoVariant, state: YummyDroidUiState) {
        val details = state.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
            ?: return
        if (!state.settings.autoMarkWatchedOnCompletedFinalEpisode) return
        if (state.auth.profile == null) return
        if (!details.isFullyReleased()) return
        if (!video.isFinalEpisodeFor(details, state.videos.readyListOrEmpty())) return
        scheduleAutoSetListMark(video.animeId, UserAnimeListMark.Watched)
    }

    private fun setMarkState(animeId: Long, animeMark: LoadState<UserAnimeMark?>) {
        updateState { it.copy(animeMark = animeMark) }
        cacheDetailsRouteState(animeId)
    }

    private fun handleMutationFailure(
        animeId: Long,
        previousMarkState: LoadState<UserAnimeMark?>,
        throwable: Throwable,
        retry: suspend () -> Unit,
    ) {
        if (throwable is CaptchaRequiredException) {
            setMarkState(animeId, previousMarkState)
            requestCaptchaRetry(throwable, retry)
            return
        }
        updateState {
            it.copy(
                animeMark = previousMarkState,
                auth = it.auth.copy(error = throwable.userMessage()),
            )
        }
        cacheDetailsRouteState(animeId)
    }

    private fun scheduleAutoSetListMark(
        animeId: Long,
        mark: UserAnimeListMark,
        preserveWatched: Boolean = false,
    ) {
        autoMarkJobs.remove(animeId)?.cancel()
        val job = scope.launch {
            runCatching {
                val state = currentState()
                if (state.forcedOfflineMode || state.auth.profile == null) return@launch

                val stateMark = state.animeMark.readyDataOrNull()
                    ?.takeIf { state.details.readyDataOrNull()?.id == animeId }
                if (stateMark.alreadyHas(mark, preserveWatched)) return@launch

                val currentMark = stateMark ?: getAnimeMark(animeId)
                if (currentMark.alreadyHas(mark, preserveWatched)) return@launch
                setAnimeListMark(animeId, mark)
            }
                .onSuccess { updatedMark ->
                    updateState { current ->
                        if (current.details.readyDataOrNull()?.id == animeId) {
                            current.copy(animeMark = LoadState.Ready(updatedMark))
                        } else {
                            current
                        }
                    }
                }
                .onFailure(onAutoMarkFailure)
        }
        autoMarkJobs[animeId] = job
        job.invokeOnCompletion {
            if (autoMarkJobs[animeId] == job) autoMarkJobs.remove(animeId)
        }
    }
}

private fun UserAnimeMark?.alreadyHas(mark: UserAnimeListMark, preserveWatched: Boolean): Boolean {
    return this?.list == mark || (preserveWatched && this?.list == UserAnimeListMark.Watched)
}

// AnimeRatingCoordinator
internal data class StagedAnimeRating(
    val animeId: Long,
    val requestedRating: Int?,
    val optimisticRating: Int?,
    internal val hadPreviousRating: Boolean,
    internal val previousRating: Int?,
)

internal data class AnimeRatingUpdate(
    val summary: AnimeRatingSummary,
    val userRating: Int?,
)

internal class AnimeRatingCoordinator(
    private val readRatings: (Long) -> Map<Long, Int>,
    private val saveRatings: (Long, Map<Long, Int?>) -> Unit,
    private val setRating: suspend (Long, Int) -> AnimeRatingSummary,
    private val deleteRating: suspend (Long) -> AnimeRatingSummary,
    private val fetchUserRating: suspend (Long) -> Int?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val knownRatings = mutableMapOf<Long, Int?>()
    private var activeUserId: Long? = null
    private var accountGeneration = 0L

    suspend fun restore(userId: Long?) {
        val generation = ++accountGeneration
        knownRatings.clear()
        val validUserId = userId?.takeIf { it > 0L }
        activeUserId = validUserId
        if (validUserId == null) return

        val restored = try {
            withContext(ioDispatcher) { readRatings(validUserId) }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyMap()
        }
        if (generation != accountGeneration || activeUserId != validUserId) return
        knownRatings.putAll(restored)
    }

    fun clear() {
        accountGeneration += 1L
        activeUserId = null
        knownRatings.clear()
    }

    suspend fun effectiveRating(
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ): Int? {
        val normalized = remoteRating.normalizedRating()
        if (!trustRemote) return normalized ?: knownRatings[animeId]

        val changed = if (normalized != null) {
            knownRatings.put(animeId, normalized) != normalized
        } else {
            knownRatings.remove(animeId) != null
        }
        if (changed) persistBestEffort()
        return normalized
    }

    fun stage(animeId: Long, rating: Int?): StagedAnimeRating {
        val staged = StagedAnimeRating(
            animeId = animeId,
            requestedRating = rating,
            optimisticRating = rating.normalizedRating(),
            hadPreviousRating = knownRatings.containsKey(animeId),
            previousRating = knownRatings[animeId],
        )
        knownRatings[animeId] = staged.optimisticRating
        return staged
    }

    suspend fun submit(staged: StagedAnimeRating): AnimeRatingUpdate {
        return try {
            val summary = staged.requestedRating?.let { rating ->
                setRating(staged.animeId, rating)
            } ?: deleteRating(staged.animeId)
            val confirmedRating = if (staged.requestedRating == null) {
                null
            } else {
                fetchConfirmedRating(staged.animeId)
            }
            val selectedRating = if (staged.requestedRating == null) {
                null
            } else {
                confirmedRating ?: staged.optimisticRating
            }
            knownRatings[staged.animeId] = selectedRating
            persistBestEffort()
            AnimeRatingUpdate(
                summary = summary.copy(userRating = selectedRating),
                userRating = selectedRating,
            )
        } catch (throwable: Throwable) {
            restoreStagedRating(staged)
            throw throwable
        }
    }

    internal fun snapshot(): Map<Long, Int?> = knownRatings.toMap()

    private suspend fun fetchConfirmedRating(animeId: Long): Int? {
        return try {
            fetchUserRating(animeId).normalizedRating()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private fun restoreStagedRating(staged: StagedAnimeRating) {
        if (staged.hadPreviousRating) {
            knownRatings[staged.animeId] = staged.previousRating
        } else {
            knownRatings.remove(staged.animeId)
        }
    }

    private suspend fun persistBestEffort() {
        val userId = activeUserId ?: return
        val snapshot = knownRatings.toMap()
        try {
            withContext(ioDispatcher) { saveRatings(userId, snapshot) }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
        }
    }
}

private fun Int?.normalizedRating(): Int? = this?.takeIf { it in 1..10 }

// AnimeRatingState
internal fun YummyDroidUiState.withOptimisticAnimeRating(
    animeId: Long,
    rating: Int?,
): YummyDroidUiState {
    if (!acceptsAnimeRatingUpdate(animeId)) return this
    val extras = detailsExtras.readyDataOrNull()
    return copy(
        details = details.withAnimeUserRating(animeId, rating),
        detailsExtras = if (extras != null) {
            LoadState.Ready(extras.copy(rating = extras.rating.copy(userRating = rating)))
        } else {
            detailsExtras
        },
    )
}

internal fun YummyDroidUiState.withConfirmedAnimeRating(
    animeId: Long,
    update: AnimeRatingUpdate,
): YummyDroidUiState {
    if (!acceptsAnimeRatingUpdate(animeId)) return this
    val extras = detailsExtras.readyDataOrNull()
    return copy(
        details = details.withAnimeUserRating(animeId, update.userRating),
        detailsExtras = LoadState.Ready(
            extras?.copy(rating = update.summary) ?: AnimeDetailsExtras(rating = update.summary),
        ),
    )
}

internal fun YummyDroidUiState.withRestoredAnimeRating(
    animeId: Long,
    previousDetails: LoadState<AnimeDetails>,
    previousExtras: LoadState<AnimeDetailsExtras>,
    error: String?,
): YummyDroidUiState {
    val stateWithError = if (error == null) this else copy(auth = auth.copy(error = error))
    if (!acceptsAnimeRatingUpdate(animeId)) return stateWithError
    return stateWithError.copy(
        details = previousDetails,
        detailsExtras = previousExtras,
    )
}

private fun YummyDroidUiState.acceptsAnimeRatingUpdate(animeId: Long): Boolean {
    val detailsRoute = route as? AppRoute.Details
    if (detailsRoute != null) return detailsRoute.animeId == animeId
    return details.readyDataOrNull()?.id == animeId
}

private fun LoadState<AnimeDetails>.withAnimeUserRating(
    animeId: Long,
    rating: Int?,
): LoadState<AnimeDetails> {
    val current = readyDataOrNull()?.takeIf { it.id == animeId } ?: return this
    return LoadState.Ready(current.copy(userRating = rating))
}
