package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.YummyDroidUiState

internal data class BrowseScreenNavigation(
    val focusActions: BrowseFocusActions,
    val focusFirstRequests: BrowseFocusFirstRequests,
    val pagerBinding: BrowsePagerBinding,
)

internal data class BrowsePhoneScheduleRuntime(
    val dayGroups: List<ScheduleDayGroup>,
    val showInBottomChrome: Boolean,
)

@Composable
internal fun rememberBrowsePhoneScheduleRuntime(
    state: YummyDroidUiState,
    environment: BrowseScreenEnvironment,
): BrowsePhoneScheduleRuntime {
    val dayGroups = rememberPhoneScheduleDayGroups(
        state.schedule,
        environment.isWide,
        environment.forcedOfflineMode,
    )
    return BrowsePhoneScheduleRuntime(
        dayGroups = dayGroups,
        showInBottomChrome = !environment.isWide &&
            !environment.forcedOfflineMode &&
            environment.effectiveSection == BrowseSection.Schedule &&
            dayGroups.isNotEmpty(),
    )
}

@Composable
internal fun rememberBrowseScreenNavigation(
    state: YummyDroidUiState,
    environment: BrowseScreenEnvironment,
    config: BrowseScreenRuntimeConfig,
    focusBinding: BrowseFocusBinding,
    pagerRuntime: BrowsePagerRuntime,
    dpadLayerFocusRequestNonce: Long,
    phoneSchedule: BrowsePhoneScheduleRuntime,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): BrowseScreenNavigation {
    val focusRuntime = focusBinding.runtime
    val focusActions = focusRuntime.bindActions(
        section = environment.effectiveSection,
        dpadFocusEnabled = environment.dpadFocusEnabled,
        forcedOfflineMode = environment.forcedOfflineMode,
        showPhoneScheduleCalendar = phoneSchedule.showInBottomChrome,
        scheduleGridState = config.browseCoordinator.scheduleGridState,
        browseCoordinator = config.browseCoordinator,
        sectionFocusRequesters = focusBinding.sectionFocusRequesters,
        pagerRuntime = pagerRuntime,
        topBarFullyVisible = {
            environment.topBarFullyVisible(config.browseCoordinator, environment.effectiveSection)
        },
        onRegisterHomeBackToTopHandler = config.onRegisterHomeBackToTopHandler,
    )
    DisposableEffect(config.onRegisterDpadFocusRecoveryHandler) {
        config.onRegisterDpadFocusRecoveryHandler(focusActions.recoverFirstContentFocus)
        onDispose { config.onRegisterDpadFocusRecoveryHandler(null) }
    }
    val focusFirstRequests = resolveBrowseFocusFirstRequests(
        section = environment.effectiveSection,
        persistentCatalogNonce = state.homeFocusResetNonce,
        transientNonce = focusRuntime.firstFocusRequestNonce,
    )
    val pagerBinding = rememberBrowsePagerBinding(
        active = config.active,
        effectiveSection = environment.effectiveSection,
        pagerSections = environment.pagerSections,
        usePager = environment.usePager,
        dpadFocusEnabled = environment.dpadFocusEnabled,
        dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        isWide = environment.isWide,
        forcedOfflineMode = environment.forcedOfflineMode,
        browseCoordinator = config.browseCoordinator,
        topBarCollapseDistancePx = environment.topBarCollapseDistancePx,
        runtime = pagerRuntime,
        onBrowseSectionChange = onBrowseSectionChange,
        onHomeBrowseBackStateChange = config.onHomeBrowseBackStateChange,
        onRequestSectionTabsFocus = focusActions.requestSectionTabsFocus,
    )
    return BrowseScreenNavigation(focusActions, focusFirstRequests, pagerBinding)
}
