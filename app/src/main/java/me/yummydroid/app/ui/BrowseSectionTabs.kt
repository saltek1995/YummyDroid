package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

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
        ?: visibleSections.indexOf(activeSection).takeIf { it >= 0 }?.toFloat()
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
                onFocusedSectionChanged = { focused ->
                    focusedSection = focused
                },
                onSectionSelected = onSectionSelected,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

private fun Modifier.browseSectionKeyNavigation(
    focusEnabled: Boolean,
    focusedSection: () -> BrowseSection?,
    visibleSections: List<BrowseSection>,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> {
                if (onExitUp == null) false else {
                    onExitUp()
                    true
                }
            }

            Key.DirectionDown -> {
                if (onExitDown == null) false else {
                    onExitDown()
                    true
                }
            }

            Key.DirectionLeft -> {
                val focusedIndex = focusedSection()?.let(visibleSections::indexOf) ?: -1
                focusEnabled && focusedIndex == 0
            }

            Key.DirectionRight -> {
                val focusedIndex = focusedSection()?.let(visibleSections::indexOf) ?: -1
                focusEnabled && focusedIndex == visibleSections.lastIndex
            }

            else -> false
        }
    }
}

@Composable
private fun BrowseSectionTab(
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
            if (focusedSection == section) {
                onFocusedSectionChanged(null)
            }
        }
    }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focusEnabled && focused && inputModeManager.inputMode != InputMode.Touch
    val surfaceColor = if (focusVisible) {
        yummyActionSurfaceColor(focused = true)
    } else {
        yummyActionSurfaceColor()
    }
    val contentColor = if (focusVisible) {
        yummyActionContentColor(focused = true)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
    }
    val shape = if (squareTopCorners) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
    } else {
        RoundedCornerShape(7.dp)
    }
    Box(
        modifier = modifier
            .then(
                focusRequester?.let { requester -> Modifier.focusRequester(requester) }
                    ?: Modifier,
            )
            .onFocusChanged { focusState ->
                val hasFocus = focusState.isFocused || focusState.hasFocus
                focused = hasFocus
                if (hasFocus) {
                    onFocusedSectionChanged(section)
                } else if (focusedSection == section) {
                    onFocusedSectionChanged(null)
                }
            }
            .focusProperties { canFocus = focusEnabled }
            .clearFocusAfterTouch()
            .clip(shape)
            .background(
                color = surfaceColor,
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onSectionSelected(section) },
    ) {
        Text(
            text = section.localizedTitle(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = YummySpacing.xs),
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
}
