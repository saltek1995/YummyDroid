package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// DetailsSubscriptionsSection
private data class DetailsSubscriptionFocus(
    val state: VisualFocusGridState,
    val indexOffset: Int,
    val blockKey: Any?,
) {
    val contentEntryIndex: Int = indexOffset + 1
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsSubscriptionsSection(
    auth: AuthUiState,
    videos: List<VideoVariant>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos.detailsSubscriptionSourceGroups()
    if (groups.isEmpty()) return
    val activeCount = groups.count(VideoVariant::subscribed)
    val localFocusGridState = rememberVisualFocusGridState(
        size = groups.size + 1,
        key = groups.map { it.id to it.matchingVoiceKey },
    )
    val focus = DetailsSubscriptionFocus(
        state = focusGridState ?: localFocusGridState,
        indexOffset = if (focusGridState == null) 0 else focusIndexOffset,
        blockKey = if (focusGridState == null) null else focusBlockKey,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailsSubscriptionsHeader(
            activeCount = activeCount,
            expanded = expanded,
            onClick = { onExpandedChange(!expanded) },
            focus = focus,
            verticalFocusEnabled = focusGridState != null,
        )
        if (expanded) {
            DetailsSubscriptionOptions(
                groups = groups,
                focus = focus,
                verticalFocusEnabled = focusGridState != null,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }
    }
}

@Composable
private fun DetailsSubscriptionsHeader(
    activeCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    focus: DetailsSubscriptionFocus,
    verticalFocusEnabled: Boolean,
) {
    AccordionHeader(
        title = uiText(UiStringKey.Subscriptions),
        summary = activeCount.takeIf { it > 0 }?.let { uiText(UiStringKey.ActiveCount, it) }.orEmpty(),
        expanded = expanded,
        active = activeCount > 0,
        onClick = onClick,
        centerTitle = true,
        modifier = Modifier.visualFocusGridItem(
            state = focus.state,
            index = focus.indexOffset,
            horizontal = true,
            vertical = verticalFocusEnabled,
            blockKey = focus.blockKey,
            blockEntryIndex = focus.indexOffset,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSubscriptionOptions(
    groups: List<VideoVariant>,
    focus: DetailsSubscriptionFocus,
    verticalFocusEnabled: Boolean,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        groups.forEachIndexed { index, video ->
            DetailsSubscriptionOption(
                video = video,
                subscribed = video.subscribed,
                focus = focus,
                focusIndex = focus.indexOffset + index + 1,
                verticalFocusEnabled = verticalFocusEnabled,
                onClick = { onToggleVideoSubscription(video) },
            )
        }
    }
}

@Composable
private fun DetailsSubscriptionOption(
    video: VideoVariant,
    subscribed: Boolean,
    focus: DetailsSubscriptionFocus,
    focusIndex: Int,
    verticalFocusEnabled: Boolean,
    onClick: () -> Unit,
) {
    val itemShape = RoundedCornerShape(8.dp)
    var itemFocused by remember(video.id, video.matchingVoiceKey) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .visualFocusGridItem(
                state = focus.state,
                index = focusIndex,
                horizontal = true,
                vertical = verticalFocusEnabled,
                blockKey = focus.blockKey,
                blockEntryIndex = focus.contentEntryIndex,
            )
            .onFocusChanged { itemFocused = it.isFocused }
            .dpadClickable(itemShape, onClick = onClick),
        color = yummyActionSurfaceColor(selected = subscribed, focused = itemFocused),
        contentColor = yummyActionContentColor(selected = subscribed, focused = itemFocused),
        border = yummyActionBorder(selected = subscribed, focused = itemFocused),
        shape = itemShape,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${video.matchingDubbingTitle} \u00b7 ${video.player}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun List<VideoVariant>.detailsSubscriptionSourceGroups(): List<VideoVariant> {
    val siteVoiceOrder = siteVoiceOrderIndex()
    return filter { it.matchingVoiceKey.isNotBlank() }
        .groupBy { it.matchingSourceKey }
        .values
        .mapNotNull { group ->
            group.minByOrNull { it.index }?.copy(
                subscribed = group.any(VideoVariant::subscribed),
            )
        }
        .sortedWith(
            compareBy<VideoVariant> { siteVoiceOrder[it.matchingVoiceKey] ?: Int.MAX_VALUE }
                .thenBy { it.matchingDubbingTitle }
                .thenBy { it.player },
        )
        .take(18)
}
