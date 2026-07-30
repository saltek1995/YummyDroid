package me.yummydroid.app.ui
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.canExitRootCatalog
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

private enum class AppModalInputOwner {
    ProfileDialog,
    SettingsDialog,
}

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onFilterByGenre: (Long, FilterOption) -> Unit,
    onFilterByYear: (Long, Int) -> Unit,
    onFilterByStudio: (Long, FilterOption) -> Unit,
    onFilterByCreator: (Long, FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    onChoosePlayerResumePosition: (Long) -> Unit,
    onRetryVideo: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
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
    onRefreshProfileNotifications: () -> Unit,
    onMarkProfileNotificationRead: (SiteNotification) -> Unit,
    onMarkAllProfileNotificationsRead: () -> Unit,
    onDeleteProfileNotification: (SiteNotification) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCheckForUpdates: () -> Unit,
    onConsumePlayerNotice: (Long) -> Unit,
    onBack: () -> Unit,
    onExitApp: () -> Unit,
    openProfileNotificationsRequest: Long,
    onProfileNotificationsRequestConsumed: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
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
    var playerInputController by remember { mutableStateOf<PlayerInputController?>(null) }
    var homeBackToTopHandler by remember { mutableStateOf<HomeBackToTopHandler?>(null) }
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
    val catalogGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scheduleListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val historyGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
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
    var activeLayerHasContentFocus by remember { mutableStateOf(false) }
    var activeLayerHadPointerInput by remember { mutableStateOf(false) }
    var activeLayerFocusRestoreRequested by remember { mutableStateOf(false) }
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
            homeBrowseBackState = HomeBrowseBackState(state.homeSection, settledAtStateSection = true)
        }
        activeLayerHasContentFocus = false
        activeLayerHadPointerInput = false
        activeLayerFocusRestoreRequested = false
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

    fun requestActiveLayerContentFocus(): Boolean {
        if (hasTopAppModal || state.route is AppRoute.Player) return false
        inputModeManager.requestInputMode(InputMode.Keyboard)
        activeLayerHasContentFocus = false
        activeLayerHadPointerInput = false
        activeLayerFocusRestoreRequested = true
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

    fun rootHomeBackSectionForBack(): BrowseSection {
        return when (homeBrowseBackState.visualSection) {
            BrowseSection.Schedule,
            BrowseSection.History -> homeBrowseBackState.visualSection
            BrowseSection.Catalog,
            BrowseSection.Downloads -> state.homeSection
        }
    }

    fun canScrollRootHomeToTop(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack()
        val handler = homeBackToTopHandler
            ?.takeIf { it.section == backSection }
        val scrollStateCanHandle = when (backSection) {
            BrowseSection.Catalog -> catalogGridState.canScrollBackward ||
                canHandleRootHomeBackToTop(
                    isRootHome = true,
                    homeSection = BrowseSection.Catalog,
                    firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
                )
            BrowseSection.Schedule -> scheduleListState.canScrollBackward ||
                canHandleRootHomeBackToTop(
                    isRootHome = true,
                    homeSection = BrowseSection.Schedule,
                    firstVisibleItemIndex = scheduleListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = scheduleListState.firstVisibleItemScrollOffset,
                )
            BrowseSection.History -> historyGridState.canScrollBackward ||
                canHandleRootHomeBackToTop(
                    isRootHome = true,
                    homeSection = BrowseSection.History,
                    firstVisibleItemIndex = historyGridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = historyGridState.firstVisibleItemScrollOffset,
                )
            BrowseSection.Downloads -> false
        }
        return scrollStateCanHandle || handler?.canHandleBackToTop() == true
    }

    fun scrollRootHomeToTopFromBack(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack()
        val handler = homeBackToTopHandler
            ?.takeIf { it.section == backSection }
        if (handler?.handleBackToTop() == true) return true
        if (!canScrollRootHomeToTop()) return false
        appScope.launch {
            when (backSection) {
                BrowseSection.Catalog -> catalogGridState.scrollToItem(0, 0)
                BrowseSection.Schedule -> scheduleListState.scrollToItem(0, 0)
                BrowseSection.History -> historyGridState.scrollToItem(0, 0)
                BrowseSection.Downloads -> Unit
            }
        }
        return true
    }

    fun canExitAppFromBack(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        val backSection = rootHomeBackSectionForBack()
        if (catalogGridState.canScrollBackward) return false
        return canExitRootCatalog(
            isRootHome = true,
            homeSection = backSection,
            firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
            browsePagerSettledAtStateSection = homeBrowseBackState.settledAtStateSection,
        )
    }

    fun canReturnRootHomeToCatalogFromBack(): Boolean {
        if (state.route != AppRoute.Home || state.canNavigateBack) return false
        return canReturnRootHomeToCatalog(
            isRootHome = true,
            homeSection = state.homeSection,
            visualHomeSection = homeBrowseBackState.visualSection,
        )
    }

    fun currentBackAction(): AppBackAction {
        return resolveAppBackAction(
            hasModal = hasTopAppModal,
            canHidePlayerControls = state.route is AppRoute.Player &&
                !isInPictureInPicture &&
                playerInputController?.hasVisibleControls() == true,
            canNavigateBack = state.canNavigateBack,
            canScrollRootHomeToTop = canScrollRootHomeToTop(),
            canReturnRootHomeToCatalog = canReturnRootHomeToCatalogFromBack(),
            canExitApp = canExitAppFromBack(),
        )
    }

    fun handleBackAction(event: InputActionEvent): Boolean {
        val backAction = currentBackAction()
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
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack()
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
        activeLayerHasContentFocus = false
        activeLayerFocusRestoreRequested = false
        focusManager.clearFocus(force = true)
    }

    val inputActionHandler by rememberUpdatedState {
            event: InputActionEvent ->
        val action = event.action
        if (action == InputAction.Back) {
            return@rememberUpdatedState handleBackAction(event)
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
                    val shouldRestoreFocus = event.followsPointerInput ||
                        activeLayerHadPointerInput ||
                        wasTouchInputMode ||
                        (!activeLayerHasContentFocus && !activeLayerFocusRestoreRequested)
                    if (shouldRestoreFocus) {
                        requestActiveLayerContentFocus()
                    } else {
                        false
                    }
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
        layerKey: AppScreenKey,
        active: Boolean,
        zIndex: Float,
        visible: Boolean,
        requestRootFocusWhenActive: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val layerFocusRequester = remember(layerKey) { FocusRequester() }
        LaunchedEffect(active, layerKey, requestRootFocusWhenActive, inputModeManager.inputMode) {
            if (active && requestRootFocusWhenActive && inputModeManager.inputMode != InputMode.Touch) {
                withFrameNanos { }
                runCatching { layerFocusRequester.requestFocus() }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(zIndex)
                .yummyAppearMotion(visible = visible)
                .focusRequester(layerFocusRequester)
                .onFocusChanged { focusState ->
                    if (active) {
                        activeLayerHasContentFocus = focusState.hasFocus && !focusState.isFocused
                    }
                }
                .focusProperties { canFocus = active && visible }
                .focusable(enabled = active && visible),
        ) {
            content()
        }
    }

    val activeLayerFocusRequestNonce = if (inputModeManager.inputMode == InputMode.Touch) {
        0L
    } else {
        activeLayerFocusNonce
    }

    @Composable
    fun HomeLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float, visible: Boolean) {
        AppLayerContainer(
            layerKey = AppScreenKey.Home,
            active = active,
            zIndex = zIndex,
            visible = visible,
            requestRootFocusWhenActive = false,
        ) {
            key(AppScreenKey.Home) {
                BrowseScreen(
                    state = layer.state,
                    catalogGridState = catalogGridState,
                    scheduleListState = scheduleListState,
                    historyGridState = historyGridState,
                    activeFocusRequestNonce = if (active) activeLayerFocusRequestNonce else 0L,
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
        AppLayerContainer(
            layerKey = layerKey,
            active = active,
            zIndex = zIndex,
            visible = visible,
        ) {
            key(layerKey) {
                DetailsScreenModern(
                    state = layer.state,
                    screenUiState = detailsScreenUiState,
                    activeFocusRequestNonce = if (active) activeLayerFocusRequestNonce else 0L,
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
                )
            }
        }
    }

    @Composable
    fun PlayerLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float, visible: Boolean) {
        val route = layer.state.route as? AppRoute.Player ?: return
        AppLayerContainer(
            layerKey = AppScreenKey.Player,
            active = active,
            zIndex = zIndex,
            visible = visible,
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
                    playbackMetadataLoading = layer.state.playbackMetadataLoading,
                    pendingPlaybackRecovery = layer.state.pendingPlaybackRecovery,
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
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onRetry = if (active) onRetryVideo else ({}),
                    onPlaybackFailed = if (active) onPlaybackFailed else { _, _, _ -> },
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
