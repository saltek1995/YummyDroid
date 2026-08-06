package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState

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

@Composable
internal fun DetailsHeroModern(
    details: AnimeDetails,
    activeFocusRequestNonce: Long,
    isWide: Boolean,
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
                modifier = Modifier.matchParentSize(),
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
        val gap = 10.dp
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
