package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing

// DetailsContentModels
internal data class DetailsContentModel(
    val details: AnimeDetails,
    val screenUiState: DetailsScreenUiState,
    val interactive: Boolean,
    val activeFocusRequestNonce: Long,
    val retainedFocusRequestNonce: Long,
    val settings: AppSettings,
    val videos: LoadState<List<VideoVariant>>,
    val selectedGroup: String?,
    val auth: AuthUiState,
    val animeMark: LoadState<UserAnimeMark?>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val forcedOfflineMode: Boolean,
    val playbackProgress: PlaybackProgress?,
    val playbackHistory: List<PlaybackProgress>,
    val playbackHistoryLoading: Boolean,
)

internal data class DetailsContentActions(
    val onOpenAnime: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onGenreFilterSelected: (Long, FilterOption) -> Unit,
    val onYearFilterSelected: (Long, Int) -> Unit,
    val onStudioFilterSelected: (Long, FilterOption) -> Unit,
    val onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    val onSelectVideoGroup: (String) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onAddAnimeComment: (String) -> Unit,
    val onLoadMoreAnimeComments: () -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onResetAnimeWatchProgress: (Long) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit,
    val onRetry: () -> Unit,
)

// DetailsContentPresentation
internal data class DetailsContentPresentation(
    val isWide: Boolean,
    val readyVideos: List<VideoVariant>,
    val playableVideos: List<VideoVariant>,
    val downloadedSummary: String?,
    val episodeSummary: String,
    val apiEpisodeCount: Int,
    val watchVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val hasWatchProgress: Boolean,
    val playbackHistoryLoading: Boolean,
    val focusLayout: DetailsFocusLayout,
)

@Composable
internal fun rememberDetailsContentPresentation(model: DetailsContentModel): DetailsContentPresentation {
    val windowSize = currentResponsiveWindowSizeDp()
    val isLandscape = windowSize.width > windowSize.height
    val readyVideos = model.videos.readyListOrEmpty()
    val playableVideos = remember(readyVideos, model.forcedOfflineMode) {
        if (model.forcedOfflineMode) readyVideos.filter { it.isOfflineAvailable } else readyVideos
    }
    val watchVideo = remember(playableVideos, model.selectedGroup) {
        playableVideos.heroStartVideo(model.selectedGroup)
    }
    val resumeTarget = remember(playableVideos, model.playbackProgress, model.playbackHistory) {
        (listOfNotNull(model.playbackProgress) + model.playbackHistory)
            .resolveLatestResumeTarget(playableVideos)
    }
    val focusLayout = rememberDetailsFocusLayout(model, readyVideos)
    return DetailsContentPresentation(
        isWide = windowSize.width >= 900.dp || (isLandscape && windowSize.width >= 600.dp),
        readyVideos = readyVideos,
        playableVideos = playableVideos,
        downloadedSummary = readyVideos.downloadedEpisodeSummary(),
        episodeSummary = model.details.effectiveEpisodeSummary(),
        apiEpisodeCount = model.details.episodeCount,
        watchVideo = watchVideo,
        resumeTarget = resumeTarget,
        hasWatchProgress = model.playbackProgress != null || model.playbackHistory.isNotEmpty(),
        playbackHistoryLoading = model.playbackHistoryLoading &&
            model.auth.profile != null &&
            !model.forcedOfflineMode &&
            model.playbackProgress == null &&
            model.playbackHistory.isEmpty() &&
            resumeTarget == null,
        focusLayout = focusLayout,
    )
}

@Composable
private fun rememberDetailsFocusLayout(
    model: DetailsContentModel,
    readyVideos: List<VideoVariant>,
): DetailsFocusLayout {
    val details = model.details
    val screenUiState = model.screenUiState
    return remember(
        details.id,
        details.screenshots.size,
        details.relatedAnime.size,
        screenUiState.relatedExpanded,
        readyVideos,
        model.videos,
        model.detailsExtras,
        model.auth.profile,
        model.forcedOfflineMode,
        screenUiState.subscriptionsExpanded,
        screenUiState.commentsExpanded,
    ) {
        resolveDetailsFocusLayout(
            details = details,
            videos = model.videos,
            readyVideos = readyVideos,
            auth = model.auth,
            detailsExtras = model.detailsExtras,
            forcedOfflineMode = model.forcedOfflineMode,
            relatedExpanded = screenUiState.relatedExpanded,
            subscriptionsExpanded = screenUiState.subscriptionsExpanded,
            commentsExpanded = screenUiState.commentsExpanded,
        )
    }
}

// DetailsContentSections
@Composable
internal fun DetailsContentSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    DetailsOverviewSections(model, actions, presentation, focusGridState)
    DetailsVideoSection(model, actions, presentation, focusGridState)
    if (!model.forcedOfflineMode) {
        DetailsOnlineSections(model, actions, presentation, focusGridState)
    }
}

@Composable
private fun DetailsOverviewSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val details = model.details
    DetailsHeroModern(
        model = model.toDetailsHeroModel(presentation),
        actions = actions.toDetailsHeroActions(details.id),
        focusGridState = focusGridState,
        modifier = Modifier.fillMaxWidth(),
    )
    DetailsDescriptionSection(description = details.description)
    DetailsScreenshotsSection(
        screenshots = details.screenshots,
        interactive = model.interactive,
        onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Screenshots),
        focusBlockKey = DetailsFocusBlockKey.Screenshots,
    )
    DetailsRelatedAnimeSection(
        relatedAnime = details.relatedAnime,
        expanded = model.screenUiState.relatedExpanded,
        onExpandedChange = { model.screenUiState.relatedExpanded = it },
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.RelatedAnime),
        focusBlockKey = DetailsFocusBlockKey.RelatedAnime,
    )
}

private fun DetailsContentModel.toDetailsHeroModel(
    presentation: DetailsContentPresentation,
): DetailsHeroModel = DetailsHeroModel(
    details = details,
    interactive = interactive,
    activeFocusRequestNonce = activeFocusRequestNonce,
    isWide = presentation.isWide,
    watchVideo = presentation.watchVideo,
    resumeTarget = presentation.resumeTarget,
    downloadVideos = presentation.playableVideos,
    downloadedSummary = presentation.downloadedSummary,
    episodeSummary = presentation.episodeSummary,
    apiEpisodeCount = presentation.apiEpisodeCount,
    auth = auth,
    animeMark = animeMark,
    detailsExtras = detailsExtras,
    showMarkPanel = !forcedOfflineMode && auth.profile != null,
    showHeroRating = !forcedOfflineMode,
    defaultDownloadQuality = settings.defaultQuality,
    canDownload = !forcedOfflineMode,
    hasWatchProgress = presentation.hasWatchProgress,
    playbackHistoryLoading = presentation.playbackHistoryLoading,
)

private fun DetailsContentActions.toDetailsHeroActions(animeId: Long): DetailsHeroActions =
    DetailsHeroActions(
        onOpenLogin = onOpenLogin,
        onGenreFilterSelected = onGenreFilterSelected,
        onYearFilterSelected = onYearFilterSelected,
        onStudioFilterSelected = onStudioFilterSelected,
        onCreatorFilterSelected = onCreatorFilterSelected,
        onSelectListMark = onSelectAnimeListMark,
        onToggleFavorite = onToggleFavorite,
        onRetry = onRetry,
        onSetAnimeRating = onSetAnimeRating,
        onPlayVideo = onPlayVideo,
        onPlayVideoAt = onPlayVideoAt,
        onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
        onDownloadAllVideos = onDownloadAllVideos,
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
        onResetWatchProgress = { onResetAnimeWatchProgress(animeId) },
    )

@Composable
private fun DetailsVideoSection(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    when (val videos = model.videos) {
        LoadState.Loading -> LoadingPane(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Error -> ErrorPane(
            message = videos.message,
            onRetry = actions.onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Ready -> VideoPickerModern(
            videos = videos.data,
            selectedGroup = model.selectedGroup,
            playbackHistory = model.playbackHistory,
            onSelectGroup = actions.onSelectVideoGroup,
            onPlayVideo = actions.onPlayVideo,
            onPlayVideoWithResumeChoice = actions.onPlayVideoWithResumeChoice,
            forcedOfflineMode = model.forcedOfflineMode,
            modifier = Modifier.fillMaxWidth(),
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Episodes),
            focusBlockKey = DetailsFocusBlockKey.Episodes,
        )
    }
}

@Composable
private fun DetailsOnlineSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val screenUiState = model.screenUiState
    val extras = when (val extrasState = model.detailsExtras) {
        is LoadState.Ready -> extrasState.data
        LoadState.Loading -> return
        is LoadState.Error -> {
            ErrorPane(
                message = extrasState.message,
                onRetry = actions.onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
            )
            return
        }
    }
    if (model.details.canShowVideoSubscriptions()) {
        DetailsSubscriptionsSection(
            auth = model.auth,
            videos = presentation.readyVideos,
            subscriptions = extras.subscriptions,
            expanded = screenUiState.subscriptionsExpanded,
            onExpandedChange = { screenUiState.subscriptionsExpanded = it },
            onToggleVideoSubscription = actions.onToggleVideoSubscription,
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Subscriptions),
            focusBlockKey = DetailsFocusBlockKey.Subscriptions,
        )
    }
    DetailsAnimeRowSection(
        title = uiText(UiStringKey.Similar),
        animes = extras.recommendations,
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Recommendations),
        focusBlockKey = DetailsFocusBlockKey.Recommendations,
    )
    DetailsCommentsSection(
        comments = extras.comments,
        totalComments = model.details.commentsCount,
        commentsPaging = extras.commentsPaging,
        isAuthorized = model.auth.profile != null,
        scrollState = screenUiState.scrollState,
        expanded = screenUiState.commentsExpanded,
        onExpandedChange = { screenUiState.commentsExpanded = it },
        onAddAnimeComment = actions.onAddAnimeComment,
        onLoadMoreAnimeComments = actions.onLoadMoreAnimeComments,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Comments),
        focusBlockKey = DetailsFocusBlockKey.Comments,
    )
}

private fun DetailsContentModel.openAnime(
    actions: DetailsContentActions,
    animeId: Long,
    focusKey: Any?,
) {
    if (focusKey != null) screenUiState.retainedFocusKey = focusKey
    actions.onOpenAnime(animeId)
}

// DetailsDescriptionSection
@Composable
internal fun DetailsDescriptionSection(description: String) {
    val normalizedDescription = description.trim()
    if (normalizedDescription.isBlank()) return
    Text(
        text = normalizedDescription,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

// DetailsFactValue
private val MissingFactValues = setOf(
    "-",
    "\u2014",
    "\u0432\u0402\u201d",
)

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized !in MissingFactValues
}

// DetailsPoster
@Composable
internal fun DetailsPoster(
    posterUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactiveModifier = if (onClick != null) {
        Modifier.dpadClickable(shape, onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(interactiveModifier),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
