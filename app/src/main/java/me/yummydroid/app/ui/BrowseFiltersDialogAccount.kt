package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.data.userMarkFilterOptions

@Composable
internal fun AdvancedAccountFilterSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    if (state.isAuthorized) {
        FiltersDialogSelectionSection(
            id = "user_marks",
            title = uiText(UiStringKey.Marks),
            options = userMarkFilterOptions,
            selected = filters.userMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(userMarks = filters.userMarks.toggle(it)) },
        )
        FiltersDialogSelectionSection(
            id = "excluded_user_marks",
            title = uiText(UiStringKey.ExcludeMarks),
            options = userMarkFilterOptions,
            selected = filters.excludedUserMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(excludedUserMarks = filters.excludedUserMarks.toggle(it)) },
        )
    }
    if (state.forcedOfflineMode) {
        OfflineFilterNotice()
    } else {
        SettingsSwitchRow(
            title = uiText(UiStringKey.AvailableOffline),
            checked = filters.offlineOnly,
            onCheckedChange = { callbacks.onFiltersChange(filters.copy(offlineOnly = it)) },
        )
    }
}
