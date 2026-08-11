package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.YummyDroidUiState

internal data class BrowseScreenEnvironment(
    val effectiveSection: BrowseSection,
    val pagerSections: List<BrowseSection>,
    val pagerPage: Int,
    val usePager: Boolean,
    val catalogActionsEnabled: Boolean,
    val isSearching: Boolean,
    val density: Density,
    val dpadFocusEnabled: Boolean,
    val isWide: Boolean,
    val forcedOfflineMode: Boolean,
    val chromePolicy: BrowseChromePolicy,
    val topBarCollapseDistancePx: Float,
) {
    fun topBarFullyVisible(
        browseCoordinator: BrowseRootUiCoordinator,
        section: BrowseSection,
    ): Boolean {
        if (chromePolicy.pinTopChrome) return true
        return browseCoordinator.topBarVisibilityProgress(
            section = section,
            collapseDistancePx = topBarCollapseDistancePx,
        ) > 0.999f
    }
}

@Composable
internal fun rememberBrowseScreenEnvironment(
    state: YummyDroidUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): BrowseScreenEnvironment {
    val isAuthorized = state.auth.profile != null
    val forcedOffline = state.forcedOfflineMode
    val pagerSections = remember(isAuthorized, forcedOffline) {
        resolveBrowsePagerSections(isAuthorized, forcedOffline)
    }
    val effectiveSection = resolveEffectiveBrowseSection(state.homeSection, isAuthorized, forcedOffline)
    LaunchedEffect(state.homeSection, isAuthorized, forcedOffline) {
        resolveBrowseSectionCorrection(state.homeSection, isAuthorized, forcedOffline)
            ?.let(onBrowseSectionChange)
    }
    val density = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val isWide = currentResponsiveWindowSizeDp().width >= 720.dp
    val pagerPage = pagerSections.indexOf(effectiveSection).takeIf { it >= 0 } ?: 0
    return BrowseScreenEnvironment(
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = !forcedOffline && pagerSections.size > 1,
        catalogActionsEnabled = browseCatalogActionsEnabledForSection(effectiveSection, forcedOffline),
        isSearching = effectiveSection == BrowseSection.Catalog && state.searchQuery.isNotBlank(),
        density = density,
        dpadFocusEnabled = inputModeManager.inputMode != InputMode.Touch,
        isWide = isWide,
        forcedOfflineMode = forcedOffline,
        chromePolicy = resolveBrowseChromePolicy(isWide, forcedOffline),
        topBarCollapseDistancePx = with(density) { BrowseTopBarScrollCollapseDistance.toPx() },
    )
}

private val BrowseTopBarScrollCollapseDistance = 180.dp
