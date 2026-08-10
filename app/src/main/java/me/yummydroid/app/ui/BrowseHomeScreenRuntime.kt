package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters

@Composable
internal fun BrowseScreenRuntime(
    state: YummyDroidUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    activeFocusRequestNonce: Long,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit = {},
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    loginDialogOpen: Boolean = false,
    profileDialogOpen: Boolean = false,
    settingsDialogOpen: Boolean = false,
    active: Boolean = true,
    onOpenAnime: (Long) -> Unit,
) {
    val isAuthorized = state.auth.profile != null
    val forcedOffline = state.forcedOfflineMode
    val browsePagerSections = remember(isAuthorized, forcedOffline) {
        resolveBrowsePagerSections(isAuthorized, forcedOffline)
    }
    val effectiveHomeSection = resolveEffectiveBrowseSection(state.homeSection, isAuthorized, forcedOffline)
    LaunchedEffect(state.homeSection, isAuthorized, forcedOffline) {
        resolveBrowseSectionCorrection(state.homeSection, isAuthorized, forcedOffline)
            ?.let(onBrowseSectionChange)
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
    val catalogActionsEnabled = browseCatalogActionsEnabledForSection(
        section = effectiveHomeSection,
        forcedOfflineMode = forcedOffline,
    )
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val browseScreenDensity = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val browseDpadFocusEnabled = inputModeManager.inputMode != InputMode.Touch
    val isWide = currentResponsiveWindowSizeDp().width >= 720.dp
    val browseChromePolicy = resolveBrowseChromePolicy(isWide, forcedOffline)
    val scheduleGridState = browseCoordinator.scheduleGridState
    val tvTopChromePinned = browseChromePolicy.pinTopChrome
    val browseTopBarCollapseDistance = BrowseTopBarScrollCollapseDistance
    val browseTopBarCollapseDistancePx = with(browseScreenDensity) {
        browseTopBarCollapseDistance.toPx()
    }
    fun browseTopBarProgressFor(section: BrowseSection): Float {
        if (tvTopChromePinned) return 1f
        return browseCoordinator.topBarVisibilityProgress(
            section = section,
            collapseDistancePx = browseTopBarCollapseDistancePx,
        )
    }
    fun browseTopBarFullyVisibleFor(section: BrowseSection): Boolean {
        return browseTopBarProgressFor(section) > 0.999f
    }
    val browseFocusBinding = rememberBrowseFocusBinding(browsePagerSections)
    val browseFocusRuntime = browseFocusBinding.runtime
    val browseSectionTabFocusRequesters = browseFocusBinding.sectionFocusRequesters
    val browsePagerPage = browsePagerSections.indexOf(effectiveHomeSection).takeIf { it >= 0 } ?: 0
    val useBrowsePager = !forcedOffline && browsePagerSections.size > 1
    val browsePagerRuntime = rememberBrowsePagerRuntime(
        initialPage = browsePagerPage,
        initialSection = effectiveHomeSection,
        pageCount = { browsePagerSections.size },
    )
    val browseVisualRuntime = rememberBrowseHomeVisualRuntime()
    var scheduleSelectedEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    val dpadLayerFocusRequestNonce = browseFocusRuntime.layerFocusRequestNonce(
        browseDpadFocusEnabled,
        activeFocusRequestNonce,
    )
    val catalogDialogRuntime = rememberBrowseCatalogDialogRuntime(
        catalogActionsEnabled,
        onRegisterModalInputActionHandler,
    )
    val phoneScheduleDayGroups = rememberPhoneScheduleDayGroups(state.schedule, isWide, forcedOffline)
    val showPhoneScheduleCalendarInBottomChrome = !isWide &&
        !forcedOffline &&
        effectiveHomeSection == BrowseSection.Schedule &&
        phoneScheduleDayGroups.isNotEmpty()
    val browseFocusActions = browseFocusRuntime.bindActions(
        section = effectiveHomeSection,
        dpadFocusEnabled = browseDpadFocusEnabled,
        forcedOfflineMode = forcedOffline,
        showPhoneScheduleCalendar = showPhoneScheduleCalendarInBottomChrome,
        scheduleGridState = scheduleGridState,
        browseCoordinator = browseCoordinator,
        sectionFocusRequesters = browseSectionTabFocusRequesters,
        pagerRuntime = browsePagerRuntime,
        topBarFullyVisible = { browseTopBarFullyVisibleFor(effectiveHomeSection) },
        onRegisterHomeBackToTopHandler = onRegisterHomeBackToTopHandler,
    )

    DisposableEffect(onRegisterDpadFocusRecoveryHandler) {
        onRegisterDpadFocusRecoveryHandler(browseFocusActions.recoverFirstContentFocus)
        onDispose { onRegisterDpadFocusRecoveryHandler(null) }
    }
    val focusFirstRequests = resolveBrowseFocusFirstRequests(
        section = effectiveHomeSection,
        persistentCatalogNonce = state.homeFocusResetNonce,
        transientNonce = browseFocusRuntime.firstFocusRequestNonce,
    )
    val browsePagerBinding = rememberBrowsePagerBinding(
        active = active,
        effectiveSection = effectiveHomeSection,
        pagerSections = browsePagerSections,
        usePager = useBrowsePager,
        dpadFocusEnabled = browseDpadFocusEnabled,
        dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        isWide = isWide,
        forcedOfflineMode = forcedOffline,
        browseCoordinator = browseCoordinator,
        topBarCollapseDistancePx = browseTopBarCollapseDistancePx,
        runtime = browsePagerRuntime,
        onBrowseSectionChange = onBrowseSectionChange,
        onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
        onRequestSectionTabsFocus = browseFocusActions.requestSectionTabsFocus,
    )
    BrowseHomeContent(
        model = BrowseHomeContentModel(
            state = state,
            browseCoordinator = browseCoordinator,
            effectiveSection = effectiveHomeSection,
            pagerSections = browsePagerSections,
            pagerPage = browsePagerPage,
            usePager = useBrowsePager,
            catalogActionsEnabled = catalogActionsEnabled,
            isSearching = isSearching,
            isWide = isWide,
            forcedOfflineMode = forcedOffline,
            dpadFocusEnabled = browseDpadFocusEnabled,
            active = active,
            loginDialogOpen = loginDialogOpen,
            profileDialogOpen = profileDialogOpen,
            settingsDialogOpen = settingsDialogOpen,
            density = browseScreenDensity,
            chromePolicy = browseChromePolicy,
            visualRuntime = browseVisualRuntime,
            phoneScheduleDayGroups = phoneScheduleDayGroups,
            scheduleSelectedEpochDay = scheduleSelectedEpochDay,
            showPhoneScheduleCalendar = showPhoneScheduleCalendarInBottomChrome,
            dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
            catalogFocusFirstRequest = focusFirstRequests.catalog,
            scheduleFocusFirstRequest = focusFirstRequests.schedule,
            historyFocusFirstRequest = focusFirstRequests.history,
            focusBinding = browseFocusBinding,
            focusActions = browseFocusActions,
            pagerRuntime = browsePagerRuntime,
            pagerBinding = browsePagerBinding,
            catalogDialogRuntime = catalogDialogRuntime,
        ),
        actions = BrowseHomeContentActions(
            onQueryChange = onQueryChange,
            onSearchSubmitted = onSearchSubmitted,
            onSearchHistorySelected = onSearchHistorySelected,
            onRefresh = onRefresh,
            onLoadMoreAnime = onLoadMoreAnime,
            onFiltersChange = onFiltersChange,
            onResetFilters = onResetFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onClearDownloadHistory = onClearDownloadHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onScheduleSelectedEpochDayChange = { epochDay -> scheduleSelectedEpochDay = epochDay },
            onOpenAnime = onOpenAnime,
        ),
    )
}

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean {
    return !forcedOfflineMode && section == BrowseSection.Catalog
}

private val BrowseTopBarScrollCollapseDistance = 180.dp
