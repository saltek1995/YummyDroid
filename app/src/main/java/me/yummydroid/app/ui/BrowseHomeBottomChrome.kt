package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import me.yummydroid.app.BrowseSection

@Composable
internal fun BoxScope.BrowseHomeBottomChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    BrowseHomeBottomBar(
        state = state.homeChromeState,
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = actions.onSectionSelected,
        sectionTabsFocusRequester = state.sectionTabFocusRequesters[state.effectiveSection],
        sectionTabFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsOnExitUp = if (state.showPhoneScheduleCalendar) {
            actions.onRequestScheduleCalendarFocus
        } else {
            actions.onRequestContentFocus
        },
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        hazeState = if (state.chromeHazeActive) state.chromeHazeState else null,
        showScheduleCalendar = state.showPhoneScheduleCalendarVisual,
        scheduleDayGroups = state.phoneScheduleDayGroups,
        selectedScheduleEpochDay = state.selectedScheduleEpochDay,
        scheduleLocale = state.scheduleLocale,
        scheduleCalendarFocusRequestNonce = state.scheduleCalendarFocusRequestNonce,
        scheduleCalendarFocusEnabled = state.dpadFocusEnabled,
        scheduleCalendarOnExitUp = actions.onRequestContentFocus,
        scheduleCalendarOnExitDown = {
            actions.onRequestSectionTabsFocus(BrowseSection.Schedule, true)
        },
        onScheduleDaySelected = actions.onScheduleDaySelected,
        scheduleCalendarVisibilityProgress = state.scheduleCalendarVisualProgress,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { size ->
                actions.onBottomChromeMeasured(
                    size.height,
                    state.showPhoneScheduleCalendarVisual,
                )
            },
    )
}
