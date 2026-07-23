package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
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
    onGenreFilterSelected: (FilterOption) -> Unit,
    onYearFilterSelected: (Int) -> Unit,
    onStudioFilterSelected: (FilterOption) -> Unit,
    onCreatorFilterSelected: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
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
                onPlayVideoAt = onPlayVideoAt,
                onSelectAnimeListMark = onSelectAnimeListMark,
                onToggleFavorite = onToggleFavorite,
                onSetAnimeRating = onSetAnimeRating,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                onToggleVideoSubscription = onToggleVideoSubscription,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
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
    onGenreFilterSelected: (FilterOption) -> Unit,
    onYearFilterSelected: (Int) -> Unit,
    onStudioFilterSelected: (FilterOption) -> Unit,
    onCreatorFilterSelected: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWide = configuration.screenWidthDp >= 900 || (isLandscape && configuration.screenWidthDp >= 600)
    val useThreeColumnHero = configuration.screenWidthDp >= 1180
    val compactWideHero = isWide && configuration.screenHeightDp < 560
    val heroHeight = if (!isWide) {
        null
    } else if (compactWideHero) {
        (configuration.screenHeightDp * 0.44f).dp.coerceIn(210.dp, 250.dp)
    } else if (useThreeColumnHero) {
        (configuration.screenHeightDp * 0.42f).dp.coerceIn(270.dp, 340.dp)
    } else {
        (configuration.screenHeightDp * 0.42f).dp.coerceIn(250.dp, 320.dp)
    }
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
    var factsExpanded by remember(details.id) { mutableStateOf(false) }
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
            showMarkPanel = isWide && !forcedOfflineMode,
            showHeroRating = isWide && !forcedOfflineMode,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onSelectListMark = onSelectAnimeListMark,
            onToggleFavorite = onToggleFavorite,
            onSetAnimeRating = onSetAnimeRating,
            onResolveDownloadQualities = onResolveDownloadQualities,
            onPlayVideo = onPlayVideo,
            onPlayVideoAt = onPlayVideoAt,
            defaultDownloadQuality = settings.defaultQuality,
            onDownloadAllVideos = onDownloadAllVideos,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            canDownload = !forcedOfflineMode,
            hasWatchProgress = hasWatchProgress,
            onResetWatchProgress = { onResetAnimeWatchProgress(details.id) },
            modifier = Modifier.fillMaxWidth().then(
                if (heroHeight != null) Modifier.height(heroHeight) else Modifier,
            ),
        )

        if (!forcedOfflineMode) {
            DetailsCompactRatingSection(
                extrasState = detailsExtras,
                auth = auth,
                showRating = !isWide,
                onSetAnimeRating = onSetAnimeRating,
            )
            if (!isWide) {
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectAnimeListMark,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                )
            }
            if (!isWide) {
                DetailsRatingStrip(
                    ratingDetails = details.ratingDetails,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                )
            }
        }
        DetailsFactsSection(
            details = details,
            expanded = factsExpanded,
            onExpandedChange = { expanded -> factsExpanded = expanded },
            onGenreClick = onGenreFilterSelected,
            onYearClick = onYearFilterSelected,
            onStudioClick = onStudioFilterSelected,
            onCreatorClick = onCreatorFilterSelected,
        )
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
        if (!forcedOfflineMode) {
            DetailsExtrasTopSection(
                extrasState = detailsExtras,
                auth = auth,
                videos = readyVideos,
                onSetAnimeRating = onSetAnimeRating,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }

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
                onPlayVideoAt = onPlayVideoAt,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadVideo = onDownloadVideo,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                defaultDownloadQuality = settings.defaultQuality,
                forcedOfflineMode = forcedOfflineMode,
                canDownload = !forcedOfflineMode,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                modifier = Modifier.fillMaxWidth(),
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
            )
        }
    }
}
