package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowseWideTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .statusBarsPadding()
            .padding(horizontal = BrowseChromeWideHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppWordmark(modifier = Modifier.weight(1f), height = 52.dp)
            if (state.forcedOfflineMode) OfflineModeChip()
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                entryFocusRequester = navigation.actionsFocusRequester,
                downFocusRequester = navigation.sectionTabsFocusRequester,
                consumeUpWhenNoRequester = true,
                consumeHorizontalEdgesWhenNoRequester = true,
            )
        }
    }
}
