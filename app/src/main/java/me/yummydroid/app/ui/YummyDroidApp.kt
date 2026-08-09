package me.yummydroid.app.ui
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canReturnRootHomeToCatalog
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.ui.theme.yummyAppBackground

private enum class AppModalInputOwner {
    ProfileDialog,
    SettingsDialog,
}

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
    val onQueryChange = actions.onQueryChange
    val onSearchSubmitted = actions.onSearchSubmitted
    val onSearchHistorySelected = actions.onSearchHistorySelected
    val onRefresh = actions.onRefresh
    val onLoadMoreAnime = actions.onLoadMoreAnime
    val onBrowseSectionChange = actions.onBrowseSectionChange
    val onFiltersChange = actions.onFiltersChange
    val onResetFilters = actions.onResetFilters
    val onSettingsChange = actions.onSettingsChange
    val onOpenAnime = actions.onOpenAnime
    val onFilterByGenre = actions.onFilterByGenre
    val onFilterByYear = actions.onFilterByYear
    val onFilterByStudio = actions.onFilterByStudio
    val onFilterByCreator = actions.onFilterByCreator
    val onSelectVideoGroup = actions.onSelectVideoGroup
    val onPlayVideo = actions.onPlayVideo
    val onPlayVideoWithResumeChoice = actions.onPlayVideoWithResumeChoice
    val onPlayVideoAt = actions.onPlayVideoAt
    val onPlayVideoAtQuality = actions.onPlayVideoAtQuality
    val onSelectPlaybackSource = actions.onSelectPlaybackSource
    val onChoosePlayerResumePosition = actions.onChoosePlayerResumePosition
    val onRetryVideo = actions.onRetryVideo
    val onPlaybackFailed = actions.onPlaybackFailed
    val onPlaybackStarted = actions.onPlaybackStarted
    val onPlaybackEnded = actions.onPlaybackEnded
    val onPlaybackProgress = actions.onPlaybackProgress
    val onResetAnimeWatchProgress = actions.onResetAnimeWatchProgress
    val onEnterPictureInPicture = actions.onEnterPictureInPicture
    val onLogin = actions.onLogin
    val onCaptchaSolved = actions.onCaptchaSolved
    val onCaptchaCanceled = actions.onCaptchaCanceled
    val onLogout = actions.onLogout
    val onOpenLibraryFilter = actions.onOpenLibraryFilter
    val onSelectAnimeListMark = actions.onSelectAnimeListMark
    val onToggleFavorite = actions.onToggleFavorite
    val onSetAnimeRating = actions.onSetAnimeRating
    val onAddAnimeComment = actions.onAddAnimeComment
    val onLoadMoreAnimeComments = actions.onLoadMoreAnimeComments
    val onToggleVideoSubscription = actions.onToggleVideoSubscription
    val onTogglePlayerVideoSubscription = actions.onTogglePlayerVideoSubscription
    val onUnsubscribeVideoSubscription = actions.onUnsubscribeVideoSubscription
    val onRefreshVideoSubscriptions = actions.onRefreshVideoSubscriptions
    val onRefreshProfileNotifications = actions.onRefreshProfileNotifications
    val onMarkProfileNotificationRead = actions.onMarkProfileNotificationRead
    val onMarkAllProfileNotificationsRead = actions.onMarkAllProfileNotificationsRead
    val onDeleteProfileNotification = actions.onDeleteProfileNotification
    val onResolveSampledDownloadQualities = actions.onResolveSampledDownloadQualities
    val onDownloadAllVideos = actions.onDownloadAllVideos
    val onDeleteOfflineVideo = actions.onDeleteOfflineVideo
    val onDeleteOfflineAnime = actions.onDeleteOfflineAnime
    val onClearAppContentCache = actions.onClearAppContentCache
    val onRefreshAppContentCacheSize = actions.onRefreshAppContentCacheSize
    val onClearDownloadHistory = actions.onClearDownloadHistory
    val onCancelDownload = actions.onCancelDownload
    val onPauseDownload = actions.onPauseDownload
    val onResumeDownload = actions.onResumeDownload
    val onCheckForUpdates = actions.onCheckForUpdates
    val onConsumePlayerNotice = actions.onConsumePlayerNotice
    val onBack = actions.onBack
    val onExitApp = actions.onExitApp
    val onProfileNotificationsRequestConsumed = actions.onProfileNotificationsRequestConsumed
    val registerInputActionHandler = actions.registerInputActionHandler
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val appScope = rememberCoroutineScope()
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var autoUpdatePromptDismissed by remember { mutableStateOf(false) }
    var modalInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var modalInputActionHandlerOwner by remember { mutableStateOf<Any?>(null) }
    var dpadFocusRecoveryHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var dpadFocusRecoveryHandlerOwner by remember { mutableStateOf<Any?>(null) }
    var playerInputController by remember { mutableStateOf<PlayerInputController?>(null) }
    val homeBackToTopHandlers = remember { mutableStateMapOf<BrowseSection, HomeBackToTopHandler>() }
    var homeBrowseBackState by remember {
        mutableStateOf(HomeBrowseBackState(state.homeSection, settledAtStateSection = true))
    }
    CaptchaChallengeEffect(
        requestNonce = state.auth.captchaRequestNonce,
        onSolved = onCaptchaSolved,
        onCanceled = onCaptchaCanceled,
    )
    LaunchedEffect(state.playerNotice?.id) {
        val notice = state.playerNotice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_LONG).show()
        onConsumePlayerNotice(notice.id)
    }
    LaunchedEffect(openProfileNotificationsRequest) {
        if (openProfileNotificationsRequest > 0L) {
            loginDialogOpen = false
            settingsDialogOpen = false
            profileDialogOpen = true
        }
    }
    val catalogGridState = rememberBrowseRootLazyGridState()
    val scheduleGridState = rememberBrowseRootLazyGridState()
    val historyGridState = rememberBrowseRootLazyGridState()
    val browseCoordinator = rememberBrowseRootUiCoordinator(
        catalogGridState = catalogGridState,
        scheduleGridState = scheduleGridState,
        historyGridState = historyGridState,
    )
    val detailsScreenUiStates = remember { mutableStateMapOf<AppScreenKey.Details, DetailsScreenUiState>() }
    var appLayers by remember { mutableStateOf(emptyList<AppScreenLayer>()) }
    val renderedAppLayers = appLayers.syncedWith(state)
    val renderedAppLayerKeys = renderedAppLayers.map { layer -> layer.key }.toSet()
    val pendingExitingAppLayers = appLayers.filter { layer -> layer.key !in renderedAppLayerKeys }
    var exitingAppLayers by remember { mutableStateOf(emptyList<AppScreenLayer>()) }
    val displayedExitingAppLayers = (exitingAppLayers + pendingExitingAppLayers)
        .filter { layer -> layer.key !in renderedAppLayerKeys }
        .distinctBy { layer -> layer.key }
    SideEffect {
        if (exitingAppLayers != displayedExitingAppLayers) {
            exitingAppLayers = displayedExitingAppLayers
        }
        if (appLayers != renderedAppLayers) {
            appLayers = renderedAppLayers
        }
    }
    LaunchedEffect(renderedAppLayerKeys, displayedExitingAppLayers.map { layer -> layer.key }) {
        val retainedDetailsKeys = (renderedAppLayerKeys + displayedExitingAppLayers.map { layer -> layer.key })
            .filterIsInstance<AppScreenKey.Details>()
            .toSet()
        detailsScreenUiStates.keys.toList().forEach { key ->
            if (key !in retainedDetailsKeys) {
                detailsScreenUiStates.remove(key)
            }
        }
    }
    LaunchedEffect(displayedExitingAppLayers.map { layer -> layer.key }) {
        if (displayedExitingAppLayers.isEmpty()) return@LaunchedEffect
        val exitingKeys = displayedExitingAppLayers.map { layer -> layer.key }.toSet()
        delay(YUMMY_FADE_OUT_MS.toLong())
        exitingAppLayers = exitingAppLayers.filterNot { layer -> layer.key in exitingKeys }
    }
    val activeLayerKey = renderedAppLayers.lastOrNull()?.key
    var activeLayerFocusNonce by remember { mutableLongStateOf(0L) }
    var activeLayerHadPointerInput by remember { mutableStateOf(false) }
    LaunchedEffect(activeLayerKey) {
        focusManager.clearFocus(force = true)
        if (modalInputActionHandlerOwner is AppScreenKey && modalInputActionHandlerOwner != activeLayerKey) {
            modalInputActionHandler = null
            modalInputActionHandlerOwner = null
        }
        if (dpadFocusRecoveryHandlerOwner is AppScreenKey && dpadFocusRecoveryHandlerOwner != activeLayerKey) {
            dpadFocusRecoveryHandler = null
            dpadFocusRecoveryHandlerOwner = null
        }
        if (activeLayerKey != AppScreenKey.Player) {
            playerInputController = null
        }
        if (activeLayerKey != AppScreenKey.Home) {
            homeBackToTopHandlers.clear()
            homeBrowseBackState = HomeBrowseBackState(state.homeSection, settledAtStateSection = true)
        }
        activeLayerHadPointerInput = false
        activeLayerFocusNonce += 1L
    }
    LaunchedEffect(activeLayerKey, state.homeSection) {
        if (activeLayerKey == AppScreenKey.Home) {
            activeLayerFocusNonce += 1L
        }
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

    fun registerDpadFocusRecoveryHandler(
        owner: Any,
        handler: (() -> Boolean)?,
    ) {
        if (handler != null) {
            dpadFocusRecoveryHandlerOwner = owner
            dpadFocusRecoveryHandler = handler
        } else if (dpadFocusRecoveryHandlerOwner == owner) {
            dpadFocusRecoveryHandler = null
            dpadFocusRecoveryHandlerOwner = null
        }
    }

    fun activeDpadFocusRecoveryHandler(): (() -> Boolean)? {
        val owner = dpadFocusRecoveryHandlerOwner
        return if (owner is AppScreenKey && owner != activeLayerKey) {
            null
        } else {
            dpadFocusRecoveryHandler
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

    fun requestActiveLayerContentFocus(): Boolean {
        if (hasTopAppModal || state.route is AppRoute.Player) return false
        inputModeManager.requestInputMode(InputMode.Keyboard)
        activeLayerHadPointerInput = false
        if (activeDpadFocusRecoveryHandler()?.invoke() == true) return true
        activeLayerFocusNonce += 1L
        return true
    }

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

    fun openDownloadsSection() {
        loginDialogOpen = false
        profileDialogOpen = false
        settingsDialogOpen = false
        onBrowseSectionChange(BrowseSection.Downloads)
    }

    fun rootHomeBackSectionForBack(treatAsTouchBack: Boolean = false): BrowseSection {
        if (treatAsTouchBack || inputModeManager.inputMode == InputMode.Touch) {
            return state.homeSection
        }
        return when (homeBrowseBackState.visualSection) {
            BrowseSection.Schedule,
            BrowseSection.History -> homeBrowseBackState.visualSection
            BrowseSection.Catalog,
            BrowseSection.Downloads -> state.homeSection
        }
    }

    fun canScrollRootHomeToTop(treatAsTouchBack: Boolean = false): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack(treatAsTouchBack)
        val handler = homeBackToTopHandlers[backSection]
        val scrollStateCanHandle = browseCoordinator.canScrollToTop(backSection)
        return scrollStateCanHandle || handler?.canHandleBackToTop() == true
    }

    fun scrollRootHomeToTopFromBack(treatAsTouchBack: Boolean = false): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack(treatAsTouchBack)
        val handler = homeBackToTopHandlers[backSection]
        val shouldMoveFocus = !treatAsTouchBack && inputModeManager.inputMode != InputMode.Touch
        if (shouldMoveFocus && handler?.handleBackToTop(withFocus = true) == true) return true
        if (!canScrollRootHomeToTop(treatAsTouchBack)) return false
        if (treatAsTouchBack) {
            inputModeManager.requestInputMode(InputMode.Touch)
            activeLayerHadPointerInput = true
            focusManager.clearFocus(force = true)
        }
        appScope.launch {
            browseCoordinator.scrollToTop(backSection)
        }
        return true
    }

    fun canExitAppFromBack(treatAsTouchBack: Boolean = false): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack(treatAsTouchBack)
        return browseCoordinator.canExitAppFromBack(
            section = backSection,
            settledAtSection = homeBrowseBackState.settledAtStateSection,
        )
    }

    fun canReturnRootHomeToCatalogFromBack(treatAsTouchBack: Boolean = false): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        return canReturnRootHomeToCatalog(
            isRootHome = true,
            homeSection = state.homeSection,
            visualHomeSection = if (treatAsTouchBack) state.homeSection else homeBrowseBackState.visualSection,
        )
    }

    fun currentBackAction(treatAsTouchBack: Boolean = false): AppBackAction {
        return resolveAppBackAction(
            hasModal = hasTopAppModal,
            canHidePlayerControls = state.route is AppRoute.Player &&
                !isInPictureInPicture &&
                playerInputController?.hasVisibleControls() == true,
            canNavigateBack = state.canNavigateBack,
            canScrollRootHomeToTop = canScrollRootHomeToTop(treatAsTouchBack),
            canReturnRootHomeToCatalog = canReturnRootHomeToCatalogFromBack(treatAsTouchBack),
            canExitApp = canExitAppFromBack(treatAsTouchBack),
        )
    }

    fun handleBackAction(event: InputActionEvent): Boolean {
        val treatAsTouchBack = event.followsPointerInput || activeLayerHadPointerInput ||
            inputModeManager.inputMode == InputMode.Touch
        if (treatAsTouchBack) {
            inputModeManager.requestInputMode(InputMode.Touch)
            focusManager.clearFocus(force = true)
        }
        val backAction = currentBackAction(treatAsTouchBack)
        val activeModalHandler = activeModalInputActionHandler()
        if (
            event.isRepeated &&
            (backAction != AppBackAction.Ignore || activeModalHandler != null)
        ) {
            return true
        }

        if (activeModalHandler?.invoke(InputAction.Back) == true) {
            return true
        }

        return when (backAction) {
            AppBackAction.CloseModal -> closeTopAppModalFromBack()
            AppBackAction.HidePlayerControls -> {
                playerInputController?.hideVisibleControls()
                true
            }
            AppBackAction.NavigateBack -> {
                onBack()
                true
            }
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack(treatAsTouchBack)
            AppBackAction.ReturnRootHomeToCatalog -> {
                onBack()
                true
            }
            AppBackAction.ExitApp -> {
                onExitApp()
                true
            }
            AppBackAction.Ignore -> true
        }
    }

    fun markPointerInputAndClearFocus() {
        inputModeManager.requestInputMode(InputMode.Touch)
        activeLayerHadPointerInput = true
        focusManager.clearFocus(force = true)
    }

    val inputActionHandler by rememberUpdatedState {
            event: InputActionEvent ->
        val action = event.action
        if (action == InputAction.Back) {
            return@rememberUpdatedState handleBackAction(event)
        }
        if (event.focusRecovery) {
            return@rememberUpdatedState requestActiveLayerContentFocus()
        }
        val wasTouchInputMode = inputModeManager.inputMode == InputMode.Touch
        inputModeManager.requestInputMode(InputMode.Keyboard)
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
                InputAction.Right,
                InputAction.Confirm -> {
                    val shouldRestoreFocus = event.shouldInitializeFocusBeforePlatformDispatch(
                        layerHadPointerInput = activeLayerHadPointerInput,
                        touchInputMode = wasTouchInputMode,
                    )
                    if (shouldRestoreFocus) {
                        return@rememberUpdatedState requestActiveLayerContentFocus()
                    }
                    false
                }
                InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                InputAction.NextEpisode -> playAdjacentEpisode(true)
                InputAction.Play,
                InputAction.Pause,
                InputAction.PlayPause -> false
                InputAction.Back -> false
            }
        }
    }

    DisposableEffect(registerInputActionHandler) {
        registerInputActionHandler { action -> inputActionHandler(action) }
        onDispose { registerInputActionHandler(null) }
    }

    @Composable
    fun AppLayerContainer(
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

    val activeLayerFocusRequestNonce = if (inputModeManager.inputMode == InputMode.Touch) {
        0L
    } else {
        activeLayerFocusNonce
    }
    LaunchedEffect(settingsDialogOpen) {
        if (settingsDialogOpen) {
            onRefreshAppContentCacheSize()
        }
    }

    @Composable
    fun HomeLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float, visible: Boolean) {
        AppLayerContainer(
            zIndex = zIndex,
            visible = visible,
        ) {
            key(AppScreenKey.Home) {
                BrowseScreen(
                    state = layer.state,
                    browseCoordinator = browseCoordinator,
                    activeFocusRequestNonce = if (active) activeLayerFocusRequestNonce else 0L,
                    onRegisterHomeBackToTopHandler = if (active) {
                        { section, handler ->
                            if (handler != null) {
                                homeBackToTopHandlers[section] = handler
                            } else {
                                homeBackToTopHandlers.remove(section)
                            }
                        }
                    } else {
                        { _, _ -> }
                    },
                    onHomeBrowseBackStateChange = if (active) {
                        { backState -> homeBrowseBackState = backState }
                    } else {
                        {}
                    },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> registerModalInputActionHandler(AppScreenKey.Home, handler) }
                    } else {
                        {}
                    },
                    onRegisterDpadFocusRecoveryHandler = if (active) {
                        { handler -> registerDpadFocusRecoveryHandler(AppScreenKey.Home, handler) }
                    } else {
                        {}
                    },
                    onQueryChange = if (active) onQueryChange else { _ -> },
                    onSearchSubmitted = if (active) onSearchSubmitted else { _ -> },
                    onSearchHistorySelected = if (active) onSearchHistorySelected else { _ -> },
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
                        { openDownloadsSection() }
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
                    loginDialogOpen = loginDialogOpen,
                    profileDialogOpen = profileDialogOpen,
                    settingsDialogOpen = settingsDialogOpen,
                    active = active,
                    onOpenAnime = if (active) openAnimeFromCatalog else { _ -> },
                )
            }
        }
    }

    @Composable
    fun DetailsLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float, visible: Boolean) {
        val layerKey = layer.key as? AppScreenKey.Details ?: return
        val detailsScreenUiState = remember(layerKey) {
            detailsScreenUiStates.getOrPut(layerKey) { DetailsScreenUiState() }
        }
        LaunchedEffect(active) {
            if (!active) {
                detailsScreenUiState.suppressInitialFocusOnReactivation = true
            }
        }
        val hasRetainedDetailsFocus = detailsScreenUiState.retainedFocusKey != null
        val detailsFocusRequestNonce = if (
            active &&
            !hasRetainedDetailsFocus &&
            !detailsScreenUiState.suppressInitialFocusOnReactivation
        ) {
            activeLayerFocusRequestNonce
        } else {
            0L
        }
        val retainedDetailsFocusRequestNonce = if (active) {
            activeLayerFocusNonce
        } else {
            0L
        }
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
                    onRefresh = if (active) onRefresh else ({}),
                    onOpenAnime = if (active) onOpenAnime else { _ -> },
                    onOpenLogin = if (active) {
                        { loginDialogOpen = true }
                    } else {
                        {}
                    },
                    onGenreFilterSelected = if (active) onFilterByGenre else { _, _ -> },
                    onYearFilterSelected = if (active) onFilterByYear else { _, _ -> },
                    onStudioFilterSelected = if (active) onFilterByStudio else { _, _ -> },
                    onCreatorFilterSelected = if (active) onFilterByCreator else { _, _ -> },
                    onSelectVideoGroup = if (active) onSelectVideoGroup else { _ -> },
                    onPlayVideo = if (active) onPlayVideo else { _ -> },
                    onPlayVideoWithResumeChoice = if (active) onPlayVideoWithResumeChoice else { _, _ -> },
                    onPlayVideoAt = if (active) onPlayVideoAt else { _, _ -> },
                    onSelectAnimeListMark = if (active) onSelectAnimeListMark else { _ -> },
                    onToggleFavorite = if (active) onToggleFavorite else ({}),
                    onSetAnimeRating = if (active) onSetAnimeRating else { _ -> },
                    onAddAnimeComment = if (active) onAddAnimeComment else { _ -> },
                    onLoadMoreAnimeComments = if (active) onLoadMoreAnimeComments else ({}),
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onResolveSampledDownloadQualities = if (active) {
                        onResolveSampledDownloadQualities
                    } else {
                        { _, _ -> emptyMap() }
                    },
                    onDownloadAllVideos = if (active) onDownloadAllVideos else { _ -> },
                    onResetAnimeWatchProgress = if (active) onResetAnimeWatchProgress else { _ -> },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> registerModalInputActionHandler(layerKey, handler) }
                    } else {
                        {}
                    },
                    onRegisterDpadFocusRecoveryHandler = if (active) {
                        { handler -> registerDpadFocusRecoveryHandler(layerKey, handler) }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    @Composable
    fun PlayerLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float, visible: Boolean) {
        val route = layer.state.route as? AppRoute.Player ?: return
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
                    onSelectPlaybackSource = if (active) onSelectPlaybackSource else { _, _ -> },
                    onChooseResumePosition = if (active) onChoosePlayerResumePosition else { _ -> },
                    onToggleVideoSubscription = if (active) onTogglePlayerVideoSubscription else { _ -> },
                    onRetry = if (active) onRetryVideo else ({}),
                    onPlaybackFailed = if (active) onPlaybackFailed else { _, _, _ -> },
                    onPlaybackStarted = if (active) onPlaybackStarted else { _ -> },
                    onPlaybackEnded = if (active) onPlaybackEnded else { _ -> },
                    onPlaybackProgress = if (active) onPlaybackProgress else { _, _, _ -> },
                    canUsePictureInPicture = active && canUsePictureInPicture,
                    onEnterPictureInPicture = if (active) onEnterPictureInPicture else ({}),
                    onSettingsChange = if (active) onSettingsChange else { _ -> },
                    onBack = if (active) onBack else ({}),
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> registerModalInputActionHandler(AppScreenKey.Player, handler) }
                    } else {
                        {}
                    },
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { change -> change.changedToDownIgnoreConsumed() }) {
                                markPointerInputAndClearFocus()
                            }
                        }
                    }
                }
                .then(
                    if (state.route is AppRoute.Player) {
                        Modifier
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    },
                )
                .then(
                    if (state.route is AppRoute.Player) {
                        Modifier
                    } else {
                        Modifier.yummyAppBackground()
                    },
                )
        ) {
        renderedAppLayers.forEachIndexed { index, layer ->
            val active = index == renderedAppLayers.lastIndex
            when (layer.key) {
                AppScreenKey.Home -> key(layer.key) {
                    HomeLayerScreen(
                        layer = layer,
                        active = active,
                        zIndex = index.toFloat(),
                        visible = true,
                    )
                }
                is AppScreenKey.Details -> key(layer.key) {
                    DetailsLayerScreen(
                        layer = layer,
                        active = active,
                        zIndex = index.toFloat(),
                        visible = true,
                    )
                }
                AppScreenKey.Player -> key(layer.key) {
                    PlayerLayerScreen(
                        layer = layer,
                        active = active,
                        zIndex = index.toFloat(),
                        visible = true,
                    )
                }
            }
        }

        displayedExitingAppLayers.forEachIndexed { index, layer ->
            val zIndex = (renderedAppLayers.size + index).toFloat() + 1_000f
            when (layer.key) {
                AppScreenKey.Home -> key("exiting:${layer.key}") {
                    HomeLayerScreen(
                        layer = layer,
                        active = false,
                        zIndex = zIndex,
                        visible = false,
                    )
                }
                is AppScreenKey.Details -> key("exiting:${layer.key}") {
                    DetailsLayerScreen(
                        layer = layer,
                        active = false,
                        zIndex = zIndex,
                        visible = false,
                    )
                }
                AppScreenKey.Player -> key("exiting:${layer.key}") {
                    PlayerLayerScreen(
                        layer = layer,
                        active = false,
                        zIndex = zIndex,
                        visible = false,
                    )
                }
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
                notificationsState = state.profileNotifications,
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
                onRefreshProfileNotifications = onRefreshProfileNotifications,
                onMarkProfileNotificationRead = onMarkProfileNotificationRead,
                onMarkAllProfileNotificationsRead = onMarkAllProfileNotificationsRead,
                onDeleteProfileNotification = onDeleteProfileNotification,
                openNotificationsRequest = openProfileNotificationsRequest,
                onOpenNotificationsRequestConsumed = onProfileNotificationsRequestConsumed,
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
                appContentCacheSizeBytes = state.appContentCacheSizeBytes,
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
        }
    }

}
