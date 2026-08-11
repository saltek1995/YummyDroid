package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters

internal data class BrowseScreenRuntimeConfig(
    val browseCoordinator: BrowseRootUiCoordinator,
    val activeFocusRequestNonce: Long,
    val onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    val onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val active: Boolean,
)

internal data class BrowseScreenRuntimeActions(
    val onQueryChange: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onSearchHistorySelected: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMoreAnime: () -> Unit,
    val onBrowseSectionChange: (BrowseSection) -> Unit,
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onResetFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onClearDownloadHistory: () -> Unit,
    val onCancelDownload: (Long) -> Unit,
    val onPauseDownload: (Long) -> Unit,
    val onResumeDownload: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenAnime: (Long) -> Unit,
)

@Composable
internal fun BrowseScreenRuntime(
    state: YummyDroidUiState,
    config: BrowseScreenRuntimeConfig,
    actions: BrowseScreenRuntimeActions,
) {
    val environment = rememberBrowseScreenEnvironment(
        state = state,
        browseCoordinator = config.browseCoordinator,
        onBrowseSectionChange = actions.onBrowseSectionChange,
    )
    val focusBinding = rememberBrowseFocusBinding(environment.pagerSections)
    val pagerRuntime = rememberBrowsePagerRuntime(
        initialPage = environment.pagerPage,
        initialSection = environment.effectiveSection,
        pageCount = { environment.pagerSections.size },
    )
    val visualRuntime = rememberBrowseHomeVisualRuntime()
    var scheduleSelectedEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    val dpadLayerFocusRequestNonce = focusBinding.runtime.layerFocusRequestNonce(
        environment.dpadFocusEnabled,
        config.activeFocusRequestNonce,
    )
    val catalogDialogRuntime = rememberBrowseCatalogDialogRuntime(
        environment.catalogActionsEnabled,
        config.onRegisterModalInputActionHandler,
    )
    val phoneSchedule = rememberBrowsePhoneScheduleRuntime(state, environment)
    val navigation = rememberBrowseScreenNavigation(
        state = state,
        environment = environment,
        config = config,
        focusBinding = focusBinding,
        pagerRuntime = pagerRuntime,
        dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        phoneSchedule = phoneSchedule,
        onBrowseSectionChange = actions.onBrowseSectionChange,
    )

    BrowseHomeContent(
        model = createBrowseHomeContentModel(
            state = state,
            config = config,
            environment = environment,
            navigation = navigation,
            focusBinding = focusBinding,
            pagerRuntime = pagerRuntime,
            visualRuntime = visualRuntime,
            catalogDialogRuntime = catalogDialogRuntime,
            phoneSchedule = phoneSchedule,
            scheduleSelectedEpochDay = scheduleSelectedEpochDay,
            dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        ),
        actions = actions.toBrowseHomeContentActions { epochDay ->
            scheduleSelectedEpochDay = epochDay
        },
    )
}

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean = !forcedOfflineMode && section == BrowseSection.Catalog
