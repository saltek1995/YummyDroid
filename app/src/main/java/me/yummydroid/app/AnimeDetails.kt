package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.RepositoryContent
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PlaybackSelection
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.matchingVoiceKey
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
    val isAuthenticated: Boolean,
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
    private val addComment: suspend (animeId: Long, text: String) -> AnimeComment?,
    private val commentsPageSize: Int = DEFAULT_COMMENTS_PAGE_SIZE,
) {
    suspend fun load(request: AnimeDetailsExtrasLoadRequest): AnimeDetailsExtras {
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
        return AnimeDetailsExtras(
            recommendations = recommendations,
            rating = rating,
        ).withLoadedAnimeComments(comments, commentsPageSize)
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

    private companion object {
        const val DEFAULT_COMMENTS_PAGE_SIZE = 20
    }
}

// AnimeDetailsLoadCoordinator
internal data class LoadedAnimeDetails(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val offlineMode: Boolean,
    val selectedVideoGroup: String?,
    val restoredVideoGroup: String? = null,
)

internal class AnimeDetailsLoadCoordinator(
    private val fetchAnimeWithVideos: suspend (Long) -> RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>>,
    private val fetchAnimeWithVideosByAlias: suspend (String) -> RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>>,
    private val resolveEffectiveRating: suspend (
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ) -> Int?,
    private val saveAnimeSummary: (Anime) -> Unit,
    private val readPlaybackSelection: (Long) -> PlaybackSelection? = { null },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fetchOfflineAnimeWithVideos: suspend (Long) -> RepositoryContent<Pair<AnimeDetails, List<VideoVariant>>> = {
        error("Offline content loader is not configured")
    },
) {
    suspend fun load(
        animeId: Long,
        animeAlias: String? = null,
        offlineOnly: Boolean = false,
        isAuthenticated: () -> Boolean,
    ): LoadedAnimeDetails {
        val loaded = withContext(ioDispatcher) {
            val content = if (offlineOnly) {
                fetchOfflineAnimeWithVideos(animeId)
            } else if (animeAlias.isNullOrBlank()) {
                fetchAnimeWithVideos(animeId)
            } else {
                fetchAnimeWithVideosByAlias(animeAlias)
            }
            val (details, videos) = content.value
            val offlineMode = offlineOnly || content.offlineFallback
            val playbackSelection = readPlaybackSelection(details.id)
            val initialVideoSelection = selectInitialVideoSelection(
                videos = videos,
                offlineMode = offlineMode,
                playbackSelection = playbackSelection,
            )
            LoadedAnimeDetails(
                details = details,
                videos = videos,
                offlineMode = offlineMode,
                selectedVideoGroup = initialVideoSelection.groupKey,
                restoredVideoGroup = initialVideoSelection.restoredGroupKey,
            )
        }
        val effectiveRating = resolveEffectiveRating(
            loaded.details.id,
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
    offlineMode: Boolean,
    playbackSelection: PlaybackSelection? = null,
): String? = selectInitialVideoSelection(videos, offlineMode, playbackSelection).groupKey

private data class InitialVideoSelection(
    val groupKey: String?,
    val restoredGroupKey: String?,
)

private fun selectInitialVideoSelection(
    videos: List<VideoVariant>,
    offlineMode: Boolean,
    playbackSelection: PlaybackSelection?,
): InitialVideoSelection {
    val playableVideos = if (offlineMode) {
        videos.filter(VideoVariant::isOfflineAvailable)
    } else {
        videos
    }
    playableVideos.preferredPlaybackSelection(playbackSelection)?.groupKey?.let { restoredGroup ->
        return InitialVideoSelection(groupKey = restoredGroup, restoredGroupKey = restoredGroup)
    }
    playableVideos.siteDefaultVideo()?.groupKey?.let { defaultGroup ->
        return InitialVideoSelection(groupKey = defaultGroup, restoredGroupKey = null)
    }
    val restoredGroup = playableVideos.preferredPlaybackSelection(playbackSelection)?.groupKey
    return InitialVideoSelection(
        groupKey = restoredGroup ?: playableVideos.siteDefaultVideo()?.groupKey,
        restoredGroupKey = restoredGroup,
    )
}

internal fun List<VideoVariant>.preferredPlaybackSelection(selection: PlaybackSelection?): VideoVariant? {
    val preferred = selection ?: return null
    val voiceCandidates = if (preferred.voiceKey.isBlank()) {
        this
    } else {
        filter { it.matchingVoiceKey == preferred.voiceKey }
    }
    if (voiceCandidates.isEmpty()) return null
    return voiceCandidates.firstOrNull { it.matchesSourceSelectionKey(preferred.sourceKey) }
        ?: voiceCandidates.firstOrNull { it.groupKey == preferred.groupKey }
        ?: voiceCandidates.siteDefaultVideo()
}

internal fun resolveSelectedPlaybackGroup(
    videos: List<VideoVariant>,
    playbackSelection: PlaybackSelection?,
    progressGroupKey: String?,
    currentGroupKey: String?,
    groupAtRefreshStart: String? = currentGroupKey,
): String? {
    fun String?.validGroup(): String? = takeIf { key ->
        !key.isNullOrBlank() && videos.any { it.groupKey == key }
    }

    val currentGroup = currentGroupKey.validGroup()
    val changedDuringRefresh = currentGroup?.takeIf { it != groupAtRefreshStart }
    return changedDuringRefresh
        ?: videos.preferredPlaybackSelection(playbackSelection)?.groupKey
        ?: progressGroupKey.validGroup()
        ?: currentGroup
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
    val progressGroup = playbackProgress
        ?.takeIf { progress -> progress.animeId == loaded.details.id }
        ?.groupKey
        ?.takeIf { groupKey -> loaded.videos.any { video -> video.groupKey == groupKey } }
    return copy(
        route = AppRoute.Details(loaded.details.id),
        details = LoadState.Ready(loaded.details),
        videos = LoadState.Ready(loaded.videos),
        forcedOfflineMode = forcedOfflineMode || loaded.offlineMode,
        selectedVideoGroup = loaded.restoredVideoGroup ?: progressGroup ?: loaded.selectedVideoGroup,
        detailsExtras = if (loaded.offlineMode) LoadState.Ready(AnimeDetailsExtras()) else detailsExtras,
        animeMark = if (loaded.offlineMode) LoadState.Ready(null) else animeMark,
    ).withOfflineDetailsState()
}

internal fun YummyDroidUiState.withLoadedAnimeDetailsExtras(
    animeId: Long,
    loaded: AnimeDetailsExtras,
): YummyDroidUiState {
    if ((route as? AppRoute.Details)?.animeId != animeId) return this
    return copy(detailsExtras = LoadState.Ready(loaded))
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
    private val onMutationFailure: (String) -> Unit,
    private val onAutoMarkFailure: (Throwable) -> Unit,
) {
    private val loadOperations = LatestStateOperationCoordinator()
    private val markMutations = SerialStateOperationCoordinator()

    fun load(animeId: Long) {
        cancelLoad()
        val state = currentState()
        if (state.forcedOfflineMode || state.auth.profile == null) {
            updateState { it.copy(animeMark = LoadState.Ready(null)) }
            return
        }

        val profileId = state.auth.profile.id
        updateState { current ->
            if (current.acceptsAnimeMarkLoad(animeId, profileId)) {
                current.copy(animeMark = LoadState.Loading)
            } else {
                current
            }
        }
        loadOperations.launchLatest(scope) { lease ->
            runCatching { getAnimeMark(animeId) }
                .onSuccess { mark ->
                    if (lease.isCurrent) setMarkState(animeId, profileId, LoadState.Ready(mark))
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (lease.isCurrent) {
                        setMarkState(animeId, profileId, LoadState.Error(throwable.userMessage()))
                    }
                }
        }
    }

    fun cancelLoad() {
        loadOperations.cancel()
    }

    fun clear() {
        cancelLoad()
        markMutations.cancel()
    }

    fun toggleListMark(mark: UserAnimeListMark) {
        val animeId = authenticatedDetailsAnimeId() ?: return
        val state = currentState()
        val profileId = state.auth.profile?.id ?: return
        val previousMarkState = state.animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        val optimisticMark = if (current.list == mark) {
            current.copy(list = null)
        } else {
            current.copy(list = mark)
        }
        if (!setMarkState(animeId, profileId, LoadState.Ready(optimisticMark))) return
        launchMutation { lease ->
            runCatching {
                if (current.list == mark) {
                    removeAnimeListMark(animeId)
                } else {
                    setAnimeListMark(animeId, mark)
                }
            }
                .onSuccess { updatedMark ->
                    if (lease.isCurrent) {
                        setMarkState(animeId, profileId, LoadState.Ready(updatedMark))
                    }
                }
                .onFailure { throwable ->
                    if (!lease.isCurrent) return@onFailure
                    handleMutationFailure(
                        animeId = animeId,
                        profileId = profileId,
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
        val state = currentState()
        val profileId = state.auth.profile?.id ?: return
        val previousMarkState = state.animeMark
        val current = previousMarkState.readyDataOrNull() ?: UserAnimeMark()
        if (!setMarkState(animeId, profileId, LoadState.Ready(current.copy(isFavorite = !current.isFavorite)))) {
            return
        }
        launchMutation { lease ->
            runCatching { setFavorite(animeId, !current.isFavorite) }
                .onSuccess { updatedMark ->
                    if (lease.isCurrent) {
                        setMarkState(animeId, profileId, LoadState.Ready(updatedMark))
                    }
                }
                .onFailure { throwable ->
                    if (!lease.isCurrent) return@onFailure
                    handleMutationFailure(
                        animeId = animeId,
                        profileId = profileId,
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

    private fun setMarkState(
        animeId: Long,
        profileId: Long,
        animeMark: LoadState<UserAnimeMark?>,
    ): Boolean {
        var accepted = false
        updateState { state ->
            if (state.acceptsAnimeMarkLoad(animeId, profileId)) {
                accepted = true
                state.copy(animeMark = animeMark)
            } else {
                state
            }
        }
        if (accepted) cacheDetailsRouteState(animeId)
        return accepted
    }

    private fun handleMutationFailure(
        animeId: Long,
        profileId: Long,
        previousMarkState: LoadState<UserAnimeMark?>,
        throwable: Throwable,
        retry: suspend () -> Unit,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!setMarkState(animeId, profileId, previousMarkState)) return
        if (throwable is CaptchaRequiredException) {
            requestCaptchaRetry(throwable, retry)
            return
        }
        onMutationFailure(throwable.userMessage())
    }

    private fun launchMutation(block: suspend (StateOperationLease) -> Unit) {
        markMutations.launch(scope, block)
    }

    private fun scheduleAutoSetListMark(
        animeId: Long,
        mark: UserAnimeListMark,
        preserveWatched: Boolean = false,
    ) {
        val profileId = currentState().auth.profile?.id ?: return
        launchMutation { lease ->
            runCatching {
                val state = currentState()
                if (state.forcedOfflineMode || state.auth.profile?.id != profileId) return@runCatching null

                val stateMark = state.animeMark.readyDataOrNull()
                    ?.takeIf { state.details.readyDataOrNull()?.id == animeId }
                if (stateMark.alreadyHas(mark, preserveWatched)) return@runCatching null

                val currentMark = stateMark ?: getAnimeMark(animeId)
                if (currentMark.alreadyHas(mark, preserveWatched)) return@runCatching null
                setAnimeListMark(animeId, mark)
            }
                .onSuccess { updatedMark ->
                    if (updatedMark == null) return@onSuccess
                    if (!lease.isCurrent) return@onSuccess
                    updateState { current ->
                        if (current.acceptsAutoAnimeMark(animeId, profileId)) {
                            current.copy(animeMark = LoadState.Ready(updatedMark))
                        } else {
                            current
                        }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (lease.isCurrent) onAutoMarkFailure(throwable)
                }
        }
    }
}

private fun YummyDroidUiState.acceptsAnimeMarkLoad(animeId: Long, profileId: Long): Boolean {
    return (route as? AppRoute.Details)?.animeId == animeId && auth.profile?.id == profileId
}

private fun YummyDroidUiState.acceptsAutoAnimeMark(animeId: Long, profileId: Long): Boolean {
    return details.readyDataOrNull()?.id == animeId && auth.profile?.id == profileId
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
    internal val accountGeneration: Long,
    internal val userId: Long?,
    internal val mutationId: Long,
)

internal data class AnimeRatingUpdate(
    val summary: AnimeRatingSummary,
    val userRating: Int?,
    val accepted: Boolean = true,
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
    private val latestMutationIds = mutableMapOf<Long, Long>()
    private var activeUserId: Long? = null
    private var accountGeneration = 0L
    private var mutationSequence = 0L

    suspend fun restore(userId: Long?) {
        val generation = ++accountGeneration
        knownRatings.clear()
        latestMutationIds.clear()
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
        latestMutationIds.clear()
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
        val mutationId = ++mutationSequence
        val staged = StagedAnimeRating(
            animeId = animeId,
            requestedRating = rating,
            optimisticRating = rating.normalizedRating(),
            hadPreviousRating = knownRatings.containsKey(animeId),
            previousRating = knownRatings[animeId],
            accountGeneration = accountGeneration,
            userId = activeUserId,
            mutationId = mutationId,
        )
        latestMutationIds[animeId] = mutationId
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
            val accepted = isCurrent(staged)
            if (accepted) {
                knownRatings[staged.animeId] = selectedRating
                persistBestEffort()
            }
            AnimeRatingUpdate(
                summary = summary.copy(userRating = selectedRating),
                userRating = selectedRating,
                accepted = accepted,
            )
        } catch (throwable: Throwable) {
            if (isCurrent(staged)) {
                restoreStagedRating(staged)
                persistBestEffort()
            }
            throw throwable
        }
    }

    internal fun isCurrent(staged: StagedAnimeRating): Boolean {
        return staged.accountGeneration == accountGeneration &&
            staged.userId == activeUserId &&
            latestMutationIds[staged.animeId] == staged.mutationId
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
): YummyDroidUiState {
    if (!acceptsAnimeRatingUpdate(animeId)) return this
    return copy(
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

// AnimeCommentSubmissionCoordinator
internal class AnimeCommentSubmissionCoordinator(
    private val scope: CoroutineScope,
    private val operations: SerialStateOperationCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val send: suspend (Long, String) -> AnimeComment?,
    private val onSent: (Long, AnimeComment?) -> Unit,
    private val onFailure: (AnimeCommentSubmission, Throwable) -> Unit,
) {
    fun submit(animeId: Long, profileId: Long, text: String) {
        val state = currentState()
        if (!state.acceptsCommentSubmission(animeId, profileId) || text.isBlank()) return
        if (state.commentSubmission?.status == CommentSubmissionStatus.Sending) return
        val request = AnimeCommentSubmission(animeId, profileId, text.trim())
        updateState { it.copy(commentSubmission = request) }
        operations.launch(scope) {
            try {
                val comment = send(animeId, request.text)
                currentCoroutineContext().ensureActive()
                if (currentState().commentSubmission !== request) return@launch
                finish(request, CommentSubmissionStatus.Sent)
                if (currentState().acceptsCommentSubmission(animeId, profileId)) onSent(animeId, comment)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (currentState().commentSubmission !== request) return@launch
                finish(request, CommentSubmissionStatus.Failed)
                if (currentState().acceptsCommentSubmission(animeId, profileId)) onFailure(request, failure)
            }
        }.invokeOnCompletion { finish(request, CommentSubmissionStatus.Failed) }
    }

    private fun finish(request: AnimeCommentSubmission, status: CommentSubmissionStatus) {
        updateState { state ->
            if (state.commentSubmission !== request) state
            else state.copy(commentSubmission = request.copy(status = status))
        }
    }
}

private fun YummyDroidUiState.acceptsCommentSubmission(animeId: Long, profileId: Long): Boolean =
    !forcedOfflineMode && auth.profile?.id == profileId && (route as? AppRoute.Details)?.animeId == animeId

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
    private val updateCachedPlaybackProgress: (
        PlaybackProgress,
        List<PlaybackProgress>,
        PlaybackSelection?,
    ) -> Unit,
    private val refreshPlaybackProgressFromSite: (Long) -> Unit,
    private val restoreNavigationEntry: (NavigationEntry, List<NavigationEntry>, Boolean) -> Unit,
    private val authenticatedDetailsAnimeId: () -> Long?,
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
                val loaded = animeDetailsLoadCoordinator.load(animeId, animeAlias, currentState().forcedOfflineMode) {
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
                if (currentState().forcedOfflineMode) {
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

    private val commentSubmissionCoordinator = AnimeCommentSubmissionCoordinator(
        scope = scope,
        operations = commentMutations,
        currentState = currentState,
        updateState = updateState,
        send = animeDetailsExtrasCoordinator::submitComment,
        onSent = { animeId, comment ->
            if (comment != null) {
                updateState { state ->
                    val extras = state.detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
                    state.copy(detailsExtras = LoadState.Ready(extras.withAddedAnimeComment(comment)))
                }
                cacheDetailsRouteState(animeId)
            }
        },
        onFailure = ::handleCommentSubmissionFailure,
    )

    fun addAnimeComment(text: String) {
        val animeId = authenticatedDetailsAnimeId() ?: return
        val profileId = currentState().auth.profile?.id ?: return
        commentSubmissionCoordinator.submit(animeId, profileId, text)
    }

    private fun handleCommentSubmissionFailure(request: AnimeCommentSubmission, failure: Throwable) {
        if (!requestCaptchaRetry(failure) {
                commentSubmissionCoordinator.submit(request.animeId, request.profileId, request.text)
            }
        ) showNotice(failure.userMessage())
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
        val currentGroupKey = selectedVideoGroup
            ?.takeIf { groupKey -> videos.readyListOrEmpty().any { it.groupKey == groupKey } }
        return copy(
            selectedVideoGroup = currentGroupKey ?: progressGroupKey,
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
        val groupAtRefreshStart = currentState().selectedVideoGroup
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            val (progress, history, selection) = withContext(Dispatchers.IO) {
                Triple(
                    playbackProgressStorage.read(animeId),
                    playbackProgressStorage.readAnimeHistory(animeId),
                    playbackProgressStorage.readSelection(animeId),
                )
            }
            if (!lease.isCurrent) return@launchLatest
            if (progress != null) updateCachedPlaybackProgress(progress, history, selection)
            updateState { state ->
                state.withRefreshedPlaybackHistory(
                    animeId = animeId,
                    progress = progress,
                    history = history,
                    selection = selection,
                    groupAtRefreshStart = groupAtRefreshStart,
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

internal class AnimeRatingStateRuntime(
    private val scope: CoroutineScope,
    private val coordinator: AnimeRatingCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val authenticatedDetailsAnimeId: () -> Long?,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val showErrorNotice: (String) -> Unit,
) {
    private val mutations = SerialStateOperationCoordinator()

    fun cancel() {
        mutations.cancel()
    }

    fun setRating(rating: Int?) {
        if (currentState().forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeId() ?: return
        val operationState = currentState()
        val profileId = operationState.auth.profile?.id ?: return
        val previousDetails = operationState.details
        val previousExtras = operationState.detailsExtras
        val stagedRating = coordinator.stage(animeId, rating)
        updateState { state ->
            state.withOptimisticAnimeRating(animeId, stagedRating.optimisticRating)
        }
        cacheDetailsRouteState(animeId)
        mutations.launch(scope) { lease ->
            runCatching { coordinator.submit(stagedRating) }
                .onSuccess { update ->
                    if (!lease.isCurrent || !update.accepted || !acceptsResult(animeId, profileId, stagedRating)) {
                        return@onSuccess
                    }
                    updateState { state -> state.withConfirmedAnimeRating(animeId, update) }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent || !acceptsResult(animeId, profileId, stagedRating)) {
                        return@onFailure
                    }
                    updateState { state ->
                        state.withRestoredAnimeRating(
                            animeId = animeId,
                            previousDetails = previousDetails,
                            previousExtras = previousExtras,
                        )
                    }
                    cacheDetailsRouteState(animeId)
                    if (throwable is CaptchaRequiredException) {
                        requestCaptchaRetry(throwable) { setRating(rating) }
                    } else {
                        showErrorNotice(throwable.userMessage())
                    }
                }
        }
    }

    private fun acceptsResult(
        animeId: Long,
        profileId: Long,
        stagedRating: StagedAnimeRating,
    ): Boolean {
        val current = currentState()
        return coordinator.isCurrent(stagedRating) &&
            current.auth.profile?.id == profileId &&
            (current.route as? AppRoute.Details)?.animeId == animeId
    }
}
