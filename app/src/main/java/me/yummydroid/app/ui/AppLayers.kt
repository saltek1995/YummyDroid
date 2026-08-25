package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty

// AppScreenLayerModels
internal data class AppScreenLayer(
    val key: AppScreenKey,
    val state: YummyDroidUiState,
)

internal sealed interface AppScreenKey {
    data object Home : AppScreenKey
    data class Details(val animeId: Long) : AppScreenKey
    data object Player : AppScreenKey
}

internal const val APP_LAYER_STACK_LIMIT = 40

// AppScreenLayerReducer
internal fun List<AppScreenLayer>.syncedWith(state: YummyDroidUiState): List<AppScreenLayer> {
    return when (val route = state.route) {
        AppRoute.Home -> syncHomeLayer(state)
        is AppRoute.Details -> syncDetailsLayer(state, route.animeId)
        is AppRoute.Player -> syncPlayerLayer(state)
    }
}

private fun List<AppScreenLayer>.syncHomeLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    val updatedLayer = AppScreenLayer(AppScreenKey.Home, state)
    val existingIndex = indexOfLast { it.key == AppScreenKey.Home }
    return if (existingIndex >= 0) {
        take(existingIndex) + updatedLayer
    } else {
        listOf(updatedLayer)
    }
}

private fun List<AppScreenLayer>.syncDetailsLayer(
    state: YummyDroidUiState,
    animeId: Long,
): List<AppScreenLayer> {
    val key = AppScreenKey.Details(animeId)
    val baseLayers = ensureHomeLayer(state)
    return baseLayers.replaceTailFrom(key, AppScreenLayer(key, state))
        .trimAppScreenLayers()
}

private fun List<AppScreenLayer>.ensureHomeLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    if (any { it.key == AppScreenKey.Home }) return this
    return listOf(
        AppScreenLayer(
            key = AppScreenKey.Home,
            state = state.copy(route = AppRoute.Home),
        ),
    ) + this
}

private fun List<AppScreenLayer>.syncPlayerLayer(
    state: YummyDroidUiState,
): List<AppScreenLayer> {
    return replaceTailFrom(
        key = AppScreenKey.Player,
        updatedLayer = AppScreenLayer(AppScreenKey.Player, state),
    ).trimAppScreenLayers()
}

private fun List<AppScreenLayer>.replaceTailFrom(
    key: AppScreenKey,
    updatedLayer: AppScreenLayer,
): List<AppScreenLayer> {
    val existingIndex = indexOfLast { it.key == key }
    return if (existingIndex >= 0) {
        take(existingIndex) + updatedLayer
    } else {
        this + updatedLayer
    }
}

internal fun List<AppScreenLayer>.trimAppScreenLayers(): List<AppScreenLayer> {
    if (size <= APP_LAYER_STACK_LIMIT) return this
    val homeLayer = firstOrNull { it.key == AppScreenKey.Home }
    val tailLimit = APP_LAYER_STACK_LIMIT - if (homeLayer != null) 1 else 0
    val tail = filterNot { it.key == AppScreenKey.Home }
        .takeLast(tailLimit.coerceAtLeast(0))
    return if (homeLayer != null) listOf(homeLayer) + tail else tail
}

// YummyDroidAppLayers
internal class YummyDroidAppLayerRuntime(
    val actions: YummyDroidAppActions,
    val browseCoordinator: BrowseRootUiCoordinator,
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState>,
    val activeLayerFocusRequestNonce: Long,
    val activeLayerFocusNonce: Long,
    val isInPictureInPicture: Boolean,
    val canUsePictureInPicture: Boolean,
    val topAppModal: AppModalBackTarget?,
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
    val interactive = active && runtime.topAppModal == null
    when (layer.key) {
        AppScreenKey.Home -> HomeLayerScreen(layer, interactive, zIndex, visible, runtime)
        is AppScreenKey.Details -> DetailsLayerScreen(layer, interactive, zIndex, visible, runtime)
        AppScreenKey.Player -> PlayerLayerScreen(layer, interactive, zIndex, visible, runtime)
    }
}

@Composable
internal fun AppLayerContainer(
    zIndex: Float,
    visible: Boolean,
    active: Boolean,
    scaleFrom: Float = 0.99f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .focusProperties { canFocus = active }
            .focusGroup()
            .yummyAppearMotion(visible = visible, scaleFrom = scaleFrom),
    ) {
        CompositionLocalProvider(LocalUiControlEffectsEnabled provides active) {
            content()
        }
    }
}

internal fun <T> activeLayerValue(active: Boolean, activeValue: T, inactiveValue: T): T =
    if (active) activeValue else inactiveValue

// YummyDroidAppLayerState
@Stable
internal class YummyDroidAppLayerState {
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState> =
        mutableStateMapOf()
    var appLayers by mutableStateOf(emptyList<AppScreenLayer>())
    var exitingAppLayers by mutableStateOf(emptyList<AppScreenLayer>())
}

internal data class YummyDroidAppLayerSnapshot(
    val renderedLayers: List<AppScreenLayer>,
    val exitingLayers: List<AppScreenLayer>,
    val activeLayerKey: AppScreenKey?,
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState>,
)

@Composable
internal fun rememberYummyDroidAppLayerSnapshot(
    state: YummyDroidUiState,
): YummyDroidAppLayerSnapshot {
    val layerState = remember { YummyDroidAppLayerState() }
    val renderedLayers = layerState.appLayers.syncedWith(state)
    val renderedLayerKeys = renderedLayers.map { layer -> layer.key }.toSet()
    val pendingExitingLayers = layerState.appLayers.filter { layer -> layer.key !in renderedLayerKeys }
    val displayedExitingLayers = (layerState.exitingAppLayers + pendingExitingLayers)
        .filter { layer -> layer.key !in renderedLayerKeys }
        .distinctBy { layer -> layer.key }

    SideEffect {
        if (layerState.exitingAppLayers != displayedExitingLayers) {
            layerState.exitingAppLayers = displayedExitingLayers
        }
        if (layerState.appLayers != renderedLayers) {
            layerState.appLayers = renderedLayers
        }
    }
    RetainActiveLayerState(
        layerState = layerState,
        renderedLayerKeys = renderedLayerKeys,
        displayedExitingLayers = displayedExitingLayers,
    )

    return YummyDroidAppLayerSnapshot(
        renderedLayers = renderedLayers,
        exitingLayers = displayedExitingLayers,
        activeLayerKey = renderedLayers.lastOrNull()?.key,
        detailsScreenUiStates = layerState.detailsScreenUiStates,
    )
}

@Composable
private fun RetainActiveLayerState(
    layerState: YummyDroidAppLayerState,
    renderedLayerKeys: Set<AppScreenKey>,
    displayedExitingLayers: List<AppScreenLayer>,
) {
    val displayedExitingKeys = displayedExitingLayers.map { layer -> layer.key }
    LaunchedEffect(renderedLayerKeys, displayedExitingKeys) {
        val retainedDetailsKeys = (renderedLayerKeys + displayedExitingKeys)
            .filterIsInstance<AppScreenKey.Details>()
            .toSet()
        layerState.detailsScreenUiStates.keys.toList().forEach { key ->
            if (key !in retainedDetailsKeys) {
                layerState.detailsScreenUiStates.remove(key)
            }
        }
    }
    LaunchedEffect(displayedExitingKeys) {
        if (displayedExitingKeys.isEmpty()) return@LaunchedEffect
        val exitingKeys = displayedExitingKeys.toSet()
        delay(YUMMY_FADE_OUT_MS.toLong())
        layerState.exitingAppLayers = layerState.exitingAppLayers.filterNot { layer ->
            layer.key in exitingKeys
        }
    }
}

// YummyDroidAppModalState
internal enum class AppModalBackTarget {
    Update,
    LocalHistoryMerge,
    Settings,
    Profile,
    Login,
}

internal fun resolveAppModalBackTarget(
    pendingUpdateVisible: Boolean,
    settingsDialogOpen: Boolean,
    profileDialogOpen: Boolean,
    loginDialogOpen: Boolean,
    localHistoryMergePromptVisible: Boolean = false,
): AppModalBackTarget? = when {
    pendingUpdateVisible -> AppModalBackTarget.Update
    localHistoryMergePromptVisible -> AppModalBackTarget.LocalHistoryMerge
    settingsDialogOpen -> AppModalBackTarget.Settings
    profileDialogOpen -> AppModalBackTarget.Profile
    loginDialogOpen -> AppModalBackTarget.Login
    else -> null
}

@Stable
internal class YummyDroidAppModalState {
    var loginDialogOpen by mutableStateOf(false)
    var profileDialogOpen by mutableStateOf(false)
    var settingsDialogOpen by mutableStateOf(false)
    var autoUpdatePromptDismissed by mutableStateOf(false)

    fun openLogin() {
        closeAllDialogs()
        loginDialogOpen = true
    }

    fun openProfile() {
        closeAllDialogs()
        profileDialogOpen = true
    }

    fun openSettings() {
        closeAllDialogs()
        settingsDialogOpen = true
    }

    fun openProfileNotifications() {
        openProfile()
    }

    fun closeTopModal(pendingUpdateVisible: Boolean): Boolean {
        val target = resolveAppModalBackTarget(
            pendingUpdateVisible = pendingUpdateVisible,
            settingsDialogOpen = settingsDialogOpen,
            profileDialogOpen = profileDialogOpen,
            loginDialogOpen = loginDialogOpen,
        ) ?: return false
        when (target) {
            AppModalBackTarget.Update -> autoUpdatePromptDismissed = true
            AppModalBackTarget.LocalHistoryMerge -> return false
            AppModalBackTarget.Settings -> settingsDialogOpen = false
            AppModalBackTarget.Profile -> profileDialogOpen = false
            AppModalBackTarget.Login -> loginDialogOpen = false
        }
        return true
    }

    fun closeAllDialogs() {
        loginDialogOpen = false
        profileDialogOpen = false
        settingsDialogOpen = false
    }
}

@Composable
internal fun rememberYummyDroidAppModalState(
    openProfileNotificationsRequest: Long,
    onSettingsOpened: () -> Unit,
): YummyDroidAppModalState {
    val modalState = remember { YummyDroidAppModalState() }
    LaunchedEffect(openProfileNotificationsRequest) {
        if (openProfileNotificationsRequest > 0L) {
            modalState.openProfileNotifications()
        }
    }
    LaunchedEffect(modalState.settingsDialogOpen) {
        if (modalState.settingsDialogOpen) {
            onSettingsOpened()
        }
    }
    return modalState
}

// YummyDroidDetailsLayer
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
    interactive: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val layerKey = layer.key as? AppScreenKey.Details ?: return
    val actions = runtime.actions
    val focus = rememberDetailsLayerFocus(layerKey, interactive, runtime)
    AppLayerContainer(zIndex = zIndex, visible = visible, active = interactive) {
        key(layerKey) {
            DetailsScreenModern(
                state = layer.state,
                screenUiState = focus.screenUiState,
                interactive = interactive,
                activeFocusRequestNonce = focus.initialRequestNonce,
                retainedFocusRequestNonce = focus.retainedRequestNonce,
                onRefresh = activeLayerValue(interactive, actions.onRefresh, {}),
                onOpenAnime = activeLayerValue(interactive, actions.onOpenAnime, { _ -> }),
                onOpenLogin = activeLayerValue(interactive, runtime.onOpenLogin, {}),
                onGenreFilterSelected = activeLayerValue(interactive, actions.onFilterByGenre, { _, _ -> }),
                onYearFilterSelected = activeLayerValue(interactive, actions.onFilterByYear, { _, _ -> }),
                onStudioFilterSelected = activeLayerValue(interactive, actions.onFilterByStudio, { _, _ -> }),
                onCreatorFilterSelected = activeLayerValue(interactive, actions.onFilterByCreator, { _, _ -> }),
                onSelectVideoGroup = activeLayerValue(interactive, actions.onSelectVideoGroup, { _ -> }),
                onPlayVideo = activeLayerValue(interactive, actions.onPlayVideo, { _ -> }),
                onPlayVideoWithResumeChoice = activeLayerValue(
                    interactive,
                    actions.onPlayVideoWithResumeChoice,
                    { _, _ -> },
                ),
                onPlayVideoAt = activeLayerValue(interactive, actions.onPlayVideoAt, { _, _ -> }),
                onSelectAnimeListMark = activeLayerValue(interactive, actions.onSelectAnimeListMark, { _ -> }),
                onToggleFavorite = activeLayerValue(interactive, actions.onToggleFavorite, {}),
                onSetAnimeRating = activeLayerValue(interactive, actions.onSetAnimeRating, { _ -> }),
                onAddAnimeComment = activeLayerValue(interactive, actions.onAddAnimeComment, { _ -> }),
                onLoadMoreAnimeComments = activeLayerValue(interactive, actions.onLoadMoreAnimeComments, {}),
                onToggleVideoSubscription = activeLayerValue(
                    interactive,
                    actions.onToggleVideoSubscription,
                    { _ -> },
                ),
                onResolveSampledDownloadQualities = activeLayerValue(
                    interactive,
                    actions.onResolveSampledDownloadQualities,
                    { _, _ -> emptyMap() },
                ),
                onDownloadAllVideos = activeLayerValue(interactive, actions.onDownloadAllVideos, { _ -> }),
                onResetAnimeWatchProgress = activeLayerValue(
                    interactive,
                    actions.onResetAnimeWatchProgress,
                    { _ -> },
                ),
                onRegisterModalInputActionHandler = activeLayerValue(
                    interactive,
                    { handler -> runtime.onRegisterModalInputActionHandler(layerKey, handler) },
                    {},
                ),
                onRegisterDpadFocusRecoveryHandler = activeLayerValue(
                    interactive,
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(layerKey, handler) },
                    {},
                ),
            )
        }
    }
}

// YummyDroidHomeLayer
@Composable
internal fun HomeLayerScreen(
    layer: AppScreenLayer,
    interactive: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val actions = runtime.actions
    AppLayerContainer(zIndex = zIndex, visible = visible, active = interactive) {
        key(AppScreenKey.Home) {
            BrowseScreen(
                state = layer.state,
                browseCoordinator = runtime.browseCoordinator,
                activeFocusRequestNonce = activeLayerValue(interactive, runtime.activeLayerFocusRequestNonce, 0L),
                onRegisterHomeBackToTopHandler = activeLayerValue(
                    interactive,
                    runtime.onHomeBackToTopHandlerChange,
                    { _, _ -> },
                ),
                onHomeBrowseBackStateChange = activeLayerValue(
                    interactive,
                    runtime.onHomeBrowseBackStateChange,
                    {},
                ),
                onRegisterModalInputActionHandler = activeLayerValue(
                    interactive,
                    { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Home, handler) },
                    {},
                ),
                onRegisterDpadFocusRecoveryHandler = activeLayerValue(
                    interactive,
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(AppScreenKey.Home, handler) },
                    {},
                ),
                onQueryChange = activeLayerValue(interactive, actions.onQueryChange, { _ -> }),
                onSearchSubmitted = activeLayerValue(interactive, actions.onSearchSubmitted, { _ -> }),
                onSearchHistorySelected = activeLayerValue(interactive, actions.onSearchHistorySelected, { _ -> }),
                onRefresh = activeLayerValue(interactive, actions.onRefresh, {}),
                onRefreshFilterCatalog = activeLayerValue(interactive, actions.onRefreshFilterCatalog, {}),
                onLoadMoreAnime = activeLayerValue(interactive, actions.onLoadMoreAnime, {}),
                onBrowseSectionChange = activeLayerValue(interactive, actions.onBrowseSectionChange, { _ -> }),
                onFiltersChange = activeLayerValue(interactive, actions.onFiltersChange, { _ -> }),
                onResetFilters = activeLayerValue(interactive, actions.onResetFilters, {}),
                onOpenSettings = activeLayerValue(interactive, runtime.onOpenSettings, {}),
                onOpenDownloads = activeLayerValue(interactive, runtime.onOpenDownloads, {}),
                onClearDownloadHistory = activeLayerValue(interactive, actions.onClearDownloadHistory, {}),
                onCancelDownload = activeLayerValue(interactive, actions.onCancelDownload, { _ -> }),
                onPauseDownload = activeLayerValue(interactive, actions.onPauseDownload, { _ -> }),
                onResumeDownload = activeLayerValue(interactive, actions.onResumeDownload, { _ -> }),
                onOpenLogin = activeLayerValue(interactive, runtime.onOpenLogin, {}),
                onOpenProfile = activeLayerValue(interactive, runtime.onOpenProfile, {}),
                loginDialogOpen = runtime.loginDialogOpen,
                profileDialogOpen = runtime.profileDialogOpen,
                settingsDialogOpen = runtime.settingsDialogOpen,
                active = interactive,
                onOpenAnime = activeLayerValue(interactive, runtime.onOpenAnimeFromCatalog, { _ -> }),
            )
        }
    }
}

// YummyDroidPlayerLayer
@Composable
internal fun PlayerLayerScreen(
    layer: AppScreenLayer,
    interactive: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val route = layer.state.route as? AppRoute.Player ?: return
    AppLayerContainer(zIndex = zIndex, visible = visible, active = interactive, scaleFrom = 1f) {
        key(AppScreenKey.Player) {
            PlayerScreen(
                state = playerScreenStateForLayer(layer, route, interactive, runtime),
                actions = playerScreenActionsForLayer(interactive, runtime),
            )
        }
    }
}

private fun playerScreenStateForLayer(
    layer: AppScreenLayer,
    route: AppRoute.Player,
    active: Boolean,
    runtime: YummyDroidAppLayerRuntime,
): PlayerScreenState = PlayerScreenState(
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
    playWhenReady = route.playWhenReady,
    isInPictureInPicture = runtime.isInPictureInPicture,
    forcedOfflineMode = layer.state.forcedOfflineMode,
    allowSubscriptions = layer.state.auth.profile != null &&
        !layer.state.forcedOfflineMode &&
        (layer.state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
    canUsePictureInPicture = active && runtime.canUsePictureInPicture,
)

private fun playerScreenActionsForLayer(
    active: Boolean,
    runtime: YummyDroidAppLayerRuntime,
): PlayerScreenActions {
    val actions = runtime.actions
    return PlayerScreenActions(
        onSelectGroup = activeLayerValue(active, actions.onSelectVideoGroup, { _ -> }),
        onPlayVideoAtQuality = activeLayerValue(active, actions.onPlayVideoAtQuality, { _, _, _ -> }),
        onSelectPlaybackSource = activeLayerValue(active, actions.onSelectPlaybackSource, { _, _ -> }),
        onChooseResumePosition = activeLayerValue(active, actions.onChoosePlayerResumePosition, { _ -> }),
        onToggleVideoSubscription = activeLayerValue(
            active,
            actions.onTogglePlayerVideoSubscription,
            { _ -> },
        ),
        onRetry = activeLayerValue(active, actions.onRetryVideo, {}),
        onPlaybackFailed = activeLayerValue(active, actions.onPlaybackFailed, { _, _, _ -> }),
        onPlaybackStarted = activeLayerValue(active, actions.onPlaybackStarted, { _ -> }),
        onPlaybackEnded = activeLayerValue(active, actions.onPlaybackEnded, { _ -> }),
        onPlaybackProgress = activeLayerValue(active, actions.onPlaybackProgress, { _, _, _ -> }),
        onEnterPictureInPicture = activeLayerValue(active, actions.onEnterPictureInPicture, {}),
        onSettingsChange = activeLayerValue(active, actions.onSettingsChange, { _ -> }),
        onBack = activeLayerValue(active, actions.onBack, {}),
        onRegisterModalInputActionHandler = activeLayerValue(
            active,
            { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Player, handler) },
            {},
        ),
        onRegisterPlayerInputActionHandler = activeLayerValue(
            active,
            runtime.onPlayerInputControllerChange,
            {},
        ),
    )
}
