package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.readyListOrEmpty

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
    val resumeTarget = remember(playableVideos, model.playbackProgress) {
        model.playbackProgress.resolveResumeTarget(playableVideos)
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
