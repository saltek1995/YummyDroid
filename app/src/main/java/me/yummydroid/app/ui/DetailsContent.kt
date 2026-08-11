package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
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
    DetailsContentRuntime(
        model = DetailsContentModel(
            details = details,
            screenUiState = screenUiState,
            activeFocusRequestNonce = activeFocusRequestNonce,
            retainedFocusRequestNonce = retainedFocusRequestNonce,
            settings = settings,
            videos = videos,
            selectedGroup = selectedGroup,
            auth = auth,
            animeMark = animeMark,
            detailsExtras = detailsExtras,
            forcedOfflineMode = forcedOfflineMode,
            playbackProgress = playbackProgress,
            playbackHistory = playbackHistory,
        ),
        actions = DetailsContentActions(
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
            onRetry = onRetry,
        ),
    )
}
