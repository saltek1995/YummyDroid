package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant

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
    onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
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
    val model = DetailsHeroModel(
        details = details,
        activeFocusRequestNonce = activeFocusRequestNonce,
        watchVideo = watchVideo,
        resumeTarget = resumeTarget,
        downloadVideos = downloadVideos,
        downloadedSummary = downloadedSummary,
        episodeSummary = episodeSummary,
        apiEpisodeCount = apiEpisodeCount,
        auth = auth,
        animeMark = animeMark,
        detailsExtras = detailsExtras,
        showMarkPanel = showMarkPanel,
        showHeroRating = showHeroRating,
        defaultDownloadQuality = defaultDownloadQuality,
        canDownload = canDownload,
        hasWatchProgress = hasWatchProgress,
    )
    val actions = DetailsHeroActions(
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
        onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
        onDownloadAllVideos = onDownloadAllVideos,
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
        onResetWatchProgress = onResetWatchProgress,
    )
    Box(modifier = modifier) {
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
            model = model,
            actions = actions,
            heroFocusGridState = focusGridState ?: localHeroFocusGridState,
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
