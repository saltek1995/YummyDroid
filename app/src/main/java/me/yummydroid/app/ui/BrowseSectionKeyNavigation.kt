package me.yummydroid.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import me.yummydroid.app.BrowseSection

internal fun Modifier.browseSectionKeyNavigation(
    focusEnabled: Boolean,
    focusedSection: () -> BrowseSection?,
    visibleSections: List<BrowseSection>,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> onExitUp.consumeSectionExit()
            Key.DirectionDown -> onExitDown.consumeSectionExit()
            Key.DirectionLeft -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = 0,
            )
            Key.DirectionRight -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = visibleSections.lastIndex,
            )
            else -> false
        }
    }
}

private fun (() -> Boolean)?.consumeSectionExit(): Boolean {
    if (this == null) return false
    invoke()
    return true
}

private fun isFocusedSectionEdge(
    focusEnabled: Boolean,
    focusedSection: BrowseSection?,
    visibleSections: List<BrowseSection>,
    edgeIndex: Int,
): Boolean {
    if (!focusEnabled) return false
    val focusedIndex = focusedSection?.let(visibleSections::indexOf) ?: -1
    return focusedIndex == edgeIndex
}
