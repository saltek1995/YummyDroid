package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import me.yummydroid.app.data.FilterOption

internal fun Modifier.onHorizontalFilterExit(onExit: (() -> Unit)?): Modifier {
    if (onExit == null) return this
    return onPreviewKeyEvent { event ->
        if (!event.isHorizontalFilterExit()) return@onPreviewKeyEvent false
        onExit()
        true
    }
}

internal fun KeyEvent.isHorizontalFilterExit(): Boolean {
    return type == KeyEventType.KeyDown && (key == Key.DirectionLeft || key == Key.DirectionRight)
}

internal fun Modifier.horizontalEdgeFocusHints(
    index: Int,
    total: Int,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
): Modifier {
    if (total <= 0 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst && leftExit != null) left = leftExit
        if (isLast && rightExit != null) right = rightExit
    }
}

@Composable
internal fun rangeSummary(from: Number?, to: Number?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> uiText(UiStringKey.All)
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "${uiText(UiStringKey.FromDba126)} $start"
        else -> "${uiText(UiStringKey.To7618b0)} $end"
    }
}

@Composable
internal fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return uiText(UiStringKey.All)
    val titles = options
        .filter { it.value in selected }
        .map { it.localizedTitle() }
    return when {
        titles.isEmpty() -> "${selected.size} ${uiText(UiStringKey.Selected)}"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}
