package me.yummydroid.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.yummyAppBackground

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
            DetailsContentRuntime(
                model = DetailsContentModel(
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
                    onRetry = onRefresh,
                ),
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
