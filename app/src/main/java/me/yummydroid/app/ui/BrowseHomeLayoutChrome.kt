package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import me.yummydroid.app.BrowseSection

@Composable
internal fun BrowseHomeContentLayout(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BrowseHomeTopChrome(state, actions)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (state.chromeHazeActive) {
                        Modifier.hazeSource(state.chromeHazeState)
                    } else {
                        Modifier
                    },
                ),
        ) {
            BrowsePageHost(state, actions)
        }
    }
}

@Composable
private fun BrowseHomeTopChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    if (state.chromePolicy.pinTopChrome) {
        BrowseTopBarChrome(state, actions, collapseWhenHidden = false)
        if (state.chromePolicy.showTvSectionTabs) {
            BrowseHomeTvTabs(state, actions)
        }
    } else {
        BrowseTopBarChrome(state, actions)
    }
}

@Composable
private fun BrowseHomeTvTabs(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    BrowseHomeTvSectionTabs(
        state = state.homeChromeState,
        sectionFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        onSectionSelected = actions.onSectionSelected,
        onExitUp = actions.onRequestTopActionsFocus,
        onExitDown = {
            if (state.effectiveSection == BrowseSection.Schedule) {
                actions.onRequestScheduleCalendarFocus()
            } else {
                actions.onRequestContentFocus()
            }
        },
    )
}

@Composable
private fun BrowseTopBarChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
    collapseWhenHidden: Boolean = true,
) {
    val wideSectionTabsVisible = state.isWide && !state.forcedOfflineMode
    BrowseHomeTopBar(
        state = state.homeChromeState,
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = actions.onSectionSelected,
        onExitDown = {
            if (wideSectionTabsVisible) {
                actions.onRequestSectionTabsFocus(state.effectiveSection, false)
            } else {
                actions.onRequestContentFocus()
            }
        },
        actionsFocusRequester = state.topActionsFocusRequester,
        sectionTabsFocusRequester = if (wideSectionTabsVisible) {
            state.sectionTabFocusRequesters[state.effectiveSection]
        } else {
            null
        },
        sectionTabFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        collapseWhenHidden = collapseWhenHidden,
        visible = state.topBarVisible,
        visibilityProgressProvider = state.topBarVisibilityProgressProvider,
    )
}
