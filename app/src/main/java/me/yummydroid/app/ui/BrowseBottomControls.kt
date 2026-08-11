package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

private val BrowseBottomChromeItemGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomControls(
    state: BrowseHomeChromeState,
    actions: BrowseBottomActionHandlers,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
    modifier: Modifier = Modifier,
) {
    val trackedModifier = with(geometry) { modifier.fillMaxWidth().trackBaseControls() }
    Column(
        modifier = trackedModifier
            .padding(
                start = 16.dp,
                top = BrowseBottomChromeInteractiveTopPadding,
                end = 16.dp,
                bottom = BrowseBottomChromeItemGap,
            ),
    ) {
        if (showSectionTabs) {
            BrowseBottomSectionTabs(
                state = state,
                navigation = sectionNavigation,
                protectedSlotActive = protectedSlotActive,
                actionFocusRequester = actionFocusRequester,
                geometry = geometry,
            )
            Spacer(modifier = Modifier.height(BrowseBottomChromeItemGap))
        }
        BrowseBottomActions(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedSlotActive,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
        )
    }
}

@Composable
private fun BrowseBottomSectionTabs(
    state: BrowseHomeChromeState,
    navigation: BrowseBottomSectionNavigation,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val focusRequesters = navigation.focusRequesters.ifEmpty {
        navigation.focusRequester
            ?.let { requester -> mapOf(state.activeSection to requester) }
            .orEmpty()
    }
    BrowseSectionTabs(
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = navigation.onSectionSelected,
        sectionFocusRequesters = focusRequesters,
        onExitUp = navigation.onExitUp,
        onExitDown = { actionFocusRequester.requestFocusSafely() },
        focusEnabled = navigation.focusEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (protectedSlotActive) Modifier else with(geometry) {
                    Modifier.pointerBlockStartAnchor()
                },
            ),
    )
}

@Composable
private fun BrowseBottomActions(
    state: BrowseHomeChromeState,
    actions: BrowseBottomActionHandlers,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val stackActions = currentWindowSizeDp().width < 360.dp
    BrowseTopBarActions(
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
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
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showSectionTabs || protectedSlotActive) Modifier else with(geometry) {
                    Modifier.pointerBlockStartAnchor()
                },
            ),
        spreadActions = !stackActions,
        stackActions = stackActions,
        entryFocusRequester = actionFocusRequester,
        upFocusRequester = sectionNavigation.focusRequester,
        consumeDownWhenNoRequester = true,
        consumeHorizontalEdgesWhenNoRequester = true,
    )
}
