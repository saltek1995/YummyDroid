package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroGenreRow(
    details: AnimeDetails,
    narrow: Boolean,
    compact: Boolean,
    onSelected: (FilterOption) -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    if (details.genreTags.isEmpty()) return
    val genres = details.genreTags.take(if (compact) 4 else 8)
    val localFocusGridState = rememberVisualFocusGridState(
        size = genres.size,
        key = details.id to "genres" to genres.map { it.value },
    )
    val focusGridState = heroFocusGridState ?: localFocusGridState
    val indexOffset = if (heroFocusGridState != null) DetailsHeroFocusIndex.FactGenreStart else 0
    val blockKey = if (heroFocusGridState != null) DetailsFocusBlockKey.HeroFacts else null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DetailsHeroFactLabel(uiText(UiStringKey.Genres), narrow, compact)
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            genres.forEachIndexed { index, genre ->
                DetailsHeroInfoBadge(
                    text = genre.title,
                    onClick = { onSelected(genre) },
                    modifier = Modifier.heroFactFocusItem(focusGridState, indexOffset + index, blockKey),
                )
            }
        }
    }
}

@Composable
internal fun DetailsHeroYearRow(
    animeId: Long,
    year: Int,
    narrow: Boolean,
    compact: Boolean,
    onSelected: (Int) -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    val localFocusGridState = rememberVisualFocusGridState(
        size = 1,
        key = animeId to "year" to year,
    )
    val focusGridState = heroFocusGridState ?: localFocusGridState
    val focusIndex = if (heroFocusGridState != null) DetailsHeroFocusIndex.FactYear else 0
    val blockKey = if (heroFocusGridState != null) DetailsFocusBlockKey.HeroFacts else null
    DetailsHeroValueRow(uiText(UiStringKey.Year92264e), narrow, compact) {
        DetailsHeroInfoBadge(
            text = year.toString(),
            onClick = { onSelected(year) },
            modifier = Modifier.heroFactFocusItem(focusGridState, focusIndex, blockKey),
        )
    }
}

@Composable
internal fun DetailsHeroOptionalYearRow(
    animeId: Long,
    year: Int?,
    narrow: Boolean,
    compact: Boolean,
    onSelected: (Int) -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    if (year == null) return
    DetailsHeroYearRow(animeId, year, narrow, compact, onSelected, heroFocusGridState)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroOptionRow(
    label: String,
    narrow: Boolean,
    compact: Boolean,
    options: List<FilterOption>,
    onSelected: (FilterOption) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (options.isEmpty()) return
    val localFocusGridState = rememberVisualFocusGridState(
        size = options.size,
        key = label to options.map { it.value },
    )
    val effectiveGridState = focusGridState ?: localFocusGridState
    val effectiveIndexOffset = if (focusGridState != null) focusIndexOffset else 0
    val effectiveBlockKey = if (focusGridState != null) focusBlockKey else null
    DetailsHeroValueRow(label, narrow, compact) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEachIndexed { index, option ->
                DetailsHeroInfoBadge(
                    text = option.title,
                    onClick = { onSelected(option) },
                    modifier = Modifier.heroFactFocusItem(
                        effectiveGridState,
                        effectiveIndexOffset + index,
                        effectiveBlockKey,
                    ),
                )
            }
        }
    }
}

private fun Modifier.heroFactFocusItem(
    state: VisualFocusGridState,
    index: Int,
    blockKey: Any?,
): Modifier = visualFocusGridItem(
    state = state,
    index = index,
    horizontal = true,
    vertical = true,
    blockKey = blockKey,
    blockEntryIndex = index,
)
