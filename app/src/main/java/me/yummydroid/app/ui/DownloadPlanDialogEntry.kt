package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

@Composable
internal fun DownloadPlanDialog(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onConfirm: (DownloadPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    DownloadPlanDialogRuntime(
        animeId = animeId,
        animeTitle = animeTitle,
        videos = videos,
        selectedVideo = selectedVideo,
        selected = selected,
        onResolveSampledQualities = onResolveSampledQualities,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
