package me.yummydroid.app

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
