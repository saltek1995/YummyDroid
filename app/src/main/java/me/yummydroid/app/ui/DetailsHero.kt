package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// DetailsHeroActionButtons
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActionButtons(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    dialogState: DetailsHeroActionDialogState,
    focus: DetailsHeroActionFocus,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        policy.primaryVideo?.let {
            DetailsHeroPrimaryAction(policy, actions, focus)
        }
        if (policy.showDownload) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = focus.actionModifier(DetailsHeroFocusIndex.DownloadAction),
                onClick = { dialogState.downloadOpen = true },
            )
        }
        if (policy.showReset) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = focus.resetModifier(policy.primaryVideo),
                onClick = { dialogState.resetOpen = true },
            )
        }
    }
}

@Composable
private fun DetailsHeroPrimaryAction(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    focus: DetailsHeroActionFocus,
) {
    val primaryVideo = policy.primaryVideo ?: return
    val resumeTarget = policy.resumeTarget
    DialogActionButton(
        text = if (resumeTarget != null) uiText(UiStringKey.Continue) else uiText(UiStringKey.Watch5af041),
        primary = true,
        modifier = focus.primaryModifier(),
        onClick = if (resumeTarget != null) {
            { actions.onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) }
        } else {
            { actions.onPlayVideo(primaryVideo) }
        },
    )
}

// DetailsHeroActionDialogs
@Composable
internal fun DetailsHeroActionDialogs(
    model: DetailsHeroModel,
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    state: DetailsHeroActionDialogState,
) {
    val selectedDownloadVideo = policy.selectedDownloadVideo
    if (state.downloadOpen && selectedDownloadVideo != null) {
        DownloadPlanDialog(
            animeId = model.details.id,
            animeTitle = model.details.title,
            videos = model.downloadVideos,
            selectedVideo = selectedDownloadVideo,
            selected = model.defaultDownloadQuality,
            onResolveSampledQualities = actions.onResolveSampledDownloadQualities,
            onConfirm = { plan ->
                state.downloadOpen = false
                actions.onDownloadAllVideos(plan)
            },
            onDismiss = { state.downloadOpen = false },
        )
    }
    if (state.resetOpen) {
        ResetWatchProgressDialog(
            onConfirm = {
                state.resetOpen = false
                actions.onResetWatchProgress()
            },
            onDismiss = { state.resetOpen = false },
        )
    }
}

@Composable
private fun ResetWatchProgressDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ResetWatchProgress)) },
        text = { Text(uiText(UiStringKey.DeleteWatchProgressForAllEpisodesOfThisAnime)) },
        confirmButton = {
            DialogActionButton(
                text = uiText(UiStringKey.Reset),
                primary = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = onDismiss)
        },
    )
}

// DetailsHeroActionDialogState
internal class DetailsHeroActionDialogState {
    var downloadOpen by mutableStateOf(false)
    var resetOpen by mutableStateOf(false)

    fun handleInput(action: InputAction): Boolean {
        if (action != InputAction.Back) return false
        return when {
            downloadOpen -> {
                downloadOpen = false
                true
            }
            resetOpen -> {
                resetOpen = false
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun rememberDetailsHeroActionDialogState(
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): DetailsHeroActionDialogState {
    val state = remember { DetailsHeroActionDialogState() }
    val inputActionHandler by rememberUpdatedState { action: InputAction -> state.handleInput(action) }
    DisposableEffect(state.downloadOpen, state.resetOpen, onRegisterModalInputActionHandler) {
        if (state.downloadOpen || state.resetOpen) {
            onRegisterModalInputActionHandler { action -> inputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return state
}

// DetailsHeroActionFocus
internal class DetailsHeroActionFocus(
    private val primaryRequester: FocusRequester,
    private val gridState: VisualFocusGridState?,
) {
    fun primaryModifier(): Modifier {
        return if (gridState == null) {
            Modifier.focusRequester(primaryRequester)
        } else {
            actionModifier(DetailsHeroFocusIndex.PrimaryAction)
        }
    }

    fun actionModifier(index: Int): Modifier {
        val state = gridState ?: return Modifier
        return Modifier.visualFocusGridItem(
            state = state,
            index = index,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroActions,
            blockEntryIndex = index,
        )
    }

    fun resetModifier(watchVideo: VideoVariant?): Modifier = when {
        gridState != null -> actionModifier(DetailsHeroFocusIndex.ResetAction)
        watchVideo == null -> Modifier.focusRequester(primaryRequester)
        else -> Modifier
    }
}

@Composable
internal fun rememberDetailsHeroActionFocus(
    policy: DetailsHeroActionPolicy,
    externalPrimaryFocusRequester: FocusRequester?,
    focusRequestNonce: Long,
    heroFocusGridState: VisualFocusGridState?,
): DetailsHeroActionFocus {
    val primaryVideoId = policy.primaryVideo?.id ?: -1L
    val resumeVideoId = policy.resumeTarget?.video?.id ?: -1L
    val internalRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(policy.primaryFocusIndex)
        ?: internalRequester
    val inputModeManager = LocalInputModeManager.current

    LaunchedEffect(focusRequestNonce, primaryVideoId, resumeVideoId, policy.showReset) {
        if (focusRequestNonce <= 0L || inputModeManager.inputMode == InputMode.Touch) {
            return@LaunchedEffect
        }
        repeat(4) {
            withFrameNanos { }
            if (primaryRequester.requestFocusSafely()) return@LaunchedEffect
        }
    }
    return DetailsHeroActionFocus(primaryRequester, heroFocusGridState)
}

// DetailsHeroActions
@Composable
internal fun DetailsHeroActionPanel(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    externalPrimaryFocusRequester: FocusRequester? = null,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    val policy = resolveDetailsHeroActionPolicy(
        watchVideo = model.watchVideo,
        resumeTarget = model.resumeTarget,
        canDownload = model.canDownload,
        hasDownloadVideos = model.downloadVideos.isNotEmpty(),
        hasWatchProgress = model.hasWatchProgress,
    )
    if (!policy.showPanel) return
    val dialogState = rememberDetailsHeroActionDialogState(actions.onRegisterModalInputActionHandler)
    val focus = rememberDetailsHeroActionFocus(
        policy = policy,
        externalPrimaryFocusRequester = externalPrimaryFocusRequester,
        focusRequestNonce = model.activeFocusRequestNonce,
        heroFocusGridState = heroFocusGridState,
    )
    DetailsHeroActionButtons(policy, actions, dialogState, focus)
    DetailsHeroActionDialogs(model, policy, actions, dialogState)
}

internal data class DetailsHeroActionPolicy(
    val primaryVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val selectedDownloadVideo: VideoVariant?,
    val showDownload: Boolean,
    val showReset: Boolean,
) {
    val showPanel: Boolean
        get() = primaryVideo != null || showReset

    val primaryFocusIndex: Int
        get() = if (primaryVideo != null) {
            DetailsHeroFocusIndex.PrimaryAction
        } else {
            DetailsHeroFocusIndex.ResetAction
        }
}

internal fun resolveDetailsHeroActionPolicy(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    canDownload: Boolean,
    hasDownloadVideos: Boolean,
    hasWatchProgress: Boolean,
): DetailsHeroActionPolicy = DetailsHeroActionPolicy(
    primaryVideo = watchVideo,
    resumeTarget = resumeTarget,
    selectedDownloadVideo = resumeTarget?.video ?: watchVideo,
    showDownload = watchVideo != null && canDownload && hasDownloadVideos,
    showReset = hasWatchProgress,
)

// DetailsHeroActionsModel
internal data class DetailsHeroActions(
    val onOpenLogin: () -> Unit,
    val onGenreFilterSelected: (Long, FilterOption) -> Unit,
    val onYearFilterSelected: (Long, Int) -> Unit,
    val onStudioFilterSelected: (Long, FilterOption) -> Unit,
    val onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    val onSelectListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onResetWatchProgress: () -> Unit,
)

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

// DetailsHeroFocus
internal const val DETAILS_HERO_FOCUS_GRAPH_SIZE = 80

internal object DetailsHeroFocusIndex {
    const val PrimaryAction = 0
    const val DownloadAction = 1
    const val ResetAction = 2
    const val RatingBadge = 3
    const val Poster = 4
    const val MarkStart = 24
    const val FactGenreStart = 32
    const val FactYear = 40
    const val FactStudioStart = 41
    const val FactCreatorStart = 47
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
                focusGridState = focusGridState,
                focusIndexOffset = DetailsHeroFocusIndex.MarkStart,
                focusBlockKey = DetailsFocusBlockKey.HeroMarks,
                maxWidth = markMaxWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (posterViewerOpen) {
        ScreenshotViewerDialog(
            screenshots = listOf(model.details.posterUrl),
            initialIndex = 0,
            onDismiss = { posterViewerOpen = false },
            onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        )
    }
}

// DetailsHeroModel
internal data class DetailsHeroModel(
    val details: AnimeDetails,
    val activeFocusRequestNonce: Long,
    val isWide: Boolean,
    val watchVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val downloadVideos: List<VideoVariant>,
    val downloadedSummary: String?,
    val episodeSummary: String,
    val apiEpisodeCount: Int,
    val auth: AuthUiState,
    val animeMark: LoadState<UserAnimeMark?>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val showMarkPanel: Boolean,
    val showHeroRating: Boolean,
    val defaultDownloadQuality: PreferredQuality,
    val canDownload: Boolean,
    val hasWatchProgress: Boolean,
)

// DetailsHeroProgressSummary
@Composable
internal fun DetailsHeroProgressSummary(
    episodeSummary: String,
    downloadedSummary: String?,
) {
    if (episodeSummary.isBlank() && downloadedSummary == null) return
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

// DetailsHeroRatingBadge
@Composable
internal fun HeroRatingBadge(
    rating: Double,
    enabled: Boolean,
    onClick: () -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    val shape = RoundedCornerShape(8.dp)
    val ratingColor = ratingColorForSiteScale(rating)
    val textStyle = MaterialTheme.typography.headlineSmall.withoutFontPadding()
    val focusModifier = if (enabled && heroFocusGridState != null) {
        Modifier.visualFocusGridItem(
            state = heroFocusGridState,
            index = DetailsHeroFocusIndex.RatingBadge,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroStats,
            blockEntryIndex = DetailsHeroFocusIndex.RatingBadge,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier
            .then(focusModifier)
            .then(if (enabled) Modifier.dpadClickable(shape, onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        contentColor = ratingColor,
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(25.dp))
            Text(
                text = formatRating(rating),
                style = textStyle,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

internal fun ratingColorForSiteScale(rating: Double): Color = when {
    rating < 5.0 -> Color(0xFFFF6666)
    rating < 7.0 -> Color(0xFFF2B800)
    else -> Color(0xFF3CCE7B)
}

// DetailsHeroRatingDialog
@Composable
internal fun DetailsHeroRatingDialogInputEffect(
    open: Boolean,
    onDismiss: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val ratingDialogInputHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && open) {
            onDismiss()
            true
        } else {
            false
        }
    }
    DisposableEffect(open, onRegisterModalInputActionHandler) {
        if (open) {
            onRegisterModalInputActionHandler { action -> ratingDialogInputHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
}

@Composable
internal fun DetailsHeroRatingDialog(
    open: Boolean,
    detailsId: Long,
    ratingSummary: AnimeRatingSummary?,
    onDismiss: () -> Unit,
    onSelected: (Int?) -> Unit,
) {
    if (!open || ratingSummary == null) return
    val inputModeManager = LocalInputModeManager.current
    val dialogFocusGridState = rememberVisualFocusGridState(
        size = 10,
        key = detailsId to ratingSummary.userRating,
    )
    LaunchedEffect(dialogFocusGridState, inputModeManager.inputMode) {
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        withFrameNanos { }
        val focusIndex = ((ratingSummary.userRating ?: 1).coerceIn(1, 10) - 1)
        dialogFocusGridState.requester(focusIndex)?.requestFocusSafely()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .yummyDialogMotion(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Text(
                    text = uiText(UiStringKey.RateAnime),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                RatingScale(
                    selected = ratingSummary.userRating,
                    onSelected = onSelected,
                    focusGridState = dialogFocusGridState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// DetailsHeroRatings
@Composable
internal fun DetailsHeroAlternateTitles(
    details: AnimeDetails,
    compact: Boolean,
) {
    val alternateTitle = details.otherTitles
        .filter { it.isPresentFactValue() && !it.equals(details.title, ignoreCase = true) }
        .take(if (compact) 2 else 3)
        .joinToString(" | ")
        .ifBlank { details.meta }
    if (alternateTitle.isBlank()) return
    Text(
        text = alternateTitle,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DetailsHeroRatingAndStats(
    details: AnimeDetails,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showHeroRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    compact: Boolean,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    var ratingDialogOpen by remember(details.id) { mutableStateOf(false) }
    val ratingSummary = (detailsExtras as? LoadState.Ready)?.data?.rating
    val canRate = showHeroRating && auth.profile != null && ratingSummary != null
    DetailsHeroRatingDialogInputEffect(
        open = ratingDialogOpen,
        onDismiss = { ratingDialogOpen = false },
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
    )
    DetailsHeroMetrics(
        details = details,
        canRate = canRate,
        compact = compact,
        heroFocusGridState = heroFocusGridState,
        onOpenRatingDialog = { ratingDialogOpen = true },
    )
    DetailsHeroRatingDialog(
        open = ratingDialogOpen,
        detailsId = details.id,
        ratingSummary = ratingSummary,
        onDismiss = { ratingDialogOpen = false },
        onSelected = { rating ->
            ratingDialogOpen = false
            onSetAnimeRating(rating)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroMetrics(
    details: AnimeDetails,
    canRate: Boolean,
    compact: Boolean,
    heroFocusGridState: VisualFocusGridState?,
    onOpenRatingDialog: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        details.rating?.let { rating ->
            HeroRatingBadge(
                rating = rating,
                enabled = canRate,
                heroFocusGridState = heroFocusGridState,
                onClick = onOpenRatingDialog,
            )
        }
        HeroMetricItem(
            icon = Icons.Default.Visibility,
            text = localizedViews(details.views),
        )
        details.listsCount.takeIf { it > 0L }?.let { count ->
            HeroMetricItem(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                text = localizedViews(count),
            )
        }
        if (details.ratingDetails.hasExternalRatings()) {
            HeroMetricSeparator()
        }
        HeroExternalRatingBadges(ratingDetails = details.ratingDetails)
    }
}

@Composable
private fun HeroMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val textStyle = MaterialTheme.typography.titleMedium.withoutFontPadding()
    Row(
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroMetricSeparator() {
    val textStyle = MaterialTheme.typography.titleLarge.withoutFontPadding()
    Box(
        modifier = Modifier.height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "|",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
        )
    }
}

internal fun androidx.compose.ui.text.TextStyle.withoutFontPadding(): androidx.compose.ui.text.TextStyle =
    copy(platformStyle = PlatformTextStyle(includeFontPadding = false))

// DetailsHeroRuntime
@Composable
internal fun DetailsHeroModern(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    modifier: Modifier = Modifier,
    focusGridState: VisualFocusGridState? = null,
) {
    val localHeroFocusGridState = rememberVisualFocusGridState(
        size = DETAILS_HERO_FOCUS_GRAPH_SIZE,
        key = model.details.id,
        allowLoosePerpendicularMatch = true,
    )
    Box(modifier = modifier) {
        model.details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(heroBackdropScrim()),
            )
        }
        DetailsHeroSiteLayout(
            model = model,
            actions = actions,
            heroFocusGridState = focusGridState ?: localHeroFocusGridState,
            modifier = Modifier
                .align(if (model.isWide) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .then(if (!model.isWide) Modifier.statusBarsPadding() else Modifier),
        )
    }
}

@Composable
private fun heroBackdropScrim(): Brush {
    val background = MaterialTheme.colorScheme.background
    return remember(background) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.18f),
                background.copy(alpha = 0.48f),
                background.copy(alpha = 0.86f),
            ),
        )
    }
}

// DetailsHeroSiteLayout
@Composable
internal fun DetailsHeroSiteLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    heroFocusGridState: VisualFocusGridState,
    modifier: Modifier = Modifier,
) {
    val windowSize = currentWindowSizeDp()
    val responsiveWindowSize = currentResponsiveWindowSizeDp()
    BoxWithConstraints(modifier = modifier) {
        val geometry = resolveDetailsHeroLayoutGeometry(
            maxWidth = maxWidth,
            windowHeight = windowSize.height,
            responsiveWidth = responsiveWindowSize.width,
            responsiveHeight = responsiveWindowSize.height,
        )
        if (geometry.expanded) {
            DetailsHeroWideLayout(model, actions, heroFocusGridState, geometry)
        } else {
            DetailsHeroCompactLayout(model, actions, heroFocusGridState, geometry)
        }
    }
}

@Composable
private fun DetailsHeroWideLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    geometry: DetailsHeroLayoutGeometry,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = geometry.horizontalPadding,
                vertical = geometry.verticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(geometry.gap),
        verticalAlignment = Alignment.Top,
    ) {
        DetailsHeroInfoBlock(
            model = model,
            actions = actions,
            compact = geometry.compact,
            isWide = true,
            focusGridState = focusGridState,
            modifier = Modifier.weight(1f),
        )
        DetailsHeroMediaCard(
            model = model,
            actions = actions,
            focusGridState = focusGridState,
            markMaxWidth = geometry.markMaxWidth,
            modifier = Modifier.width(geometry.posterWidth),
        )
    }
}

@Composable
private fun DetailsHeroCompactLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    geometry: DetailsHeroLayoutGeometry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = geometry.verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(geometry.gap),
    ) {
        DetailsHeroMediaCard(
            model = model,
            actions = actions,
            focusGridState = focusGridState,
            markMaxWidth = geometry.markMaxWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        DetailsHeroInfoBlock(
            model = model,
            actions = actions,
            compact = geometry.compact,
            isWide = false,
            focusGridState = focusGridState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.horizontalPadding),
        )
    }
}

@Composable
private fun DetailsHeroInfoBlock(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    compact: Boolean,
    isWide: Boolean,
    focusGridState: VisualFocusGridState,
    modifier: Modifier,
) {
    DetailsHeroSiteInfo(
        model = model,
        actions = actions,
        compact = compact,
        isWide = isWide,
        heroFocusGridState = focusGridState,
        modifier = modifier,
    )
}
