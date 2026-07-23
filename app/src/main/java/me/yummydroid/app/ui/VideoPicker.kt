package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val EpisodeGridHorizontalPadding = 24.dp
private val EpisodeGridGap = 10.dp
private const val EpisodeGridCollapsedRows = 4
private const val EpisodeProgressMinVisibleFraction = 0.08f
private val EpisodeActionButtonSize = 32.dp
private val EpisodeActionIconSize = 18.dp

@Composable
internal fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    playbackHistory: List<PlaybackProgress> = emptyList(),
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
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
    var episodePage by remember(selectedKey, displayVideos.size) { mutableIntStateOf(0) }
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EpisodeGridHorizontalPadding),
        ) {
            val columns = episodeGridColumns(maxWidth)
            val pageSize = visualGridPageSize(columns, EpisodeGridCollapsedRows)
            val pageCount = visualGridPageCount(displayVideos.size, pageSize)
            val normalizedPage = episodePage.coerceIn(0, pageCount - 1)
            val pageStart = visualGridPageStart(normalizedPage, pageSize, displayVideos.size)
            val pageEnd = (pageStart + pageSize).coerceAtMost(displayVideos.size)
            val visibleVideos = displayVideos.subList(pageStart, pageEnd)
            val visibleRows = remember(visibleVideos, columns) { visibleVideos.chunked(columns) }

            LaunchedEffect(normalizedPage, episodePage) {
                if (episodePage != normalizedPage) {
                    episodePage = normalizedPage
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(EpisodeGridGap),
            ) {
                visibleRows.forEachIndexed { rowIndex, rowVideos ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(EpisodeGridGap),
                    ) {
                        rowVideos.forEachIndexed { columnIndex, video ->
                            key("episode-grid:$normalizedPage:$rowIndex:$columnIndex:${video.id}:${video.groupKey}:${video.episode}") {
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
                                    onClick = {
                                        if (enabled) {
                                            val resumePositionMs = watchProgress?.safeResumePositionMs()
                                            if (resumePositionMs != null) {
                                                onPlayVideoWithResumeChoice(video, resumePositionMs)
                                            } else {
                                                onPlayVideo(video)
                                            }
                                        }
                                    },
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
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(columns - rowVideos.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (pageCount > 1) {
                    EpisodePagerControls(
                        page = normalizedPage,
                        pageCount = pageCount,
                        start = pageStart + 1,
                        end = pageEnd,
                        total = displayVideos.size,
                        onPrevious = { episodePage = (normalizedPage - 1).coerceAtLeast(0) },
                        onNext = { episodePage = (normalizedPage + 1).coerceAtMost(pageCount - 1) },
                    )
                }
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

private fun episodeGridColumns(width: Dp): Int = when {
    width >= 1120.dp -> 5
    width >= 820.dp -> 4
    width >= 580.dp -> 3
    width >= 360.dp -> 2
    else -> 1
}

@Composable
private fun EpisodePagerControls(
    page: Int,
    pageCount: Int,
    start: Int,
    end: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EpisodeGridGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page > 0) {
            DialogActionButton(
                text = uiText("Предыдущие"),
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "$start-$end ${uiText("из")} $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (page < pageCount - 1) {
            DialogActionButton(
                text = uiText("Следующие"),
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
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
    val progressFraction = watchProgress?.watchProgressFraction() ?: 0f
    val shape = YummyRadii.smallShape
    Surface(
        shape = shape,
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(YummySizes.episodeHeight)
            .dpadClickable(shape, enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .background(YummyColors.watched.copy(alpha = 0.26f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .graphicsLayer { alpha = contentAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                            .padding(9.dp)
                            .size(20.dp),
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
                            style = MaterialTheme.typography.titleSmall,
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
                }

                if (canDownload || downloadedVariants.isNotEmpty()) {
                    Column(
                        modifier = Modifier.width(EpisodeActionButtonSize),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs, Alignment.CenterVertically),
                    ) {
                        if (canDownload) {
                            IconButton(
                                onClick = onDownloadClick,
                                enabled = canDownload,
                                modifier = Modifier
                                    .size(EpisodeActionButtonSize)
                                    .focusRing(shape),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = uiText("Скачать серию"),
                                    modifier = Modifier.size(EpisodeActionIconSize),
                                )
                            }
                        }
                        if (downloadedVariants.isNotEmpty()) {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier
                                    .size(EpisodeActionButtonSize)
                                    .focusRing(shape),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = uiText("Удалить скачанную серию"),
                                    modifier = Modifier.size(EpisodeActionIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

private fun PlaybackProgress.watchProgressFraction(): Float {
    if (positionMs <= 0L) return 0f
    val duration = durationMs.takeIf { it > 0L } ?: return EpisodeProgressMinVisibleFraction
    return (positionMs.toFloat() / duration.toFloat())
        .coerceIn(EpisodeProgressMinVisibleFraction, 1f)
}

private fun PlaybackProgress.safeResumePositionMs(): Long? {
    val knownDurationMs = durationMs.takeIf { it > 0L }
    val safePositionMs = if (knownDurationMs != null) {
        positionMs.coerceIn(0L, (knownDurationMs - 5_000L).coerceAtLeast(0L))
    } else {
        positionMs.coerceAtLeast(0L)
    }
    return safePositionMs.takeIf { it > 0L }
}
