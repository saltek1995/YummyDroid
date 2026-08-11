package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

private data class BrowseSectionTabStyle(
    val shape: Shape,
    val surfaceColor: Color,
    val contentColor: Color,
)

@Composable
internal fun BrowseSectionTab(
    section: BrowseSection,
    selectedFraction: Float,
    focusEnabled: Boolean,
    squareTopCorners: Boolean,
    focusRequester: FocusRequester?,
    focusedSection: BrowseSection?,
    onFocusedSectionChanged: (BrowseSection?) -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusEnabled) {
        if (!focusEnabled) {
            focused = false
            if (focusedSection == section) onFocusedSectionChanged(null)
        }
    }
    val focusVisible = focusEnabled &&
        focused &&
        LocalInputModeManager.current.inputMode != InputMode.Touch
    val style = resolveBrowseSectionTabStyle(focusVisible, squareTopCorners)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focusState ->
                val hasFocus = focusState.isFocused || focusState.hasFocus
                focused = hasFocus
                when {
                    hasFocus -> onFocusedSectionChanged(section)
                    focusedSection == section -> onFocusedSectionChanged(null)
                }
            }
            .focusProperties { canFocus = focusEnabled }
            .clearFocusAfterTouch()
            .clip(style.shape)
            .background(style.surfaceColor, style.shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                onSectionSelected(section)
            },
    ) {
        BrowseSectionTabContent(section, selectedFraction, style.contentColor)
    }
}

@Composable
private fun resolveBrowseSectionTabStyle(
    focusVisible: Boolean,
    squareTopCorners: Boolean,
): BrowseSectionTabStyle {
    val shape = if (squareTopCorners) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
    } else {
        RoundedCornerShape(7.dp)
    }
    return BrowseSectionTabStyle(
        shape = shape,
        surfaceColor = if (focusVisible) yummyActionSurfaceColor(focused = true) else yummyActionSurfaceColor(),
        contentColor = if (focusVisible) {
            yummyActionContentColor(focused = true)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
        },
    )
}

@Composable
private fun BoxScope.BrowseSectionTabContent(
    section: BrowseSection,
    selectedFraction: Float,
    contentColor: Color,
) {
    Text(
        text = section.localizedTitle(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = YummySpacing.xs),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(3.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = selectedFraction),
                shape = RoundedCornerShape(1.dp),
            ),
    )
}
