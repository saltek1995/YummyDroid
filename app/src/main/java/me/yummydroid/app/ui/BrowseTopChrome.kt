package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection

internal val BrowseChromePhoneHorizontalPadding = 16.dp
internal val BrowseChromeWideHorizontalPadding = 24.dp

internal data class BrowseTopSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val onExitDown: (() -> Unit)?,
    val actionsFocusRequester: FocusRequester?,
    val sectionTabsFocusRequester: FocusRequester?,
    val sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    val sectionTabsFocusEnabled: Boolean,
)

internal data class BrowseTopChromeVisibility(
    val collapseWhenHidden: Boolean,
    val visible: Boolean,
    val progress: Float?,
    val progressProvider: (() -> Float)?,
)

@Composable
internal fun BrowseTopBarModern(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    if (state.isWide) {
        BrowseWideTopChrome(state, callbacks, navigation, visibility, modifier)
    } else {
        BrowsePhoneTopChrome(
            state = state,
            callbacks = callbacks,
            navigation = navigation,
            showCompactControls = showCompactControls,
            visibility = visibility,
            modifier = modifier,
        )
    }
}
