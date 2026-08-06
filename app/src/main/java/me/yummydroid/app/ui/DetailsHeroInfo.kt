package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatDuration
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

private data class DetailsHeroFact(
    val label: UiStringKey,
    val value: String,
)

private val DetailsHeroLinkedFactLabels = setOf(
    UiStringKey.Year92264e,
    UiStringKey.Studio,
    UiStringKey.Director,
)

@Composable
internal fun DetailsHeroSiteInfo(
    details: AnimeDetails,
    compact: Boolean,
    isWide: Boolean,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    apiEpisodeCount: Int,
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    showHeroRating: Boolean,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    hasWatchProgress: Boolean,
    onResetWatchProgress: () -> Unit,
    actionsFocusRequestNonce: Long,
    modifier: Modifier = Modifier,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        Text(
            text = details.title,
            style = when {
                !isWide -> MaterialTheme.typography.headlineSmall
                compact -> MaterialTheme.typography.headlineMedium
                else -> MaterialTheme.typography.displaySmall
            },
            fontWeight = FontWeight.Black,
        )
        DetailsHeroAlternateTitles(details = details, compact = compact || !isWide)
        DetailsHeroRatingAndStats(
            details = details,
            detailsExtras = detailsExtras,
            auth = auth,
            showHeroRating = showHeroRating,
            onSetAnimeRating = onSetAnimeRating,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            heroFocusGridState = heroFocusGridState,
            compact = compact || !isWide,
        )
        DetailsHeroActions(
            animeId = details.id,
            animeTitle = details.title,
            watchVideo = watchVideo,
            resumeTarget = resumeTarget,
            downloadVideos = downloadVideos,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = defaultDownloadQuality,
            onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
            onDownloadAllVideos = onDownloadAllVideos,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            canDownload = canDownload,
            hasWatchProgress = hasWatchProgress,
            onResetWatchProgress = onResetWatchProgress,
            externalPrimaryFocusRequester = heroFocusGridState?.requester(DetailsHeroFocusIndex.PrimaryAction),
            focusRequestNonce = actionsFocusRequestNonce,
            heroFocusGridState = heroFocusGridState,
        )
        if (episodeSummary.isNotBlank() || downloadedSummary != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (episodeSummary.isNotBlank()) {
                    Text(
                        text = episodeSummary,
                        style = MaterialTheme.typography.labelLarge,
                        color = YummyColors.watched,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                downloadedSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DetailsHeroFactRows(
            details = details,
            apiEpisodeCount = apiEpisodeCount,
            narrow = !isWide,
            compact = compact,
            onGenreFilterSelected = { genre -> onGenreFilterSelected(details.id, genre) },
            onYearFilterSelected = { year -> onYearFilterSelected(details.id, year) },
            onStudioFilterSelected = { studio -> onStudioFilterSelected(details.id, studio) },
            onCreatorFilterSelected = { creator -> onCreatorFilterSelected(details.id, creator) },
            heroFocusGridState = heroFocusGridState,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroFactRows(
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
    val linkedFactValues = buildSet {
        details.year?.takeIf { it > 0 }?.let { year -> add(year.toString()) }
        details.studios.takeIf { it.isNotEmpty() }?.let { studios -> add(studios.joinToString { it.title }) }
        details.creators.takeIf { it.isNotEmpty() }?.let { creators -> add(creators.joinToString { it.title }) }
    }
    val hasLinkedFacts = details.year?.takeIf { it > 0 } != null ||
        details.studios.isNotEmpty() ||
        details.creators.isNotEmpty()
    val facts = buildList {
        add(DetailsHeroFact(UiStringKey.Type, details.type))
        add(DetailsHeroFact(UiStringKey.AgeRating, details.minAge))
        add(DetailsHeroFact(UiStringKey.Status, details.status))
        details.year?.let { year -> add(DetailsHeroFact(UiStringKey.Year92264e, year.toString())) }
        if (details.studios.isNotEmpty()) {
            add(DetailsHeroFact(UiStringKey.Studio, details.studios.joinToString { it.title }))
        }
        if (details.creators.isNotEmpty()) {
            add(DetailsHeroFact(UiStringKey.Director, details.creators.joinToString { it.title }))
        }
        apiEpisodeCount.takeIf { it > 0 }?.let { count ->
            add(DetailsHeroFact(UiStringKey.EpisodeCount, count.toString()))
        }
        details.durationSeconds.takeIf { it > 0 }?.let { seconds ->
            formatDuration(seconds)?.let { duration -> add(DetailsHeroFact(UiStringKey.Duration, duration)) }
        }
    }.filter { it.value.isPresentFactValue() && it.value !in linkedFactValues }

    if (details.genreTags.isEmpty() && facts.isEmpty() && !hasLinkedFacts) return
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        if (details.genreTags.isNotEmpty()) {
            val genres = details.genreTags.take(if (compact) 4 else 8)
            val localGenreFocusGridState = rememberVisualFocusGridState(
                size = genres.size,
                key = details.id to "genres" to genres.map { it.value },
            )
            val genreFocusGridState = heroFocusGridState ?: localGenreFocusGridState
            val genreFocusIndexOffset = if (heroFocusGridState != null) DetailsHeroFocusIndex.FactGenreStart else 0
            val focusBlockKey = if (heroFocusGridState != null) DetailsFocusBlockKey.HeroFacts else null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DetailsHeroFactLabel(text = uiText(UiStringKey.Genres), narrow = narrow, compact = compact)
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    genres.forEachIndexed { index, genre ->
                        InfoBadge(
                            text = genre.title,
                            onClick = { onGenreFilterSelected(genre) },
                            modifier = Modifier.visualFocusGridItem(
                                state = genreFocusGridState,
                                index = genreFocusIndexOffset + index,
                                horizontal = true,
                                vertical = true,
                                blockKey = focusBlockKey,
                                blockEntryIndex = genreFocusIndexOffset + index,
                            ),
                        )
                    }
                }
            }
        }
        details.year?.takeIf { it > 0 }?.let { year ->
            val localYearFocusGridState = rememberVisualFocusGridState(
                size = 1,
                key = details.id to "year" to year,
            )
            val yearFocusGridState = heroFocusGridState ?: localYearFocusGridState
            val yearFocusIndex = if (heroFocusGridState != null) DetailsHeroFocusIndex.FactYear else 0
            val focusBlockKey = if (heroFocusGridState != null) DetailsFocusBlockKey.HeroFacts else null
            DetailsHeroValueRow(
                label = uiText(UiStringKey.Year92264e),
                narrow = narrow,
                compact = compact,
            ) {
                InfoBadge(
                    text = year.toString(),
                    onClick = { onYearFilterSelected(year) },
                    modifier = Modifier.visualFocusGridItem(
                        state = yearFocusGridState,
                        index = yearFocusIndex,
                        horizontal = true,
                        vertical = true,
                        blockKey = focusBlockKey,
                        blockEntryIndex = yearFocusIndex,
                    ),
                )
            }
        }
        if (details.studios.isNotEmpty()) {
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
        }
        if (details.creators.isNotEmpty()) {
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
        }
        facts.take(if (compact) 5 else 8).forEach { fact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DetailsHeroFactLabel(text = uiText(fact.label), narrow = narrow, compact = compact)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroOptionRow(
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
    val effectiveFocusGridState = focusGridState ?: localFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState != null) focusIndexOffset else 0
    val effectiveFocusBlockKey = if (focusGridState != null) focusBlockKey else null
    DetailsHeroValueRow(
        label = label,
        narrow = narrow,
        compact = compact,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            options.forEachIndexed { index, option ->
                InfoBadge(
                    text = option.title,
                    onClick = { onSelected(option) },
                    modifier = Modifier.visualFocusGridItem(
                        state = effectiveFocusGridState,
                        index = effectiveFocusIndexOffset + index,
                        horizontal = true,
                        vertical = true,
                        blockKey = effectiveFocusBlockKey,
                        blockEntryIndex = effectiveFocusIndexOffset + index,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DetailsHeroValueRow(
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
        DetailsHeroFactLabel(text = label, narrow = narrow, compact = compact)
        content()
    }
}

@Composable
private fun DetailsHeroFactLabel(
    text: String,
    narrow: Boolean,
    compact: Boolean,
) {
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
private fun InfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactiveModifier = if (onClick != null) {
        Modifier.dpadClickable(shape, onClick)
    } else {
        Modifier
    }
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

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized != "-" &&
        normalized != "\u2014" &&
        normalized != "\u0432\u0402\u201d"
}
