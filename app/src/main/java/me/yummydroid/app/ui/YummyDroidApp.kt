package me.yummydroid.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import me.yummydroid.app.AppRoute
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.ui.theme.yummyAppBackground

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val appScope = rememberCoroutineScope()
    val modalState = rememberYummyDroidAppModalState(
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        onSettingsOpened = actions.onRefreshAppContentCacheSize,
    )
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
    val catalogGridState = rememberBrowseRootLazyGridState()
    val scheduleGridState = rememberBrowseRootLazyGridState()
    val historyGridState = rememberBrowseRootLazyGridState()
    val browseCoordinator = rememberBrowseRootUiCoordinator(
        catalogGridState = catalogGridState,
        scheduleGridState = scheduleGridState,
        historyGridState = historyGridState,
    )
    val layerSnapshot = rememberYummyDroidAppLayerSnapshot(state)
    val inputState = rememberYummyDroidAppInputState(state.homeSection)
    YummyDroidAppInputEffects(
        inputState = inputState,
        activeLayerKey = layerSnapshot.activeLayerKey,
        homeSection = state.homeSection,
        focusManager = focusManager,
    )
    val openAnimeFromCatalog = remember(actions.onOpenAnime) {
        { animeId: Long ->
            actions.onOpenAnime(animeId)
        }
    }
    val pendingUpdate = state.updateState
        .readyDataOrNull()
        ?.takeIf {
            it.isNewerThanInstalled() &&
                !modalState.autoUpdatePromptDismissed &&
                !modalState.settingsDialogOpen
        }
    val hasTopAppModal = modalState.loginDialogOpen ||
        modalState.profileDialogOpen ||
        modalState.settingsDialogOpen ||
        pendingUpdate != null
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
        hasTopAppModal = hasTopAppModal,
        isInPictureInPicture = isInPictureInPicture,
    )
    RegisterYummyDroidAppInputHandler(actions, inputRouter)
    val activeLayerFocusRequestNonce = resolveActiveLayerFocusRequestNonce(
        inputModeIsTouch = inputModeManager.inputMode == InputMode.Touch,
        activeLayerFocusNonce = inputState.activeLayerFocusNonce,
    )

    CompositionLocalProvider(LocalUiLanguage provides state.settings.contentLanguage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { change -> change.changedToDownIgnoreConsumed() }) {
                                inputRouter.markPointerInputAndClearFocus()
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
                renderedLayers = layerSnapshot.renderedLayers,
                exitingLayers = layerSnapshot.exitingLayers,
                runtime = YummyDroidAppLayerRuntime(
                    actions = actions,
                    browseCoordinator = browseCoordinator,
                    detailsScreenUiStates = layerSnapshot.detailsScreenUiStates,
                    activeLayerFocusRequestNonce = activeLayerFocusRequestNonce,
                    activeLayerFocusNonce = inputState.activeLayerFocusNonce,
                    isInPictureInPicture = isInPictureInPicture,
                    canUsePictureInPicture = canUsePictureInPicture,
                    loginDialogOpen = modalState.loginDialogOpen,
                    profileDialogOpen = modalState.profileDialogOpen,
                    settingsDialogOpen = modalState.settingsDialogOpen,
                    onOpenAnimeFromCatalog = openAnimeFromCatalog,
                    onOpenLogin = { modalState.loginDialogOpen = true },
                    onOpenProfile = { modalState.profileDialogOpen = true },
                    onOpenSettings = { modalState.settingsDialogOpen = true },
                    onOpenDownloads = inputRouter::openDownloadsSection,
                    onHomeBackToTopHandlerChange = inputState::registerHomeBackToTopHandler,
                    onHomeBrowseBackStateChange = { backState -> inputState.homeBrowseBackState = backState },
                    onRegisterModalInputActionHandler = inputState::registerModalInputActionHandler,
                    onRegisterDpadFocusRecoveryHandler = inputState::registerDpadFocusRecoveryHandler,
                    onPlayerInputControllerChange = { controller ->
                        inputState.playerInputController = controller
                    },
                ),
            )
            YummyDroidAppDialogHost(
                state = state,
                runtime = YummyDroidAppDialogRuntime(
                    context = context,
                    actions = actions,
                    openProfileNotificationsRequest = openProfileNotificationsRequest,
                    loginDialogOpen = modalState.loginDialogOpen,
                    profileDialogOpen = modalState.profileDialogOpen,
                    settingsDialogOpen = modalState.settingsDialogOpen,
                    pendingUpdate = pendingUpdate,
                    onLoginDialogOpenChange = { open -> modalState.loginDialogOpen = open },
                    onProfileDialogOpenChange = { open -> modalState.profileDialogOpen = open },
                    onSettingsDialogOpenChange = { open -> modalState.settingsDialogOpen = open },
                    onAutoUpdatePromptDismissed = { modalState.autoUpdatePromptDismissed = true },
                    onRegisterModalInputActionHandler = inputState::registerModalInputActionHandler,
                ),
            )
        }
    }
}
