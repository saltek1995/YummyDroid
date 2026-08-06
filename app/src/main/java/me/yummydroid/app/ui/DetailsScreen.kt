package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.ui.theme.yummyAppBackground

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

private const val DETAILS_SCREEN_EPISODE_FOCUS_CAPACITY = 24

private enum class DetailsFocusBlock {
    Screenshots,
    RelatedAnime,
    Episodes,
    Subscriptions,
    Recommendations,
    Comments,
}

private data class DetailsFocusLayout(
    val size: Int,
    private val offsets: Map<DetailsFocusBlock, Int>,
) {
    fun offset(block: DetailsFocusBlock): Int = offsets.getValue(block)
}

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
    var retainedFocusKey by mutableStateOf<Any?>(null)
    var suppressInitialFocusOnReactivation by mutableStateOf(false)
}

@Composable
internal fun DetailsScreenModern(
    state: YummyDroidUiState,
    screenUiState: DetailsScreenUiState,
    activeFocusRequestNonce: Long,
    retainedFocusRequestNonce: Long = 0L,
    onRefresh: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
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
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
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
                retainedFocusRequestNonce = retainedFocusRequestNonce,
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
                onRegisterDpadFocusRecoveryHandler = onRegisterDpadFocusRecoveryHandler,
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
    retainedFocusRequestNonce: Long = 0L,
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
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 900 || (isLandscape && configuration.screenWidthDp >= 600)
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
    val detailsFocusLayout = remember(
        details.id,
        details.screenshots.size,
        details.relatedAnime.size,
        screenUiState.relatedExpanded,
        readyVideos,
        videos,
        detailsExtras,
        auth.profile,
        forcedOfflineMode,
        screenUiState.subscriptionsExpanded,
        screenUiState.commentsExpanded,
    ) {
        buildDetailsFocusLayout(
            details = details,
            videos = videos,
            readyVideos = readyVideos,
            auth = auth,
            detailsExtras = detailsExtras,
            forcedOfflineMode = forcedOfflineMode,
            relatedExpanded = screenUiState.relatedExpanded,
            subscriptionsExpanded = screenUiState.subscriptionsExpanded,
            commentsExpanded = screenUiState.commentsExpanded,
        )
    }
    val detailsFocusGridState = rememberVisualFocusGridState(
        size = detailsFocusLayout.size,
        key = details.id,
        allowLoosePerpendicularMatch = true,
    )
    val lastFocusedDetailsKey = detailsFocusGridState.lastFocusedKey
    LaunchedEffect(lastFocusedDetailsKey) {
        if (lastFocusedDetailsKey != null) {
            screenUiState.retainedFocusKey = lastFocusedDetailsKey
        }
    }
    val hasHeroActions = watchVideo != null || hasWatchProgress
    var detailsLayerHasFocus by remember { mutableStateOf(false) }

    fun requestFirstDetailsFocus(): Boolean {
        return detailsFocusGridState.requestFirstAvailableFocus()
    }

    fun recoverFirstDetailsFocusIfMissing(): Boolean {
        if (detailsLayerHasFocus && detailsFocusGridState.focusedIndex != null) return false
        val restored = detailsFocusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
            detailsFocusGridState.requestRetainedOrFirstAvailableFocus()
        if (restored) {
            screenUiState.suppressInitialFocusOnReactivation = false
        }
        return restored
    }

    fun openAnimeFromDetailsLayer(animeId: Long, focusKey: Any?) {
        if (focusKey != null) {
            screenUiState.retainedFocusKey = focusKey
        }
        onOpenAnime(animeId)
    }

    DisposableEffect(detailsFocusGridState, onRegisterDpadFocusRecoveryHandler) {
        onRegisterDpadFocusRecoveryHandler(::recoverFirstDetailsFocusIfMissing)
        onDispose { onRegisterDpadFocusRecoveryHandler(null) }
    }

    LaunchedEffect(activeFocusRequestNonce, details.id, hasHeroActions, detailsFocusLayout.size) {
        if (activeFocusRequestNonce <= 0L || hasHeroActions) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (requestFirstDetailsFocus()) return@LaunchedEffect
        }
    }

    LaunchedEffect(retainedFocusRequestNonce, details.id, detailsFocusLayout.size) {
        if (retainedFocusRequestNonce <= 0L) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            val restored = detailsFocusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
                (screenUiState.retainedFocusKey == null && detailsFocusGridState.requestLastFocusedFocus())
            if (restored) {
                screenUiState.suppressInitialFocusOnReactivation = false
                return@LaunchedEffect
            }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailsBringIntoViewSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    detailsLayerHasFocus = focusState.isFocused || focusState.hasFocus
                }
                .focusGroup()
                .visualFocusGridNavigation(detailsFocusGridState)
                .verticalScroll(detailsScrollState),
        ) {
            DetailsHeroModern(
                details = details,
                activeFocusRequestNonce = activeFocusRequestNonce,
                isWide = isWide,
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
                focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.Screenshots),
                focusBlockKey = DetailsFocusBlockKey.Screenshots,
            )
            DetailsRelatedAnimeSection(
                relatedAnime = details.relatedAnime,
                expanded = screenUiState.relatedExpanded,
                onExpandedChange = { expanded -> screenUiState.relatedExpanded = expanded },
                onOpenAnime = ::openAnimeFromDetailsLayer,
                focusGridState = detailsFocusGridState,
                focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.RelatedAnime),
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
                    focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.Episodes),
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
                    focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.Subscriptions),
                    focusBlockKey = DetailsFocusBlockKey.Subscriptions,
                )
                DetailsRecommendationsSection(
                    extrasState = detailsExtras,
                    onOpenAnime = ::openAnimeFromDetailsLayer,
                    focusGridState = detailsFocusGridState,
                    focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.Recommendations),
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
                    focusIndexOffset = detailsFocusLayout.offset(DetailsFocusBlock.Comments),
                    focusBlockKey = DetailsFocusBlockKey.Comments,
                )
            }
        }
    }
}

private fun buildDetailsFocusLayout(
    details: AnimeDetails,
    videos: LoadState<List<VideoVariant>>,
    readyVideos: List<VideoVariant>,
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    relatedExpanded: Boolean,
    subscriptionsExpanded: Boolean,
    commentsExpanded: Boolean,
): DetailsFocusLayout {
    var nextIndex = DETAILS_HERO_FOCUS_GRAPH_SIZE
    val offsets = mutableMapOf<DetailsFocusBlock, Int>()

    fun allocate(block: DetailsFocusBlock, count: Int) {
        offsets[block] = nextIndex
        nextIndex += count.coerceAtLeast(0)
    }

    allocate(
        DetailsFocusBlock.Screenshots,
        details.screenshots.take(24).size,
    )
    allocate(
        DetailsFocusBlock.RelatedAnime,
        details.relatedAnime.detailsExpandedListFocusCount(relatedExpanded),
    )
    allocate(
        DetailsFocusBlock.Episodes,
        if (videos is LoadState.Ready && videos.data.isNotEmpty()) DETAILS_SCREEN_EPISODE_FOCUS_CAPACITY else 0,
    )
    allocate(
        DetailsFocusBlock.Subscriptions,
        if (forcedOfflineMode) {
            0
        } else {
            detailsSubscriptionFocusItemCount(
                auth = auth,
                videos = readyVideos,
                detailsExtras = detailsExtras,
                allowSubscriptions = details.canShowVideoSubscriptions(),
                expanded = subscriptionsExpanded,
            )
        },
    )
    allocate(
        DetailsFocusBlock.Recommendations,
        if (!forcedOfflineMode && detailsExtras is LoadState.Ready) {
            detailsExtras.data.recommendations.size
        } else {
            0
        },
    )
    allocate(
        DetailsFocusBlock.Comments,
        if (!forcedOfflineMode) {
            detailsCommentsFocusItemCount(
                auth = auth,
                detailsExtras = detailsExtras,
                expanded = commentsExpanded,
            )
        } else {
            0
        },
    )

    return DetailsFocusLayout(
        size = nextIndex.coerceAtLeast(DETAILS_HERO_FOCUS_GRAPH_SIZE),
        offsets = offsets,
    )
}

private fun List<RelatedAnime>.detailsExpandedListFocusCount(expanded: Boolean): Int {
    if (isEmpty()) return 0
    return 1 + if (expanded) size else 0
}

private fun detailsSubscriptionFocusItemCount(
    auth: AuthUiState,
    videos: List<VideoVariant>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    allowSubscriptions: Boolean,
    expanded: Boolean,
): Int {
    if (!allowSubscriptions || auth.profile == null || videos.isEmpty() || detailsExtras !is LoadState.Ready) {
        return 0
    }
    val groups = videos.detailsSubscriptionVoiceGroups()
    if (groups.isEmpty()) return 0
    return 1 + if (expanded) groups.size else 0
}

private fun detailsCommentsFocusItemCount(
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    expanded: Boolean,
): Int {
    if (detailsExtras !is LoadState.Ready) return 0
    val comments = detailsExtras.data.comments
    val isAuthorized = auth.profile != null
    if (comments.isEmpty() && !isAuthorized) return 0
    if (!expanded) return 1
    return 1 + if (isAuthorized) 2 else 0 + comments.size
}
