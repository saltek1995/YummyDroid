package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant

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
