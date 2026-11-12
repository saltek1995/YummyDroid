package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.ui.matchingVoiceKeyForGroup
import me.yummydroid.app.ui.preferredEpisodeVariant
import me.yummydroid.app.ui.sortedForPlayer

data class NextTitleSuggestion(
    val requestId: Long,
    val fromAnimeId: Long,
    val nextAnimeId: Long,
    val title: String,
)

/** Pin rating trust to the account/session which issued this background load. */
internal suspend fun AnimeDetailsLoadCoordinator.loadNextTitleDetails(
    animeId: Long,
    offlineOnly: Boolean,
    currentState: () -> YummyDroidUiState,
): LoadedAnimeDetails {
    val context = currentState().contentContext()
    return load(animeId, offlineOnly = offlineOnly) {
        context.profileId != null && currentState().contentContext() == context
    }
}

internal class NextTitlePlaybackCoordinator(
    private val scope: CoroutineScope,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val loadAnime: suspend (Long, Boolean) -> LoadedAnimeDetails,
    private val startPlayback: (LoadedAnimeDetails, VideoVariant) -> Unit,
    private val onUnavailable: (Long) -> Unit,
) {
    private data class Request(
        val id: Long,
        val route: AppRoute.Player,
        val context: ContentContext,
        val video: VideoVariant,
    )

    private data class HandledCompletion(
        val route: AppRoute.Player,
        val context: ContentContext,
        val video: VideoVariant,
    ) {
        fun matches(route: AppRoute.Player, context: ContentContext, video: VideoVariant): Boolean =
            this.route === route && this.context == context && this.video.hasSamePlaybackSourceAs(video)
    }

    private var nextRequestId = 0L
    private var request: Request? = null
    private var lookup: Job? = null
    private var candidate: Pair<LoadedAnimeDetails, VideoVariant>? = null
    private var handledCompletion: HandledCompletion? = null

    fun onPlaybackCompleted(video: VideoVariant): Boolean {
        val state = currentState()
        val currentAnimeVideos = state.videos.readyDataOrNull()
            ?.filter { it.animeId == video.animeId }
            ?.takeIf(List<VideoVariant>::isNotEmpty)
            ?: return false
        if (!state.acceptsPlaybackCompletion(video) || video.hasFollowingEpisodeIn(currentAnimeVideos)) return false
        val route = state.route as? AppRoute.Player ?: return false
        val context = state.contentContext()
        if (handledCompletion?.matches(route, context, video) == true) return true
        request?.takeIf { it.route === route && it.context == context && it.video.hasSamePlaybackSourceAs(video) }
            ?.let { return true }

        clearActiveSuggestion()
        lookup?.cancel()
        val started = Request(++nextRequestId, route, context, video)
        request = started
        handledCompletion = HandledCompletion(route, context, video)
        lookup = scope.launch {
            try {
                withTimeout(LookupTimeoutMs) { findSuggestion(started) }
            } catch (timeout: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                unavailableIfCurrent(started)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                unavailableIfCurrent(started)
            }
        }
        return true
    }

    fun confirm(requestId: Long) {
        val active = request?.takeIf { it.id == requestId } ?: return
        val selected = candidate ?: run {
            clearRequest(active)
            return
        }
        if (!isCurrent(active)) {
            clearRequest(active)
            return
        }
        clearRequest(active)
        startPlayback(selected.first, selected.second)
    }

    fun dismiss(requestId: Long) {
        val active = request?.takeIf { it.id == requestId } ?: return
        lookup?.cancel()
        clearRequest(active)
    }

    fun cancel() {
        lookup?.cancel()
        request = null
        lookup = null
        candidate = null
        handledCompletion = null
        updateState { state -> state.copy(nextTitleSuggestion = null) }
    }

    private suspend fun findSuggestion(active: Request) {
        if (!isCurrent(active)) return
        val initial = currentState()
        val current = initial.details.readyDataOrNull()?.takeIf { it.id == active.video.animeId }
            ?: loadAnime(active.video.animeId, initial.forcedOfflineMode).details.takeIf { it.id == active.video.animeId }
            ?: run {
                unavailableIfCurrent(active)
                return
            }
        val related = current.relatedAnime
        val currentIndex = related.indexOfFirst { it.id == active.video.animeId }
        val next = currentIndex.takeIf { it >= 0 }?.let { related.getOrNull(it + 1) } ?: run {
            unavailableIfCurrent(active)
            return
        }
        val stateBeforeLoad = currentState()
        if (!isCurrent(active)) return
        val loaded = loadAnime(next.id, stateBeforeLoad.forcedOfflineMode)
        if (loaded.details.id != next.id) {
            unavailableIfCurrent(active)
            return
        }
        val eligible = if (stateBeforeLoad.forcedOfflineMode || loaded.offlineMode) {
            loaded.videos.filter { it.animeId == next.id && it.isOfflineAvailable }
        } else {
            loaded.videos.filter { it.animeId == next.id }
        }
        val earliest = eligible.sortedForPlayer().firstOrNull()
            ?: run {
                unavailableIfCurrent(active)
                return
            }
        val preferredGroup = loaded.restoredVideoGroup ?: loaded.selectedVideoGroup
        val first = eligible.filter { it.matchingEpisodeKey == earliest.matchingEpisodeKey }
            .preferredEpisodeVariant(preferredGroup, eligible.matchingVoiceKeyForGroup(preferredGroup))
            ?: earliest
        if (!isCurrent(active)) return
        candidate = loaded to first
        updateState { state ->
            if (isCurrent(active, state)) state.copy(
                nextTitleSuggestion = NextTitleSuggestion(
                    active.id, active.video.animeId, next.id, loaded.details.title.ifBlank { next.title },
                ),
            ) else state
        }
    }

    private fun unavailableIfCurrent(active: Request) {
        if (!isCurrent(active)) return
        candidate = null
        updateState { state -> if (isCurrent(active, state)) state.copy(nextTitleSuggestion = null) else state }
        if (isCurrent(active)) onUnavailable(active.video.animeId)
    }

    private fun isCurrent(active: Request, state: YummyDroidUiState = currentState()): Boolean {
        return request === active && state.route === active.route && state.contentContext() == active.context &&
            state.acceptsPlaybackCompletion(active.video)
    }

    private fun clearActiveSuggestion() {
        candidate = null
        updateState { state -> state.copy(nextTitleSuggestion = null) }
    }

    private fun clearRequest(active: Request) {
        if (request !== active) return
        request = null
        lookup = null
        candidate = null
        updateState { state ->
            if (state.nextTitleSuggestion?.requestId == active.id) state.copy(nextTitleSuggestion = null) else state
        }
    }

    private companion object {
        const val LookupTimeoutMs = 10_000L
    }
}
