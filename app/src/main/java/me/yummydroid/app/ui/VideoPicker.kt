package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatViews
import me.yummydroid.app.InputAction
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    playbackHistory: List<PlaybackProgress> = emptyList(),
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    forcedOfflineMode: Boolean,
    canDownload: Boolean,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) {
        EmptyPane(
            message = uiText("Видео для этого аниме пока нет"),
            modifier = modifier.heightIn(min = 180.dp),
        )
        return
    }

    val configuration = LocalConfiguration.current
    val cardWidth = when {
        configuration.screenWidthDp >= 1180 -> 330.dp
        configuration.screenWidthDp >= 760 -> 300.dp
        configuration.screenWidthDp >= 560 -> 280.dp
        else -> 300.dp
    }
    val groups = videos.groupBy { it.groupKey }
    val selectedKey = selectedGroup?.takeIf(groups::containsKey) ?: groups.keys.first()
    val displayVideos = remember(videos, selectedKey) {
        videos.sortedForPlayer(selectedKey)
    }
    val episodeViewsByKey = remember(videos) {
        videos
            .distinctBy { it.id }
            .groupBy { it.matchingEpisodeKey }
            .mapValues { (_, episodeVideos) -> episodeVideos.sumOf { it.views } }
    }
    var pendingDownloadVideo by remember { mutableStateOf<VideoVariant?>(null) }
    var pendingDeleteVideo by remember { mutableStateOf<VideoVariant?>(null) }
    val pickerDialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                pendingDeleteVideo != null -> {
                    pendingDeleteVideo = null
                    true
                }
                pendingDownloadVideo != null -> {
                    pendingDownloadVideo = null
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(pendingDownloadVideo, pendingDeleteVideo, onRegisterModalInputActionHandler) {
        if (pendingDownloadVideo != null || pendingDeleteVideo != null) {
            onRegisterModalInputActionHandler { action -> pickerDialogInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

    Column(
        modifier = modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Просмотр"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lazyItemsIndexed(
                displayVideos,
                key = { index, video -> "episode-row:$index:${video.id}:${video.groupKey}:${video.episode}" },
            ) { index, video ->
                val enabled = !forcedOfflineMode || video.isOfflineAvailable
                val downloadedVariants = videos.downloadEpisodeCandidates(video).filter { it.isOfflineAvailable }
                val watchProgress = remember(playbackHistory, video.id, video.episode) {
                    playbackHistory.progressFor(video)
                }
                EpisodeCard(
                    video = video,
                    episodeViews = episodeViewsByKey[video.matchingEpisodeKey] ?: video.views,
                    watchProgress = watchProgress,
                    downloadedVariants = downloadedVariants,
                    enabled = enabled,
                    canDownload = canDownload,
                    onClick = { if (enabled) onPlayVideo(video) },
                    onDownloadClick = { pendingDownloadVideo = video },
                    onDeleteClick = {
                        val targets = downloadedVariants.offlineDeleteTargets()
                        if (targets.size <= 1) {
                            targets.firstOrNull()?.let {
                                onDeleteOfflineVideo(it.animeId, it.videoId, it.playbackUrl)
                            }
                        } else {
                            pendingDeleteVideo = video
                        }
                    },
                    modifier = Modifier
                        .width(cardWidth)
                        .stopHorizontalFocusEscape(index, displayVideos.size),
                )
            }
        }
    }

    pendingDownloadVideo?.let { video ->
        DownloadSelectionDialog(
            title = "${uiText("Скачать")} ${video.localizedEpisodeTitle()}",
            videos = videos.downloadEpisodeCandidates(video),
            selectedVideo = video,
            selected = defaultDownloadQuality,
            allEpisodes = false,
            onResolveQualities = onResolveDownloadQualities,
            confirmText = uiText("Скачать"),
            onConfirm = { selectedVideo, quality ->
                pendingDownloadVideo = null
                onDownloadVideo(selectedVideo, quality)
            },
            onDismiss = { pendingDownloadVideo = null },
        )
    }

    pendingDeleteVideo?.let { video ->
        EpisodeDeleteDialog(
            video = video,
            downloadedVariants = videos.downloadEpisodeCandidates(video).filter { it.isOfflineAvailable },
            onDelete = { targets ->
                pendingDeleteVideo = null
                targets.forEach { onDeleteOfflineVideo(it.animeId, it.videoId, it.playbackUrl) }
            },
            onDismiss = { pendingDeleteVideo = null },
        )
    }
}

@Composable
internal fun DetailsPoster(
    posterUrl: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun EpisodeCard(
    video: VideoVariant,
    episodeViews: Long,
    modifier: Modifier = Modifier,
    watchProgress: PlaybackProgress? = null,
    downloadedVariants: List<VideoVariant> = if (video.isOfflineAvailable) listOf(video) else emptyList(),
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    enabled: Boolean = true,
    canDownload: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    val watchedAtText = watchProgress?.watchedAtText()
    val shape = YummyRadii.smallShape
    Surface(
        shape = shape,
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(if (watchedAtText == null) YummySizes.episodeHeight else YummySizes.episodeWatchedHeight)
            .dpadClickable(shape, enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = YummySpacing.md, vertical = 10.dp)
                .graphicsLayer { alpha = contentAlpha },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Surface(
                shape = YummyRadii.pillShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(11.dp)
                        .size(YummySizes.episodePlayIcon),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                ) {
                    Text(
                        text = video.localizedEpisodeTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (video.isOfflineAvailable) {
                        Surface(
                            color = YummyColors.offline,
                            contentColor = Color.Black,
                            shape = YummyRadii.pillShape,
                        ) {
                            Text(
                                text = "OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(
                        formatDuration(video.durationSeconds),
                        formatViews(episodeViews),
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (watchedAtText != null) {
                    Text(
                        text = "\u2713 $watchedAtText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (canDownload || downloadedVariants.isNotEmpty()) {
                Column(
                    modifier = Modifier.width(YummySizes.compactIconButton),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs, Alignment.CenterVertically),
                ) {
                    if (canDownload) {
                        IconButton(
                            onClick = onDownloadClick,
                            enabled = canDownload,
                            modifier = Modifier
                                .size(YummySizes.compactIconButton)
                                .focusRing(shape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = uiText("Скачать серию"),
                                modifier = Modifier.size(YummySizes.actionIcon),
                            )
                        }
                    }
                    if (downloadedVariants.isNotEmpty()) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .size(YummySizes.compactIconButton)
                                .focusRing(shape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = uiText("Удалить скачанную серию"),
                                modifier = Modifier.size(YummySizes.actionIcon),
                            )
                        }
                    }
                }
            }
        }
    }

}
