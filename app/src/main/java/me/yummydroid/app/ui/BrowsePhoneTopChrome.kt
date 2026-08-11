package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowsePhoneTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .padding(horizontal = BrowseChromePhoneHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().statusBarsPadding())
        if (state.forcedOfflineMode) BrowsePhoneOfflineIndicator()
        if (showCompactControls) {
            BrowseSectionTabs(
                activeSection = state.activeSection,
                visibleSections = state.visibleSections,
                activeSectionPosition = state.activeSectionPosition,
                onSectionSelected = navigation.onSectionSelected,
                sectionFocusRequesters = navigation.sectionTabFocusRequesters,
                focusEnabled = navigation.sectionTabsFocusEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            val stackActions = currentWindowSizeDp().width < 360.dp
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                modifier = Modifier.fillMaxWidth(),
                spreadActions = !stackActions,
                stackActions = stackActions,
                entryFocusRequester = navigation.actionsFocusRequester,
            )
        }
    }
}

@Composable
private fun BrowsePhoneOfflineIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        OfflineModeChip()
    }
}
