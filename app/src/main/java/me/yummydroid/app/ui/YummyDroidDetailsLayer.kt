package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember

private data class DetailsLayerFocus(
    val screenUiState: DetailsScreenUiState,
    val initialRequestNonce: Long,
    val retainedRequestNonce: Long,
)

@Composable
private fun rememberDetailsLayerFocus(
    layerKey: AppScreenKey.Details,
    active: Boolean,
    runtime: YummyDroidAppLayerRuntime,
): DetailsLayerFocus {
    val screenUiState = remember(layerKey) {
        runtime.detailsScreenUiStates.getOrPut(layerKey) { DetailsScreenUiState() }
    }
    LaunchedEffect(active) {
        if (!active) screenUiState.suppressInitialFocusOnReactivation = true
    }
    val shouldRequestInitialFocus = active &&
        screenUiState.retainedFocusKey == null &&
        !screenUiState.suppressInitialFocusOnReactivation
    return DetailsLayerFocus(
        screenUiState = screenUiState,
        initialRequestNonce = activeLayerValue(shouldRequestInitialFocus, runtime.activeLayerFocusRequestNonce, 0L),
        retainedRequestNonce = activeLayerValue(active, runtime.activeLayerFocusNonce, 0L),
    )
}

@Composable
internal fun DetailsLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val layerKey = layer.key as? AppScreenKey.Details ?: return
    val actions = runtime.actions
    val focus = rememberDetailsLayerFocus(layerKey, active, runtime)
    AppLayerContainer(zIndex = zIndex, visible = visible) {
        key(layerKey) {
            DetailsScreenModern(
                state = layer.state,
                screenUiState = focus.screenUiState,
                activeFocusRequestNonce = focus.initialRequestNonce,
                retainedFocusRequestNonce = focus.retainedRequestNonce,
                onRefresh = activeLayerValue(active, actions.onRefresh, {}),
                onOpenAnime = activeLayerValue(active, actions.onOpenAnime, { _ -> }),
                onOpenLogin = activeLayerValue(active, runtime.onOpenLogin, {}),
                onGenreFilterSelected = activeLayerValue(active, actions.onFilterByGenre, { _, _ -> }),
                onYearFilterSelected = activeLayerValue(active, actions.onFilterByYear, { _, _ -> }),
                onStudioFilterSelected = activeLayerValue(active, actions.onFilterByStudio, { _, _ -> }),
                onCreatorFilterSelected = activeLayerValue(active, actions.onFilterByCreator, { _, _ -> }),
                onSelectVideoGroup = activeLayerValue(active, actions.onSelectVideoGroup, { _ -> }),
                onPlayVideo = activeLayerValue(active, actions.onPlayVideo, { _ -> }),
                onPlayVideoWithResumeChoice = activeLayerValue(
                    active,
                    actions.onPlayVideoWithResumeChoice,
                    { _, _ -> },
                ),
                onPlayVideoAt = activeLayerValue(active, actions.onPlayVideoAt, { _, _ -> }),
                onSelectAnimeListMark = activeLayerValue(active, actions.onSelectAnimeListMark, { _ -> }),
                onToggleFavorite = activeLayerValue(active, actions.onToggleFavorite, {}),
                onSetAnimeRating = activeLayerValue(active, actions.onSetAnimeRating, { _ -> }),
                onAddAnimeComment = activeLayerValue(active, actions.onAddAnimeComment, { _ -> }),
                onLoadMoreAnimeComments = activeLayerValue(active, actions.onLoadMoreAnimeComments, {}),
                onToggleVideoSubscription = activeLayerValue(
                    active,
                    actions.onToggleVideoSubscription,
                    { _ -> },
                ),
                onResolveSampledDownloadQualities = activeLayerValue(
                    active,
                    actions.onResolveSampledDownloadQualities,
                    { _, _ -> emptyMap() },
                ),
                onDownloadAllVideos = activeLayerValue(active, actions.onDownloadAllVideos, { _ -> }),
                onResetAnimeWatchProgress = activeLayerValue(
                    active,
                    actions.onResetAnimeWatchProgress,
                    { _ -> },
                ),
                onRegisterModalInputActionHandler = activeLayerValue(
                    active,
                    { handler -> runtime.onRegisterModalInputActionHandler(layerKey, handler) },
                    {},
                ),
                onRegisterDpadFocusRecoveryHandler = activeLayerValue(
                    active,
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(layerKey, handler) },
                    {},
                ),
            )
        }
    }
}
