package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.Anime
import me.yummydroid.app.ui.components.HorizontalScrollEdgeContentPadding
import me.yummydroid.app.ui.components.HorizontalScrollEdgeFrame

// DetailsAnimeRowSection
@Composable
internal fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
    horizontalEdgeBridgeTargetOffset: Int = 0,
    horizontalEdgeBridgeTargetCount: Int = 0,
    horizontalEdgeBridgeTargetBlockKey: Any? = null,
) {
    if (animes.isEmpty()) return
    val rowState = remember(title, animes.size, animes.firstOrNull()?.id) { LazyListState() }
    SyncDetailsAnimeRowFocus(rowState, animes.size, focusGridState, focusIndexOffset, focusBlockKey)
    RegisterVirtualFocusRowEntry(rowState, focusGridState, focusIndexOffset, focusBlockKey)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(entryFocusRequester)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        HorizontalScrollEdgeFrame(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HorizontalScrollEdgeContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                lazyItemsIndexed(
                    animes,
                    key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
                ) { index, anime ->
                    DetailsAnimeRowItem(
                        anime = anime,
                        index = index,
                        itemCount = animes.size,
                        onOpenAnime = onOpenAnime,
                        entryFocusRequester = entryFocusRequester,
                        focusGridState = focusGridState,
                        focusIndexOffset = focusIndexOffset,
                        focusBlockKey = focusBlockKey,
                        horizontalEdgeBridgeTargetOffset = horizontalEdgeBridgeTargetOffset,
                        horizontalEdgeBridgeTargetCount = horizontalEdgeBridgeTargetCount,
                        horizontalEdgeBridgeTargetBlockKey = horizontalEdgeBridgeTargetBlockKey,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RegisterVirtualFocusRowEntry(
    rowState: LazyListState,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    val state = focusGridState ?: return
    val blockKey = focusBlockKey ?: return
    DisposableEffect(state, blockKey, focusIndexOffset, rowState) {
        val registrationId = state.registerVirtualBlockEntry(blockKey, focusIndexOffset) {
            rowState.requestScrollToItem(0)
        }
        onDispose {
            state.unregisterVirtualBlockEntry(blockKey, focusIndexOffset, registrationId)
        }
    }
    LaunchedEffect(
        state,
        blockKey,
        focusIndexOffset,
        rowState.layoutInfo.visibleItemsInfo.firstOrNull()?.index,
    ) {
        if (rowState.layoutInfo.visibleItemsInfo.any { item -> item.index == 0 }) {
            withFrameNanos { }
            state.completePendingMaterializedFocus()
        }
    }
}

@Composable
private fun SyncDetailsAnimeRowFocus(
    rowState: LazyListState,
    itemCount: Int,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    var wasFocusedInside by remember(focusGridState, focusBlockKey, focusIndexOffset) { mutableStateOf(false) }
    val focusedIndex = focusGridState?.focusedIndex
    val focusedInside = focusedIndex != null && focusedIndex in focusIndexOffset until (focusIndexOffset + itemCount)
    UiControlEffect(
        focusedIndex,
        itemCount,
        focusIndexOffset,
        focusGridState,
        enabled = focusedInside && !wasFocusedInside && focusedIndex == focusIndexOffset,
    ) {
        val state = focusGridState ?: return@UiControlEffect
        rowState.scrollToItem(0)
        withFrameNanos { }
        state.requester(focusIndexOffset)?.requestFocusSafely()
        wasFocusedInside = true
    }
    LaunchedEffect(focusedInside) {
        if (!focusedInside) wasFocusedInside = false
    }
}

@Composable
private fun DetailsAnimeRowItem(
    anime: Anime,
    index: Int,
    itemCount: Int,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
    horizontalEdgeBridgeTargetOffset: Int,
    horizontalEdgeBridgeTargetCount: Int,
    horizontalEdgeBridgeTargetBlockKey: Any?,
) {
    val itemFocusKey = detailsAnimeRowFocusKey(focusBlockKey, anime.id)
    AnimeCard(
        anime = anime,
        onClick = { onOpenAnime(anime.id, itemFocusKey) },
        modifier = Modifier
            .width(172.dp)
            .then(
                detailsAnimeRowItemFocusModifier(
                    index = index,
                    entryFocusRequester = entryFocusRequester,
                    focusGridState = focusGridState,
                    focusIndexOffset = focusIndexOffset,
                    focusBlockKey = focusBlockKey,
                    itemFocusKey = itemFocusKey,
                ),
            )
            .horizontalEdgeFocusHints(index, itemCount)
            .onPreviewKeyEvent { event ->
                val state = focusGridState ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = event.key.toVisualGridDirectionOrNull()
                    ?: return@onPreviewKeyEvent false
                val targetIndex = detailsHorizontalEdgeBridgeTargetIndex(
                    localIndex = index,
                    itemCount = itemCount,
                    targetIndexOffset = horizontalEdgeBridgeTargetOffset,
                    targetItemCount = horizontalEdgeBridgeTargetCount,
                    direction = direction,
                ) ?: return@onPreviewKeyEvent false
                val targetBlockKey = horizontalEdgeBridgeTargetBlockKey ?: return@onPreviewKeyEvent false
                state.requestVirtualBlockEntry(targetBlockKey, targetIndex)
            },
    )
}

private fun detailsAnimeRowItemFocusModifier(
    index: Int,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
    itemFocusKey: Any?,
): Modifier = when {
    focusGridState != null -> Modifier.visualFocusGridItem(
        state = focusGridState,
        index = focusIndexOffset + index,
        horizontal = true,
        vertical = true,
        blockKey = focusBlockKey,
        blockEntryIndex = focusIndexOffset,
        focusKey = itemFocusKey,
    )
    index == 0 && entryFocusRequester != null -> Modifier.focusRequester(entryFocusRequester)
    else -> Modifier
}

internal fun detailsAnimeRowFocusKey(blockKey: Any?, animeId: Long): Any? {
    return blockKey?.let { "$it:anime:$animeId" }
}
