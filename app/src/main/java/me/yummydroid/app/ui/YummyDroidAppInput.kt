package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.canReturnRootHomeToCatalog
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction

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
    private data class InputContext(
        val activeLayerKey: AppScreenKey?,
        val homeSection: BrowseSection,
        val topAppModal: AppModalBackTarget?,
    )

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
    private var observedInputContext: InputContext? = null

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
            AppModalBackTarget.LocalHistoryMerge,
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

    fun synchronizeInputContext(
        activeLayerKey: AppScreenKey?,
        homeSection: BrowseSection,
        topAppModal: AppModalBackTarget?,
    ): Boolean {
        val next = InputContext(activeLayerKey, homeSection, topAppModal)
        val previous = observedInputContext
        observedInputContext = next
        val layerChanged = previous == null || previous.activeLayerKey != next.activeLayerKey
        if (layerChanged) {
            activateLayer(activeLayerKey, homeSection)
        } else {
            synchronizeSameLayerInputContext(requireNotNull(previous), next)
        }
        return layerChanged
    }

    private fun synchronizeSameLayerInputContext(previous: InputContext, next: InputContext) {
        if (previous.topAppModal != next.topAppModal) uiControls.cancelInteractive()
        val homeSectionChanged = next.activeLayerKey == AppScreenKey.Home &&
            previous.homeSection != next.homeSection
        val modalClosed = previous.topAppModal != null && next.topAppModal == null
        if (homeSectionChanged || modalClosed) activeLayerFocusNonce += 1L
    }

    private fun activateLayer(activeLayerKey: AppScreenKey?, homeSection: BrowseSection) {
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
    LaunchedEffect(activeLayerKey, homeSection, topAppModal) {
        val layerChanged = inputState.synchronizeInputContext(
            activeLayerKey = activeLayerKey,
            homeSection = homeSection,
            topAppModal = topAppModal,
        )
        if (layerChanged) {
            focusManager.clearFocus(force = true)
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
        if (event.focusRecovery) {
            if (
                state.route is AppRoute.Player &&
                inputState.playerInputController?.handleInput(event) == true
            ) {
                return true
            }
            return requestActiveLayerContentFocus()
        }

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
            AppBackAction.CloseModal -> closeTopAppModal()
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

    private fun closeTopAppModal(): Boolean {
        return when (topAppModal) {
            AppModalBackTarget.LocalHistoryMerge -> {
                actions.onDismissLocalWatchHistoryMerge()
                true
            }
            else -> modalState.closeTopModal(pendingUpdateVisible)
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
