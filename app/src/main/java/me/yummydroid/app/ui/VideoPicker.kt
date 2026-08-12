package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadEpisodeCandidates
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.siteDefaultVoiceKey

// VideoPickerGrid
@Composable
internal fun EpisodeGrid(
    allVideos: List<VideoVariant>,
    displayVideos: List<VideoVariant>,
    episodeViewsByKey: Map<String, Long>,
    playbackHistory: List<PlaybackProgress>,
    stateResetKey: String,
    forcedOfflineMode: Boolean,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    var episodePage by remember(stateResetKey, displayVideos.size) { mutableIntStateOf(0) }
    var pendingFocusIndex by remember(stateResetKey, displayVideos.size) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EpisodeGridHorizontalPadding),
    ) {
        val layout = episodeGridLayout(
            width = maxWidth,
            itemCount = displayVideos.size,
            requestedPage = episodePage,
        )
        val visibleItemCount = layout.pageEnd - layout.pageStart
        val localFocusRequesters = remember(layout.normalizedPage, visibleItemCount) {
            List(visibleItemCount) { FocusRequester() }
        }
        val pagerState = rememberPagerState(
            initialPage = layout.normalizedPage,
            pageCount = { layout.pageCount },
        )

        fun focusRequesterAt(localIndex: Int): FocusRequester? {
            if (localIndex !in 0 until visibleItemCount) return null
            focusGridState?.requester(focusIndexOffset + localIndex)?.let { return it }
            if (localIndex == 0 && entryFocusRequester != null) return entryFocusRequester
            return localFocusRequesters.getOrNull(localIndex)
        }

        fun changePage(targetPage: Int, targetLocalIndex: Int? = null): Boolean {
            if (targetPage !in 0 until layout.pageCount || targetPage == layout.normalizedPage) return false
            val targetCount = layout.itemCount(targetPage, displayVideos.size)
            pendingFocusIndex = targetLocalIndex
                ?.takeIf { targetCount > 0 }
                ?.coerceIn(0, targetCount - 1)
            episodePage = targetPage
            return true
        }

        val navigator = EpisodeGridNavigator(
            layout = layout,
            totalItemCount = displayVideos.size,
            visibleItemCount = visibleItemCount,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusRequesterAt = ::focusRequesterAt,
            onChangePage = ::changePage,
        )
        EpisodeGridEffects(
            requestedPage = episodePage,
            layout = layout,
            pagerState = pagerState,
            pendingFocusIndex = pendingFocusIndex,
            visibleItemCount = visibleItemCount,
            navigator = navigator,
            onRequestedPageChange = { page -> episodePage = page },
            onPagerSettled = { page ->
                pendingFocusIndex = null
                episodePage = page
            },
            onPendingFocusHandled = { pendingFocusIndex = null },
        )
        EpisodeGridPager(
            allVideos = allVideos,
            displayVideos = displayVideos,
            playbackHistory = playbackHistory,
            episodeViewsByKey = episodeViewsByKey,
            pagerState = pagerState,
            layout = layout,
            focusBinding = EpisodeGridFocusBinding(
                focusGridState = focusGridState,
                focusIndexOffset = focusIndexOffset,
                focusBlockKey = focusBlockKey,
                requesterAt = ::focusRequesterAt,
                onDirection = navigator::handleDirection,
            ),
            playbackBinding = EpisodePlaybackBinding(
                forcedOfflineMode = forcedOfflineMode,
                onPlayVideo = onPlayVideo,
                onPlayVideoWithResumeChoice = onPlayVideoWithResumeChoice,
            ),
            onPageSelected = { page -> episodePage = page },
        )
    }
}

// VideoPickerGridContent
internal data class EpisodeGridFocusBinding(
    val focusGridState: VisualFocusGridState?,
    val focusIndexOffset: Int,
    val focusBlockKey: Any?,
    val requesterAt: (Int) -> FocusRequester?,
    val onDirection: (localIndex: Int, key: Key) -> Boolean,
)

internal data class EpisodePlaybackBinding(
    val forcedOfflineMode: Boolean,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
) {
    fun play(video: VideoVariant, watchProgress: PlaybackProgress?, enabled: Boolean) {
        if (!enabled) return
        val resumePositionMs = watchProgress?.safeResumePositionMs()
        if (resumePositionMs != null) {
            onPlayVideoWithResumeChoice(video, resumePositionMs)
        } else {
            onPlayVideo(video)
        }
    }
}

@Composable
internal fun EpisodeGridPager(
    allVideos: List<VideoVariant>,
    displayVideos: List<VideoVariant>,
    playbackHistory: List<PlaybackProgress>,
    episodeViewsByKey: Map<String, Long>,
    pagerState: PagerState,
    layout: EpisodeGridLayout,
    focusBinding: EpisodeGridFocusBinding,
    playbackBinding: EpisodePlaybackBinding,
    onPageSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(focusBinding.requesterAt(0)),
        verticalArrangement = Arrangement.spacedBy(EpisodeGridGap),
    ) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = EpisodeGridGap,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.pageContentHeight),
        ) { page ->
            EpisodeGridPage(
                page = page,
                allVideos = allVideos,
                displayVideos = displayVideos,
                playbackHistory = playbackHistory,
                episodeViewsByKey = episodeViewsByKey,
                layout = layout,
                focusBinding = focusBinding,
                playbackBinding = playbackBinding,
            )
        }
        if (layout.pageCount > 1) {
            EpisodePagerControls(
                page = layout.normalizedPage,
                pageCount = layout.pageCount,
                start = layout.pageStart + 1,
                end = layout.pageEnd,
                total = displayVideos.size,
                onPrevious = { onPageSelected((layout.normalizedPage - 1).coerceAtLeast(0)) },
                onNext = { onPageSelected((layout.normalizedPage + 1).coerceAtMost(layout.pageCount - 1)) },
            )
        }
    }
}

@Composable
private fun EpisodeGridPage(
    page: Int,
    allVideos: List<VideoVariant>,
    displayVideos: List<VideoVariant>,
    playbackHistory: List<PlaybackProgress>,
    episodeViewsByKey: Map<String, Long>,
    layout: EpisodeGridLayout,
    focusBinding: EpisodeGridFocusBinding,
    playbackBinding: EpisodePlaybackBinding,
) {
    val pageStart = visualGridPageStart(page, layout.pageSize, displayVideos.size)
    val pageEnd = (pageStart + layout.pageSize).coerceAtMost(displayVideos.size)
    val pageVideos = displayVideos.subList(pageStart, pageEnd)
    val pageRows = remember(pageVideos, layout.columns) { pageVideos.chunked(layout.columns) }
    val activePage = page == layout.normalizedPage
    val activeItemCount = layout.pageEnd - layout.pageStart

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
                    val localIndex = rowIndex * layout.columns + columnIndex
                    key("episode-grid:$page:$rowIndex:$columnIndex:${video.id}:${video.groupKey}:${video.episode}") {
                        EpisodeGridCard(
                            video = video,
                            localIndex = localIndex,
                            activePage = activePage,
                            activeItemCount = activeItemCount,
                            allVideos = allVideos,
                            playbackHistory = playbackHistory,
                            episodeViews = episodeViewsByKey[video.matchingEpisodeKey] ?: video.views,
                            compact = layout.compactCards,
                            focusBinding = focusBinding,
                            playbackBinding = playbackBinding,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(layout.columns - rowVideos.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EpisodeGridCard(
    video: VideoVariant,
    localIndex: Int,
    activePage: Boolean,
    activeItemCount: Int,
    allVideos: List<VideoVariant>,
    playbackHistory: List<PlaybackProgress>,
    episodeViews: Long,
    compact: Boolean,
    focusBinding: EpisodeGridFocusBinding,
    playbackBinding: EpisodePlaybackBinding,
    modifier: Modifier = Modifier,
) {
    val canUseActivePageFocus = visualGridActivePageLocalIndex(
        activePage = activePage,
        localIndex = localIndex,
        activeTotal = activeItemCount,
    )
    val enabled = !playbackBinding.forcedOfflineMode || video.isOfflineAvailable
    val downloadedVariants = allVideos.downloadEpisodeCandidates(video).filter(VideoVariant::isOfflineAvailable)
    val watchProgress = remember(playbackHistory, video.id, video.episode) {
        playbackHistory.progressFor(video)
    }
    val focusModifier = episodeCardFocusModifier(
        video = video,
        localIndex = localIndex,
        canUseActivePageFocus = canUseActivePageFocus,
        focusBinding = focusBinding,
    )
    EpisodeCard(
        video = video,
        episodeViews = episodeViews,
        watchProgress = watchProgress,
        downloadedVariants = downloadedVariants,
        enabled = enabled,
        onClick = { playbackBinding.play(video, watchProgress, enabled) },
        compact = compact,
        modifier = modifier.then(focusModifier),
    )
}

@Composable
private fun episodeCardFocusModifier(
    video: VideoVariant,
    localIndex: Int,
    canUseActivePageFocus: Boolean,
    focusBinding: EpisodeGridFocusBinding,
): Modifier {
    val focusRequester = focusBinding.requesterAt(localIndex)
        ?.takeIf { canUseActivePageFocus }
        ?: return Modifier
    var episodeCardFocused by remember(video.id, video.groupKey, video.episode) {
        mutableStateOf(false)
    }
    val focusModifier = if (focusBinding.focusGridState != null) {
        Modifier.visualFocusGridItem(
            state = focusBinding.focusGridState,
            index = focusBinding.focusIndexOffset + localIndex,
            horizontal = false,
            vertical = false,
            blockKey = focusBinding.focusBlockKey,
            blockEntryIndex = focusBinding.focusIndexOffset,
        )
    } else {
        Modifier.focusRequester(focusRequester)
    }
    return focusModifier
        .onPreviewKeyEvent { event ->
            episodeCardFocused &&
                event.type == KeyEventType.KeyDown &&
                focusBinding.onDirection(localIndex, event.key)
        }
        .onFocusChanged { focusState ->
            episodeCardFocused = focusState.isFocused
        }
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

// VideoPickerGridFocus
internal class EpisodeGridNavigator(
    private val layout: EpisodeGridLayout,
    private val totalItemCount: Int,
    private val visibleItemCount: Int,
    private val focusGridState: VisualFocusGridState?,
    private val focusIndexOffset: Int,
    private val focusRequesterAt: (Int) -> FocusRequester?,
    private val onChangePage: (page: Int, targetLocalIndex: Int?) -> Boolean,
) {
    fun requestFocus(localIndex: Int): Boolean {
        return focusRequesterAt(localIndex)?.requestFocusSafely() ?: false
    }

    fun handleDirection(localIndex: Int, key: Key): Boolean {
        val direction = key.visualGridDirection() ?: return false
        val target = visualGridMoveTarget(
            index = localIndex,
            total = visibleItemCount,
            columns = layout.columns,
            direction = direction,
        )
        if (target != null) {
            return requestFocus(target)
        }
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> changePageFromEdge(localIndex, direction)
            VisualGridDirection.Up,
            VisualGridDirection.Down -> focusGridState?.requestFocusTarget(
                index = focusIndexOffset + localIndex,
                direction = direction,
                exit = null,
            ) ?: false
        }
    }

    private fun changePageFromEdge(
        localIndex: Int,
        direction: VisualGridDirection,
    ): Boolean {
        val targetPage = layout.normalizedPage + if (direction == VisualGridDirection.Right) 1 else -1
        if (targetPage !in 0 until layout.pageCount) return false
        val targetLocalIndex = visualGridHorizontalPageTarget(
            sourceLocalIndex = localIndex,
            sourceTotal = visibleItemCount,
            targetTotal = layout.itemCount(targetPage, total = totalItemCount),
            columns = layout.columns,
            direction = direction,
        )
        return onChangePage(targetPage, targetLocalIndex)
    }
}

@Composable
internal fun EpisodeGridEffects(
    requestedPage: Int,
    layout: EpisodeGridLayout,
    pagerState: PagerState,
    pendingFocusIndex: Int?,
    visibleItemCount: Int,
    navigator: EpisodeGridNavigator,
    onRequestedPageChange: (Int) -> Unit,
    onPagerSettled: (Int) -> Unit,
    onPendingFocusHandled: () -> Unit,
) {
    val latestRequestedPage by rememberUpdatedState(requestedPage)

    LaunchedEffect(layout.normalizedPage, requestedPage) {
        if (requestedPage != layout.normalizedPage) {
            onRequestedPageChange(layout.normalizedPage)
        }
    }
    val needsPageAlignment = pagerState.currentPage != layout.normalizedPage
    val needsFocusRestore = pendingFocusIndex != null
    UiControlEffect(
        layout.normalizedPage,
        layout.pageCount,
        pendingFocusIndex,
        visibleItemCount,
        enabled = needsPageAlignment || needsFocusRestore,
    ) {
        if (pagerState.currentPage != layout.normalizedPage) {
            pagerState.animateScrollToPage(layout.normalizedPage)
        }
        val targetIndex = pendingFocusIndex ?: return@UiControlEffect
        repeat(6) {
            withFrameNanos { }
            if (navigator.requestFocus(targetIndex)) {
                onPendingFocusHandled()
                return@UiControlEffect
            }
        }
        onPendingFocusHandled()
    }
    LaunchedEffect(pagerState, layout.pageCount) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, layout.pageCount - 1) }
            .distinctUntilChanged()
            .collect { page ->
                if (page != latestRequestedPage) {
                    onPagerSettled(page)
                }
            }
    }
}

private fun Key.visualGridDirection(): VisualGridDirection? = when (this) {
    Key.DirectionLeft -> VisualGridDirection.Left
    Key.DirectionRight -> VisualGridDirection.Right
    Key.DirectionUp -> VisualGridDirection.Up
    Key.DirectionDown -> VisualGridDirection.Down
    else -> null
}

// VideoPickerGridLayout
internal val EpisodeGridHorizontalPadding = 24.dp
internal val EpisodeGridGap = 8.dp
internal val EpisodeCardDefaultHeight = 58.dp
internal val EpisodeCardCompactHeight = 56.dp

private val EpisodeCardMinWidth = 148.dp
private const val EpisodeGridCollapsedRows = 4

internal data class EpisodeGridLayout(
    val columns: Int,
    val compactCards: Boolean,
    val cardHeight: Dp,
    val pageSize: Int,
    val pageCount: Int,
    val normalizedPage: Int,
    val pageStart: Int,
    val pageEnd: Int,
    val pageContentHeight: Dp,
) {
    fun itemCount(page: Int, total: Int): Int {
        val start = visualGridPageStart(page, pageSize, total)
        return (total - start).coerceIn(0, pageSize)
    }
}

internal fun episodeGridLayout(
    width: Dp,
    itemCount: Int,
    requestedPage: Int,
): EpisodeGridLayout {
    val columns = episodeGridColumns(width)
    val estimatedCardWidth = (width - EpisodeGridGap * (columns - 1).coerceAtLeast(0).toFloat()) /
        columns.toFloat()
    val compactCards = estimatedCardWidth < 190.dp
    val cardHeight = if (compactCards) EpisodeCardCompactHeight else EpisodeCardDefaultHeight
    val pageSize = visualGridPageSize(columns, EpisodeGridCollapsedRows)
    val pageCount = visualGridPageCount(itemCount, pageSize)
    val normalizedPage = requestedPage.coerceIn(0, pageCount - 1)
    val pageStart = visualGridPageStart(normalizedPage, pageSize, itemCount)
    val pageEnd = (pageStart + pageSize).coerceAtMost(itemCount)
    val totalRows = ((itemCount + columns - 1) / columns).coerceAtLeast(1)
    val pageRows = if (pageCount > 1) EpisodeGridCollapsedRows else totalRows
    val pageContentHeight = cardHeight * pageRows.toFloat() +
        EpisodeGridGap * (pageRows - 1).coerceAtLeast(0).toFloat()

    return EpisodeGridLayout(
        columns = columns,
        compactCards = compactCards,
        cardHeight = cardHeight,
        pageSize = pageSize,
        pageCount = pageCount,
        normalizedPage = normalizedPage,
        pageStart = pageStart,
        pageEnd = pageEnd,
        pageContentHeight = pageContentHeight,
    )
}

internal fun episodeGridColumns(width: Dp): Int {
    val columns = ((width.value + EpisodeGridGap.value) / (EpisodeCardMinWidth.value + EpisodeGridGap.value))
        .toInt()
    return columns.coerceIn(1, 5)
}

// VideoPickerPresentation
private const val EpisodeProgressMinVisibleFraction = 0.08f

internal data class VideoPickerPresentation(
    val selectedSourceKey: String?,
    val selectedVoiceKey: String,
    val displayVideos: List<VideoVariant>,
    val episodeViewsByKey: Map<String, Long>,
)

internal fun buildVideoPickerPresentation(
    videos: List<VideoVariant>,
    selectedGroup: String?,
): VideoPickerPresentation {
    require(videos.isNotEmpty())

    val voiceGroups = videos.groupBy(VideoVariant::downloadPlanVoiceKey)
    val selectedSourceKey = selectedGroup
        ?.takeIf { groupKey -> videos.any { video -> video.groupKey == groupKey } }
    val selectedVoiceKey = videos.matchingVoiceKeyForGroup(selectedSourceKey)
        ?: selectedGroup?.takeIf(voiceGroups::containsKey)
        ?: videos.siteDefaultVoiceKey()
        ?: voiceGroups.keys.first()
    val displayVideos = videos.sortedForPlayer(selectedSourceKey, selectedVoiceKey)
    val episodeViewsByKey = videos
        .distinctBy(VideoVariant::id)
        .groupBy(VideoVariant::matchingEpisodeKey)
        .mapValues { (_, episodeVideos) -> episodeVideos.sumOf(VideoVariant::views) }

    return VideoPickerPresentation(
        selectedSourceKey = selectedSourceKey,
        selectedVoiceKey = selectedVoiceKey,
        displayVideos = displayVideos,
        episodeViewsByKey = episodeViewsByKey,
    )
}

internal fun PlaybackProgress.watchProgressFraction(): Float {
    if (positionMs <= 0L) return 0f
    val duration = durationMs.takeIf { it > 0L } ?: return EpisodeProgressMinVisibleFraction
    return (positionMs.toFloat() / duration.toFloat())
        .coerceIn(EpisodeProgressMinVisibleFraction, 1f)
}

internal fun PlaybackProgress.safeResumePositionMs(): Long? {
    val knownDurationMs = durationMs.takeIf { it > 0L }
    val safePositionMs = if (knownDurationMs != null) {
        positionMs.coerceIn(0L, (knownDurationMs - 5_000L).coerceAtLeast(0L))
    } else {
        positionMs.coerceAtLeast(0L)
    }
    return safePositionMs.takeIf { it > 0L }
}

// VideoPickerRuntime
@Composable
internal fun VideoPickerModern(
    videos: List<VideoVariant>,
    selectedGroup: String?,
    modifier: Modifier = Modifier,
    playbackHistory: List<PlaybackProgress> = emptyList(),
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    forcedOfflineMode: Boolean,
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

    val presentation = remember(videos, selectedGroup) {
        buildVideoPickerPresentation(videos, selectedGroup)
    }
    Column(
        modifier = modifier.padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EpisodeGrid(
            allVideos = videos,
            displayVideos = presentation.displayVideos,
            episodeViewsByKey = presentation.episodeViewsByKey,
            playbackHistory = playbackHistory,
            stateResetKey = presentation.selectedVoiceKey,
            forcedOfflineMode = forcedOfflineMode,
            onPlayVideo = onPlayVideo,
            onPlayVideoWithResumeChoice = onPlayVideoWithResumeChoice,
            entryFocusRequester = entryFocusRequester,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusBlockKey = focusBlockKey,
        )
    }
}
