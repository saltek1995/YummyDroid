package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.formatDuration

internal data class DetailsHeroFact(
    val label: UiStringKey,
    val value: String,
)

private val DetailsHeroLinkedFactLabels = setOf(
    UiStringKey.Year92264e,
    UiStringKey.Studio,
    UiStringKey.Director,
)

@Composable
internal fun DetailsHeroFactRows(
    details: AnimeDetails,
    apiEpisodeCount: Int,
    narrow: Boolean,
    compact: Boolean,
    onGenreFilterSelected: (FilterOption) -> Unit,
    onYearFilterSelected: (Int) -> Unit,
    onStudioFilterSelected: (FilterOption) -> Unit,
    onCreatorFilterSelected: (FilterOption) -> Unit,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    val facts = details.heroFacts(apiEpisodeCount)
    val hasLinkedFacts = details.year?.takeIf { it > 0 } != null ||
        details.studios.isNotEmpty() || details.creators.isNotEmpty()
    if (details.genreTags.isEmpty() && facts.isEmpty() && !hasLinkedFacts) return

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        DetailsHeroGenreRow(details, narrow, compact, onGenreFilterSelected, heroFocusGridState)
        details.year?.takeIf { it > 0 }?.let { year ->
            DetailsHeroYearRow(details.id, year, narrow, compact, onYearFilterSelected, heroFocusGridState)
        }
        DetailsHeroOptionRow(
            label = uiText(UiStringKey.Studio),
            narrow = narrow,
            compact = compact,
            options = details.studios.take(if (compact) 3 else 6),
            onSelected = onStudioFilterSelected,
            focusGridState = heroFocusGridState,
            focusIndexOffset = DetailsHeroFocusIndex.FactStudioStart,
            focusBlockKey = DetailsFocusBlockKey.HeroFacts,
        )
        DetailsHeroOptionRow(
            label = uiText(UiStringKey.Director),
            narrow = narrow,
            compact = compact,
            options = details.creators.take(if (compact) 3 else 6),
            onSelected = onCreatorFilterSelected,
            focusGridState = heroFocusGridState,
            focusIndexOffset = DetailsHeroFocusIndex.FactCreatorStart,
            focusBlockKey = DetailsFocusBlockKey.HeroFacts,
        )
        DetailsHeroPlainFacts(facts.take(if (compact) 5 else 8), narrow, compact)
    }
}

@Composable
private fun DetailsHeroPlainFacts(
    facts: List<DetailsHeroFact>,
    narrow: Boolean,
    compact: Boolean,
) {
    facts.forEach { fact ->
        DetailsHeroValueRow(uiText(fact.label), narrow, compact) {
            Text(
                text = fact.value,
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                color = if (fact.label in DetailsHeroLinkedFactLabels) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun AnimeDetails.heroFacts(apiEpisodeCount: Int): List<DetailsHeroFact> {
    val linkedValues = buildSet {
        year?.takeIf { it > 0 }?.let { add(it.toString()) }
        studios.takeIf { it.isNotEmpty() }?.let { add(it.joinToString { studio -> studio.title }) }
        creators.takeIf { it.isNotEmpty() }?.let { add(it.joinToString { creator -> creator.title }) }
    }
    return buildList {
        add(DetailsHeroFact(UiStringKey.Type, type))
        add(DetailsHeroFact(UiStringKey.AgeRating, minAge))
        add(DetailsHeroFact(UiStringKey.Status, status))
        year?.let { add(DetailsHeroFact(UiStringKey.Year92264e, it.toString())) }
        if (studios.isNotEmpty()) add(DetailsHeroFact(UiStringKey.Studio, studios.joinToString { it.title }))
        if (creators.isNotEmpty()) add(DetailsHeroFact(UiStringKey.Director, creators.joinToString { it.title }))
        apiEpisodeCount.takeIf { it > 0 }?.let { add(DetailsHeroFact(UiStringKey.EpisodeCount, it.toString())) }
        durationSeconds.takeIf { it > 0 }?.let { seconds ->
            formatDuration(seconds)?.let { add(DetailsHeroFact(UiStringKey.Duration, it)) }
        }
    }.filter { it.value.isPresentFactValue() && it.value !in linkedValues }
}
