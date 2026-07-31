package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.theme.yummyAppBackground
import me.yummydroid.app.YummyDroidUiState

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val DetailsBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val targetEnd = offset + size
        val edgeGuard = (containerSize * 0.06f).coerceAtMost(56f)
        val visibleStart = edgeGuard
        val visibleEnd = containerSize - edgeGuard
        return when {
            offset < visibleStart -> offset - visibleStart
            targetEnd > visibleEnd -> targetEnd - visibleEnd
            else -> 0f
        }
    }
}

private const val DETAILS_SCREEN_FOCUS_GRAPH_SIZE = 512
private const val DETAILS_SCREEN_SCREENSHOTS_FOCUS_INDEX = 80
private const val DETAILS_SCREEN_RELATED_FOCUS_INDEX = 120
private const val DETAILS_SCREEN_EPISODES_FOCUS_INDEX = 200
private const val DETAILS_SCREEN_SUBSCRIPTIONS_FOCUS_INDEX = 240
private const val DETAILS_SCREEN_RECOMMENDATIONS_FOCUS_INDEX = 260
private const val DETAILS_SCREEN_COMMENTS_FOCUS_INDEX = 340

internal object DetailsFocusBlockKey {
    const val HeroPoster = "details:hero-poster"
    const val HeroActions = "details:hero-actions"
    const val HeroStats = "details:hero-stats"
    const val HeroFacts = "details:hero-facts"
    const val HeroMarks = "details:hero-marks"
    const val Screenshots = "details:screenshots"
    const val RelatedAnime = "details:related-anime"
    const val Episodes = "details:episodes"
    const val Subscriptions = "details:subscriptions"
    const val Recommendations = "details:recommendations"
    const val Comments = "details:comments"
}

internal class DetailsScreenUiState {
    val scrollState = ScrollState(0)
    var relatedExpanded by mutableStateOf(false)
    var subscriptionsExpanded by mutableStateOf(false)
    var commentsExpanded by mutableStateOf(false)
}

@Composable
internal fun DetailsScreenModern(
    state: YummyDroidUiState,
    screenUiState: DetailsScreenUiState,
    activeFocusRequestNonce: Long,
    onRefresh: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .yummyAppBackground(),
    ) {
        DetailsStateContent(
            state = state.details,
            onRetry = onRefresh,
            emptyMessage = uiText(UiStringKey.AnimeCardNotFound),
        ) { details ->
            DetailsContentModern(
                details = details,
                screenUiState = screenUiState,
                activeFocusRequestNonce = activeFocusRequestNonce,
                settings = state.settings,
                videos = state.videos,
                selectedGroup = state.selectedVideoGroup,
                auth = state.auth,
                animeMark = state.animeMark,
                detailsExtras = state.detailsExtras,
                forcedOfflineMode = state.forcedOfflineMode,
                playbackProgress = state.playbackProgress,
                playbackHistory = state.playbackHistory,
                onOpenAnime = onOpenAnime,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onGenreFilterSelected = onGenreFilterSelected,
                onYearFilterSelected = onYearFilterSelected,
                onStudioFilterSelected = onStudioFilterSelected,
                onCreatorFilterSelected = onCreatorFilterSelected,
                onSelectVideoGroup = onSelectVideoGroup,
                onPlayVideo = onPlayVideo,
                onPlayVideoWithResumeChoice = onPlayVideoWithResumeChoice,
                onPlayVideoAt = onPlayVideoAt,
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onSetAnimeRating = onSetAnimeRating,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                onResetAnimeWatchProgress = onResetAnimeWatchProgress,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                onRetry = onRefresh,
            )
        }
        if (state.forcedOfflineMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                OfflineModeChip()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailsContentModern(
    details: AnimeDetails,
    screenUiState: DetailsScreenUiState,
    activeFocusRequestNonce: Long,
    settings: AppSettings,
    videos: LoadState<List<VideoVariant>>,
    selectedGroup: String?,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    playbackProgress: PlaybackProgress?,
    playbackHistory: List<PlaybackProgress>,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 900 || (isLandscape && configuration.screenWidthDp >= 600)
    val useThreeColumnHero = configuration.screenWidthDp >= 1180
    val readyVideos = videos.readyListOrEmpty()
    val playableVideos = remember(readyVideos, forcedOfflineMode) {
        if (forcedOfflineMode) readyVideos.filter { it.isOfflineAvailable } else readyVideos
    }
    val downloadedSummary = readyVideos.downloadedEpisodeSummary()
    val episodeSummary = details.effectiveEpisodeSummary()
    val apiEpisodeCount = details.episodeCount
    val watchVideo = remember(playableVideos, selectedGroup) {
        playableVideos.heroStartVideo(selectedGroup)
    }
    val resumeTarget = remember(playableVideos, playbackProgress) {
        playbackProgress.resolveResumeTarget(playableVideos)
    }
    val hasWatchProgress = playbackProgress != null || playbackHistory.isNotEmpty()
    val detailsScrollState = screenUiState.scrollState
    val detailsFocusGridState = rememberVisualFocusGridState(
        size = DETAILS_SCREEN_FOCUS_GRAPH_SIZE,
        key = details.id,
        allowLoosePerpendicularMatch = true,
    )

    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailsBringIntoViewSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .visualFocusGridNavigation(detailsFocusGridState)
                .verticalScroll(detailsScrollState),
        ) {
            DetailsHeroModern(
                details = details,
                activeFocusRequestNonce = activeFocusRequestNonce,
                isWide = isWide,
                useThreeColumnHero = useThreeColumnHero,
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadVideos = playableVideos,
                downloadedSummary = downloadedSummary,
                episodeSummary = episodeSummary,
                apiEpisodeCount = apiEpisodeCount,
                auth = auth,
                animeMark = animeMark,
                detailsExtras = detailsExtras,
                showMarkPanel = !forcedOfflineMode && auth.profile != null,
                showHeroRating = !forcedOfflineMode,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onGenreFilterSelected = onGenreFilterSelected,
                onYearFilterSelected = onYearFilterSelected,
                onStudioFilterSelected = onStudioFilterSelected,
                onCreatorFilterSelected = onCreatorFilterSelected,
                onSelectListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onSetAnimeRating = onSetAnimeRating,
                onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = settings.defaultQuality,
                onDownloadAllVideos = onDownloadAllVideos,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                canDownload = !forcedOfflineMode,
                hasWatchProgress = hasWatchProgress,
                onResetWatchProgress = { onResetAnimeWatchProgress(details.id) },
                focusGridState = detailsFocusGridState,
                modifier = Modifier.fillMaxWidth(),
            )

            DetailsDescriptionSection(description = details.description)
            DetailsScreenshotsSection(
                screenshots = details.screenshots,
                onRegisterInputActionHandler = onRegisterModalInputActionHandler,
                focusGridState = detailsFocusGridState,
                focusIndexOffset = DETAILS_SCREEN_SCREENSHOTS_FOCUS_INDEX,
                focusBlockKey = DetailsFocusBlockKey.Screenshots,
            )
            DetailsRelatedAnimeSection(
                relatedAnime = details.relatedAnime,
                expanded = screenUiState.relatedExpanded,
                onExpandedChange = { expanded -> screenUiState.relatedExpanded = expanded },
                onOpenAnime = onOpenAnime,
                focusGridState = detailsFocusGridState,
                focusIndexOffset = DETAILS_SCREEN_RELATED_FOCUS_INDEX,
                focusBlockKey = DetailsFocusBlockKey.RelatedAnime,
            )

            when (videos) {
                LoadState.Loading -> LoadingPane(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                )
                is LoadState.Error -> ErrorPane(
                    message = videos.message,
                    onRetry = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp),
                )
                is LoadState.Ready -> VideoPickerModern(
                    videos = videos.data,
                    selectedGroup = selectedGroup,
                    playbackHistory = playbackHistory,
                    onSelectGroup = onSelectVideoGroup,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoWithResumeChoice = onPlayVideoWithResumeChoice,
                    forcedOfflineMode = forcedOfflineMode,
                    modifier = Modifier.fillMaxWidth(),
                    focusGridState = detailsFocusGridState,
                    focusIndexOffset = DETAILS_SCREEN_EPISODES_FOCUS_INDEX,
                    focusBlockKey = DetailsFocusBlockKey.Episodes,
                )
            }
            if (!forcedOfflineMode) {
                DetailsSubscriptionsHostSection(
                    extrasState = detailsExtras,
                    auth = auth,
                    videos = readyVideos,
                    allowSubscriptions = details.canShowVideoSubscriptions(),
                    expanded = screenUiState.subscriptionsExpanded,
                    onExpandedChange = { expanded -> screenUiState.subscriptionsExpanded = expanded },
                    onToggleVideoSubscription = onToggleVideoSubscription,
                    focusGridState = detailsFocusGridState,
                    focusIndexOffset = DETAILS_SCREEN_SUBSCRIPTIONS_FOCUS_INDEX,
                    focusBlockKey = DetailsFocusBlockKey.Subscriptions,
                )
                DetailsRecommendationsSection(
                    extrasState = detailsExtras,
                    onOpenAnime = onOpenAnime,
                    focusGridState = detailsFocusGridState,
                    focusIndexOffset = DETAILS_SCREEN_RECOMMENDATIONS_FOCUS_INDEX,
                    focusBlockKey = DetailsFocusBlockKey.Recommendations,
                )
                DetailsCommentsHostSection(
                    extrasState = detailsExtras,
                    totalComments = details.commentsCount,
                    isAuthorized = auth.profile != null,
                    scrollState = detailsScrollState,
                    expanded = screenUiState.commentsExpanded,
                    onExpandedChange = { expanded -> screenUiState.commentsExpanded = expanded },
                    onAddAnimeComment = onAddAnimeComment,
                    onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                    focusGridState = detailsFocusGridState,
                    focusIndexOffset = DETAILS_SCREEN_COMMENTS_FOCUS_INDEX,
                    focusBlockKey = DetailsFocusBlockKey.Comments,
                )
            }
        }
    }
}
