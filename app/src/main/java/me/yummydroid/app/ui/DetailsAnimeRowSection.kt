package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.Anime

@Composable
internal fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (animes.isEmpty()) return
    val rowState = remember(title, animes.size, animes.firstOrNull()?.id) { LazyListState() }
    var wasFocusedInside by remember(focusGridState, focusBlockKey, focusIndexOffset) { mutableStateOf(false) }
    val focusedIndex = focusGridState?.focusedIndex

    LaunchedEffect(focusedIndex, animes.size, focusIndexOffset, focusGridState) {
        val state = focusGridState ?: return@LaunchedEffect
        val inside = focusedIndex != null && focusedIndex in focusIndexOffset until (focusIndexOffset + animes.size)
        if (inside && !wasFocusedInside && focusedIndex == focusIndexOffset) {
            rowState.scrollToItem(0)
            withFrameNanos { }
            state.requester(focusIndexOffset)?.requestFocusSafely()
        }
        wasFocusedInside = inside
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(entryFocusRequester)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            lazyItemsIndexed(
                animes,
                key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
            ) { index, anime ->
                val itemFocusKey = detailsAnimeRowFocusKey(focusBlockKey, anime.id)
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id, itemFocusKey) },
                    modifier = Modifier
                        .width(172.dp)
                        .then(
                            when {
                                focusGridState != null -> Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = focusIndexOffset + index,
                                    horizontal = true,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = focusIndexOffset,
                                    focusKey = itemFocusKey,
                                )
                                index == 0 && entryFocusRequester != null -> {
                                    Modifier.focusRequester(entryFocusRequester)
                                }
                                else -> Modifier
                            },
                        )
                        .horizontalEdgeFocusHints(index, animes.size),
                )
            }
        }
    }
}

internal fun detailsAnimeRowFocusKey(blockKey: Any?, animeId: Long): Any? {
    return blockKey?.let { "$it:anime:$animeId" }
}
