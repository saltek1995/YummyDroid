package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedVoiceKey
import me.yummydroid.app.data.siteDefaultVideo
import me.yummydroid.app.ui.theme.yummyAppBackground

// DetailsContentRuntime
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailsContentRuntime(
    model: DetailsContentModel,
    actions: DetailsContentActions,
) {
    val presentation = rememberDetailsContentPresentation(model)
    val focusGridState = rememberVisualFocusGridState(
        size = presentation.focusLayout.size,
        key = model.details.id,
        allowLoosePerpendicularMatch = true,
    )
    val layerFocusState = rememberDetailsLayerFocusState()
    DetailsContentFocusEffects(
        model = model,
        actions = actions,
        presentation = presentation,
        focusGridState = focusGridState,
        layerFocusState = layerFocusState,
    )
    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailsBringIntoViewSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    layerFocusState.hasFocus = focusState.isFocused || focusState.hasFocus
                }
                .focusGroup()
                .visualFocusGridNavigation(focusGridState)
                .verticalScroll(model.screenUiState.scrollState),
        ) {
            DetailsContentSections(model, actions, presentation, focusGridState)
        }
    }
}

// DetailsPlaybackPolicy
internal data class HeroResumeTarget(
    val video: VideoVariant,
    val positionMs: Long,
)

internal fun List<VideoVariant>.heroStartVideo(selectedGroup: String?): VideoVariant? {
    if (isEmpty()) return null
    val preferredGroup = selectedGroup?.takeIf { groupKey -> any { it.groupKey == groupKey } }
        ?: siteDefaultVideo()?.groupKey
    val preferredVoice = matchingVoiceKeyForGroup(preferredGroup)
    return sortedForPlayer(preferredGroup, preferredVoice).firstOrNull()
        ?: siteDefaultVideo()
}

internal fun PlaybackProgress?.resolveResumeTarget(
    videos: List<VideoVariant>,
): HeroResumeTarget? {
    val progress = this ?: return null
    if (progress.positionMs <= 0L || videos.isEmpty()) return null
    val video = videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = true)
    } ?: videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = false)
    } ?: return null

    val safePosition = progress.safeResumePosition()
    if (safePosition <= 0L) return null
    return HeroResumeTarget(video, safePosition)
}

private fun PlaybackProgress.safeResumePosition(): Long {
    val duration = durationMs.takeIf { it > 0L } ?: return positionMs.coerceAtLeast(0L)
    return positionMs.coerceIn(0L, (duration - 5_000L).coerceAtLeast(0L))
}

internal fun List<PlaybackProgress>.progressFor(video: VideoVariant): PlaybackProgress? {
    return firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = false) }
}

internal fun VideoVariant.matchesPlaybackProgress(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    if (matchesProgressVideoId(progress)) return true
    if (!matchesProgressSource(progress, requireGroup)) return false
    return matchesProgressEpisode(progress.episode)
}

private fun VideoVariant.matchesProgressVideoId(progress: PlaybackProgress): Boolean {
    return progress.videoId > 0L && id == progress.videoId
}

private fun VideoVariant.matchesProgressSource(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    return if (requireGroup) {
        progress.groupKey.isNotBlank() && groupKey == progress.groupKey
    } else {
        matchesProgressVoice(progress)
    }
}

private fun VideoVariant.matchesProgressEpisode(progressEpisode: String): Boolean {
    if (progressEpisode.isBlank()) return false
    return episode.matchesProgressEpisode(progressEpisode) ||
        matchingEpisodeKey.matchesProgressEpisode(progressEpisode)
}

private fun VideoVariant.matchesProgressVoice(progress: PlaybackProgress): Boolean {
    val progressVoiceKey = progress.groupKey
        .substringAfter('|', progress.groupKey)
        .normalizedVoiceKey()
    return progressVoiceKey.isBlank() || matchingVoiceKey == progressVoiceKey
}

internal fun String.matchesProgressEpisode(progressEpisode: String): Boolean {
    val current = trim()
    val saved = progressEpisode.trim()
    if (current == saved) return true
    val currentNumber = current.replace(',', '.').toDoubleOrNull()
    val savedNumber = saved.replace(',', '.').toDoubleOrNull()
    return currentNumber != null && savedNumber != null && currentNumber == savedNumber
}

// DetailsScreenRuntime
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
