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

private data class DetailsHeroFactContent(
    val details: AnimeDetails,
    val year: Int?,
    val studios: List<FilterOption>,
    val creators: List<FilterOption>,
    val facts: List<DetailsHeroFact>,
) {
    val isEmpty: Boolean
        get() = details.genreTags.isEmpty() &&
            year == null && studios.isEmpty() && creators.isEmpty() && facts.isEmpty()
}

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
    val content = details.heroFactContent(apiEpisodeCount, compact)
    if (content.isEmpty) return

    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        DetailsHeroGenreRow(content.details, narrow, compact, onGenreFilterSelected, heroFocusGridState)
        DetailsHeroOptionalYearRow(
            animeId = content.details.id,
            year = content.year,
            narrow = narrow,
            compact = compact,
            onSelected = onYearFilterSelected,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroOptionRow(
            label = uiText(UiStringKey.Studio),
            narrow = narrow,
            compact = compact,
            options = content.studios,
            onSelected = onStudioFilterSelected,
            focusGridState = heroFocusGridState,
            focusIndexOffset = DetailsHeroFocusIndex.FactStudioStart,
            focusBlockKey = DetailsFocusBlockKey.HeroFacts,
        )
        DetailsHeroOptionRow(
            label = uiText(UiStringKey.Director),
            narrow = narrow,
            compact = compact,
            options = content.creators,
            onSelected = onCreatorFilterSelected,
            focusGridState = heroFocusGridState,
            focusIndexOffset = DetailsHeroFocusIndex.FactCreatorStart,
            focusBlockKey = DetailsFocusBlockKey.HeroFacts,
        )
        DetailsHeroPlainFacts(content.facts, narrow, compact)
    }
}

private fun AnimeDetails.heroFactContent(apiEpisodeCount: Int, compact: Boolean): DetailsHeroFactContent {
    val optionLimit = if (compact) 3 else 6
    val factLimit = if (compact) 5 else 8
    return DetailsHeroFactContent(
        details = this,
        year = year?.takeIf { it > 0 },
        studios = studios.take(optionLimit),
        creators = creators.take(optionLimit),
        facts = heroFacts(apiEpisodeCount).take(factLimit),
    )
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
