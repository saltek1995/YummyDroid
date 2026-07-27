package me.yummydroid.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
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
import me.yummydroid.app.YummyDroidUiState

@Composable
internal fun DetailsScreenModern(
    state: YummyDroidUiState,
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
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailsStateContent(
            state = state.details,
            onRetry = onRefresh,
            emptyMessage = uiText("Карточка не найдена"),
        ) { details ->
            DetailsContentModern(
                details = details,
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
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
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

@Composable
internal fun DetailsContentModern(
    details: AnimeDetails,
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
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
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
    val episodeSummary = details.effectiveEpisodeSummary(readyVideos)
    val watchVideo = remember(playableVideos, selectedGroup) {
        playableVideos.heroStartVideo(selectedGroup)
    }
    val resumeTarget = remember(playableVideos, playbackProgress) {
        playbackProgress.resolveResumeTarget(playableVideos)
    }
    val hasWatchProgress = playbackProgress != null || playbackHistory.isNotEmpty()
    val detailsScrollState = remember(details.id) { ScrollState(0) }
    val episodesEntryFocusRequester = remember(details.id) { FocusRequester() }
    val recommendationsEntryFocusRequester = remember(details.id) { FocusRequester() }
    val commentsEntryFocusRequester = remember(details.id) { FocusRequester() }
    var relatedExpanded by remember(details.id) { mutableStateOf(false) }
    var subscriptionsExpanded by remember(details.id) { mutableStateOf(false) }
    var commentsExpanded by remember(details.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            onResolveDownloadQualities = onResolveDownloadQualities,
            onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = settings.defaultQuality,
            onDownloadAllVideos = onDownloadAllVideos,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            canDownload = !forcedOfflineMode,
            hasWatchProgress = hasWatchProgress,
            onResetWatchProgress = { onResetAnimeWatchProgress(details.id) },
            modifier = Modifier.fillMaxWidth(),
        )

        DetailsDescriptionSection(description = details.description)
        DetailsScreenshotsSection(
            screenshots = details.screenshots,
            onRegisterInputActionHandler = onRegisterModalInputActionHandler,
        )
        DetailsRelatedAnimeSection(
            relatedAnime = details.relatedAnime,
            expanded = relatedExpanded,
            onExpandedChange = { expanded -> relatedExpanded = expanded },
            onOpenAnime = onOpenAnime,
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
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                defaultDownloadQuality = settings.defaultQuality,
                forcedOfflineMode = forcedOfflineMode,
                canDownload = !forcedOfflineMode,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                modifier = Modifier.fillMaxWidth(),
                entryFocusRequester = episodesEntryFocusRequester,
            )
        }
        if (!forcedOfflineMode) {
            DetailsSubscriptionsHostSection(
                extrasState = detailsExtras,
                auth = auth,
                videos = readyVideos,
                allowSubscriptions = details.canShowVideoSubscriptions(),
                expanded = subscriptionsExpanded,
                onExpandedChange = { expanded -> subscriptionsExpanded = expanded },
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
            DetailsRecommendationsSection(
                extrasState = detailsExtras,
                onOpenAnime = onOpenAnime,
                entryFocusRequester = recommendationsEntryFocusRequester,
            )
            DetailsCommentsHostSection(
                extrasState = detailsExtras,
                totalComments = details.commentsCount,
                isAuthorized = auth.profile != null,
                scrollState = detailsScrollState,
                expanded = commentsExpanded,
                onExpandedChange = { expanded -> commentsExpanded = expanded },
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                entryFocusRequester = commentsEntryFocusRequester,
            )
        }
    }
}
