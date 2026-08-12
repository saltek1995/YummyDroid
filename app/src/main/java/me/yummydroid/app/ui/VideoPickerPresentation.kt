package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.siteDefaultVoiceKey

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
