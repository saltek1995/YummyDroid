package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import me.yummydroid.app.readyListOrEmpty

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
