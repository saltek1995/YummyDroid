package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.readyListOrEmpty

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
    val windowSize = currentWindowSizeDp()
    val isLandscape = windowSize.width > windowSize.height
    val isWide = windowSize.width >= 900.dp || (isLandscape && windowSize.width >= 600.dp)
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
        resolveDetailsFocusLayout(
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
