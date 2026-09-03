package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import me.yummydroid.app.InputAction
import me.yummydroid.app.ui.components.HorizontalScrollEdgeContentPadding
import me.yummydroid.app.ui.components.HorizontalScrollEdgeFrame
import me.yummydroid.app.ui.components.dpadClickable

// DetailsScreenshotsSection
@Composable
internal fun DetailsScreenshotsSection(
    screenshots: List<String>,
    interactive: Boolean,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (screenshots.isEmpty()) return
    val visibleScreenshots = remember(screenshots) { screenshots.take(24) }
    val rowState = remember(visibleScreenshots) { LazyListState() }
    var selectedIndex by remember(visibleScreenshots) { mutableStateOf<Int?>(null) }
    RegisterVirtualFocusRowEntry(rowState, focusGridState, focusIndexOffset, focusBlockKey)
    LaunchedEffect(interactive) {
        if (!interactive) selectedIndex = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalScrollEdgeFrame(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HorizontalScrollEdgeContentPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lazyItemsIndexed(
                    visibleScreenshots,
                    key = { index, screenshot -> "screenshot:$index:$screenshot" },
                ) { index, screenshot ->
                    ScreenshotThumbnail(
                        screenshot = screenshot,
                        index = index,
                        screenshotCount = visibleScreenshots.size,
                        focusGridState = focusGridState,
                        focusIndexOffset = focusIndexOffset,
                        focusBlockKey = focusBlockKey,
                        onClick = { selectedIndex = index },
                    )
                }
            }
        }
    }

    selectedIndex?.takeIf { interactive }?.let { index ->
        ScreenshotViewerDialog(
            screenshots = visibleScreenshots,
            initialIndex = index,
            onDismiss = { selectedIndex = null },
            onRegisterInputActionHandler = onRegisterInputActionHandler,
        )
    }
}

@Composable
private fun ScreenshotThumbnail(
    screenshot: String,
    index: Int,
    screenshotCount: Int,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(320.dp)
            .aspectRatio(16f / 9f)
            .visualFocusGridItemIfPresent(
                state = focusGridState,
                index = focusIndexOffset + index,
                blockKey = focusBlockKey,
                blockEntryIndex = focusIndexOffset,
                blockedDirections = detailsHorizontalEdgeBlockedDirections(index, screenshotCount),
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalEdgeFocusHints(index, screenshotCount)
            .dpadClickable(shape, onClick = onClick)
            .onPreviewKeyEvent { event ->
                val state = focusGridState ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = event.key.toVisualGridDirectionOrNull()
                    ?: return@onPreviewKeyEvent false
                if (detailsHorizontalEdgeNavigationIsBlocked(
                    localIndex = index,
                    itemCount = screenshotCount,
                    direction = direction,
                )) {
                    return@onPreviewKeyEvent true
                }
                if (direction != VisualGridDirection.Up && direction != VisualGridDirection.Down) {
                    return@onPreviewKeyEvent false
                }
                state.requestFocusTarget(
                    index = focusIndexOffset + index,
                    direction = direction,
                    exit = null,
                )
            },
    ) {
        PosterImage(
            url = screenshot,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
