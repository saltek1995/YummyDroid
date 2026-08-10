package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun SearchHistoryDropdown(
    history: List<String>,
    focusRequesters: List<FocusRequester>,
    inputFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onFocusedIndexChange: (Int, Boolean) -> Unit,
    onFocusInput: () -> Unit,
    onExitDown: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YummyRadii.smallShape)
            .background(yummySurfaceColor(YummySurfaceRole.Panel))
            .border(yummySurfaceBorder(YummySurfaceRole.Panel), YummyRadii.smallShape),
    ) {
        history.forEachIndexed { index, historyQuery ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .focusRequester(focusRequesters.getOrElse(index) { FocusRequester.Default })
                    .focusProperties {
                        up = if (index == 0) {
                            inputFocusRequester
                        } else {
                            focusRequesters[index - 1]
                        }
                        down = focusRequesters.getOrElse(index + 1) { FocusRequester.Default }
                    }
                    .onFocusChanged { focusState ->
                        onFocusedIndexChange(index, focusState.isFocused || focusState.hasFocus)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (index == 0) {
                                    onFocusInput()
                                } else {
                                    focusRequesters[index - 1].requestFocusSafely()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                val nextFocus = focusRequesters.getOrNull(index + 1)
                                if (nextFocus == null) {
                                    onExitDown()
                                } else {
                                    nextFocus.requestFocusSafely()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .dpadClickable(YummyRadii.smallShape) { onSelect(historyQuery) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = historyQuery,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
