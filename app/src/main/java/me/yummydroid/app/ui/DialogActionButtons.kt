package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogActionRow(
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    badgeText: String? = null,
) {
    val shape = YummyRadii.smallShape
    val buttonEnabled = enabled && !loading
    val interaction = rememberDialogActionInteraction(
        enabled = buttonEnabled,
        shape = shape,
        onClick = onClick,
    )
    Surface(
        modifier = modifier
            .dialogActionSize(compact = compact, primary = primary)
            .defaultMinSize(minWidth = 0.dp, minHeight = YummySizes.dialogButtonHeight)
            .then(interaction.modifier),
        color = yummyActionSurfaceColor(
            enabled = buttonEnabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        contentColor = yummyActionContentColor(
            enabled = buttonEnabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        border = yummyActionBorder(
            enabled = buttonEnabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        shadowElevation = if (interaction.focusVisible) 0.dp else 2.dp,
        shape = shape,
    ) {
        DialogActionButtonContent(
            text = text,
            loading = loading,
            compact = compact,
            buttonEnabled = buttonEnabled,
            focusVisible = interaction.focusVisible,
            badgeText = badgeText,
        )
    }
}

private data class DialogActionInteraction(
    val modifier: Modifier,
    val focusVisible: Boolean,
)

private fun Modifier.dialogActionSize(compact: Boolean, primary: Boolean): Modifier {
    if (compact) return this
    val minWidth = if (primary) {
        YummySizes.primaryDialogButtonMinWidth
    } else {
        YummySizes.dialogButtonMinWidth
    }
    return widthIn(min = minWidth)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberDialogActionInteraction(
    enabled: Boolean,
    shape: Shape,
    onClick: () -> Unit,
): DialogActionInteraction {
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val uiControls = LocalUiControlCoordinator.current
    val controlOwner = remember { Any() }
    val interactionSource = remember { MutableInteractionSource() }
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val interactionModifier = if (enabled) {
        Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                val focusedNow = focusState.isFocused || focusState.hasFocus
                focused = focusedNow
                if (focusedNow && inputModeManager.inputMode != InputMode.Touch) {
                    uiControls.launch(scope, controlOwner, UiControlOperation.RelocationLatest) {
                        withFrameNanos { }
                        bringIntoViewRequester.bringIntoView()
                    }
                } else {
                    uiControls.cancel(controlOwner, UiControlOperation.RelocationLatest)
                }
            }
            .clearFocusAfterTouch()
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier.clip(shape)
    }
    return DialogActionInteraction(interactionModifier, focusVisible)
}

@Composable
private fun DialogActionButtonContent(
    text: String,
    loading: Boolean,
    compact: Boolean,
    buttonEnabled: Boolean,
    focusVisible: Boolean,
    badgeText: String?,
) {
    val contentPadding = if (compact) {
        PaddingValues(horizontal = 6.dp, vertical = YummySpacing.xs)
    } else {
        PaddingValues(horizontal = YummySpacing.md, vertical = YummySpacing.sm)
    }
    Box(
        modifier = Modifier.defaultMinSize(minHeight = YummySizes.dialogButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) DialogActionLoadingIndicator(focusVisible)
            DialogActionLabel(text = text, compact = compact)
        }
        if (buttonEnabled && badgeText != null) DialogActionBadge(badgeText)
    }
}

@Composable
private fun DialogActionLoadingIndicator(focusVisible: Boolean) {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = if (focusVisible) YummyColors.onFocus else YummyColors.focus,
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun DialogActionLabel(text: String, compact: Boolean) {
    Text(
        text = text,
        style = if (compact) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.titleSmall
        },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
        textAlign = if (compact) TextAlign.Center else TextAlign.Unspecified,
    )
}

@Composable
private fun BoxScope.DialogActionBadge(text: String) {
    Surface(
        color = YummyColors.offline,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 2.dp, end = 2.dp)
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
