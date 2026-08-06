package me.yummydroid.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.isFullyReleased

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
        if (
            state.settings.autoMarkWatchedOnCompletedFinalEpisode &&
            state.auth.profile != null &&
            details.isFullyReleased() &&
            video.isFinalEpisodeFor(details, state.videos.readyListOrEmpty())
        ) {
            scheduleAutoSetListMark(video.animeId, UserAnimeListMark.Watched)
        }
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
