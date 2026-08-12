package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.sourceSelectionKey
import me.yummydroid.app.ui.theme.YummySpacing

// PlayerScreenModel
internal data class RetainedReadyPlayback(
    val stream: ResolvedVideoStream,
    val video: VideoVariant,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
)

internal data class PlayerScreenPresentation(
    val playbackStream: ResolvedVideoStream?,
    val playbackVideo: VideoVariant,
    val playbackStartPositionMs: Long,
    val playbackPreferredQuality: PreferredQuality,
    val videos: List<VideoVariant>,
    val groups: Map<String, List<VideoVariant>>,
    val selectedVoiceKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val useRetainedPlayback: Boolean,
)

private data class PlayerPlaybackTarget(
    val stream: ResolvedVideoStream?,
    val video: VideoVariant,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
    val retained: Boolean,
)

private data class PlayerSubtitleSources(
    val sourceKeys: Set<String>,
    val selectionKeys: Set<String>,
)

internal fun buildPlayerScreenPresentation(
    video: VideoVariant,
    startPositionMs: Long,
    preferredQuality: PreferredQuality,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
    forcedOfflineMode: Boolean,
    sourceSubtitleLabel: String,
): PlayerScreenPresentation {
    val playback = resolvePlayerPlaybackTarget(
        requestedVideo = video,
        requestedStartPositionMs = startPositionMs,
        requestedQuality = preferredQuality,
        streamState = streamState,
        retainedReadyPlayback = retainedReadyPlayback,
        resumeChoicePositionMs = resumeChoicePositionMs,
    )
    val videos = resolvePlayerVideos(
        allVideos = allVideos,
        playbackVideo = playback.video,
        forcedOfflineMode = forcedOfflineMode,
    )
    val groups = videos.groupBy(VideoVariant::matchingVoiceKey)
    val selectedVoiceKey = resolvePlayerVoiceKey(videos, groups, selectedGroup, playback.video)
    val subtitleSources = resolvePlayerSubtitleSources(playback.stream, playback.video)
    val sourceOptions = resolvePlayerSourceOptions(
        videos = videos,
        playbackVideo = playback.video,
        selectedVoiceKey = selectedVoiceKey,
        subtitleSources = subtitleSources,
        sourceSubtitleLabel = sourceSubtitleLabel,
        forcedOfflineMode = forcedOfflineMode,
    )

    return PlayerScreenPresentation(
        playbackStream = playback.stream,
        playbackVideo = playback.video,
        playbackStartPositionMs = playback.startPositionMs,
        playbackPreferredQuality = playback.preferredQuality,
        videos = videos,
        groups = groups,
        selectedVoiceKey = selectedVoiceKey,
        sourceOptions = sourceOptions,
        selectedSourceKey = playback.video.sourceSelectionKey,
        previousVideo = findAdjacentPlayerVideo(
            currentVideo = playback.video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        ),
        nextVideo = findAdjacentPlayerVideo(
            currentVideo = playback.video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        ),
        useRetainedPlayback = playback.retained,
    )
}
private fun resolvePlayerPlaybackTarget(
    requestedVideo: VideoVariant,
    requestedStartPositionMs: Long,
    requestedQuality: PreferredQuality,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
): PlayerPlaybackTarget {
    val retained = shouldUseRetainedPlayback(
        requestedVideo = requestedVideo,
        streamState = streamState,
        retainedReadyPlayback = retainedReadyPlayback,
        resumeChoicePositionMs = resumeChoicePositionMs,
    )
    val readyStream = (streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    return PlayerPlaybackTarget(
        stream = readyStream ?: retainedReadyPlayback?.stream?.takeIf { retained },
        video = retainedReadyPlayback?.video?.takeIf { retained } ?: requestedVideo,
        startPositionMs = retainedReadyPlayback?.startPositionMs?.takeIf { retained }
            ?: requestedStartPositionMs,
        preferredQuality = retainedReadyPlayback?.preferredQuality?.takeIf { retained }
            ?: requestedQuality,
        retained = retained,
    )
}
private fun shouldUseRetainedPlayback(
    requestedVideo: VideoVariant,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
): Boolean {
    if (streamState != LoadState.Loading) return false
    if (resumeChoicePositionMs != null) return false
    return retainedReadyPlayback?.video?.animeId == requestedVideo.animeId
}

private fun resolvePlayerVideos(
    allVideos: List<VideoVariant>,
    playbackVideo: VideoVariant,
    forcedOfflineMode: Boolean,
): List<VideoVariant> {
    val sourceVideos = allVideos.ifEmpty { listOf(playbackVideo) }
    if (!forcedOfflineMode) return sourceVideos
    return sourceVideos.filter(VideoVariant::isOfflineAvailable)
        .ifEmpty { listOf(playbackVideo).filter(VideoVariant::isOfflineAvailable) }
}

private fun resolvePlayerVoiceKey(
    videos: List<VideoVariant>,
    groups: Map<String, List<VideoVariant>>,
    selectedGroup: String?,
    playbackVideo: VideoVariant,
): String? {
    val selectedVoice = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { candidate -> candidate.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
    return selectedVoice
        ?: playbackVideo.matchingVoiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
}

private fun resolvePlayerSubtitleSources(
    stream: ResolvedVideoStream?,
    playbackVideo: VideoVariant,
): PlayerSubtitleSources {
    if (stream == null) return PlayerSubtitleSources(emptySet(), emptySet())
    val sourceKey = playbackVideo.matchingSourceKey.takeIf {
        it.isNotBlank() && stream.hasResolvedSubtitles
    }
    val selectionKey = playbackVideo.sourceSelectionKey.takeIf {
        it.isNotBlank() && stream.hasResolvedSubtitles
    }
    return PlayerSubtitleSources(
        sourceKeys = stream.sourceSubtitleSourceKeys + listOfNotNull(sourceKey),
        selectionKeys = setOfNotNull(selectionKey),
    )
}

private fun resolvePlayerSourceOptions(
    videos: List<VideoVariant>,
    playbackVideo: VideoVariant,
    selectedVoiceKey: String?,
    subtitleSources: PlayerSubtitleSources,
    sourceSubtitleLabel: String,
    forcedOfflineMode: Boolean,
): List<SourceOption> {
    if (forcedOfflineMode) return emptyList()
    return videos.sourceOptionsFor(
        currentVideo = playbackVideo,
        selectedVoiceKey = selectedVoiceKey,
        sourceSubtitleSourceKeys = subtitleSources.sourceKeys,
        sourceSubtitleSelectionKeys = subtitleSources.selectionKeys,
        sourceSubtitleLabel = sourceSubtitleLabel,
    )
}

@Composable
internal fun PlayerResumeChoiceDialog(
    video: VideoVariant,
    positionMs: Long,
    onStartOver: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resumeTime = formatPlaybackTime(positionMs)
    val resumeFocusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    UiControlEffect(
        video.id,
        positionMs,
        inputModeManager.inputMode,
        enabled = inputModeManager.inputMode != InputMode.Touch,
    ) {
        withFrameNanos { }
        resumeFocusRequester.requestFocusSafely()
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ContinueWatching)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
            ) {
                Text(
                    text = video.localizedEpisodeTitle(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${uiText(UiStringKey.SavedPosition)}: $resumeTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                text = "${uiText(UiStringKey.Continue)} $resumeTime",
                primary = true,
                modifier = Modifier.focusRequester(resumeFocusRequester),
                onClick = onResume,
            )
        },
        dismissButton = {
            DialogActionButton(
                text = uiText(UiStringKey.FromStart),
                onClick = onStartOver,
            )
        },
    )
}

// PlayerScreenContent
internal data class PlayerScreenState(
    val animeTitle: String,
    val video: VideoVariant,
    val interactive: Boolean,
    val settings: AppSettings,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
    val allVideos: List<VideoVariant>,
    val selectedGroup: String?,
    val streamState: LoadState<ResolvedVideoStream>,
    val playbackMetadataLoading: Boolean,
    val resumeChoicePositionMs: Long?,
    val isInPictureInPicture: Boolean,
    val forcedOfflineMode: Boolean,
    val allowSubscriptions: Boolean,
    val subscriptions: List<VideoSubscription>,
    val canUsePictureInPicture: Boolean,
)

internal data class PlayerScreenActions(
    val onSelectGroup: (String) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    val onChooseResumePosition: (Long) -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onRetry: () -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val onEnterPictureInPicture: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
)

internal data class PlayerControlFocusBinding(
    val restoreId: Int?,
    val keepVisibleAfterReady: Boolean,
    val onRemember: (Int) -> Unit,
    val onRestored: () -> Unit,
    val onKeepVisibleRequested: () -> Unit,
    val onKeptVisible: () -> Unit,
)

@Composable
internal fun PlayerScreenContent(
    state: PlayerScreenState,
    presentation: PlayerScreenPresentation,
    resumeChoicePositionMs: Long?,
    actions: PlayerScreenActions,
    controlFocus: PlayerControlFocusBinding,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (presentation.playbackStream != null && resumeChoicePositionMs == null) {
            ReadyPlayerContent(
                state = state,
                presentation = presentation,
                actions = actions,
                controlFocus = controlFocus,
            )
        } else {
            ShellPlayerContent(
                state = state,
                presentation = presentation,
                actions = actions,
                controlFocus = controlFocus,
            )
        }

        if (state.interactive && resumeChoicePositionMs != null) {
            PlayerResumeChoiceDialog(
                video = state.video,
                positionMs = resumeChoicePositionMs,
                onStartOver = { actions.onChooseResumePosition(0L) },
                onResume = { actions.onChooseResumePosition(resumeChoicePositionMs) },
                onDismiss = actions.onBack,
            )
        }
    }
}
@Composable
private fun ReadyPlayerContent(
    state: PlayerScreenState,
    presentation: PlayerScreenPresentation,
    actions: PlayerScreenActions,
    controlFocus: PlayerControlFocusBinding,
) {
    val stream = presentation.playbackStream ?: return
    NativeVideoPlayer(
        stream = stream,
        animeTitle = state.animeTitle,
        currentVideo = presentation.playbackVideo,
        interactive = state.interactive,
        settings = state.settings,
        startPositionMs = presentation.playbackStartPositionMs,
        playbackPreferredQuality = presentation.playbackPreferredQuality,
        playbackMetadataLoading = state.playbackMetadataLoading,
        groups = presentation.groups,
        selectedKey = presentation.selectedVoiceKey,
        sourceOptions = presentation.sourceOptions,
        selectedSourceKey = presentation.selectedSourceKey,
        previousVideo = presentation.previousVideo,
        nextVideo = presentation.nextVideo,
        allowSubscription = state.allowSubscriptions,
        subscriptionActive = state.subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
        onToggleSubscription = { actions.onToggleVideoSubscription(presentation.playbackVideo) },
        onSelectGroup = { groupKey, replacement, positionMs ->
            selectPlayerGroup(actions, presentation, groupKey, replacement, positionMs)
        },
        onSelectSource = { source, positionMs ->
            actions.onSelectGroup(source.groupKey)
            actions.onSelectPlaybackSource(source, positionMs)
        },
        onPlayVideoAt = { next, positionMs ->
            actions.onSelectGroup(next.groupKey)
            actions.onPlayVideoAtQuality(next, positionMs, presentation.playbackPreferredQuality)
        },
        onPlayVideoAtQuality = { next, positionMs, quality ->
            actions.onSelectGroup(next.groupKey)
            actions.onPlayVideoAtQuality(next, positionMs, quality)
        },
        onPlaybackFailed = actions.onPlaybackFailed,
        onPlaybackStarted = actions.onPlaybackStarted,
        onPlaybackEnded = actions.onPlaybackEnded,
        onPlaybackProgress = actions.onPlaybackProgress,
        canUsePictureInPicture = state.canUsePictureInPicture,
        isInPictureInPicture = state.isInPictureInPicture,
        onEnterPictureInPicture = actions.onEnterPictureInPicture,
        onSettingsChange = actions.onSettingsChange,
        onBack = actions.onBack,
        onRegisterPlayerInputActionHandler = actions.onRegisterPlayerInputActionHandler,
        offlineMode = state.forcedOfflineMode,
        playerControlFocusToRestoreId = controlFocus.restoreId,
        keepControlsVisibleAfterReady = controlFocus.keepVisibleAfterReady,
        onRememberPlayerControlFocus = controlFocus.onRemember,
        onPlayerControlFocusRestored = controlFocus.onRestored,
        onKeepControlsVisibleAfterReadyRequested = controlFocus.onKeepVisibleRequested,
        onControlsKeptVisibleAfterReady = controlFocus.onKeptVisible,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ShellPlayerContent(
    state: PlayerScreenState,
    presentation: PlayerScreenPresentation,
    actions: PlayerScreenActions,
    controlFocus: PlayerControlFocusBinding,
) {
    PlayerShellPane(
        model = PlayerShellModel(
            animeTitle = state.animeTitle,
            currentVideo = presentation.playbackVideo,
            settings = state.settings,
            groups = presentation.groups,
            selectedKey = presentation.selectedVoiceKey,
            sourceOptions = presentation.sourceOptions,
            selectedSourceKey = presentation.selectedSourceKey,
            previousVideo = presentation.previousVideo,
            nextVideo = presentation.nextVideo,
            allowSubscription = state.allowSubscriptions,
            subscriptionActive = state.subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
            canUsePictureInPicture = state.canUsePictureInPicture,
        ),
        actions = PlayerShellActions(
            onToggleSubscription = { actions.onToggleVideoSubscription(presentation.playbackVideo) },
            onSelectGroup = { groupKey, replacement ->
                selectPlayerGroup(
                    actions = actions,
                    presentation = presentation,
                    groupKey = groupKey,
                    replacement = replacement,
                    positionMs = presentation.playbackStartPositionMs,
                )
            },
            onSelectSource = { source ->
                actions.onSelectGroup(source.groupKey)
                actions.onSelectPlaybackSource(source, presentation.playbackStartPositionMs)
            },
            onPlayVideo = { next ->
                actions.onSelectGroup(next.groupKey)
                actions.onPlayVideoAtQuality(next, 0L, presentation.playbackPreferredQuality)
            },
            onRetry = actions.onRetry,
            onBack = actions.onBack,
        ),
        message = (state.streamState as? LoadState.Error)?.message,
        playerControlFocusToRestoreId = controlFocus.restoreId,
        onRememberPlayerControlFocus = controlFocus.onRemember,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun selectPlayerGroup(
    actions: PlayerScreenActions,
    presentation: PlayerScreenPresentation,
    groupKey: String,
    replacement: VideoVariant?,
    positionMs: Long,
) {
    if (replacement == null) {
        actions.onSelectGroup(groupKey)
        return
    }
    actions.onSelectGroup(replacement.groupKey)
    actions.onPlayVideoAtQuality(replacement, positionMs, presentation.playbackPreferredQuality)
}
