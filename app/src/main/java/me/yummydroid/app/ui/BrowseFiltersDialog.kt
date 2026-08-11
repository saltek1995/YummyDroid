package me.yummydroid.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.OfflineAnimeEntry

@Composable
internal fun FiltersDialogAccordion(
    filters: BrowseFilters,
    auth: AuthUiState,
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null && !forcedOfflineMode
    var draft by remember(filters, isAuthorized, forcedOfflineMode) {
        mutableStateOf(filters.normalizedForFiltersDialog(isAuthorized, forcedOfflineMode))
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = rememberFiltersDialogCatalog(catalogState, offlineEntries, forcedOfflineMode)
    val options = rememberFiltersDialogOptions(catalog, draft)
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        { applyFocusRequester.requestFocusSafely() }
    }
    val contentState = FiltersDialogContentState(
        filters = draft,
        catalog = catalog,
        options = options,
        expandedSection = expandedSection,
        advancedVisible = advancedVisible,
        hiddenActiveCount = hiddenActiveCount,
        isAuthorized = isAuthorized,
        forcedOfflineMode = forcedOfflineMode,
        errorMessage = (catalogState as? LoadState.Error)?.message,
    )
    val callbacks = FiltersDialogContentCallbacks(
        onFiltersChange = { draft = it },
        onExpandedSectionChange = { expandedSection = it },
        onShowAdvanced = { advancedVisible = true },
        onSideExit = moveFocusToActions,
    )

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Filters)) },
        text = { FiltersDialogContent(contentState, callbacks) },
        confirmButton = {
            FiltersDialogActions(
                applyFocusRequester = applyFocusRequester,
                onReset = {
                    draft = BrowseFilters().normalizedForFiltersDialog(isAuthorized, forcedOfflineMode)
                    onReset()
                    onDismiss()
                },
                onCancel = onDismiss,
                onApply = {
                    onApply(draft.normalizedForFiltersDialog(isAuthorized, forcedOfflineMode))
                    onDismiss()
                },
            )
        },
    )
}
