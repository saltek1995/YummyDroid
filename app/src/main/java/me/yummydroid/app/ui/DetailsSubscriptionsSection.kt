package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsSubscriptionsSection(
    auth: AuthUiState,
    videos: List<VideoVariant>,
    subscriptions: List<VideoSubscription>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos.detailsSubscriptionVoiceGroups()
    if (groups.isEmpty()) return
    val activeCount = groups.count { subscriptions.isVideoVoiceSubscribed(it) }
    val localFocusGridState = rememberVisualFocusGridState(
        size = groups.size + 1,
        key = groups.map { it.id to it.matchingVoiceKey },
    )
    val effectiveFocusGridState = focusGridState ?: localFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
    val effectiveFocusBlockKey = if (focusGridState == null) null else focusBlockKey
    val contentEntryIndex = effectiveFocusIndexOffset + 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccordionHeader(
            title = uiText(UiStringKey.Subscriptions),
            summary = activeCount.takeIf { it > 0 }?.let { uiText(UiStringKey.ActiveCount, it) }.orEmpty(),
            expanded = expanded,
            active = activeCount > 0,
            onClick = { onExpandedChange(!expanded) },
            centerTitle = true,
            modifier = Modifier.visualFocusGridItem(
                state = effectiveFocusGridState,
                index = effectiveFocusIndexOffset,
                horizontal = true,
                vertical = focusGridState != null,
                blockKey = effectiveFocusBlockKey,
                blockEntryIndex = effectiveFocusIndexOffset,
            ),
        )

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                groups.forEachIndexed { index, video ->
                    val subscribed = subscriptions.isVideoVoiceSubscribed(video)
                    val itemShape = RoundedCornerShape(8.dp)
                    var itemFocused by remember(video.id, video.matchingVoiceKey) { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .visualFocusGridItem(
                                state = effectiveFocusGridState,
                                index = effectiveFocusIndexOffset + index + 1,
                                horizontal = true,
                                vertical = focusGridState != null,
                                blockKey = effectiveFocusBlockKey,
                                blockEntryIndex = contentEntryIndex,
                            )
                            .onFocusChanged { focusState -> itemFocused = focusState.isFocused }
                            .dpadClickable(itemShape) { onToggleVideoSubscription(video) },
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
                                text = video.matchingDubbingTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun List<VideoVariant>.detailsSubscriptionVoiceGroups(): List<VideoVariant> {
    val siteVoiceOrder = siteVoiceOrderIndex()
    return filter { it.matchingVoiceKey.isNotBlank() }
        .groupBy { it.matchingVoiceKey }
        .values
        .mapNotNull { group -> group.minByOrNull { it.player } }
        .sortedWith(
            compareBy<VideoVariant> { siteVoiceOrder[it.matchingVoiceKey] ?: Int.MAX_VALUE }
                .thenBy { it.matchingDubbingTitle },
        )
        .take(18)
}
