package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import kotlin.math.abs
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.theme.YummySpacing

internal fun browseSectionIndicatorFraction(activePosition: Float?, index: Int): Float {
    return activePosition
        ?.let { position -> (1f - abs(position - index)).coerceIn(0f, 1f) }
        ?: 0f
}

@Composable
internal fun BrowseSectionTabs(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    squareTopCorners: Boolean = false,
    focusEnabled: Boolean = true,
) {
    val activePosition = activeSectionPosition
        ?: visibleSections.indexOf(activeSection).takeIf { index -> index >= 0 }?.toFloat()
    var focusedSection by remember(visibleSections) { mutableStateOf<BrowseSection?>(null) }
    Row(
        modifier = modifier
            .height(BrowseSectionTabsHeight)
            .browseSectionKeyNavigation(
                focusEnabled = focusEnabled,
                focusedSection = { focusedSection },
                visibleSections = visibleSections,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
            ),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEachIndexed { index, section ->
            BrowseSectionTab(
                section = section,
                selectedFraction = browseSectionIndicatorFraction(activePosition, index),
                focusEnabled = focusEnabled,
                squareTopCorners = squareTopCorners,
                focusRequester = sectionFocusRequesters[section],
                focusedSection = focusedSection,
                onFocusedSectionChanged = { focused -> focusedSection = focused },
                onSectionSelected = onSectionSelected,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
