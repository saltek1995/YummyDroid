package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import dev.chrisbanes.haze.HazeState
import me.yummydroid.app.BrowseSection

internal data class BrowseBottomSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val focusRequester: FocusRequester?,
    val focusRequesters: Map<BrowseSection, FocusRequester>,
    val onExitUp: (() -> Boolean)?,
    val focusEnabled: Boolean,
)

@Composable
internal fun BrowseBottomBarModern(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    BrowseBottomChromeLayout(
        state = state,
        actions = actions,
        sectionNavigation = sectionNavigation,
        showSectionTabs = !state.forcedOfflineMode,
        hazeState = hazeState,
        topProtectedContent = topProtectedContent,
        topProtectedVisibilityProgress = topProtectedVisibilityProgress,
        modifier = modifier,
    )
}
