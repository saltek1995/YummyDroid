package me.yummydroid.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatRating
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

private const val DETAILS_HERO_FOCUS_GRAPH_SIZE = 80

private object DetailsHeroFocusIndex {
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

@Composable
internal fun DetailsHeroModern(
    details: AnimeDetails,
    activeFocusRequestNonce: Long,
    isWide: Boolean,
    useThreeColumnHero: Boolean,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    apiEpisodeCount: Int,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    modifier: Modifier = Modifier,
    detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    showMarkPanel: Boolean,
    showHeroRating: Boolean = false,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit = {},
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    hasWatchProgress: Boolean,
    onResetWatchProgress: () -> Unit,
    focusGridState: VisualFocusGridState? = null,
) {
    val localHeroFocusGridState = rememberVisualFocusGridState(
        size = DETAILS_HERO_FOCUS_GRAPH_SIZE,
        key = details.id,
        allowLoosePerpendicularMatch = true,
    )
    val wideHeroFocusGridState = focusGridState ?: localHeroFocusGridState

    Box(
        modifier = modifier,
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(heroBackdropScrim()),
            )
        }

        DetailsHeroSiteLayout(
            details = details,
            activeFocusRequestNonce = activeFocusRequestNonce,
            watchVideo = watchVideo,
            resumeTarget = resumeTarget,
            downloadedSummary = downloadedSummary,
            episodeSummary = episodeSummary,
            apiEpisodeCount = apiEpisodeCount,
            downloadVideos = downloadVideos,
            auth = auth,
            animeMark = animeMark,
            detailsExtras = detailsExtras,
            showMarkPanel = showMarkPanel,
            showHeroRating = showHeroRating,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onGenreFilterSelected = onGenreFilterSelected,
            onYearFilterSelected = onYearFilterSelected,
            onStudioFilterSelected = onStudioFilterSelected,
            onCreatorFilterSelected = onCreatorFilterSelected,
            onSelectListMark = onSelectListMark,
            onToggleFavorite = onToggleFavorite,
            onSetAnimeRating = onSetAnimeRating,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = defaultDownloadQuality,
            onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
            onDownloadAllVideos = onDownloadAllVideos,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            canDownload = canDownload,
            hasWatchProgress = hasWatchProgress,
            onResetWatchProgress = onResetWatchProgress,
            heroFocusGridState = wideHeroFocusGridState,
            modifier = Modifier
                .align(if (isWide) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .then(if (!isWide) Modifier.statusBarsPadding() else Modifier),
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
private fun DetailsHeroSiteLayout(
    details: AnimeDetails,
    activeFocusRequestNonce: Long,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    apiEpisodeCount: Int,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    showMarkPanel: Boolean,
    showHeroRating: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
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
    heroFocusGridState: VisualFocusGridState,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    BoxWithConstraints(modifier = modifier) {
        val expanded = maxWidth > 700.dp
        val compact = maxWidth <= 500.dp || configuration.screenHeightDp <= 500
        val horizontalPadding = if (expanded) 24.dp else 18.dp
        val verticalPadding = if (expanded) {
            if (compact) 14.dp else 22.dp
        } else {
            14.dp
        }
        val gap = if (expanded) 10.dp else 10.dp
        val posterWidth = if (expanded) {
            264.dp.coerceAtMost(maxWidth * 0.34f)
        } else {
            maxWidth
        }
        val markMaxWidth = if (expanded) posterWidth else maxWidth
        val mediaModifier = if (expanded) Modifier.width(posterWidth) else Modifier.fillMaxWidth()
        val posterModifier = Modifier.fillMaxWidth()

        val mediaCard: @Composable () -> Unit = {
            DetailsHeroMediaCard(
                details = details,
                auth = auth,
                animeMark = animeMark,
                showMarkPanel = showMarkPanel,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onSelectListMark = onSelectListMark,
                onToggleFavorite = onToggleFavorite,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                heroFocusGridState = heroFocusGridState,
                posterModifier = posterModifier,
                markMaxWidth = markMaxWidth,
                modifier = mediaModifier,
            )
        }
        val infoBlock: @Composable (Modifier) -> Unit = { infoModifier ->
            DetailsHeroSiteInfo(
                details = details,
                compact = compact,
                isWide = expanded,
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadVideos = downloadVideos,
                downloadedSummary = downloadedSummary,
                episodeSummary = episodeSummary,
                apiEpisodeCount = apiEpisodeCount,
                auth = auth,
                detailsExtras = detailsExtras,
                showHeroRating = showHeroRating,
                onGenreFilterSelected = onGenreFilterSelected,
                onYearFilterSelected = onYearFilterSelected,
                onStudioFilterSelected = onStudioFilterSelected,
                onCreatorFilterSelected = onCreatorFilterSelected,
                onSetAnimeRating = onSetAnimeRating,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = defaultDownloadQuality,
                onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                canDownload = canDownload,
                hasWatchProgress = hasWatchProgress,
                onResetWatchProgress = onResetWatchProgress,
                actionsFocusRequestNonce = activeFocusRequestNonce,
                heroFocusGridState = heroFocusGridState,
                modifier = infoModifier,
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                infoBlock(Modifier.weight(1f))
                mediaCard()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = verticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                mediaCard()
                infoBlock(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                )
            }
        }
    }
}

@Composable
private fun DetailsHeroMediaCard(
    details: AnimeDetails,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    showMarkPanel: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    modifier: Modifier = Modifier,
    heroFocusGridState: VisualFocusGridState? = null,
    posterModifier: Modifier = Modifier.fillMaxWidth(),
    markMaxWidth: Dp = 392.dp,
) {
    var posterViewerOpen by remember(details.posterUrl) { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DetailsPoster(
            posterUrl = details.posterUrl,
            title = details.title,
            onClick = details.posterUrl
                .takeIf { it.isNotBlank() }
                ?.let { { posterViewerOpen = true } },
            modifier = posterModifier.then(
                if (heroFocusGridState != null) {
                    Modifier.visualFocusGridItem(
                        state = heroFocusGridState,
                        index = DetailsHeroFocusIndex.Poster,
                        horizontal = true,
                        vertical = true,
                        blockKey = DetailsFocusBlockKey.HeroPoster,
                        blockEntryIndex = DetailsHeroFocusIndex.Poster,
                    )
                } else {
                    Modifier
                },
            ),
        )
        if (showMarkPanel) {
            AnimeMarkPanelModern(
                auth = auth,
                animeMark = animeMark,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onSelectListMark = onSelectListMark,
                onToggleFavorite = onToggleFavorite,
                focusGridState = heroFocusGridState,
                focusIndexOffset = DetailsHeroFocusIndex.MarkStart,
                focusBlockKey = DetailsFocusBlockKey.HeroMarks,
                maxWidth = markMaxWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (posterViewerOpen) {
        ScreenshotViewerDialog(
            screenshots = listOf(details.posterUrl),
            initialIndex = 0,
            onDismiss = { posterViewerOpen = false },
            onRegisterInputActionHandler = onRegisterModalInputActionHandler,
        )
    }
}

@Composable
private fun DetailsHeroSiteInfo(
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

@Composable
private fun DetailsHeroAlternateTitles(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroRatingAndStats(
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
    val ratingDialogInputHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && ratingDialogOpen) {
            ratingDialogOpen = false
            true
        } else {
            false
        }
    }

    DisposableEffect(ratingDialogOpen, onRegisterModalInputActionHandler) {
        if (ratingDialogOpen) {
            onRegisterModalInputActionHandler { action -> ratingDialogInputHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

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
                onClick = { ratingDialogOpen = true },
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

    if (ratingDialogOpen && ratingSummary != null) {
        val inputModeManager = LocalInputModeManager.current
        val dialogFocusGridState = rememberVisualFocusGridState(
            size = 10,
            key = details.id to ratingSummary.userRating,
        )
        LaunchedEffect(dialogFocusGridState, inputModeManager.inputMode) {
            if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
            withFrameNanos { }
            val focusIndex = ((ratingSummary.userRating ?: 1).coerceIn(1, 10) - 1)
            runCatching { dialogFocusGridState.requester(focusIndex)?.requestFocus() }
        }
        Dialog(
            onDismissRequest = { ratingDialogOpen = false },
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
                        onSelected = { rating ->
                            ratingDialogOpen = false
                            onSetAnimeRating(rating)
                        },
                        focusGridState = dialogFocusGridState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRatingBadge(
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

@Composable
private fun HeroExternalRatingBadges(ratingDetails: RatingDetails) {
    val entries = buildList {
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
    entries.forEach { entry ->
        HeroExternalRatingItem(entry = entry)
    }
}

private data class ExternalRatingDisplay(
    val iconResId: Int,
    val iconSize: Dp,
    val title: String,
    val value: String,
)

@Composable
private fun HeroMetricItem(
    icon: ImageVector,
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

@Composable
private fun HeroExternalRatingItem(
    entry: ExternalRatingDisplay,
) {
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

private fun androidx.compose.ui.text.TextStyle.withoutFontPadding(): androidx.compose.ui.text.TextStyle =
    copy(platformStyle = PlatformTextStyle(includeFontPadding = false))

private fun RatingDetails.hasExternalRatings(): Boolean =
    kinopoisk != null || shikimori != null || myAnimeList != null || worldArt != null || aniDub != null

private fun ratingColorForSiteScale(rating: Double): Color = when {
    rating < 5.0 -> Color(0xFFFF6666)
    rating < 7.0 -> Color(0xFFF2B800)
    else -> Color(0xFF3CCE7B)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimeMarkPanelModern(
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 392.dp,
    modifier: Modifier = Modifier,
) {
    val mark = animeMark.readyDataOrNull() ?: UserAnimeMark()
    val selectListMark: (UserAnimeListMark) -> Unit = if (auth.profile == null) {
        { if (!auth.loading) onOpenLogin() }
    } else {
        onSelectListMark
    }
    val toggleFavorite: () -> Unit = if (auth.profile == null) {
        { if (!auth.loading) onOpenLogin() }
    } else {
        onToggleFavorite
    }

    AnimeMarkSegmentedControl(
        mark = mark,
        onSelectListMark = selectListMark,
        onToggleFavorite = toggleFavorite,
        focusGridState = focusGridState,
        focusIndexOffset = focusIndexOffset,
        focusBlockKey = focusBlockKey,
        maxWidth = maxWidth,
        modifier = modifier,
    )
}

@Composable
internal fun AnimeMarkSegmentedControl(
    mark: UserAnimeMark,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
    maxWidth: androidx.compose.ui.unit.Dp = 392.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val listMarks = UserAnimeListMark.displayOrder
    val totalMarks = listMarks.size + 1
    val internalFocusGridState = rememberVisualFocusGridState(size = totalMarks)
    val effectiveFocusGridState = focusGridState ?: internalFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        color = yummyActionSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = yummyActionBorder(),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listMarks.forEachIndexed { index, listMark ->
                AnimeMarkSegment(
                    icon = listMark.icon(),
                    title = listMark.localizedTitle(),
                    color = listMark.siteColor(),
                    selected = mark.list == listMark,
                    onClick = { onSelectListMark(listMark) },
                    index = index,
                    total = totalMarks,
                    focusIndex = effectiveFocusIndexOffset + index,
                    focusGridState = effectiveFocusGridState,
                    focusBlockKey = focusBlockKey,
                    focusBlockEntryIndex = effectiveFocusIndexOffset,
                    modifier = Modifier.weight(1f),
                )
                MarkDivider()
            }
            AnimeMarkSegment(
                icon = Icons.Default.Favorite,
                title = uiText(UiStringKey.Favorites),
                color = favoriteMarkColor,
                selected = mark.isFavorite,
                onClick = onToggleFavorite,
                index = totalMarks - 1,
                total = totalMarks,
                focusIndex = effectiveFocusIndexOffset + totalMarks - 1,
                focusGridState = effectiveFocusGridState,
                focusBlockKey = focusBlockKey,
                focusBlockEntryIndex = effectiveFocusIndexOffset,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun AnimeMarkSegment(
    icon: ImageVector,
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = -1,
    total: Int = 0,
    focusIndex: Int = index,
    focusGridState: VisualFocusGridState? = null,
    focusBlockKey: Any? = null,
    focusBlockEntryIndex: Int = focusIndex,
) {
    val shape = RoundedCornerShape(6.dp)
    val focusModifier = if (focusGridState != null && index in 0 until total && focusIndex >= 0) {
        Modifier.visualFocusGridItem(
            state = focusGridState,
            index = focusIndex,
            horizontal = true,
            vertical = focusBlockKey != null,
            blockKey = focusBlockKey,
            blockEntryIndex = focusBlockEntryIndex,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(focusModifier)
            .background(if (selected) color else Color.Transparent)
            .focusRing(shape)
            .dpadClickable(shape, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) Color.White else color,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
internal fun MarkDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    )
}

internal fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/users/id$id"
}

internal fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/${path.trim().trimStart('/')}"
}

internal fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()))
    }.onFailure {
        Toast.makeText(this, getString(R.string.ui_could_not_open_the_site), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActions(
    animeId: Long,
    animeTitle: String,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    hasWatchProgress: Boolean,
    onResetWatchProgress: () -> Unit,
    externalPrimaryFocusRequester: FocusRequester? = null,
    focusRequestNonce: Long = 0L,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    if (watchVideo == null && !hasWatchProgress) return
    var downloadDialogOpen by remember { mutableStateOf(false) }
    var resetDialogOpen by remember { mutableStateOf(false) }
    val dialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                downloadDialogOpen -> {
                    downloadDialogOpen = false
                    true
                }
                resetDialogOpen -> {
                    resetDialogOpen = false
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(downloadDialogOpen, resetDialogOpen, onRegisterModalInputActionHandler) {
        if (downloadDialogOpen || resetDialogOpen) {
            onRegisterModalInputActionHandler { action -> dialogInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    val primaryVideoId = watchVideo?.id ?: -1L
    val resumeVideoId = resumeTarget?.video?.id ?: -1L
    val internalPrimaryActionFocusRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryActionFocusIndex = if (watchVideo != null) {
        DetailsHeroFocusIndex.PrimaryAction
    } else {
        DetailsHeroFocusIndex.ResetAction
    }
    val primaryActionFocusRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(primaryActionFocusIndex)
        ?: internalPrimaryActionFocusRequester
    val inputModeManager = LocalInputModeManager.current

    fun Modifier.heroActionFocus(index: Int): Modifier {
        val state = heroFocusGridState ?: return this
        return then(
            Modifier.visualFocusGridItem(
                state = state,
                index = index,
                horizontal = true,
                vertical = true,
                blockKey = DetailsFocusBlockKey.HeroActions,
                blockEntryIndex = index,
            ),
        )
    }

    suspend fun requestPrimaryActionFocus() {
        repeat(4) {
            withFrameNanos { }
            if (runCatching { primaryActionFocusRequester.requestFocus() }.getOrDefault(false)) {
                return
            }
        }
    }

    LaunchedEffect(focusRequestNonce, primaryVideoId, resumeVideoId, hasWatchProgress) {
        if (focusRequestNonce <= 0L) return@LaunchedEffect
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        requestPrimaryActionFocus()
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (watchVideo != null) {
            if (resumeTarget != null) {
                DialogActionButton(
                    text = uiText(UiStringKey.Continue),
                    primary = true,
                    modifier = if (heroFocusGridState == null) {
                        Modifier.focusRequester(primaryActionFocusRequester)
                    } else {
                        Modifier.heroActionFocus(DetailsHeroFocusIndex.PrimaryAction)
                    },
                    onClick = { onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) },
                )
            } else {
                DialogActionButton(
                    text = uiText(UiStringKey.Watch5af041),
                    primary = true,
                    modifier = if (heroFocusGridState == null) {
                        Modifier.focusRequester(primaryActionFocusRequester)
                    } else {
                        Modifier.heroActionFocus(DetailsHeroFocusIndex.PrimaryAction)
                    },
                    onClick = { onPlayVideo(watchVideo) },
                )
            }
        }
        if (watchVideo != null && canDownload && downloadVideos.isNotEmpty()) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = Modifier.heroActionFocus(DetailsHeroFocusIndex.DownloadAction),
                onClick = { downloadDialogOpen = true },
            )
        }
        if (hasWatchProgress) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = when {
                    heroFocusGridState != null -> Modifier.heroActionFocus(DetailsHeroFocusIndex.ResetAction)
                    watchVideo == null -> Modifier.focusRequester(primaryActionFocusRequester)
                    else -> Modifier
                },
                onClick = { resetDialogOpen = true },
            )
        }
    }

    val selectedDownloadVideo = resumeTarget?.video ?: watchVideo
    if (downloadDialogOpen && selectedDownloadVideo != null) {
        DownloadPlanDialog(
            animeId = animeId,
            animeTitle = animeTitle,
            videos = downloadVideos,
            selectedVideo = selectedDownloadVideo,
            selected = defaultDownloadQuality,
            onResolveSampledQualities = onResolveSampledDownloadQualities,
            onConfirm = { plan ->
                downloadDialogOpen = false
                onDownloadAllVideos(plan)
            },
            onDismiss = { downloadDialogOpen = false },
        )
    }

    if (resetDialogOpen) {
        AlertDialog(
            modifier = Modifier.yummyDialogMotion(),
            onDismissRequest = { resetDialogOpen = false },
            title = { Text(uiText(UiStringKey.ResetWatchProgress)) },
            text = { Text(uiText(UiStringKey.DeleteWatchProgressForAllEpisodesOfThisAnime)) },
            confirmButton = {
                DialogActionButton(
                    text = uiText(UiStringKey.Reset),
                    primary = true,
                    onClick = {
                        resetDialogOpen = false
                        onResetWatchProgress()
                    },
                )
            },
            dismissButton = {
                DialogActionButton(
                    text = uiText(UiStringKey.Cancel),
                    onClick = { resetDialogOpen = false },
                )
            },
        )
    }
}

@Composable
private fun InfoBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactiveModifier = if (onClick != null) {
        Modifier
            .dpadClickable(shape, onClick)
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
        normalized != "—"
}
