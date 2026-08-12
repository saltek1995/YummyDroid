package me.yummydroid.app.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.sourceSelectionKey
import me.yummydroid.app.ui.theme.YummySpacing

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
            if (presentation.useRetainedPlayback) {
                PlayerLoadingOverlay()
            }
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

// PlayerScreenOverlays
@Composable
internal fun PlayerLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
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

    LaunchedEffect(video.id, positionMs, inputModeManager.inputMode) {
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
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

// PlayerScreenRuntime
@Composable
internal fun PlayerScreen(
    state: PlayerScreenState,
    actions: PlayerScreenActions,
) {
    val resumeChoicePosition = state.resumeChoicePositionMs?.takeIf { it > 0L }
    val retainedReadyPlayback = rememberRetainedReadyPlayback(state, resumeChoicePosition)
    val presentation = rememberPlayerScreenPresentation(state, retainedReadyPlayback, resumeChoicePosition)
    val controlFocus = rememberPlayerControlFocusBinding(presentation.useRetainedPlayback)
    PlayerResumeInputEffect(resumeChoicePosition?.takeIf { state.interactive }, actions)

    PlayerScreenContent(
        state = state,
        presentation = presentation,
        resumeChoicePositionMs = resumeChoicePosition,
        actions = actions,
        controlFocus = controlFocus,
    )
}

@Composable
private fun rememberRetainedReadyPlayback(
    state: PlayerScreenState,
    resumeChoicePositionMs: Long?,
): RetainedReadyPlayback? {
    val readyStream = (state.streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    var retained by remember { mutableStateOf<RetainedReadyPlayback?>(null) }
    LaunchedEffect(
        readyStream,
        state.video,
        state.startPositionMs,
        state.preferredQuality,
        resumeChoicePositionMs,
    ) {
        if (readyStream != null && resumeChoicePositionMs == null) {
            retained = RetainedReadyPlayback(
                stream = readyStream,
                video = state.video,
                startPositionMs = state.startPositionMs,
                preferredQuality = state.preferredQuality,
            )
        }
    }
    return retained
}

@Composable
private fun rememberPlayerScreenPresentation(
    state: PlayerScreenState,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
): PlayerScreenPresentation {
    val sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles)
    return remember(state, retainedReadyPlayback, resumeChoicePositionMs, sourceSubtitleLabel) {
        buildPlayerScreenPresentation(
            video = state.video,
            startPositionMs = state.startPositionMs,
            preferredQuality = state.preferredQuality,
            allVideos = state.allVideos,
            selectedGroup = state.selectedGroup,
            streamState = state.streamState,
            retainedReadyPlayback = retainedReadyPlayback,
            resumeChoicePositionMs = resumeChoicePositionMs,
            forcedOfflineMode = state.forcedOfflineMode,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
}

@Composable
private fun rememberPlayerControlFocusBinding(useRetainedPlayback: Boolean): PlayerControlFocusBinding {
    var playerControlFocusToRestoreId by remember { mutableStateOf<Int?>(null) }
    var keepPlayerControlsVisibleAfterReady by remember { mutableStateOf(false) }
    return PlayerControlFocusBinding(
        restoreId = playerControlFocusToRestoreId,
        keepVisibleAfterReady = keepPlayerControlsVisibleAfterReady,
        onRemember = { controlId -> playerControlFocusToRestoreId = controlId },
        onRestored = {
            if (!keepPlayerControlsVisibleAfterReady) playerControlFocusToRestoreId = null
        },
        onKeepVisibleRequested = { keepPlayerControlsVisibleAfterReady = true },
        onKeptVisible = {
            if (!useRetainedPlayback) {
                keepPlayerControlsVisibleAfterReady = false
                playerControlFocusToRestoreId = null
            }
        },
    )
}

@Composable
private fun PlayerResumeInputEffect(
    resumeChoicePositionMs: Long?,
    actions: PlayerScreenActions,
) {
    val latestOnBack by rememberUpdatedState(actions.onBack)
    DisposableEffect(resumeChoicePositionMs, actions.onRegisterModalInputActionHandler) {
        if (resumeChoicePositionMs == null) {
            actions.onRegisterModalInputActionHandler(null)
        } else {
            actions.onRegisterModalInputActionHandler { action ->
                if (action != InputAction.Back) return@onRegisterModalInputActionHandler false
                latestOnBack()
                true
            }
        }
        onDispose { actions.onRegisterModalInputActionHandler(null) }
    }
}

// PlayerScreenSupport
internal const val PLAYER_CONTROLS_AUTO_HIDE_MS = 4_000L
internal const val VOICE_MENU_GROUP_ID = 19
internal const val QUALITY_MENU_GROUP_ID = 20
internal const val SPEED_MENU_GROUP_ID = 21
internal const val SUBTITLE_MENU_GROUP_ID = 22
internal const val SOURCE_MENU_GROUP_ID = 23
internal const val SUBTITLE_OFF_KEY = "off"
internal const val PIP_ENTER_DELAY_MS = 120L
internal const val PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS = 900L
internal const val PLAYER_TIMELINE_MANUAL_FREEZE_MS = 2_000L
internal const val PLAYER_TOUCH_FOCUS_CLEAR_DELAY_MS = 80L
internal const val PLAYER_TOUCH_FOCUS_CLEAR_WINDOW_MS = 500L
internal const val PLAYER_TIMELINE_BASE_STEP_MS = 5_000L
internal const val PLAYER_TIMELINE_MAX_STEP_DIVISOR = 20L
internal const val PLAYBACK_PROGRESS_SAVE_INTERVAL_MS = 15_000L
internal const val PLAYBACK_BUFFERING_FALLBACK_DELAY_MS = 900L
internal const val PLAYBACK_SEEK_BUFFER_GRACE_MS = 4_500L
internal const val PLAYBACK_BUFFER_END_IGNORE_MS = 30_000L
internal const val PLAYBACK_BUFFER_END_EPSILON_MS = 1_000L
internal const val SKIP_PROMPT_COUNTDOWN_SECONDS = 8
internal const val SKIP_PROMPT_POLL_MS = 500L
internal const val SKIP_PROMPT_ZERO_DISPLAY_MS = 350L
internal const val SKIP_PROMPT_MIN_REMAINING_MS = 1_500L
internal const val SKIP_SEGMENT_CLUSTER_TOLERANCE_MS = 2_000L

internal data class VideoZoomGestureState(
    var scale: Float = 1f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var moved: Boolean = false,
    var handlingTouch: Boolean = false,
)

internal data class ActiveSkipPrompt(
    val key: String,
    val segment: VideoSkipSegment,
    val dismissKeys: Set<String> = setOf(key),
    val activeStartMs: Long = segment.startMs,
    val targetEndMs: Long = segment.endMs,
)

internal data class SkipCountdownState(
    val startedAtMs: Long,
    val deadlineMs: Long,
    var autoSkipEnabled: Boolean,
)

internal fun VideoSkipSegment.hasUsefulSkipAt(positionMs: Long): Boolean {
    return isActive(positionMs) && endMs - positionMs > SKIP_PROMPT_MIN_REMAINING_MS
}

internal fun ActiveSkipPrompt.hasUsefulSkipAt(positionMs: Long): Boolean {
    return positionMs >= activeStartMs &&
        positionMs < targetEndMs &&
        targetEndMs - positionMs > SKIP_PROMPT_MIN_REMAINING_MS
}

internal fun List<VideoSkipSegment>.skipPromptCluster(seed: VideoSkipSegment): List<VideoSkipSegment> {
    val sameKind = filter { it.kind == seed.kind }
    var bounds = SkipClusterBounds(seed.startMs, seed.endMs)
    while (true) {
        val expanded = sameKind.fold(bounds) { current, candidate -> current.includeIfConnected(candidate) }
        if (expanded == bounds) {
            return sameKind.filter(bounds::isConnected).ifEmpty { listOf(seed) }
        }
        bounds = expanded
    }
}

private data class SkipClusterBounds(
    val startMs: Long,
    val endMs: Long,
) {
    fun isConnected(segment: VideoSkipSegment): Boolean {
        val startsBeforeClusterEnds = segment.startMs <= endMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS
        val endsAfterClusterStarts = segment.endMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS >= startMs
        return startsBeforeClusterEnds && endsAfterClusterStarts
    }

    fun includeIfConnected(segment: VideoSkipSegment): SkipClusterBounds {
        if (!isConnected(segment)) return this
        return SkipClusterBounds(
            startMs = minOf(startMs, segment.startMs),
            endMs = maxOf(endMs, segment.endMs),
        )
    }
}

internal fun PlayerView.dismissedSkipKeys(): MutableSet<String> {
    @Suppress("UNCHECKED_CAST")
    return tagValue<MutableSet<String>>(R.id.yummy_player_skip_dismissed_keys)
        ?: mutableSetOf<String>().also { dismissedKeys ->
            setTag(R.id.yummy_player_skip_dismissed_keys, dismissedKeys)
        }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearActiveSkipPrompt(markDismissed: Boolean) {
    val skipOnlyMode = isSkipOnlyControllerMode()
    val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
    if (markDismissed && prompt != null) {
        dismissedSkipKeys().addAll(prompt.dismissKeys)
    }
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
    clearTagValue(R.id.yummy_player_active_skip_key)
    clearTagValue(R.id.yummy_player_active_skip_segment)
    clearTagValue(R.id.yummy_player_skip_auto_cancelled)
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
    configureSkipFocusNavigation(active = false)
    if (skipOnlyMode) {
        setSkipOnlyControllerMode(false)
        setTag(R.id.yummy_player_controls_visible, false)
        hideController()
        setPlayerControlChromeAlpha(0f)
    }
}

// PlayerShellControllerBinding
internal inline fun <reified T> View.tagValue(tagId: Int): T? {
    return getTag(tagId) as? T
}

internal fun View.clearTagValue(tagId: Int) {
    setTag(tagId, null)
}

internal fun View.removeTaggedRunnable(tagId: Int) {
    tagValue<Runnable>(tagId)?.let(::removeCallbacks)
    clearTagValue(tagId)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyShellController(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    showCenterControls: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
) {
    applyPlayerControlIconColors()
    bindShellHeader(
        animeTitle = animeTitle,
        currentVideo = currentVideo,
        videos = groups.values.flatten(),
        texts = texts,
    )
    bindShellTransport(showCenterControls, previousVideo, nextVideo, onPlayVideo, onBack)
    bindVoiceSelector(
        currentVideo = currentVideo,
        groups = groups,
        selectedKey = selectedKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectGroup = onSelectGroup,
    )
    bindSourceSelector(
        sourceOptions = sourceOptions,
        selectedSourceKey = selectedSourceKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectSource = onSelectSource,
    )
    bindStaticShellControls(
        settings = settings,
        allowSubscription = allowSubscription,
        subscriptionActive = subscriptionActive,
        canUsePictureInPicture = canUsePictureInPicture,
        texts = texts,
        onToggleSubscription = onToggleSubscription,
    )
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellHeader(
    animeTitle: String,
    currentVideo: VideoVariant,
    videos: List<VideoVariant>,
    texts: PlayerControlTexts,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle(texts, videos)
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = context.getString(R.string.player_zero_time)
    findViewById<TextView>(Media3R.id.exo_duration)?.text = context.getString(R.string.player_zero_time)
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellTransport(
    showCenterControls: Boolean,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    setSkipControlsActive(false)
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (showCenterControls) {
        View.VISIBLE
    } else {
        View.GONE
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (showCenterControls && previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (showCenterControls && nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindVoiceSelector(
    currentVideo: VideoVariant,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    bindPopupSelector(
        controlId = R.id.yummy_player_voice,
        iconResId = R.drawable.ic_player_voice,
        label = texts.voice,
        optionCount = groups.size,
    ) { anchor ->
        showVoicePopup(
            anchor = anchor,
            groups = groups,
            selectedKey = selectedKey,
            preferredGroupKey = currentVideo.groupKey,
            currentVideo = currentVideo,
            texts = texts,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onSelectGroup = onSelectGroup,
        )
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindSourceSelector(
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
) {
    bindPopupSelector(
        controlId = R.id.yummy_player_source,
        iconResId = R.drawable.ic_player_source,
        label = texts.source,
        optionCount = sourceOptions.size,
    ) { anchor ->
        showSourcePopup(
            anchor = anchor,
            options = sourceOptions,
            selectedSourceKey = selectedSourceKey,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onSelectSource = onSelectSource,
        )
    }
}

internal fun playerSelectorEnabled(optionCount: Int): Boolean = optionCount > 1

private fun PlayerView.bindPopupSelector(
    controlId: Int,
    iconResId: Int,
    label: String,
    optionCount: Int,
    openPopup: (ImageButton) -> Unit,
) {
    val enabled = playerSelectorEnabled(optionCount)
    findViewById<ImageButton>(controlId)?.apply {
        applyPlayerIconControl(iconResId, label)
        visibility = View.VISIBLE
        setPlayerControlEnabled(enabled)
        setOnClickListener {
            if (!enabled) return@setOnClickListener
            this@bindPopupSelector.showPlayerControls()
            openPopup(this)
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindStaticShellControls(
    settings: AppSettings,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        applyPlayerQualityControl(PLAYER_AUTO_QUALITY_LABEL, texts.quality)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_subtitles, texts.subtitles)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subscription)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subscription,
            label = if (subscriptionActive) texts.subscribed else texts.subscription,
            active = subscriptionActive,
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(allowSubscription)
        setOnClickListener {
            if (!allowSubscription) return@setOnClickListener
            showPlayerControls()
            onToggleSubscription()
        }
    }
    findViewById<ImageButton>(R.id.yummy_player_speed)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_speed,
            label = "${context.getString(R.string.player_speed)}: ${settings.playerSpeed.title}",
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setPlayerControlEnabled(false)
    }

    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isEnabled = false
        isFocusable = false
    }
}

// PlayerShellPane
private data class PlayerShellModel(
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val settings: AppSettings,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val canUsePictureInPicture: Boolean,
)

private data class PlayerShellActions(
    val onToggleSubscription: () -> Unit,
    val onSelectGroup: (String, VideoVariant?) -> Unit,
    val onSelectSource: (VideoVariant) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
@OptIn(UnstableApi::class)
private fun PlayerShellPane(
    model: PlayerShellModel,
    actions: PlayerShellActions,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    message: String? = null,
) {
    val playerControlTexts = rememberPlayerControlTexts()
    val retryFocusRequester = rememberPlayerShellRetryFocus(message)
    Box(modifier = modifier.background(Color.Black)) {
        PlayerShellAndroidView(
            model = model,
            actions = actions,
            texts = playerControlTexts,
            showCenterControls = message == null,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        )
        PlayerShellStatus(
            message = message,
            retryFocusRequester = retryFocusRequester,
            onRetry = actions.onRetry,
        )
    }
}

@Composable
private fun rememberPlayerShellRetryFocus(message: String?): FocusRequester {
    val focusRequester = remember(message) { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(message, inputModeManager.inputMode) {
        if (message == null || inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (focusRequester.requestFocusSafely()) return@LaunchedEffect
        }
    }
    return focusRequester
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerShellAndroidView(
    model: PlayerShellModel,
    actions: PlayerShellActions,
    texts: PlayerControlTexts,
    showCenterControls: Boolean,
    playerControlFocusToRestoreId: Int?,
    onRememberPlayerControlFocus: (Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    key(
        configuration.orientation,
        windowSize.width,
        windowSize.height,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val parent = FrameLayout(viewContext)
                LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                view.player = null
                view.useController = true
                view.controllerAutoShow = false
                view.setControllerAnimationEnabled(false)
                view.installPlayerControlsVisibilitySync()
                view.setControllerShowTimeoutMs(0)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.keepScreenOn = true
                view.bindYummyShellController(
                    animeTitle = model.animeTitle,
                    currentVideo = model.currentVideo,
                    settings = model.settings,
                    groups = model.groups,
                    selectedKey = model.selectedKey,
                    sourceOptions = model.sourceOptions,
                    selectedSourceKey = model.selectedSourceKey,
                    previousVideo = model.previousVideo,
                    nextVideo = model.nextVideo,
                    allowSubscription = model.allowSubscription,
                    subscriptionActive = model.subscriptionActive,
                    canUsePictureInPicture = model.canUsePictureInPicture,
                    showCenterControls = showCenterControls,
                    texts = texts,
                    onToggleSubscription = actions.onToggleSubscription,
                    onSelectGroup = actions.onSelectGroup,
                    onSelectSource = actions.onSelectSource,
                    onPlayVideo = actions.onPlayVideo,
                    onBack = actions.onBack,
                    onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                )
                view.showPlayerControls()
                view.restorePlayerControlFocus(playerControlFocusToRestoreId)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BoxScope.PlayerShellStatus(
    message: String?,
    retryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
) {
    if (message == null) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .padding(top = 112.dp, bottom = 176.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        DialogActionButton(
            text = uiText(UiStringKey.Retry),
            primary = true,
            modifier = Modifier.focusRequester(retryFocusRequester),
            onClick = onRetry,
        )
    }
}
