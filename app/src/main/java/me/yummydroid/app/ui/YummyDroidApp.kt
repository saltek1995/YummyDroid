package me.yummydroid.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.canReturnRootHomeToCatalog
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.ui.theme.yummyAppBackground

// YummyDroidAppActions
@Stable
class YummyDroidAppActions(
    val onQueryChange: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onSearchHistorySelected: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshFilterCatalog: () -> Unit,
    val onLoadMoreAnime: () -> Unit,
    val onBrowseSectionChange: (BrowseSection) -> Unit,
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onResetFilters: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onOpenAnime: (Long) -> Unit,
    val onFilterByGenre: (Long, FilterOption) -> Unit,
    val onFilterByYear: (Long, Int) -> Unit,
    val onFilterByStudio: (Long, FilterOption) -> Unit,
    val onFilterByCreator: (Long, FilterOption) -> Unit,
    val onSelectVideoGroup: (String) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    val onChoosePlayerResumePosition: (Long) -> Unit,
    val onRetryVideo: () -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val onResetAnimeWatchProgress: (Long) -> Unit,
    val onEnterPictureInPicture: () -> Unit,
    val onLogin: (String, String, String?) -> Unit,
    val onCaptchaSolved: (String) -> Unit,
    val onCaptchaCanceled: (String?) -> Unit,
    val onLogout: () -> Unit,
    val onOpenLibraryFilter: () -> Unit,
    val onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onAddAnimeComment: (String) -> Unit,
    val onLoadMoreAnimeComments: () -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onTogglePlayerVideoSubscription: (VideoVariant) -> Unit,
    val onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    val onRefreshVideoSubscriptions: () -> Unit,
    val onRefreshProfileNotifications: () -> Unit,
    val onMarkProfileNotificationRead: (SiteNotification) -> Unit,
    val onMarkAllProfileNotificationsRead: () -> Unit,
    val onDeleteProfileNotification: (SiteNotification) -> Unit,
    val onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    val onDeleteOfflineAnime: (Long) -> Unit,
    val onClearAppContentCache: () -> Unit,
    val onRefreshAppContentCacheSize: () -> Unit,
    val onRefreshOfflineDownloads: () -> Unit,
    val onClearDownloadHistory: () -> Unit,
    val onCancelDownload: (Long) -> Unit,
    val onPauseDownload: (Long) -> Unit,
    val onResumeDownload: (Long) -> Unit,
    val onCheckForUpdates: () -> Unit,
    val onConsumePlayerNotice: (Long) -> Unit,
    val onBack: () -> Unit,
    val onExitApp: () -> Unit,
    val onProfileNotificationsRequestConsumed: () -> Unit,
    val registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
)

// YummyDroidAppEntryPoint
@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
    YummyDroidAppRuntime(
        state = state,
        isInPictureInPicture = isInPictureInPicture,
        canUsePictureInPicture = canUsePictureInPicture,
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        actions = actions,
    )
}

// YummyDroidAppInput
internal fun resolveRootHomeBackSection(
    treatAsTouchBack: Boolean,
    inputModeIsTouch: Boolean,
    stateSection: BrowseSection,
    visualSection: BrowseSection,
): BrowseSection {
    if (treatAsTouchBack || inputModeIsTouch) return stateSection
    return when (visualSection) {
        BrowseSection.Schedule,
        BrowseSection.History -> visualSection
        BrowseSection.Catalog,
        BrowseSection.Downloads -> stateSection
    }
}

internal fun shouldConsumeRepeatedAppBack(
    isRepeated: Boolean,
    backAction: AppBackAction,
    hasActiveModalHandler: Boolean,
): Boolean = isRepeated && (backAction != AppBackAction.Ignore || hasActiveModalHandler)

internal fun resolveActiveLayerFocusRequestNonce(
    inputModeIsTouch: Boolean,
    activeLayerFocusNonce: Long,
): Long = if (inputModeIsTouch) 0L else activeLayerFocusNonce

internal fun isAppInputHandlerOwnerActive(
    owner: Any?,
    activeLayerKey: AppScreenKey?,
): Boolean = owner !is AppScreenKey || owner == activeLayerKey

@Stable
internal class YummyDroidAppInputState(initialHomeSection: BrowseSection) {
    val uiControls = UiControlCoordinator()
    private val modalInputActionHandlers = mutableStateMapOf<Any, (InputAction) -> Boolean>()
    private var dpadFocusRecoveryHandler by mutableStateOf<(() -> Boolean)?>(null)
    private var dpadFocusRecoveryHandlerOwner by mutableStateOf<Any?>(null)
    var playerInputController by mutableStateOf<PlayerInputController?>(null)
    val homeBackToTopHandlers = mutableStateMapOf<BrowseSection, HomeBackToTopHandler>()
    var homeBrowseBackState by mutableStateOf(
        HomeBrowseBackState(initialHomeSection, settledAtStateSection = true),
    )
    var activeLayerFocusNonce by mutableLongStateOf(0L)
    var activeLayerHadPointerInput by mutableStateOf(false)

    fun registerModalInputActionHandler(owner: Any, handler: ((InputAction) -> Boolean)?) {
        if (handler != null) {
            modalInputActionHandlers[owner] = handler
        } else {
            modalInputActionHandlers.remove(owner)
        }
    }

    fun activeModalInputActionHandler(
        activeLayerKey: AppScreenKey?,
        topAppModal: AppModalBackTarget?,
    ): ((InputAction) -> Boolean)? {
        val owner = when (topAppModal) {
            null -> activeLayerKey
            AppModalBackTarget.Profile -> AppModalInputOwner.ProfileDialog
            AppModalBackTarget.Settings -> AppModalInputOwner.SettingsDialog
            AppModalBackTarget.Login,
            AppModalBackTarget.Update -> null
        }
        return owner?.let(modalInputActionHandlers::get)
    }

    fun registerDpadFocusRecoveryHandler(owner: Any, handler: (() -> Boolean)?) {
        if (handler != null) {
            dpadFocusRecoveryHandlerOwner = owner
            dpadFocusRecoveryHandler = handler
        } else if (dpadFocusRecoveryHandlerOwner == owner) {
            dpadFocusRecoveryHandler = null
            dpadFocusRecoveryHandlerOwner = null
        }
    }

    fun activeDpadFocusRecoveryHandler(activeLayerKey: AppScreenKey?): (() -> Boolean)? {
        return dpadFocusRecoveryHandler.takeIf {
            isAppInputHandlerOwnerActive(dpadFocusRecoveryHandlerOwner, activeLayerKey)
        }
    }

    fun activateLayer(activeLayerKey: AppScreenKey?, homeSection: BrowseSection) {
        uiControls.cancelAll()
        modalInputActionHandlers.keys
            .filterIsInstance<AppScreenKey>()
            .filter { owner -> owner != activeLayerKey }
            .toList()
            .forEach(modalInputActionHandlers::remove)
        if (!isAppInputHandlerOwnerActive(dpadFocusRecoveryHandlerOwner, activeLayerKey)) {
            dpadFocusRecoveryHandler = null
            dpadFocusRecoveryHandlerOwner = null
        }
        if (activeLayerKey != AppScreenKey.Player) {
            playerInputController = null
        }
        if (activeLayerKey != AppScreenKey.Home) {
            homeBackToTopHandlers.clear()
            homeBrowseBackState = HomeBrowseBackState(homeSection, settledAtStateSection = true)
        }
        activeLayerHadPointerInput = false
        activeLayerFocusNonce += 1L
    }

    fun launchRootUiTransition(scope: CoroutineScope, block: suspend () -> Unit) {
        uiControls.launch(scope, this, UiControlOperation.NavigationLatest, block)
    }

    fun cancelRootUiTransition() = uiControls.cancel(UiControlOperation.NavigationLatest)

    fun registerHomeBackToTopHandler(section: BrowseSection, handler: HomeBackToTopHandler?) {
        if (handler != null) {
            homeBackToTopHandlers[section] = handler
        } else {
            homeBackToTopHandlers.remove(section)
        }
    }
}

@Composable
internal fun rememberYummyDroidAppInputState(homeSection: BrowseSection): YummyDroidAppInputState {
    return remember { YummyDroidAppInputState(homeSection) }
}

@Composable
internal fun YummyDroidAppInputEffects(
    inputState: YummyDroidAppInputState,
    activeLayerKey: AppScreenKey?,
    homeSection: BrowseSection,
    topAppModal: AppModalBackTarget?,
    focusManager: FocusManager,
) {
    var previousTopAppModal by remember { mutableStateOf(topAppModal) }
    LaunchedEffect(activeLayerKey) {
        inputState.activateLayer(activeLayerKey, homeSection)
        focusManager.clearFocus(force = true)
    }
    LaunchedEffect(activeLayerKey, homeSection) {
        if (activeLayerKey == AppScreenKey.Home) {
            inputState.activeLayerFocusNonce += 1L
        }
    }
    LaunchedEffect(topAppModal) {
        val modalClosed = previousTopAppModal != null && topAppModal == null
        previousTopAppModal = topAppModal
        if (modalClosed) {
            inputState.activeLayerFocusNonce += 1L
        }
    }
}

internal class YummyDroidAppInputRouter(
    private val state: YummyDroidUiState,
    private val actions: YummyDroidAppActions,
    private val modalState: YummyDroidAppModalState,
    private val inputState: YummyDroidAppInputState,
    private val browseCoordinator: BrowseRootUiCoordinator,
    private val inputModeManager: InputModeManager,
    private val focusManager: FocusManager,
    private val appScope: CoroutineScope,
    private val activeLayerKey: AppScreenKey?,
    private val pendingUpdateVisible: Boolean,
    private val topAppModal: AppModalBackTarget?,
    private val isInPictureInPicture: Boolean,
) {
    private val hasTopAppModal: Boolean get() = topAppModal != null

    fun handleInput(event: InputActionEvent): Boolean {
        inputState.cancelRootUiTransition()
        if (event.action == InputAction.Back) return handleBackAction(event)
        if (event.focusRecovery) return requestActiveLayerContentFocus()

        val wasTouchInputMode = inputModeManager.inputMode == InputMode.Touch
        inputModeManager.requestInputMode(InputMode.Keyboard)
        val modalHandler = activeModalInputActionHandler()
        if (modalHandler?.invoke(event.action) == true) return true
        if (modalHandler != null || hasTopAppModal) return false
        return if (state.route is AppRoute.Player) {
            handlePlayerInput(event)
        } else {
            handleScreenInput(event, wasTouchInputMode)
        }
    }

    fun markPointerInputAndClearFocus() {
        inputState.cancelRootUiTransition()
        inputModeManager.requestInputMode(InputMode.Touch)
        inputState.activeLayerHadPointerInput = true
        focusManager.clearFocus(force = true)
    }

    fun openDownloadsSection() {
        modalState.closeAllDialogs()
        actions.onBrowseSectionChange(BrowseSection.Downloads)
    }

    private fun handlePlayerInput(event: InputActionEvent): Boolean {
        return when {
            inputState.playerInputController?.handleInput(event) == true -> true
            event.action == InputAction.PreviousEpisode -> playAdjacentEpisode(false)
            event.action == InputAction.NextEpisode -> playAdjacentEpisode(true)
            else -> false
        }
    }

    private fun handleScreenInput(event: InputActionEvent, wasTouchInputMode: Boolean): Boolean {
        return when (event.action) {
            InputAction.Up,
            InputAction.Down,
            InputAction.Left,
            InputAction.Right,
            InputAction.Confirm -> restoreFocusBeforePlatformDispatch(event, wasTouchInputMode)
            InputAction.PreviousEpisode -> playAdjacentEpisode(false)
            InputAction.NextEpisode -> playAdjacentEpisode(true)
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.Back -> false
        }
    }

    private fun restoreFocusBeforePlatformDispatch(
        event: InputActionEvent,
        wasTouchInputMode: Boolean,
    ): Boolean {
        val shouldRestoreFocus = event.shouldInitializeFocusBeforePlatformDispatch(
            layerHadPointerInput = inputState.activeLayerHadPointerInput,
            touchInputMode = wasTouchInputMode,
        )
        return shouldRestoreFocus && requestActiveLayerContentFocus()
    }

    private fun handleBackAction(event: InputActionEvent): Boolean {
        val treatAsTouchBack = prepareBackInput(event)
        val backAction = currentBackAction(treatAsTouchBack)
        val activeModalHandler = activeModalInputActionHandler()
        if (shouldConsumeRepeatedAppBack(event.isRepeated, backAction, activeModalHandler != null)) {
            return true
        }
        if (activeModalHandler?.invoke(InputAction.Back) == true) return true
        return executeBackAction(backAction, treatAsTouchBack)
    }

    private fun prepareBackInput(event: InputActionEvent): Boolean {
        val treatAsTouchBack = event.followsPointerInput || inputState.activeLayerHadPointerInput ||
            inputModeManager.inputMode == InputMode.Touch
        if (treatAsTouchBack) {
            inputModeManager.requestInputMode(InputMode.Touch)
            focusManager.clearFocus(force = true)
        }
        return treatAsTouchBack
    }

    private fun executeBackAction(backAction: AppBackAction, treatAsTouchBack: Boolean): Boolean {
        return when (backAction) {
            AppBackAction.CloseModal -> modalState.closeTopModal(pendingUpdateVisible)
            AppBackAction.HidePlayerControls -> {
                inputState.playerInputController?.hideVisibleControls()
                true
            }
            AppBackAction.NavigateBack,
            AppBackAction.ReturnRootHomeToCatalog -> {
                actions.onBack()
                true
            }
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack(treatAsTouchBack)
            AppBackAction.ExitApp -> {
                actions.onExitApp()
                true
            }
            AppBackAction.Ignore -> true
        }
    }

    private fun requestActiveLayerContentFocus(): Boolean {
        if (hasTopAppModal || state.route is AppRoute.Player) return false
        inputModeManager.requestInputMode(InputMode.Keyboard)
        inputState.activeLayerHadPointerInput = false
        if (activeDpadFocusRecoveryHandler()?.invoke() == true) return true
        if (focusManager.moveFocus(FocusDirection.Next)) return true
        inputState.activeLayerFocusNonce += 1L
        return true
    }

    private fun currentBackAction(treatAsTouchBack: Boolean): AppBackAction {
        return resolveAppBackAction(
            hasModal = hasTopAppModal,
            canHidePlayerControls = state.route is AppRoute.Player &&
                !isInPictureInPicture &&
                inputState.playerInputController?.hasVisibleControls() == true,
            canNavigateBack = state.canNavigateBack,
            canScrollRootHomeToTop = canScrollRootHomeToTop(treatAsTouchBack),
            canReturnRootHomeToCatalog = canReturnRootHomeToCatalogFromBack(treatAsTouchBack),
            canExitApp = canExitAppFromBack(treatAsTouchBack),
        )
    }

    private fun rootHomeBackSection(treatAsTouchBack: Boolean): BrowseSection {
        return resolveRootHomeBackSection(
            treatAsTouchBack = treatAsTouchBack,
            inputModeIsTouch = inputModeManager.inputMode == InputMode.Touch,
            stateSection = state.homeSection,
            visualSection = inputState.homeBrowseBackState.visualSection,
        )
    }

    private fun canScrollRootHomeToTop(treatAsTouchBack: Boolean): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSection(treatAsTouchBack)
        val handler = inputState.homeBackToTopHandlers[backSection]
        return browseCoordinator.canScrollToTop(backSection) || handler?.canHandleBackToTop() == true
    }

    private fun scrollRootHomeToTopFromBack(treatAsTouchBack: Boolean): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSection(treatAsTouchBack)
        val handler = inputState.homeBackToTopHandlers[backSection]
        val shouldMoveFocus = !treatAsTouchBack && inputModeManager.inputMode != InputMode.Touch
        if (shouldMoveFocus && handler?.handleBackToTop(withFocus = true) == true) return true
        if (!canScrollRootHomeToTop(treatAsTouchBack)) return false
        if (treatAsTouchBack) {
            inputModeManager.requestInputMode(InputMode.Touch)
            inputState.activeLayerHadPointerInput = true
            focusManager.clearFocus(force = true)
        }
        inputState.launchRootUiTransition(appScope) {
            browseCoordinator.scrollToTop(backSection)
        }
        return true
    }

    private fun canExitAppFromBack(treatAsTouchBack: Boolean): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSection(treatAsTouchBack)
        return browseCoordinator.canExitAppFromBack(
            section = backSection,
            settledAtSection = inputState.homeBrowseBackState.settledAtStateSection,
        )
    }

    private fun canReturnRootHomeToCatalogFromBack(treatAsTouchBack: Boolean): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        return canReturnRootHomeToCatalog(
            isRootHome = true,
            homeSection = state.homeSection,
            visualHomeSection = if (treatAsTouchBack) {
                state.homeSection
            } else {
                inputState.homeBrowseBackState.visualSection
            },
        )
    }

    private fun playAdjacentEpisode(forward: Boolean): Boolean {
        val route = state.route as? AppRoute.Player ?: return false
        val adjacent = findAdjacentPlayerVideo(
            currentVideo = route.video,
            allVideos = state.videos.readyListOrEmpty(),
            selectedGroup = state.selectedVideoGroup,
            forward = forward,
        ) ?: return false
        actions.onSelectVideoGroup(adjacent.groupKey)
        actions.onPlayVideoAtQuality(adjacent, 0L, route.preferredQuality)
        return true
    }

    private fun activeModalInputActionHandler(): ((InputAction) -> Boolean)? {
        return inputState.activeModalInputActionHandler(activeLayerKey, topAppModal)
    }

    private fun activeDpadFocusRecoveryHandler(): (() -> Boolean)? {
        return inputState.activeDpadFocusRecoveryHandler(activeLayerKey)
    }
}

@Composable
internal fun RegisterYummyDroidAppInputHandler(
    actions: YummyDroidAppActions,
    inputRouter: YummyDroidAppInputRouter,
) {
    val currentInputRouter by rememberUpdatedState(inputRouter)
    DisposableEffect(actions.registerInputActionHandler) {
        actions.registerInputActionHandler { event -> currentInputRouter.handleInput(event) }
        onDispose { actions.registerInputActionHandler(null) }
    }
}

// YummyDroidAppRuntime
private data class YummyDroidAppRuntimeCore(
    val context: Context,
    val modalState: YummyDroidAppModalState,
    val browseCoordinator: BrowseRootUiCoordinator,
    val layerSnapshot: YummyDroidAppLayerSnapshot,
    val inputState: YummyDroidAppInputState,
    val inputRouter: YummyDroidAppInputRouter,
    val pendingUpdate: AppUpdateInfo?,
    val topAppModal: AppModalBackTarget?,
    val activeLayerFocusRequestNonce: Long,
    val openAnimeFromCatalog: (Long) -> Unit,
)

@Composable
internal fun YummyDroidAppRuntime(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
    val core = rememberYummyDroidAppRuntimeCore(
        state = state,
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        isInPictureInPicture = isInPictureInPicture,
        actions = actions,
    )
    CaptchaChallengeEffect(
        requestNonce = state.auth.captchaRequestNonce,
        onSolved = actions.onCaptchaSolved,
        onCanceled = actions.onCaptchaCanceled,
    )
    YummyDroidAppNoticeEffect(core.context, state, actions)
    RegisterYummyDroidAppInputHandler(actions, core.inputRouter)
    CompositionLocalProvider(
        LocalUiLanguage provides state.settings.contentLanguage,
        LocalUiControlCoordinator provides core.inputState.uiControls,
    ) {
        YummyDroidAppContent(
            state = state,
            actions = actions,
            core = core,
            isInPictureInPicture = isInPictureInPicture,
            canUsePictureInPicture = canUsePictureInPicture,
            openProfileNotificationsRequest = openProfileNotificationsRequest,
        )
    }
}

@Composable
private fun rememberYummyDroidAppRuntimeCore(
    state: YummyDroidUiState,
    openProfileNotificationsRequest: Long,
    isInPictureInPicture: Boolean,
    actions: YummyDroidAppActions,
): YummyDroidAppRuntimeCore {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val appScope = rememberCoroutineScope()
    val modalState = rememberYummyDroidAppModalState(
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        onSettingsOpened = {
            actions.onRefreshAppContentCacheSize()
            actions.onRefreshOfflineDownloads()
        },
    )
    val browseCoordinator = rememberYummyDroidBrowseCoordinator()
    val layerSnapshot = rememberYummyDroidAppLayerSnapshot(state)
    val inputState = rememberYummyDroidAppInputState(state.homeSection)
    val pendingUpdate = resolvePendingAppUpdate(state, modalState)
    val topAppModal = resolveAppModalBackTarget(
        pendingUpdateVisible = pendingUpdate != null,
        settingsDialogOpen = modalState.settingsDialogOpen,
        profileDialogOpen = modalState.profileDialogOpen,
        loginDialogOpen = modalState.loginDialogOpen,
    )
    YummyDroidAppInputEffects(
        inputState = inputState,
        activeLayerKey = layerSnapshot.activeLayerKey,
        homeSection = state.homeSection,
        topAppModal = topAppModal,
        focusManager = focusManager,
    )
    val openAnimeFromCatalog = remember(actions.onOpenAnime) {
        { animeId: Long -> actions.onOpenAnime(animeId) }
    }
    val inputRouter = YummyDroidAppInputRouter(
        state = state,
        actions = actions,
        modalState = modalState,
        inputState = inputState,
        browseCoordinator = browseCoordinator,
        inputModeManager = inputModeManager,
        focusManager = focusManager,
        appScope = appScope,
        activeLayerKey = layerSnapshot.activeLayerKey,
        pendingUpdateVisible = pendingUpdate != null,
        topAppModal = topAppModal,
        isInPictureInPicture = isInPictureInPicture,
    )
    val activeLayerFocusRequestNonce = resolveActiveLayerFocusRequestNonce(
        inputModeIsTouch = inputModeManager.inputMode == InputMode.Touch,
        activeLayerFocusNonce = inputState.activeLayerFocusNonce,
    )
    return YummyDroidAppRuntimeCore(
        context = context,
        modalState = modalState,
        browseCoordinator = browseCoordinator,
        layerSnapshot = layerSnapshot,
        inputState = inputState,
        inputRouter = inputRouter,
        pendingUpdate = pendingUpdate,
        topAppModal = topAppModal,
        activeLayerFocusRequestNonce = activeLayerFocusRequestNonce,
        openAnimeFromCatalog = openAnimeFromCatalog,
    )
}

private fun resolvePendingAppUpdate(
    state: YummyDroidUiState,
    modalState: YummyDroidAppModalState,
): AppUpdateInfo? = state.updateState
    .readyDataOrNull()
    ?.takeIf {
        it.isNewerThanInstalled() &&
            !modalState.autoUpdatePromptDismissed &&
            !modalState.settingsDialogOpen &&
            !modalState.profileDialogOpen &&
            !modalState.loginDialogOpen
    }

@Composable
private fun rememberYummyDroidBrowseCoordinator(): BrowseRootUiCoordinator {
    val catalogGridState = rememberBrowseRootLazyGridState()
    val scheduleGridState = rememberBrowseRootLazyGridState()
    val historyGridState = rememberBrowseRootLazyGridState()
    return rememberBrowseRootUiCoordinator(
        catalogGridState = catalogGridState,
        scheduleGridState = scheduleGridState,
        historyGridState = historyGridState,
    )
}

@Composable
private fun YummyDroidAppNoticeEffect(
    context: Context,
    state: YummyDroidUiState,
    actions: YummyDroidAppActions,
) {
    LaunchedEffect(state.playerNotice?.id) {
        val notice = state.playerNotice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_LONG).show()
        actions.onConsumePlayerNotice(notice.id)
    }
}

@Composable
private fun YummyDroidAppContent(
    state: YummyDroidUiState,
    actions: YummyDroidAppActions,
    core: YummyDroidAppRuntimeCore,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDownIgnoreConsumed() }) {
                            core.inputRouter.markPointerInputAndClearFocus()
                        }
                    }
                }
            }
            .then(appChromeModifier(state.route)),
    ) {
        YummyDroidAppLayerHost(
            renderedLayers = core.layerSnapshot.renderedLayers,
            exitingLayers = core.layerSnapshot.exitingLayers,
            runtime = buildYummyDroidAppLayerRuntime(
                core = core,
                actions = actions,
                isInPictureInPicture = isInPictureInPicture,
                canUsePictureInPicture = canUsePictureInPicture,
            ),
        )
        YummyDroidAppDialogHost(
            state = state,
            runtime = buildYummyDroidAppDialogRuntime(core, actions, openProfileNotificationsRequest),
        )
    }
}

private fun appChromeModifier(route: AppRoute): Modifier {
    if (route is AppRoute.Player) return Modifier
    return Modifier
        .statusBarsPadding()
        .navigationBarsPadding()
        .yummyAppBackground()
}

private fun buildYummyDroidAppLayerRuntime(
    core: YummyDroidAppRuntimeCore,
    actions: YummyDroidAppActions,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
): YummyDroidAppLayerRuntime {
    val modalState = core.modalState
    val inputState = core.inputState
    return YummyDroidAppLayerRuntime(
        actions = actions,
        browseCoordinator = core.browseCoordinator,
        detailsScreenUiStates = core.layerSnapshot.detailsScreenUiStates,
        activeLayerFocusRequestNonce = core.activeLayerFocusRequestNonce,
        activeLayerFocusNonce = inputState.activeLayerFocusNonce,
        isInPictureInPicture = isInPictureInPicture,
        canUsePictureInPicture = canUsePictureInPicture,
        topAppModal = core.topAppModal,
        loginDialogOpen = modalState.loginDialogOpen,
        profileDialogOpen = modalState.profileDialogOpen,
        settingsDialogOpen = modalState.settingsDialogOpen,
        onOpenAnimeFromCatalog = core.openAnimeFromCatalog,
        onOpenLogin = modalState::openLogin,
        onOpenProfile = modalState::openProfile,
        onOpenSettings = modalState::openSettings,
        onOpenDownloads = core.inputRouter::openDownloadsSection,
        onHomeBackToTopHandlerChange = inputState::registerHomeBackToTopHandler,
        onHomeBrowseBackStateChange = { inputState.homeBrowseBackState = it },
        onRegisterModalInputActionHandler = inputState::registerModalInputActionHandler,
        onRegisterDpadFocusRecoveryHandler = inputState::registerDpadFocusRecoveryHandler,
        onPlayerInputControllerChange = { inputState.playerInputController = it },
    )
}

private fun buildYummyDroidAppDialogRuntime(
    core: YummyDroidAppRuntimeCore,
    actions: YummyDroidAppActions,
    openProfileNotificationsRequest: Long,
): YummyDroidAppDialogRuntime {
    val modalState = core.modalState
    return YummyDroidAppDialogRuntime(
        context = core.context,
        actions = actions,
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        loginDialogOpen = modalState.loginDialogOpen,
        profileDialogOpen = modalState.profileDialogOpen,
        settingsDialogOpen = modalState.settingsDialogOpen,
        pendingUpdate = core.pendingUpdate,
        onLoginDialogOpenChange = { modalState.loginDialogOpen = it },
        onProfileDialogOpenChange = { modalState.profileDialogOpen = it },
        onSettingsDialogOpenChange = { modalState.settingsDialogOpen = it },
        onAutoUpdatePromptDismissed = { modalState.autoUpdatePromptDismissed = true },
        onRegisterModalInputActionHandler = core.inputState::registerModalInputActionHandler,
    )
}
