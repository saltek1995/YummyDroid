package me.yummydroid.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.LoadState
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState

private enum class AppModalInputOwner {
    ProfileDialog,
    SettingsDialog,
}

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onFilterByGenre: (FilterOption) -> Unit,
    onFilterByYear: (Int) -> Unit,
    onFilterByStudio: (FilterOption) -> Unit,
    onFilterByCreator: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onRetryVideo: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onLogin: (String, String, String?) -> Unit,
    onCaptchaSolved: (String) -> Unit,
    onCaptchaCanceled: (String?) -> Unit,
    onLogout: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val appScope = rememberCoroutineScope()
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var autoUpdatePromptDismissed by remember { mutableStateOf(false) }
    var modalInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var modalInputActionHandlerOwner by remember { mutableStateOf<Any?>(null) }
    var playerInputController by remember { mutableStateOf<PlayerInputController?>(null) }
    var homeBackToTopHandler by remember { mutableStateOf<HomeBackToTopHandler?>(null) }
    CaptchaChallengeEffect(
        requestNonce = state.auth.captchaRequestNonce,
        onSolved = onCaptchaSolved,
        onCanceled = onCaptchaCanceled,
    )
    val catalogGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scheduleListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val historyGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var appLayers by remember { mutableStateOf(emptyList<AppScreenLayer>()) }
    val renderedAppLayers = appLayers.syncedWith(state)
    SideEffect {
        if (appLayers != renderedAppLayers) {
            appLayers = renderedAppLayers
        }
    }
    val activeLayerKey = renderedAppLayers.lastOrNull()?.key
    var activeLayerFocusNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activeLayerKey) {
        if (modalInputActionHandlerOwner is AppScreenKey && modalInputActionHandlerOwner != activeLayerKey) {
            modalInputActionHandler = null
            modalInputActionHandlerOwner = null
        }
        if (activeLayerKey != AppScreenKey.Player) {
            playerInputController = null
        }
        if (activeLayerKey != AppScreenKey.Home) {
            homeBackToTopHandler = null
        }
        focusManager.clearFocus(force = true)
        activeLayerFocusNonce += 1L
    }

    fun registerModalInputActionHandler(
        owner: Any,
        handler: ((InputAction) -> Boolean)?,
    ) {
        if (handler != null) {
            modalInputActionHandlerOwner = owner
            modalInputActionHandler = handler
        } else if (modalInputActionHandlerOwner == owner) {
            modalInputActionHandler = null
            modalInputActionHandlerOwner = null
        }
    }

    fun activeModalInputActionHandler(): ((InputAction) -> Boolean)? {
        val owner = modalInputActionHandlerOwner
        return if (owner is AppScreenKey && owner != activeLayerKey) {
            null
        } else {
            modalInputActionHandler
        }
    }
    val openAnimeFromCatalog = remember(onOpenAnime) {
        { animeId: Long ->
            onOpenAnime(animeId)
        }
    }
    val playAdjacentEpisode = playAdjacentEpisode@{ forward: Boolean ->
        val route = state.route as? AppRoute.Player ?: return@playAdjacentEpisode false
        val adjacent = findAdjacentPlayerVideo(
            currentVideo = route.video,
            allVideos = state.videos.readyListOrEmpty(),
            selectedGroup = state.selectedVideoGroup,
            forward = forward,
        ) ?: return@playAdjacentEpisode false
        onSelectVideoGroup(adjacent.groupKey)
        onPlayVideoAtQuality(adjacent, 0L, route.preferredQuality)
        true
    }
    val pendingUpdate = state.updateState
        .readyDataOrNull()
        ?.takeIf { it.isNewerThanInstalled() && !autoUpdatePromptDismissed && !settingsDialogOpen }
    val hasTopAppModal = loginDialogOpen ||
        profileDialogOpen ||
        settingsDialogOpen ||
        pendingUpdate != null

    fun closeTopAppModalFromBack(): Boolean {
        return when {
            pendingUpdate != null -> {
                autoUpdatePromptDismissed = true
                true
            }
            settingsDialogOpen -> {
                settingsDialogOpen = false
                true
            }
            profileDialogOpen -> {
                profileDialogOpen = false
                true
            }
            loginDialogOpen -> {
                loginDialogOpen = false
                true
            }
            else -> false
        }
    }

    fun canScrollRootHomeToTop(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val handler = homeBackToTopHandler
            ?.takeIf { it.section == state.homeSection }
        val scrollStateCanHandle = when (state.homeSection) {
            BrowseSection.Catalog -> canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
                focusedItemIndex = -1,
            )
            BrowseSection.Schedule -> canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Schedule,
                firstVisibleItemIndex = scheduleListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = scheduleListState.firstVisibleItemScrollOffset,
                focusedItemIndex = -1,
            )
            BrowseSection.History -> canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.History,
                firstVisibleItemIndex = historyGridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = historyGridState.firstVisibleItemScrollOffset,
                focusedItemIndex = -1,
            )
            BrowseSection.Downloads -> false
        }
        return scrollStateCanHandle || handler?.canHandleBackToTop() == true
    }

    fun scrollRootHomeToTopFromBack(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val handler = homeBackToTopHandler
            ?.takeIf { it.section == state.homeSection }
        if (handler?.handleBackToTop() == true) return true
        if (!canScrollRootHomeToTop()) return false
        appScope.launch {
            when (state.homeSection) {
                BrowseSection.Catalog -> catalogGridState.scrollToItem(0, 0)
                BrowseSection.Schedule -> scheduleListState.scrollToItem(0, 0)
                BrowseSection.History -> historyGridState.scrollToItem(0, 0)
                BrowseSection.Downloads -> Unit
            }
        }
        return true
    }

    fun currentBackAction(): AppBackAction {
        return resolveAppBackAction(
            hasModal = hasTopAppModal,
            canHidePlayerControls = state.route is AppRoute.Player &&
                !isInPictureInPicture &&
                playerInputController?.hasVisibleControls() == true,
            canNavigateBack = state.canNavigateBack,
            canScrollRootHomeToTop = canScrollRootHomeToTop(),
        )
    }

    fun handleBackAction(event: InputActionEvent): Boolean {
        val backAction = currentBackAction()
        val activeModalHandler = activeModalInputActionHandler()
        if (
            event.isRepeated &&
            (backAction != AppBackAction.LetSystemHandle || activeModalHandler != null)
        ) {
            return true
        }

        if (activeModalHandler?.invoke(InputAction.Back) == true) {
            return true
        }

        return when (backAction) {
            AppBackAction.CloseModal -> closeTopAppModalFromBack()
            AppBackAction.HidePlayerControls -> {
                if (playerInputController?.handleInput(event) == true) {
                    true
                } else if (state.canNavigateBack) {
                    onBack()
                    true
                } else {
                    scrollRootHomeToTopFromBack()
                }
            }
            AppBackAction.NavigateBack -> {
                onBack()
                true
            }
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack()
            AppBackAction.LetSystemHandle -> false
        }
    }

    val inputActionHandler by rememberUpdatedState {
            event: InputActionEvent ->
        val action = event.action
        if (action == InputAction.Back) {
            return@rememberUpdatedState handleBackAction(event)
        }
        activeModalInputActionHandler()?.let { handler ->
            if (handler(action)) return@rememberUpdatedState true
        }
        if (state.route is AppRoute.Player) {
            when {
                playerInputController?.handleInput(event) == true -> true
                action == InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                action == InputAction.NextEpisode -> playAdjacentEpisode(true)
                else -> false
            }
        } else {
            when (action) {
                InputAction.Up,
                InputAction.Down,
                InputAction.Left,
                InputAction.Right -> false
                InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                InputAction.NextEpisode -> playAdjacentEpisode(true)
                InputAction.Play,
                InputAction.Pause,
                InputAction.PlayPause -> false
                InputAction.Back -> false
                InputAction.Confirm -> false
            }
        }
    }

    val appHandlesSystemBack = currentBackAction() != AppBackAction.LetSystemHandle ||
        activeModalInputActionHandler() != null
    BackHandler(enabled = appHandlesSystemBack) {
        inputActionHandler(InputActionEvent(InputAction.Back))
    }

    DisposableEffect(registerInputActionHandler) {
        registerInputActionHandler { action -> inputActionHandler(action) }
        onDispose { registerInputActionHandler(null) }
    }

    @Composable
    fun AppLayerContainer(
        layerKey: AppScreenKey,
        active: Boolean,
        zIndex: Float,
        requestRootFocusWhenActive: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val layerFocusRequester = remember(layerKey) { FocusRequester() }
        LaunchedEffect(active, activeLayerFocusNonce, requestRootFocusWhenActive) {
            if (active && requestRootFocusWhenActive) {
                withFrameNanos { }
                runCatching { layerFocusRequester.requestFocus() }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(zIndex)
                .focusRequester(layerFocusRequester)
                .focusProperties { canFocus = active }
                .focusable(enabled = active),
        ) {
            content()
        }
    }

    @Composable
    fun HomeLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        AppLayerContainer(
            layerKey = AppScreenKey.Home,
            active = active,
            zIndex = zIndex,
            requestRootFocusWhenActive = false,
        ) {
            key(AppScreenKey.Home) {
                BrowseScreen(
                    state = layer.state,
                    catalogGridState = catalogGridState,
                    scheduleListState = scheduleListState,
                    historyGridState = historyGridState,
                    activeFocusRequestNonce = if (active) activeLayerFocusNonce else 0L,
                    onRegisterHomeBackToTopHandler = if (active) {
                        { section, handler ->
                            if (handler != null) {
                                if (section == layer.state.homeSection) {
                                    homeBackToTopHandler = handler
                                }
                            } else if (homeBackToTopHandler?.section == section) {
                                homeBackToTopHandler = null
                            }
                        }
                    } else {
                        { _, _ -> }
                    },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> registerModalInputActionHandler(AppScreenKey.Home, handler) }
                    } else {
                        {}
                    },
                    onQueryChange = if (active) onQueryChange else { _ -> },
                    onRefresh = if (active) onRefresh else ({}),
                    onLoadMoreAnime = if (active) onLoadMoreAnime else ({}),
                    onBrowseSectionChange = if (active) onBrowseSectionChange else { _ -> },
                    onFiltersChange = if (active) onFiltersChange else { _ -> },
                    onResetFilters = if (active) onResetFilters else ({}),
                    onOpenSettings = if (active) {
                        { settingsDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenDownloads = if (active) {
                        { onBrowseSectionChange(BrowseSection.Downloads) }
                    } else {
                        {}
                    },
                    onClearDownloadHistory = if (active) onClearDownloadHistory else ({}),
                    onCancelDownload = if (active) onCancelDownload else { _ -> },
                    onPauseDownload = if (active) onPauseDownload else { _ -> },
                    onResumeDownload = if (active) onResumeDownload else { _ -> },
                    onOpenLogin = if (active) {
                        { loginDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenProfile = if (active) {
                        { profileDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenAnime = if (active) openAnimeFromCatalog else { _ -> },
                )
            }
        }
    }

    @Composable
    fun DetailsLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        val layerKey = layer.key as? AppScreenKey.Details ?: return
        AppLayerContainer(
            layerKey = layerKey,
            active = active,
            zIndex = zIndex,
        ) {
            key(layerKey) {
                DetailsScreenModern(
                    state = layer.state,
                    onRefresh = if (active) onRefresh else ({}),
                    onOpenAnime = if (active) onOpenAnime else { _ -> },
                    onOpenLogin = if (active) {
                        { loginDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenProfile = if (active) {
                        { profileDialogOpen = true }
                    } else {
                        {}
                    },
                    onGenreFilterSelected = if (active) onFilterByGenre else { _ -> },
                    onYearFilterSelected = if (active) onFilterByYear else { _ -> },
                    onStudioFilterSelected = if (active) onFilterByStudio else { _ -> },
                    onCreatorFilterSelected = if (active) onFilterByCreator else { _ -> },
                    onSelectVideoGroup = if (active) onSelectVideoGroup else { _ -> },
                    onPlayVideo = if (active) onPlayVideo else { _ -> },
                    onPlayVideoAt = if (active) onPlayVideoAt else { _, _ -> },
                    onSelectAnimeListMark = if (active) onSelectAnimeListMark else { _ -> },
                    onToggleFavorite = if (active) onToggleFavorite else ({}),
                    onSetAnimeRating = if (active) onSetAnimeRating else { _ -> },
                    onAddAnimeComment = if (active) onAddAnimeComment else { _ -> },
                    onLoadMoreAnimeComments = if (active) onLoadMoreAnimeComments else ({}),
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onResolveDownloadQualities = if (active) {
                        onResolveDownloadQualities
                    } else {
                        { _, _, _ -> emptyList() }
                    },
                    onDownloadVideo = if (active) onDownloadVideo else { _, _ -> },
                    onDownloadAllVideos = if (active) onDownloadAllVideos else { _, _ -> },
                    onDeleteOfflineVideo = if (active) onDeleteOfflineVideo else { _, _, _ -> },
                    onResetAnimeWatchProgress = if (active) onResetAnimeWatchProgress else { _ -> },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> registerModalInputActionHandler(layerKey, handler) }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    @Composable
    fun PlayerLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        val route = layer.state.route as? AppRoute.Player ?: return
        AppLayerContainer(
            layerKey = AppScreenKey.Player,
            active = active,
            zIndex = zIndex,
            requestRootFocusWhenActive = false,
        ) {
            key(AppScreenKey.Player) {
                PlayerScreen(
                    animeTitle = route.animeTitle,
                    video = route.video,
                    settings = layer.state.settings,
                    startPositionMs = route.startPositionMs,
                    preferredQuality = route.preferredQuality,
                    allVideos = layer.state.videos.readyListOrEmpty(),
                    selectedGroup = layer.state.selectedVideoGroup,
                    streamState = layer.state.playerStream,
                    pendingPlaybackRecovery = layer.state.pendingPlaybackRecovery,
                    isInPictureInPicture = isInPictureInPicture,
                    forcedOfflineMode = layer.state.forcedOfflineMode,
                    allowSubscriptions = layer.state.auth.profile != null &&
                        !layer.state.forcedOfflineMode &&
                        (layer.state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
                    subscriptions = layer.state.detailsExtras.readyDataOrNull()?.subscriptions.orEmpty(),
                    onSelectGroup = if (active) onSelectVideoGroup else { _ -> },
                    onPlayVideo = if (active) onPlayVideo else { _ -> },
                    onPlayVideoAt = if (active) onPlayVideoAt else { _, _ -> },
                    onPlayVideoAtQuality = if (active) onPlayVideoAtQuality else { _, _, _ -> },
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onRetry = if (active) onRetryVideo else ({}),
                    onPlaybackFailed = if (active) onPlaybackFailed else { _, _ -> },
                    onPrepareFallbackSource = if (active) onPrepareFallbackSource else { _ -> },
                    onSwitchToPreparedFallbackSource = if (active) onSwitchToPreparedFallbackSource else { _, _ -> false },
                    onRecoveryPrebufferReady = if (active) onRecoveryPrebufferReady else { _, _ -> false },
                    onRecoveryPrebufferFailed = if (active) onRecoveryPrebufferFailed else { _ -> },
                    onPlaybackStarted = if (active) onPlaybackStarted else { _ -> },
                    onPlaybackEnded = if (active) onPlaybackEnded else { _ -> },
                    onPlaybackProgress = if (active) onPlaybackProgress else { _, _, _ -> },
                    canUsePictureInPicture = active && canUsePictureInPicture,
                    onEnterPictureInPicture = if (active) onEnterPictureInPicture else ({}),
                    onSettingsChange = if (active) onSettingsChange else { _ -> },
                    onBack = if (active) onBack else ({}),
                    onRegisterPlayerInputActionHandler = if (active) {
                        { controller -> playerInputController = controller }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    CompositionLocalProvider(LocalUiLanguage provides state.settings.contentLanguage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.route is AppRoute.Player) {
                        Modifier
                    } else {
                        Modifier
                            .navigationBarsPadding()
                    },
                )
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }

                    when (event.key) {
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
        ) {
        renderedAppLayers.forEachIndexed { index, layer ->
            val active = index == renderedAppLayers.lastIndex
            when (layer.key) {
                AppScreenKey.Home -> HomeLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
                is AppScreenKey.Details -> DetailsLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
                AppScreenKey.Player -> PlayerLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
            }
        }

        if (loginDialogOpen) {
            LoginDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                onLogin = onLogin,
                onDismiss = { loginDialogOpen = false },
            )
        }

        if (profileDialogOpen) {
            ProfileDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                subscriptionsState = state.globalSubscriptions,
                onOpenLogin = {
                    profileDialogOpen = false
                    loginDialogOpen = true
                },
                onOpenLibrary = {
                    profileDialogOpen = false
                    onOpenLibraryFilter()
                },
                onOpenAnime = { animeId ->
                    profileDialogOpen = false
                    onOpenAnime(animeId)
                },
                onUnsubscribeVideoSubscription = onUnsubscribeVideoSubscription,
                onRefreshVideoSubscriptions = onRefreshVideoSubscriptions,
                onLogout = {
                    profileDialogOpen = false
                    onLogout()
                },
                onRegisterModalInputActionHandler = { handler ->
                    registerModalInputActionHandler(AppModalInputOwner.ProfileDialog, handler)
                },
                onDismiss = { profileDialogOpen = false },
            )
        }

        if (settingsDialogOpen) {
            SettingsDialog(
                settings = state.settings,
                offlineEntries = state.offlineEntries,
                updateState = state.updateState,
                onSettingsChange = onSettingsChange,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                onDeleteOfflineAnime = onDeleteOfflineAnime,
                onClearAppContentCache = onClearAppContentCache,
                onCheckForUpdates = onCheckForUpdates,
                onRegisterModalInputActionHandler = { handler ->
                    registerModalInputActionHandler(AppModalInputOwner.SettingsDialog, handler)
                },
                onDismiss = { settingsDialogOpen = false },
            )
        }
        if (pendingUpdate != null) {
            UpdateCheckDialog(
                updateState = LoadState.Ready(pendingUpdate),
                onInstallUpdate = { info ->
                    autoUpdatePromptDismissed = true
                    UpdateDownloadService.start(context, info.apkUrl, info.version)
                },
                onDismiss = { autoUpdatePromptDismissed = true },
            )
        }
        if (state.forcedOfflineMode && state.route !is AppRoute.Player) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                OfflineModeChip()
            }
        }
        }
    }

}
