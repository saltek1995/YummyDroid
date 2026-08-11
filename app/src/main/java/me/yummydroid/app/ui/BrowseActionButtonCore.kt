package me.yummydroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@Composable
internal fun BrowseActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badgeText: String? = null,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .size(48.dp)
            .then(enabledActionModifier(enabled, shape, interactionSource, focusLinks, onClick) { focused = it }),
        color = yummyActionSurfaceColor(enabled = enabled, selected = active, focused = focusVisible),
        contentColor = yummyActionContentColor(enabled = enabled, selected = active, focused = focusVisible),
        border = yummyActionBorder(enabled = enabled, selected = active, focused = focusVisible),
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(27.dp))
            if (enabled && badgeText != null) BrowseActionBadge(badgeText)
        }
    }
}

private fun enabledActionModifier(
    enabled: Boolean,
    shape: RoundedCornerShape,
    interactionSource: MutableInteractionSource,
    focusLinks: BrowseActionFocusLinks,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
): Modifier {
    if (!enabled) return Modifier.clip(shape)
    return Modifier
        .onFocusChanged { onFocusChanged(it.isFocused || it.hasFocus) }
        .clearFocusAfterTouch()
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .previewKeyHandling(focusLinks)
}

private fun Modifier.previewKeyHandling(focusLinks: BrowseActionFocusLinks): Modifier {
    if (!focusLinks.hasCustomKeyHandling) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionLeft -> focusLinks.leftFocusRequester.requestOr(focusLinks.consumeHorizontalEdgeKey)
            Key.DirectionRight -> focusLinks.rightFocusRequester.requestOr(focusLinks.consumeHorizontalEdgeKey)
            Key.DirectionUp -> focusLinks.upFocusRequester.requestOr(false)
            Key.DirectionDown -> focusLinks.downFocusRequester.requestOr(focusLinks.consumeDownKey)
            else -> false
        }
    }
}

private fun FocusRequester?.requestOr(fallback: Boolean): Boolean {
    return if (this == null) fallback else requestFocusSafely()
}

@Composable
private fun BoxScope.BrowseActionBadge(text: String) {
    Surface(
        color = YummyColors.offline,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 4.dp, end = 4.dp)
            .widthIn(min = 16.dp)
            .height(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}
