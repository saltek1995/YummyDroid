package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import dev.chrisbanes.haze.HazeState
import java.util.Locale
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.readyListOrEmpty

internal data class BrowseHomeChromeState(
    val auth: AuthUiState,
    val activeFilters: Int,
    val activeSearch: Boolean,
    val activeFiltersPanel: Boolean,
    val activeSettings: Boolean,
    val activeDownloads: Boolean,
    val activeProfile: Boolean,
    val activeDownloadCount: Int,
    val forcedOfflineMode: Boolean,
    val catalogActionsEnabled: Boolean,
    val isWide: Boolean,
    val activeSection: BrowseSection,
    val visibleSections: List<BrowseSection>,
    val activeSectionPosition: Float?,
)

@Composable
internal fun BrowseHomeTopBar(
    state: BrowseHomeChromeState,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitDown: () -> Unit,
    actionsFocusRequester: FocusRequester,
    sectionTabsFocusRequester: FocusRequester?,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    visible: Boolean,
    visibilityProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
    collapseWhenHidden: Boolean = true,
) {
    BrowseTopBarModern(
        onOpenSearch = onOpenSearch,
        onOpenFilters = onOpenFilters,
        onOpenSettings = onOpenSettings,
        onOpenDownloads = onOpenDownloads,
        auth = state.auth,
        activeFilters = state.activeFilters,
        activeSearch = state.activeSearch,
        activeFiltersPanel = state.activeFiltersPanel,
        activeSettings = state.activeSettings,
        activeDownloads = state.activeDownloads,
        activeProfile = state.activeProfile,
        activeDownloadCount = state.activeDownloadCount,
        forcedOfflineMode = state.forcedOfflineMode,
        searchEnabled = state.catalogActionsEnabled,
        filtersEnabled = state.catalogActionsEnabled,
        onOpenLogin = onOpenLogin,
        onOpenProfile = onOpenProfile,
        isWide = state.isWide,
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = onSectionSelected,
        onExitDown = onExitDown,
        actionsFocusRequester = actionsFocusRequester,
        sectionTabsFocusRequester = sectionTabsFocusRequester,
        sectionTabFocusRequesters = sectionTabFocusRequesters,
        sectionTabsFocusEnabled = sectionTabsFocusEnabled,
        showCompactControls = false,
        modifier = modifier,
        collapseWhenHidden = collapseWhenHidden,
        visible = visible,
        visibilityProgressProvider = visibilityProgressProvider,
    )
}

@Composable
internal fun BrowseHomeTvSectionTabs(
    state: BrowseHomeChromeState,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    BrowseTvSectionIndicatorBar(
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = onSectionSelected,
        sectionFocusRequesters = sectionFocusRequesters,
        onExitUp = onExitUp,
        onExitDown = onExitDown,
        drawBackdrop = false,
        backdropVisible = false,
        sectionTabsFocusEnabled = sectionTabsFocusEnabled,
        squareTopCorners = false,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseHomeBottomBar(
    state: BrowseHomeChromeState,
    sectionTabsFocusRequester: FocusRequester?,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    sectionTabsOnExitUp: () -> Boolean,
    hazeState: HazeState?,
    showScheduleCalendar: Boolean,
    scheduleDayGroups: List<ScheduleDayGroup>,
    selectedScheduleEpochDay: Long,
    scheduleLocale: Locale,
    scheduleCalendarFocusRequestNonce: Long,
    scheduleCalendarFocusEnabled: Boolean,
    scheduleCalendarOnExitUp: () -> Boolean,
    scheduleCalendarOnExitDown: () -> Boolean,
    onScheduleDaySelected: (Long) -> Unit,
    scheduleCalendarVisibilityProgress: Float,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowseBottomBarModern(
        onOpenSearch = onOpenSearch,
        onOpenFilters = onOpenFilters,
        onOpenSettings = onOpenSettings,
        onOpenDownloads = onOpenDownloads,
        auth = state.auth,
        activeFilters = state.activeFilters,
        activeSearch = state.activeSearch,
        activeFiltersPanel = state.activeFiltersPanel,
        activeSettings = state.activeSettings,
        activeDownloads = state.activeDownloads,
        activeProfile = state.activeProfile,
        activeDownloadCount = state.activeDownloadCount,
        searchEnabled = state.catalogActionsEnabled,
        filtersEnabled = state.catalogActionsEnabled,
        onOpenLogin = onOpenLogin,
        onOpenProfile = onOpenProfile,
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = onSectionSelected,
        showSectionTabs = !state.forcedOfflineMode,
        sectionTabsFocusRequester = sectionTabsFocusRequester,
        sectionTabFocusRequesters = sectionTabFocusRequesters,
        sectionTabsOnExitUp = sectionTabsOnExitUp,
        sectionTabsFocusEnabled = sectionTabsFocusEnabled,
        hazeState = hazeState,
        topProtectedContent = if (showScheduleCalendar) {
            { calendarModifier ->
                ScheduleCalendarBlock(
                    dayGroups = scheduleDayGroups,
                    selectedEpochDay = selectedScheduleEpochDay,
                    locale = scheduleLocale,
                    focusRequestNonce = scheduleCalendarFocusRequestNonce,
                    focusEnabled = scheduleCalendarFocusEnabled,
                    onExitUp = scheduleCalendarOnExitUp,
                    onExitDown = scheduleCalendarOnExitDown,
                    onSelectDay = onScheduleDaySelected,
                    modifier = calendarModifier,
                )
            }
        } else {
            null
        },
        topProtectedVisibilityProgress = scheduleCalendarVisibilityProgress,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseCatalogDialogs(
    state: YummyDroidUiState,
    catalogActionsEnabled: Boolean,
    searchDialogOpen: Boolean,
    filtersDialogOpen: Boolean,
    searchKeyboardDismissRequest: Long,
    searchInputAction: InputAction?,
    searchInputActionRequest: Long,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onDismissSearch: () -> Unit,
    onDismissFilters: () -> Unit,
    onSearchExitDown: () -> Unit,
) {
    if (catalogActionsEnabled && searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            searchHistory = state.searchHistory,
            keyboardDismissRequest = searchKeyboardDismissRequest,
            remoteInputAction = searchInputAction,
            remoteInputActionRequest = searchInputActionRequest,
            onQueryChange = onQueryChange,
            onSubmitQuery = onSearchSubmitted,
            onHistorySelected = onSearchHistorySelected,
            onDismiss = onDismissSearch,
            onExitDown = onSearchExitDown,
        )
    }
    if (catalogActionsEnabled && filtersDialogOpen) {
        FiltersDialogAccordion(
            filters = state.filters,
            auth = state.auth,
            catalogState = state.filterCatalog,
            offlineEntries = state.offlineEntries.readyListOrEmpty(),
            forcedOfflineMode = state.forcedOfflineMode,
            onApply = onFiltersChange,
            onReset = onResetFilters,
            onDismiss = onDismissFilters,
        )
    }
}
