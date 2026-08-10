package me.yummydroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle
import java.util.Locale
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@Composable
internal fun ScheduleDayTile(
    group: ScheduleDayGroup,
    selected: Boolean,
    locale: Locale,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
    onFocusedChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onMovePrevious: () -> Boolean,
    onMoveNext: () -> Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val dayContentColor = yummyActionContentColor(selected = selected, focused = focusVisible)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ScheduleDayTileWidth),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScheduleDayTileHeight)
                .focusProperties { canFocus = focusEnabled }
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val hasFocus = focusState.isFocused || focusState.hasFocus
                    focused = hasFocus
                    onFocusedChanged(hasFocus)
                }
                .clearFocusAfterTouch()
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .scheduleDayTileKeyNavigation(onMovePrevious, onMoveNext, onExitUp, onExitDown),
            color = yummyActionSurfaceColor(selected = selected, focused = focusVisible),
            contentColor = dayContentColor,
            border = yummyActionBorder(selected = selected, focused = focusVisible),
            shape = shape,
        ) {
            ScheduleDayTileContent(
                group = group,
                locale = locale,
                focusVisible = focusVisible,
                dayContentColor = dayContentColor,
            )
        }
    }
}

@Composable
private fun ScheduleDayTileContent(
    group: ScheduleDayGroup,
    locale: Locale,
    focusVisible: Boolean,
    dayContentColor: Color,
) {
    val dayOfWeek = remember(group.date, locale) {
        group.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            .replace(".", "")
            .replaceFirstChar { char -> char.uppercase(locale) }
    }
    val isWeekend = remember(group.date) { group.date.dayOfWeek.value >= 6 }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (focusVisible) dayContentColor else if (isWeekend) Color(0xFFFF626B) else dayContentColor,
            )
            Text(
                text = group.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = dayContentColor,
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp),
            shape = YummyRadii.pillShape,
            color = YummyColors.offline,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) {
            Text(
                text = group.items.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

private fun Modifier.scheduleDayTileKeyNavigation(
    onMovePrevious: () -> Boolean,
    onMoveNext: () -> Boolean,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> onMovePrevious()
        Key.DirectionRight -> onMoveNext()
        Key.DirectionUp -> onExitUp()
        Key.DirectionDown -> onExitDown()
        else -> false
    }
}
