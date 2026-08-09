package me.yummydroid.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canReturnRootHomeToCatalog
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.ui.theme.yummyAppBackground

internal enum class AppModalBackTarget {
    Update,
    Settings,
    Profile,
    Login,
}

internal fun resolveAppModalBackTarget(
    pendingUpdateVisible: Boolean,
    settingsDialogOpen: Boolean,
    profileDialogOpen: Boolean,
    loginDialogOpen: Boolean,
): AppModalBackTarget? = when {
    pendingUpdateVisible -> AppModalBackTarget.Update
    settingsDialogOpen -> AppModalBackTarget.Settings
    profileDialogOpen -> AppModalBackTarget.Profile
    loginDialogOpen -> AppModalBackTarget.Login
    else -> null
}

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

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
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
        onSolved = actions.onCaptchaSolved,
        onCanceled = actions.onCaptchaCanceled,
    )
    LaunchedEffect(state.playerNotice?.id) {
        val notice = state.playerNotice ?: return@LaunchedEffect
        Toast.makeText(context, notice.message, Toast.LENGTH_LONG).show()
        actions.onConsumePlayerNotice(notice.id)
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
    val openAnimeFromCatalog = remember(actions.onOpenAnime) {
        { animeId: Long ->
            actions.onOpenAnime(animeId)
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
        actions.onSelectVideoGroup(adjacent.groupKey)
        actions.onPlayVideoAtQuality(adjacent, 0L, route.preferredQuality)
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
        return when (
            resolveAppModalBackTarget(
                pendingUpdateVisible = pendingUpdate != null,
                settingsDialogOpen = settingsDialogOpen,
                profileDialogOpen = profileDialogOpen,
                loginDialogOpen = loginDialogOpen,
            )
        ) {
            AppModalBackTarget.Update -> {
                autoUpdatePromptDismissed = true
                true
            }
            AppModalBackTarget.Settings -> {
                settingsDialogOpen = false
                true
            }
            AppModalBackTarget.Profile -> {
                profileDialogOpen = false
                true
            }
            AppModalBackTarget.Login -> {
                loginDialogOpen = false
                true
            }
            null -> false
        }
    }

    fun openDownloadsSection() {
        loginDialogOpen = false
        profileDialogOpen = false
        settingsDialogOpen = false
        actions.onBrowseSectionChange(BrowseSection.Downloads)
    }

    fun rootHomeBackSectionForBack(treatAsTouchBack: Boolean = false): BrowseSection {
        return resolveRootHomeBackSection(
            treatAsTouchBack = treatAsTouchBack,
            inputModeIsTouch = inputModeManager.inputMode == InputMode.Touch,
            stateSection = state.homeSection,
            visualSection = homeBrowseBackState.visualSection,
        )
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
        if (shouldConsumeRepeatedAppBack(event.isRepeated, backAction, activeModalHandler != null)) {
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
                actions.onBack()
                true
            }
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack(treatAsTouchBack)
            AppBackAction.ReturnRootHomeToCatalog -> {
                actions.onBack()
                true
            }
            AppBackAction.ExitApp -> {
                actions.onExitApp()
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

    DisposableEffect(actions.registerInputActionHandler) {
        actions.registerInputActionHandler { action -> inputActionHandler(action) }
        onDispose { actions.registerInputActionHandler(null) }
    }

    val activeLayerFocusRequestNonce = resolveActiveLayerFocusRequestNonce(
        inputModeIsTouch = inputModeManager.inputMode == InputMode.Touch,
        activeLayerFocusNonce = activeLayerFocusNonce,
    )
    LaunchedEffect(settingsDialogOpen) {
        if (settingsDialogOpen) {
            actions.onRefreshAppContentCacheSize()
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
            YummyDroidAppLayerHost(
                renderedLayers = renderedAppLayers,
                exitingLayers = displayedExitingAppLayers,
                runtime = YummyDroidAppLayerRuntime(
                    actions = actions,
                    browseCoordinator = browseCoordinator,
                    detailsScreenUiStates = detailsScreenUiStates,
                    activeLayerFocusRequestNonce = activeLayerFocusRequestNonce,
                    activeLayerFocusNonce = activeLayerFocusNonce,
                    isInPictureInPicture = isInPictureInPicture,
                    canUsePictureInPicture = canUsePictureInPicture,
                    loginDialogOpen = loginDialogOpen,
                    profileDialogOpen = profileDialogOpen,
                    settingsDialogOpen = settingsDialogOpen,
                    onOpenAnimeFromCatalog = openAnimeFromCatalog,
                    onOpenLogin = { loginDialogOpen = true },
                    onOpenProfile = { profileDialogOpen = true },
                    onOpenSettings = { settingsDialogOpen = true },
                    onOpenDownloads = { openDownloadsSection() },
                    onHomeBackToTopHandlerChange = { section, handler ->
                        if (handler != null) {
                            homeBackToTopHandlers[section] = handler
                        } else {
                            homeBackToTopHandlers.remove(section)
                        }
                    },
                    onHomeBrowseBackStateChange = { backState -> homeBrowseBackState = backState },
                    onRegisterModalInputActionHandler = ::registerModalInputActionHandler,
                    onRegisterDpadFocusRecoveryHandler = ::registerDpadFocusRecoveryHandler,
                    onPlayerInputControllerChange = { controller -> playerInputController = controller },
                ),
            )
            YummyDroidAppDialogHost(
                state = state,
                runtime = YummyDroidAppDialogRuntime(
                    context = context,
                    actions = actions,
                    openProfileNotificationsRequest = openProfileNotificationsRequest,
                    loginDialogOpen = loginDialogOpen,
                    profileDialogOpen = profileDialogOpen,
                    settingsDialogOpen = settingsDialogOpen,
                    pendingUpdate = pendingUpdate,
                    onLoginDialogOpenChange = { open -> loginDialogOpen = open },
                    onProfileDialogOpenChange = { open -> profileDialogOpen = open },
                    onSettingsDialogOpenChange = { open -> settingsDialogOpen = open },
                    onAutoUpdatePromptDismissed = { autoUpdatePromptDismissed = true },
                    onRegisterModalInputActionHandler = ::registerModalInputActionHandler,
                ),
            )
        }
    }
}
