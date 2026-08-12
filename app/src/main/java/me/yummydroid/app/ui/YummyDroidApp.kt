package me.yummydroid.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import me.yummydroid.app.AppRoute
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.readyDataOrNull
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
