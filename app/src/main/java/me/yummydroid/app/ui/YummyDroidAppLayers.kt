package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty

internal class YummyDroidAppLayerRuntime(
    val actions: YummyDroidAppActions,
    val browseCoordinator: BrowseRootUiCoordinator,
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState>,
    val activeLayerFocusRequestNonce: Long,
    val activeLayerFocusNonce: Long,
    val isInPictureInPicture: Boolean,
    val canUsePictureInPicture: Boolean,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val onOpenAnimeFromCatalog: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onHomeBackToTopHandlerChange: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    val onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    val onRegisterModalInputActionHandler: (Any, ((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: (Any, (() -> Boolean)?) -> Unit,
    val onPlayerInputControllerChange: (PlayerInputController?) -> Unit,
)

@Composable
internal fun YummyDroidAppLayerHost(
    renderedLayers: List<AppScreenLayer>,
    exitingLayers: List<AppScreenLayer>,
    runtime: YummyDroidAppLayerRuntime,
) {
    renderedLayers.forEachIndexed { index, layer ->
        key(layer.key) {
            AppLayerScreen(
                layer = layer,
                active = index == renderedLayers.lastIndex,
                zIndex = index.toFloat(),
                visible = true,
                runtime = runtime,
            )
        }
    }

    exitingLayers.forEachIndexed { index, layer ->
        key("exiting:${layer.key}") {
            AppLayerScreen(
                layer = layer,
                active = false,
                zIndex = (renderedLayers.size + index).toFloat() + 1_000f,
                visible = false,
                runtime = runtime,
            )
        }
    }
}

@Composable
private fun AppLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    when (layer.key) {
        AppScreenKey.Home -> HomeLayerScreen(layer, active, zIndex, visible, runtime)
        is AppScreenKey.Details -> DetailsLayerScreen(layer, active, zIndex, visible, runtime)
        AppScreenKey.Player -> PlayerLayerScreen(layer, active, zIndex, visible, runtime)
    }
}

@Composable
private fun AppLayerContainer(
    zIndex: Float,
    visible: Boolean,
    scaleFrom: Float = 0.99f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .yummyAppearMotion(
                visible = visible,
                scaleFrom = scaleFrom,
            ),
    ) {
        content()
    }
}

@Composable
private fun HomeLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val actions = runtime.actions
    AppLayerContainer(
        zIndex = zIndex,
        visible = visible,
    ) {
        key(AppScreenKey.Home) {
            BrowseScreen(
                state = layer.state,
                browseCoordinator = runtime.browseCoordinator,
                activeFocusRequestNonce = if (active) runtime.activeLayerFocusRequestNonce else 0L,
                onRegisterHomeBackToTopHandler = if (active) {
                    runtime.onHomeBackToTopHandlerChange
                } else {
                    { _, _ -> }
                },
                onHomeBrowseBackStateChange = if (active) {
                    runtime.onHomeBrowseBackStateChange
                } else {
                    {}
                },
                onRegisterModalInputActionHandler = if (active) {
                    { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Home, handler) }
                } else {
                    {}
                },
                onRegisterDpadFocusRecoveryHandler = if (active) {
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(AppScreenKey.Home, handler) }
                } else {
                    {}
                },
                onQueryChange = if (active) actions.onQueryChange else { _ -> },
                onSearchSubmitted = if (active) actions.onSearchSubmitted else { _ -> },
                onSearchHistorySelected = if (active) actions.onSearchHistorySelected else { _ -> },
                onRefresh = if (active) actions.onRefresh else ({}),
                onLoadMoreAnime = if (active) actions.onLoadMoreAnime else ({}),
                onBrowseSectionChange = if (active) actions.onBrowseSectionChange else { _ -> },
                onFiltersChange = if (active) actions.onFiltersChange else { _ -> },
                onResetFilters = if (active) actions.onResetFilters else ({}),
                onOpenSettings = if (active) runtime.onOpenSettings else ({}),
                onOpenDownloads = if (active) runtime.onOpenDownloads else ({}),
                onClearDownloadHistory = if (active) actions.onClearDownloadHistory else ({}),
                onCancelDownload = if (active) actions.onCancelDownload else { _ -> },
                onPauseDownload = if (active) actions.onPauseDownload else { _ -> },
                onResumeDownload = if (active) actions.onResumeDownload else { _ -> },
                onOpenLogin = if (active) runtime.onOpenLogin else ({}),
                onOpenProfile = if (active) runtime.onOpenProfile else ({}),
                loginDialogOpen = runtime.loginDialogOpen,
                profileDialogOpen = runtime.profileDialogOpen,
                settingsDialogOpen = runtime.settingsDialogOpen,
                active = active,
                onOpenAnime = if (active) runtime.onOpenAnimeFromCatalog else { _ -> },
            )
        }
    }
}

@Composable
private fun DetailsLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val layerKey = layer.key as? AppScreenKey.Details ?: return
    val actions = runtime.actions
    val detailsScreenUiState = androidx.compose.runtime.remember(layerKey) {
        runtime.detailsScreenUiStates.getOrPut(layerKey) { DetailsScreenUiState() }
    }
    LaunchedEffect(active) {
        if (!active) {
            detailsScreenUiState.suppressInitialFocusOnReactivation = true
        }
    }
    val detailsFocusRequestNonce = if (
        active &&
        detailsScreenUiState.retainedFocusKey == null &&
        !detailsScreenUiState.suppressInitialFocusOnReactivation
    ) {
        runtime.activeLayerFocusRequestNonce
    } else {
        0L
    }
    val retainedDetailsFocusRequestNonce = if (active) runtime.activeLayerFocusNonce else 0L
    AppLayerContainer(
        zIndex = zIndex,
        visible = visible,
    ) {
        key(layerKey) {
            DetailsScreenModern(
                state = layer.state,
                screenUiState = detailsScreenUiState,
                activeFocusRequestNonce = detailsFocusRequestNonce,
                retainedFocusRequestNonce = retainedDetailsFocusRequestNonce,
                onRefresh = if (active) actions.onRefresh else ({}),
                onOpenAnime = if (active) actions.onOpenAnime else { _ -> },
                onOpenLogin = if (active) runtime.onOpenLogin else ({}),
                onGenreFilterSelected = if (active) actions.onFilterByGenre else { _, _ -> },
                onYearFilterSelected = if (active) actions.onFilterByYear else { _, _ -> },
                onStudioFilterSelected = if (active) actions.onFilterByStudio else { _, _ -> },
                onCreatorFilterSelected = if (active) actions.onFilterByCreator else { _, _ -> },
                onSelectVideoGroup = if (active) actions.onSelectVideoGroup else { _ -> },
                onPlayVideo = if (active) actions.onPlayVideo else { _ -> },
                onPlayVideoWithResumeChoice = if (active) actions.onPlayVideoWithResumeChoice else { _, _ -> },
                onPlayVideoAt = if (active) actions.onPlayVideoAt else { _, _ -> },
                onSelectAnimeListMark = if (active) actions.onSelectAnimeListMark else { _ -> },
                onToggleFavorite = if (active) actions.onToggleFavorite else ({}),
                onSetAnimeRating = if (active) actions.onSetAnimeRating else { _ -> },
                onAddAnimeComment = if (active) actions.onAddAnimeComment else { _ -> },
                onLoadMoreAnimeComments = if (active) actions.onLoadMoreAnimeComments else ({}),
                onToggleVideoSubscription = if (active) actions.onToggleVideoSubscription else { _ -> },
                onResolveSampledDownloadQualities = if (active) {
                    actions.onResolveSampledDownloadQualities
                } else {
                    { _, _ -> emptyMap() }
                },
                onDownloadAllVideos = if (active) actions.onDownloadAllVideos else { _ -> },
                onResetAnimeWatchProgress = if (active) actions.onResetAnimeWatchProgress else { _ -> },
                onRegisterModalInputActionHandler = if (active) {
                    { handler -> runtime.onRegisterModalInputActionHandler(layerKey, handler) }
                } else {
                    {}
                },
                onRegisterDpadFocusRecoveryHandler = if (active) {
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(layerKey, handler) }
                } else {
                    {}
                },
            )
        }
    }
}

@Composable
private fun PlayerLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val route = layer.state.route as? AppRoute.Player ?: return
    val actions = runtime.actions
    AppLayerContainer(
        zIndex = zIndex,
        visible = visible,
        scaleFrom = 1f,
    ) {
        key(AppScreenKey.Player) {
            PlayerScreen(
                animeTitle = route.animeTitle,
                video = route.video,
                interactive = active,
                settings = layer.state.settings,
                startPositionMs = route.startPositionMs,
                preferredQuality = route.preferredQuality,
                allVideos = layer.state.videos.readyListOrEmpty(),
                selectedGroup = layer.state.selectedVideoGroup,
                streamState = layer.state.playerStream,
                playbackMetadataLoading = layer.state.playbackMetadataLoading,
                resumeChoicePositionMs = route.resumeChoicePositionMs,
                isInPictureInPicture = runtime.isInPictureInPicture,
                forcedOfflineMode = layer.state.forcedOfflineMode,
                allowSubscriptions = layer.state.auth.profile != null &&
                    !layer.state.forcedOfflineMode &&
                    (layer.state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
                subscriptions = layer.state.detailsExtras.readyDataOrNull()?.subscriptions.orEmpty(),
                onSelectGroup = if (active) actions.onSelectVideoGroup else { _ -> },
                onPlayVideo = if (active) actions.onPlayVideo else { _ -> },
                onPlayVideoAt = if (active) actions.onPlayVideoAt else { _, _ -> },
                onPlayVideoAtQuality = if (active) actions.onPlayVideoAtQuality else { _, _, _ -> },
                onSelectPlaybackSource = if (active) actions.onSelectPlaybackSource else { _, _ -> },
                onChooseResumePosition = if (active) actions.onChoosePlayerResumePosition else { _ -> },
                onToggleVideoSubscription = if (active) actions.onTogglePlayerVideoSubscription else { _ -> },
                onRetry = if (active) actions.onRetryVideo else ({}),
                onPlaybackFailed = if (active) actions.onPlaybackFailed else { _, _, _ -> },
                onPlaybackStarted = if (active) actions.onPlaybackStarted else { _ -> },
                onPlaybackEnded = if (active) actions.onPlaybackEnded else { _ -> },
                onPlaybackProgress = if (active) actions.onPlaybackProgress else { _, _, _ -> },
                canUsePictureInPicture = active && runtime.canUsePictureInPicture,
                onEnterPictureInPicture = if (active) actions.onEnterPictureInPicture else ({}),
                onSettingsChange = if (active) actions.onSettingsChange else { _ -> },
                onBack = if (active) actions.onBack else ({}),
                onRegisterModalInputActionHandler = if (active) {
                    { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Player, handler) }
                } else {
                    {}
                },
                onRegisterPlayerInputActionHandler = if (active) {
                    runtime.onPlayerInputControllerChange
                } else {
                    {}
                },
            )
        }
    }
}
