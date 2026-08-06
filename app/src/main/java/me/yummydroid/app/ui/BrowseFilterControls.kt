package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun OfflineFilterNotice() {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText(UiStringKey.OfflineOnlyDownloadedAnimeAreShown),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AdvancedFiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val title = if (activeCount > 0) {
        "${uiText(UiStringKey.AdvancedMode)} • $activeCount"
    } else {
        uiText(UiStringKey.AdvancedMode)
    }
    val shape = RoundedCornerShape(8.dp)
    val selected = activeCount > 0
    val contentColor = yummyActionContentColor(selected = selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(
                if (selected) {
                    Modifier
                        .background(yummyActionSurfaceColor(selected = true), shape)
                        .border(yummyActionBorder(selected = true), shape)
                } else {
                    Modifier
                },
            )
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = contentColor)
    }
}

@Composable
internal fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
    onSideExit: () -> Unit,
) {
    AccordionHeader(
        title = uiText(UiStringKey.Sorting),
        summary = selected.localizedTitle(),
        expanded = expanded,
        active = selected != AnimeSort.Rating,
        onClick = onToggleExpanded,
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimeSort.entries.forEach { sort ->
                SelectableFilterRow(
                    title = sort.localizedTitle(),
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun FilterAccordionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSideExit: () -> Unit,
    searchable: Boolean = false,
) {
    if (options.isEmpty()) return

    val uiLocale = LocalUiLanguage.current.uiLocale()
    val sortedOptions = remember(options, uiLocale) { options.sortedByTitle(uiLocale) }
    val expanded = expandedSection == id
    var query by remember(id, expanded) { mutableStateOf("") }
    val visibleOptions = remember(sortedOptions, query, searchable) {
        if (!searchable || query.isBlank()) {
            sortedOptions
        } else {
            sortedOptions.filter { option ->
                option.title.contains(query.trim(), ignoreCase = true) ||
                    option.value.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(uiText(UiStringKey.Search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.isHorizontalFilterExit()) {
                                onSideExit()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            visibleOptions.forEach { option ->
                SelectableFilterRow(
                    title = option.localizedTitle(),
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun RangeAccordionSection(
    id: String,
    title: String,
    summary: String,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    startLabel: String,
    endLabel: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    val expanded = expandedSection == id
    var localStart by remember(id, startText) { mutableStateOf(startText) }
    var localEnd by remember(id, endText) { mutableStateOf(endText) }

    AccordionHeader(
        title = title,
        summary = summary,
        expanded = expanded,
        active = startText.isNotBlank() || endText.isNotBlank(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = localStart,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localStart = sanitized
                    onStartChange(sanitized)
                },
                label = { Text(startLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
            OutlinedTextField(
                value = localEnd,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localEnd = sanitized
                    onEndChange(sanitized)
                },
                label = { Text(endLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@Composable
internal fun AccordionHeader(
    title: String,
    summary: String = "",
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val backgroundColor = yummyActionSurfaceColor(selected = active)
    val contentColor = yummyActionContentColor(selected = active)
    val summaryColor = if (active) {
        YummyColors.focus.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(backgroundColor, shape)
            .border(yummyActionBorder(selected = active), shape)
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        val textPadding = if (centerTitle) {
            Modifier.padding(horizontal = 34.dp)
        } else {
            Modifier.padding(end = 34.dp)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .then(textPadding),
            horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
                    textAlign = if (centerTitle) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
internal fun SelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSideExit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.isHorizontalFilterExit()) {
                    onSideExit?.invoke()
                    onSideExit != null
                } else {
                    false
                }
            }
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun androidx.compose.ui.input.key.KeyEvent.isHorizontalFilterExit(): Boolean {
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
