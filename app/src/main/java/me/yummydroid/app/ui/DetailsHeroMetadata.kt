package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// DetailsHeroExternalRatings
internal data class ExternalRatingDisplay(
    val iconResId: Int,
    val iconSize: Dp,
    val title: String,
    val value: String,
)

internal fun detailsHeroExternalRatingDisplays(ratingDetails: RatingDetails): List<ExternalRatingDisplay> {
    return buildList {
        ratingDetails.worldArt?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_world_art, 26.dp, "World Art", formatRating(it)))
        }
        ratingDetails.kinopoisk?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_kinopoisk, 18.dp, "Kinopoisk", formatRating(it)))
        }
        ratingDetails.shikimori?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_shikimori, 18.dp, "Shikimori", formatRating(it)))
        }
        ratingDetails.myAnimeList?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_mal, 33.dp, "MyAnimeList", formatRating(it)))
        }
        ratingDetails.aniDub?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_anilibria, 20.dp, "Anilibria", formatRating(it)))
        }
    }
}
@Composable
internal fun HeroExternalRatingBadges(ratingDetails: RatingDetails) {
    detailsHeroExternalRatingDisplays(ratingDetails).forEach { entry ->
        HeroExternalRatingItem(entry = entry)
    }
}

@Composable
private fun HeroExternalRatingItem(entry: ExternalRatingDisplay) {
    val textStyle = MaterialTheme.typography.titleMedium.withoutFontPadding()
    Row(
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(id = entry.iconResId),
            contentDescription = entry.title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(entry.iconSize),
        )
        Text(
            text = entry.value,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

internal fun RatingDetails.hasExternalRatings(): Boolean {
    return detailsHeroExternalRatingDisplays(this).isNotEmpty()
}
// DetailsHeroFactComponents
@Composable
internal fun DetailsHeroValueRow(
    label: String,
    narrow: Boolean,
    compact: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DetailsHeroFactLabel(label, narrow, compact)
        content()
    }
}

@Composable
internal fun DetailsHeroFactLabel(text: String, narrow: Boolean, compact: Boolean) {
    val labelText = if (compact && !text.endsWith(":")) "$text:" else text
    val labelModifier = when {
        compact -> Modifier
        narrow -> Modifier.width(160.dp)
        else -> Modifier.width(200.dp)
    }
    Text(
        text = labelText,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = labelModifier,
    )
}

@Composable
internal fun DetailsHeroInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactiveModifier = onClick?.let { Modifier.dpadClickable(shape, it) } ?: Modifier
    Surface(
        modifier = modifier.then(interactiveModifier),
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(),
        border = yummyActionBorder(),
        shape = shape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
// DetailsHeroFacts
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

internal data class DetailsHeroFactLimits(
    val genres: Int,
    val linkedOptions: Int,
    val plainFacts: Int,
)

internal fun detailsHeroFactLimits(compact: Boolean): DetailsHeroFactLimits =
    if (compact) {
        DetailsHeroFactLimits(genres = 4, linkedOptions = 3, plainFacts = 5)
    } else {
        DetailsHeroFactLimits(genres = 8, linkedOptions = 6, plainFacts = 8)
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
    val limits = detailsHeroFactLimits(compact)
    return DetailsHeroFactContent(
        details = this,
        year = year?.takeIf { it > 0 },
        studios = studios.take(limits.linkedOptions),
        creators = creators.take(limits.linkedOptions),
        facts = heroFacts(apiEpisodeCount).take(limits.plainFacts),
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
// DetailsHeroHeading
@Composable
internal fun DetailsHeroHeading(
    details: AnimeDetails,
    compact: Boolean,
    isWide: Boolean,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showHeroRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    interactive: Boolean,
    heroFocusGridState: VisualFocusGridState?,
) {
    val compactHeading = compact || !isWide
    Text(
        text = details.title,
        style = when {
            !isWide -> MaterialTheme.typography.headlineSmall
            compact -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.displaySmall
        },
        fontWeight = FontWeight.Black,
    )
    DetailsHeroAlternateTitles(details = details, compact = compactHeading)
    DetailsHeroRatingAndStats(
        details = details,
        detailsExtras = detailsExtras,
        auth = auth,
        showHeroRating = showHeroRating,
        onSetAnimeRating = onSetAnimeRating,
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
        interactive = interactive,
        heroFocusGridState = heroFocusGridState,
        compact = compactHeading,
    )
}
// DetailsHeroInfo
@Composable
internal fun DetailsHeroSiteInfo(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    compact: Boolean,
    isWide: Boolean,
    heroFocusGridState: VisualFocusGridState?,
    modifier: Modifier = Modifier,
) {
    val details = model.details
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        DetailsHeroHeading(
            details = details,
            compact = compact,
            isWide = isWide,
            detailsExtras = model.detailsExtras,
            auth = model.auth,
            showHeroRating = model.showHeroRating,
            onSetAnimeRating = actions.onSetAnimeRating,
            onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
            interactive = model.interactive,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroActionPanel(
            model = model,
            actions = actions,
            externalPrimaryFocusRequester = heroFocusGridState?.requester(DetailsHeroFocusIndex.PrimaryAction),
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroProgressSummary(model.episodeSummary, model.downloadedSummary)
        DetailsHeroFactRows(
            details = details,
            apiEpisodeCount = model.apiEpisodeCount,
            narrow = !isWide,
            compact = compact,
            onGenreFilterSelected = { genre -> actions.onGenreFilterSelected(details.id, genre) },
            onYearFilterSelected = { year -> actions.onYearFilterSelected(details.id, year) },
            onStudioFilterSelected = { studio -> actions.onStudioFilterSelected(details.id, studio) },
            onCreatorFilterSelected = { creator -> actions.onCreatorFilterSelected(details.id, creator) },
            heroFocusGridState = heroFocusGridState,
        )
    }
}
// DetailsHeroLayoutGeometry
internal data class DetailsHeroLayoutGeometry(
    val expanded: Boolean,
    val compact: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val gap: Dp,
    val posterWidth: Dp,
    val markMaxWidth: Dp,
)

internal fun resolveDetailsHeroLayoutGeometry(
    maxWidth: Dp,
    windowHeight: Dp,
    responsiveWidth: Dp = maxWidth,
    responsiveHeight: Dp = windowHeight,
): DetailsHeroLayoutGeometry {
    val expanded = responsiveWidth > 700.dp
    val compact = responsiveWidth <= 500.dp || responsiveHeight <= 500.dp
    val horizontalPadding = if (expanded) 24.dp else 18.dp
    val verticalPadding = when {
        !expanded -> 14.dp
        compact -> 14.dp
        else -> 22.dp
    }
    val posterWidth = if (expanded) 264.dp.coerceAtMost(maxWidth * 0.34f) else maxWidth
    return DetailsHeroLayoutGeometry(
        expanded = expanded,
        compact = compact,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        gap = 10.dp,
        posterWidth = posterWidth,
        markMaxWidth = if (expanded) posterWidth else maxWidth,
    )
}
// DetailsHeroLinkedFacts
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
    val genres = details.genreTags.take(detailsHeroFactLimits(compact).genres)
    DetailsHeroOptionRow(
        label = uiText(UiStringKey.Genres),
        narrow = narrow,
        compact = compact,
        options = genres,
        onSelected = onSelected,
        localFocusKey = details.id to "genres" to genres.map { it.value },
        focusGridState = heroFocusGridState,
        focusIndexOffset = DetailsHeroFocusIndex.FactGenreStart,
        focusBlockKey = DetailsFocusBlockKey.HeroFacts,
    )
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
    localFocusKey: Any? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (options.isEmpty()) return
    val localFocusGridState = rememberVisualFocusGridState(
        size = options.size,
        key = localFocusKey ?: (label to options.map { it.value }),
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
// DetailsHeroMediaCard
@Composable
internal fun DetailsHeroMediaCard(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    markMaxWidth: Dp,
    modifier: Modifier,
) {
    var posterViewerOpen by remember(model.details.posterUrl) { mutableStateOf(false) }
    LaunchedEffect(model.interactive) {
        if (!model.interactive) posterViewerOpen = false
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DetailsPoster(
            posterUrl = model.details.posterUrl,
            title = model.details.title,
            onClick = model.details.posterUrl
                .takeIf { it.isNotBlank() }
                ?.let { { posterViewerOpen = true } },
            modifier = Modifier
                .fillMaxWidth()
                .visualFocusGridItem(
                    state = focusGridState,
                    index = DetailsHeroFocusIndex.Poster,
                    horizontal = true,
                    vertical = true,
                    blockKey = DetailsFocusBlockKey.HeroPoster,
                    blockEntryIndex = DetailsHeroFocusIndex.Poster,
                ),
        )
        if (model.showMarkPanel) {
            AnimeMarkPanelModern(
                auth = model.auth,
                animeMark = model.animeMark,
                onOpenLogin = actions.onOpenLogin,
                onSelectListMark = actions.onSelectListMark,
                onToggleFavorite = actions.onToggleFavorite,
                onRetry = actions.onRetry,
                focusGridState = focusGridState,
                focusIndexOffset = DetailsHeroFocusIndex.MarkStart,
                focusBlockKey = DetailsFocusBlockKey.HeroMarks,
                maxWidth = markMaxWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (model.interactive && posterViewerOpen) {
        ScreenshotViewerDialog(
            screenshots = listOf(model.details.posterUrl),
            initialIndex = 0,
            onDismiss = { posterViewerOpen = false },
            onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        )
    }
}
