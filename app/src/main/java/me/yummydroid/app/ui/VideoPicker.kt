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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.siteDefaultVoiceKey
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatViews
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

private val EpisodeGridHorizontalPadding = 24.dp
private val EpisodeGridGap = 8.dp
private val EpisodeCardMinWidth = 148.dp
private val EpisodeCardDefaultHeight = 58.dp
private val EpisodeCardCompactHeight = 56.dp
private const val EpisodeGridCollapsedRows = 4
private const val EpisodeProgressMinVisibleFraction = 0.08f

@Composable
internal fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    playbackHistory: List<PlaybackProgress> = emptyList(),
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    forcedOfflineMode: Boolean,
    modifier: Modifier = Modifier,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (videos.isEmpty()) {
        EmptyPane(
            message = uiText(UiStringKey.NoVideosForThisAnimeYet),
            modifier = modifier.heightIn(min = 180.dp),
        )
        return
    }

    val voiceGroups = remember(videos) { videos.groupBy { it.downloadPlanVoiceKey } }
    val selectedSourceKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
    val selectedVoiceKey = remember(videos, selectedGroup, selectedSourceKey, voiceGroups) {
        videos.matchingVoiceKeyForGroup(selectedSourceKey)
            ?: selectedGroup?.takeIf { key -> key in voiceGroups }
            ?: videos.siteDefaultVoiceKey()
            ?: voiceGroups.keys.first()
    }
    val displayVideos = remember(videos, selectedSourceKey, selectedVoiceKey) {
        videos.sortedForPlayer(selectedSourceKey, selectedVoiceKey)
    }
    val episodeViewsByKey = remember(videos) {
        videos
            .distinctBy { it.id }
            .groupBy { it.matchingEpisodeKey }
            .mapValues { (_, episodeVideos) -> episodeVideos.sumOf { it.views } }
    }
    var episodePage by remember(selectedVoiceKey, displayVideos.size) { mutableIntStateOf(0) }
    var pendingEpisodeFocusLocalIndex by remember(selectedVoiceKey, displayVideos.size) { mutableStateOf<Int?>(null) }

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
            val estimatedCardWidth = (maxWidth - EpisodeGridGap * (columns - 1).coerceAtLeast(0).toFloat()) /
                columns.toFloat()
            val compactEpisodeCards = estimatedCardWidth < 190.dp
            val episodeCardHeight = if (compactEpisodeCards) EpisodeCardCompactHeight else EpisodeCardDefaultHeight
            val pageSize = visualGridPageSize(columns, EpisodeGridCollapsedRows)
            val pageCount = visualGridPageCount(displayVideos.size, pageSize)
            val normalizedPage = episodePage.coerceIn(0, pageCount - 1)
            val pageStart = visualGridPageStart(normalizedPage, pageSize, displayVideos.size)
            val pageEnd = (pageStart + pageSize).coerceAtMost(displayVideos.size)
            val visibleVideos = displayVideos.subList(pageStart, pageEnd)
            val totalRows = ((displayVideos.size + columns - 1) / columns).coerceAtLeast(1)
            val pageRows = if (pageCount > 1) EpisodeGridCollapsedRows else totalRows
            val pageContentHeight = episodeCardHeight * pageRows.toFloat() +
                EpisodeGridGap * (pageRows - 1).coerceAtLeast(0).toFloat()
            val episodeFocusRequesters = remember(normalizedPage, visibleVideos.size) {
                List(visibleVideos.size) { FocusRequester() }
            }
            val episodePagerState = rememberPagerState(
                initialPage = normalizedPage,
                pageCount = { pageCount },
            )
            val latestEpisodePage by rememberUpdatedState(episodePage)

            fun episodeFocusRequester(localIndex: Int): FocusRequester? {
                if (localIndex !in visibleVideos.indices) return null
                focusGridState?.requester(focusIndexOffset + localIndex)?.let { return it }
                if (localIndex == 0 && entryFocusRequester != null) return entryFocusRequester
                return episodeFocusRequesters.getOrNull(localIndex)
            }

            fun pageItemCount(page: Int): Int {
                val start = visualGridPageStart(page, pageSize, displayVideos.size)
                return (displayVideos.size - start).coerceIn(0, pageSize)
            }

            fun changeEpisodePage(targetPage: Int, targetLocalIndex: Int? = null): Boolean {
                if (targetPage !in 0 until pageCount || targetPage == normalizedPage) return false
                val targetCount = pageItemCount(targetPage)
                pendingEpisodeFocusLocalIndex = targetLocalIndex
                    ?.takeIf { targetCount > 0 }
                    ?.coerceIn(0, targetCount - 1)
                episodePage = targetPage
                return true
            }

            fun changeEpisodePageFromEdge(localIndex: Int, direction: VisualGridDirection): Boolean {
                val targetPage = normalizedPage + if (direction == VisualGridDirection.Right) 1 else -1
                if (targetPage !in 0 until pageCount) return true
                val targetLocalIndex = visualGridHorizontalPageTarget(
                    sourceLocalIndex = localIndex,
                    sourceTotal = visibleVideos.size,
                    targetTotal = pageItemCount(targetPage),
                    columns = columns,
                    direction = direction,
                )
                changeEpisodePage(targetPage, targetLocalIndex)
                return true
            }

            fun requestEpisodeFocus(localIndex: Int): Boolean {
                val requester = episodeFocusRequester(localIndex) ?: return false
                return runCatching { requester.requestFocus() }.getOrDefault(false)
            }

            fun handleEpisodeGridDirection(localIndex: Int, key: Key): Boolean {
                val direction = when (key) {
                    Key.DirectionLeft -> VisualGridDirection.Left
                    Key.DirectionRight -> VisualGridDirection.Right
                    Key.DirectionUp -> VisualGridDirection.Up
                    Key.DirectionDown -> VisualGridDirection.Down
                    else -> return false
                }
                if (
                    focusGridState?.requestFocusTarget(
                        index = focusIndexOffset + localIndex,
                        direction = direction,
                        exit = null,
                        cancelWhenMissing = false,
                    ) == true
                ) {
                    return true
                }
                val target = visualGridMoveTarget(
                    index = localIndex,
                    total = visibleVideos.size,
                    columns = columns,
                    direction = direction,
                )
                if (target != null) {
                    return requestEpisodeFocus(target)
                }
                return when (direction) {
                    VisualGridDirection.Left,
                    VisualGridDirection.Right -> changeEpisodePageFromEdge(localIndex, direction)
                    VisualGridDirection.Up,
                    VisualGridDirection.Down -> focusGridState?.requestFocusTarget(
                        index = focusIndexOffset + localIndex,
                        direction = direction,
                        exit = null,
                        cancelWhenMissing = false,
                    ) ?: false
                }
            }

            LaunchedEffect(normalizedPage, episodePage) {
                if (episodePage != normalizedPage) {
                    episodePage = normalizedPage
                }
            }

            LaunchedEffect(normalizedPage, pageCount) {
                if (episodePagerState.currentPage != normalizedPage) {
                    episodePagerState.animateScrollToPage(normalizedPage)
                }
            }

            LaunchedEffect(episodePagerState, pageCount) {
                snapshotFlow { episodePagerState.settledPage.coerceIn(0, pageCount - 1) }
                    .distinctUntilChanged()
                    .collect { page ->
                        if (page != latestEpisodePage) {
                            pendingEpisodeFocusLocalIndex = null
                            episodePage = page
                        }
                    }
            }

            LaunchedEffect(normalizedPage, pendingEpisodeFocusLocalIndex, visibleVideos.size) {
                val targetIndex = pendingEpisodeFocusLocalIndex ?: return@LaunchedEffect
                repeat(6) {
                    withFrameNanos { }
                    if (requestEpisodeFocus(targetIndex)) {
                        pendingEpisodeFocusLocalIndex = null
                        return@LaunchedEffect
                    }
                }
                pendingEpisodeFocusLocalIndex = null
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusEntryGroup(episodeFocusRequester(0)),
                verticalArrangement = Arrangement.spacedBy(EpisodeGridGap),
            ) {
                HorizontalPager(
                    state = episodePagerState,
                    beyondViewportPageCount = 1,
                    pageSpacing = EpisodeGridGap,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageContentHeight),
                ) { page ->
                    val pagerPageStart = visualGridPageStart(page, pageSize, displayVideos.size)
                    val pagerPageEnd = (pagerPageStart + pageSize).coerceAtMost(displayVideos.size)
                    val pageVideos = displayVideos.subList(pagerPageStart, pagerPageEnd)
                    val pageRows = remember(pageVideos, columns) { pageVideos.chunked(columns) }
                    val activePage = page == normalizedPage
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = activePage }
                            .focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(EpisodeGridGap),
                    ) {
                        pageRows.forEachIndexed { rowIndex, rowVideos ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(EpisodeGridGap),
                            ) {
                                rowVideos.forEachIndexed { columnIndex, video ->
                                    val localIndex = rowIndex * columns + columnIndex
                                    key("episode-grid:$page:$rowIndex:$columnIndex:${video.id}:${video.groupKey}:${video.episode}") {
                                        val canUseActivePageFocus = visualGridActivePageLocalIndex(
                                            activePage = activePage,
                                            localIndex = localIndex,
                                            activeTotal = visibleVideos.size,
                                        )
                                        val enabled = !forcedOfflineMode || video.isOfflineAvailable
                                        val downloadedVariants = videos.downloadEpisodeCandidates(video).filter { it.isOfflineAvailable }
                                        val watchProgress = remember(playbackHistory, video.id, video.episode) {
                                            playbackHistory.progressFor(video)
                                        }
                                        val episodeFocusRequester = episodeFocusRequester(localIndex)
                                            ?.takeIf { canUseActivePageFocus }
                                        var episodeCardFocused by remember(video.id, video.groupKey, video.episode) {
                                            mutableStateOf(false)
                                        }
                                        EpisodeCard(
                                            video = video,
                                            episodeViews = episodeViewsByKey[video.matchingEpisodeKey] ?: video.views,
                                            watchProgress = watchProgress,
                                            downloadedVariants = downloadedVariants,
                                            enabled = enabled,
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
                                            compact = compactEpisodeCards,
                                            modifier = Modifier
                                                .weight(1f)
                                                .then(
                                                    if (canUseActivePageFocus && episodeFocusRequester != null) {
                                                        val focusModifier = if (focusGridState != null) {
                                                            Modifier.visualFocusGridItem(
                                                                state = focusGridState,
                                                                index = focusIndexOffset + localIndex,
                                                                horizontal = false,
                                                                vertical = false,
                                                                blockKey = focusBlockKey,
                                                                blockEntryIndex = focusIndexOffset,
                                                            )
                                                        } else {
                                                            Modifier.focusRequester(episodeFocusRequester)
                                                        }
                                                        focusModifier
                                                            .onPreviewKeyEvent { event ->
                                                                if (!episodeCardFocused) {
                                                                    return@onPreviewKeyEvent false
                                                                }
                                                                if (event.type != KeyEventType.KeyDown) {
                                                                    return@onPreviewKeyEvent false
                                                                }
                                                                handleEpisodeGridDirection(localIndex, event.key)
                                                            }
                                                            .onFocusChanged { focusState ->
                                                                episodeCardFocused = focusState.isFocused
                                                            }
                                                    } else {
                                                        Modifier
                                                    },
                                                ),
                                        )
                                    }
                                }
                                repeat(columns - rowVideos.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
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
}

private fun episodeGridColumns(width: Dp): Int {
    val columns = ((width.value + EpisodeGridGap.value) / (EpisodeCardMinWidth.value + EpisodeGridGap.value))
        .toInt()
    return columns.coerceIn(1, 5)
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
                text = uiText(UiStringKey.Previous),
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(
            text = "$start-$end ${uiText(UiStringKey.Of)} $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (page < pageCount - 1) {
            DialogActionButton(
                text = uiText(UiStringKey.Next6ff11d),
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

@Composable
internal fun EpisodeCard(
    video: VideoVariant,
    episodeViews: Long,
    modifier: Modifier = Modifier,
    watchProgress: PlaybackProgress? = null,
    downloadedVariants: List<VideoVariant> = if (video.isOfflineAvailable) listOf(video) else emptyList(),
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    val progressFraction = watchProgress?.watchProgressFraction() ?: 0f
    val shape = YummyRadii.smallShape
    val cardHeight = if (compact) EpisodeCardCompactHeight else EpisodeCardDefaultHeight
    Surface(
        shape = shape,
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
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
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .graphicsLayer { alpha = contentAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .padding(5.dp)
                            .size(14.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
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
                        if (downloadedVariants.isNotEmpty()) {
                            EpisodeOfflineBadge()
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
            }
        }
    }

}

@Composable
private fun EpisodeOfflineBadge() {
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
