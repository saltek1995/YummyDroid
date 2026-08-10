package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import me.yummydroid.app.data.downloadEpisodeCandidates
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant

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
