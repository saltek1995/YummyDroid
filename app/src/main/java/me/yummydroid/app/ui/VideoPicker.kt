package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant

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
