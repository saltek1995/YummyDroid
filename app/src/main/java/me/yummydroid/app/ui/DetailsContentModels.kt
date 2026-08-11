package me.yummydroid.app.ui

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

internal data class DetailsContentModel(
    val details: AnimeDetails,
    val screenUiState: DetailsScreenUiState,
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
