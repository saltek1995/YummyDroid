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
    animeTitle: String,
    requestedVideo: VideoVariant,
    interactive: Boolean,
    settings: AppSettings,
    presentation: PlayerScreenPresentation,
    streamState: LoadState<*>,
    playbackMetadataLoading: Boolean,
    resumeChoicePositionMs: Long?,
    isInPictureInPicture: Boolean,
    forcedOfflineMode: Boolean,
    allowSubscriptions: Boolean,
    subscriptions: List<VideoSubscription>,
    canUsePictureInPicture: Boolean,
    actions: PlayerScreenActions,
    controlFocus: PlayerControlFocusBinding,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (presentation.playbackStream != null && resumeChoicePositionMs == null) {
            NativeVideoPlayer(
                stream = presentation.playbackStream,
                animeTitle = animeTitle,
                currentVideo = presentation.playbackVideo,
                interactive = interactive,
                settings = settings,
                startPositionMs = presentation.playbackStartPositionMs,
                playbackPreferredQuality = presentation.playbackPreferredQuality,
                playbackMetadataLoading = playbackMetadataLoading,
                groups = presentation.groups,
                selectedKey = presentation.selectedVoiceKey,
                sourceOptions = presentation.sourceOptions,
                selectedSourceKey = presentation.selectedSourceKey,
                previousVideo = presentation.previousVideo,
                nextVideo = presentation.nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
                onToggleSubscription = {
                    actions.onToggleVideoSubscription(presentation.playbackVideo)
                },
                onSelectGroup = { groupKey, replacement, positionMs ->
                    if (replacement != null) {
                        actions.onSelectGroup(replacement.groupKey)
                        actions.onPlayVideoAtQuality(
                            replacement,
                            positionMs,
                            presentation.playbackPreferredQuality,
                        )
                    } else {
                        actions.onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source, positionMs ->
                    actions.onSelectGroup(source.groupKey)
                    actions.onSelectPlaybackSource(source, positionMs)
                },
                onPlayVideoAt = { next, positionMs ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, positionMs, presentation.playbackPreferredQuality)
                },
                onPlayVideoAtQuality = { next, positionMs, nextPreferredQuality ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, positionMs, nextPreferredQuality)
                },
                onPlaybackFailed = actions.onPlaybackFailed,
                onPlaybackStarted = actions.onPlaybackStarted,
                onPlaybackEnded = actions.onPlaybackEnded,
                onPlaybackProgress = actions.onPlaybackProgress,
                canUsePictureInPicture = canUsePictureInPicture,
                isInPictureInPicture = isInPictureInPicture,
                onEnterPictureInPicture = actions.onEnterPictureInPicture,
                onSettingsChange = actions.onSettingsChange,
                onBack = actions.onBack,
                onRegisterPlayerInputActionHandler = actions.onRegisterPlayerInputActionHandler,
                offlineMode = forcedOfflineMode,
                playerControlFocusToRestoreId = controlFocus.restoreId,
                keepControlsVisibleAfterReady = controlFocus.keepVisibleAfterReady,
                onRememberPlayerControlFocus = controlFocus.onRemember,
                onPlayerControlFocusRestored = controlFocus.onRestored,
                onKeepControlsVisibleAfterReadyRequested = controlFocus.onKeepVisibleRequested,
                onControlsKeptVisibleAfterReady = controlFocus.onKeptVisible,
                modifier = Modifier.fillMaxSize(),
            )
            if (presentation.useRetainedPlayback) {
                PlayerLoadingOverlay()
            }
        } else {
            PlayerShellPane(
                animeTitle = animeTitle,
                currentVideo = presentation.playbackVideo,
                settings = settings,
                groups = presentation.groups,
                selectedKey = presentation.selectedVoiceKey,
                sourceOptions = presentation.sourceOptions,
                selectedSourceKey = presentation.selectedSourceKey,
                previousVideo = presentation.previousVideo,
                nextVideo = presentation.nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
                canUsePictureInPicture = canUsePictureInPicture,
                onToggleSubscription = {
                    actions.onToggleVideoSubscription(presentation.playbackVideo)
                },
                onSelectGroup = { groupKey, replacement ->
                    if (replacement != null) {
                        actions.onSelectGroup(replacement.groupKey)
                        actions.onPlayVideoAtQuality(
                            replacement,
                            presentation.playbackStartPositionMs,
                            presentation.playbackPreferredQuality,
                        )
                    } else {
                        actions.onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source ->
                    actions.onSelectGroup(source.groupKey)
                    actions.onSelectPlaybackSource(source, presentation.playbackStartPositionMs)
                },
                onPlayVideo = { next ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, 0L, presentation.playbackPreferredQuality)
                },
                message = (streamState as? LoadState.Error)?.message,
                onRetry = actions.onRetry,
                onBack = actions.onBack,
                playerControlFocusToRestoreId = controlFocus.restoreId,
                onRememberPlayerControlFocus = controlFocus.onRemember,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (resumeChoicePositionMs != null) {
            PlayerResumeChoiceDialog(
                video = requestedVideo,
                positionMs = resumeChoicePositionMs,
                onStartOver = { actions.onChooseResumePosition(0L) },
                onResume = { actions.onChooseResumePosition(resumeChoicePositionMs) },
                onDismiss = actions.onBack,
            )
        }
    }
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
    val readyStream = (streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    val useRetainedPlayback = streamState == LoadState.Loading &&
        resumeChoicePositionMs == null &&
        retainedReadyPlayback != null &&
        retainedReadyPlayback.video.animeId == video.animeId
    val playbackStream = readyStream ?: retainedReadyPlayback?.stream?.takeIf { useRetainedPlayback }
    val playbackVideo = retainedReadyPlayback?.video?.takeIf { useRetainedPlayback } ?: video
    val playbackStartPositionMs = retainedReadyPlayback?.startPositionMs
        ?.takeIf { useRetainedPlayback }
        ?: startPositionMs
    val playbackPreferredQuality = retainedReadyPlayback?.preferredQuality
        ?.takeIf { useRetainedPlayback }
        ?: preferredQuality
    val sourceVideos = allVideos.ifEmpty { listOf(playbackVideo) }
    val videos = if (forcedOfflineMode) {
        sourceVideos.filter(VideoVariant::isOfflineAvailable)
            .ifEmpty { listOf(playbackVideo).filter(VideoVariant::isOfflineAvailable) }
    } else {
        sourceVideos
    }
    val groups = videos.groupBy(VideoVariant::matchingVoiceKey)
    val selectedVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { candidate -> candidate.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
        ?: playbackVideo.matchingVoiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
    val sourceSubtitleSourceKeys = playbackStream
        ?.let { stream ->
            stream.sourceSubtitleSourceKeys + listOfNotNull(
                playbackVideo.matchingSourceKey.takeIf { key ->
                    key.isNotBlank() && stream.hasResolvedSubtitles
                },
            )
        }
        .orEmpty()
    val sourceSubtitleSelectionKeys = playbackStream
        ?.let { stream ->
            listOfNotNull(
                playbackVideo.sourceSelectionKey.takeIf { key ->
                    key.isNotBlank() && stream.hasResolvedSubtitles
                },
            ).toSet()
        }
        .orEmpty()
    val sourceOptions = if (forcedOfflineMode) {
        emptyList()
    } else {
        videos.sourceOptionsFor(
            currentVideo = playbackVideo,
            selectedVoiceKey = selectedVoiceKey,
            sourceSubtitleSourceKeys = sourceSubtitleSourceKeys,
            sourceSubtitleSelectionKeys = sourceSubtitleSelectionKeys,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }

    return PlayerScreenPresentation(
        playbackStream = playbackStream,
        playbackVideo = playbackVideo,
        playbackStartPositionMs = playbackStartPositionMs,
        playbackPreferredQuality = playbackPreferredQuality,
        videos = videos,
        groups = groups,
        selectedVoiceKey = selectedVoiceKey,
        sourceOptions = sourceOptions,
        selectedSourceKey = playbackVideo.sourceSelectionKey,
        previousVideo = findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        ),
        nextVideo = findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        ),
        useRetainedPlayback = useRetainedPlayback,
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
    animeTitle: String,
    video: VideoVariant,
    interactive: Boolean,
    settings: AppSettings,
    startPositionMs: Long,
    preferredQuality: PreferredQuality,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    streamState: LoadState<ResolvedVideoStream>,
    playbackMetadataLoading: Boolean,
    resumeChoicePositionMs: Long?,
    isInPictureInPicture: Boolean,
    forcedOfflineMode: Boolean,
    allowSubscriptions: Boolean,
    subscriptions: List<VideoSubscription>,
    onSelectGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    onChooseResumePosition: (Long) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRegisterPlayerInputActionHandler: ((PlayerInputController?) -> Unit),
) {
    val resumeChoicePosition = resumeChoicePositionMs?.takeIf { it > 0L }
    val readyStream = (streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    var retainedReadyPlayback by remember { mutableStateOf<RetainedReadyPlayback?>(null) }
    LaunchedEffect(readyStream, video, startPositionMs, preferredQuality, resumeChoicePosition) {
        if (readyStream != null && resumeChoicePosition == null) {
            retainedReadyPlayback = RetainedReadyPlayback(
                stream = readyStream,
                video = video,
                startPositionMs = startPositionMs,
                preferredQuality = preferredQuality,
            )
        }
    }
    val sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles)
    val presentation = remember(
        video,
        startPositionMs,
        preferredQuality,
        allVideos,
        selectedGroup,
        streamState,
        retainedReadyPlayback,
        resumeChoicePosition,
        forcedOfflineMode,
        sourceSubtitleLabel,
    ) {
        buildPlayerScreenPresentation(
            video = video,
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            allVideos = allVideos,
            selectedGroup = selectedGroup,
            streamState = streamState,
            retainedReadyPlayback = retainedReadyPlayback,
            resumeChoicePositionMs = resumeChoicePosition,
            forcedOfflineMode = forcedOfflineMode,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
    var playerControlFocusToRestoreId by remember { mutableStateOf<Int?>(null) }
    var keepPlayerControlsVisibleAfterReady by remember { mutableStateOf(false) }
    val latestOnBack by rememberUpdatedState(onBack)
    DisposableEffect(resumeChoicePosition, onRegisterModalInputActionHandler) {
        if (resumeChoicePosition != null) {
            onRegisterModalInputActionHandler { action ->
                if (action == InputAction.Back) {
                    latestOnBack()
                    true
                } else {
                    false
                }
            }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

    PlayerScreenContent(
        animeTitle = animeTitle,
        requestedVideo = video,
        interactive = interactive,
        settings = settings,
        presentation = presentation,
        streamState = streamState,
        playbackMetadataLoading = playbackMetadataLoading,
        resumeChoicePositionMs = resumeChoicePosition,
        isInPictureInPicture = isInPictureInPicture,
        forcedOfflineMode = forcedOfflineMode,
        allowSubscriptions = allowSubscriptions,
        subscriptions = subscriptions,
        canUsePictureInPicture = canUsePictureInPicture,
        actions = PlayerScreenActions(
            onSelectGroup = onSelectGroup,
            onPlayVideoAtQuality = onPlayVideoAtQuality,
            onSelectPlaybackSource = onSelectPlaybackSource,
            onChooseResumePosition = onChooseResumePosition,
            onToggleVideoSubscription = onToggleVideoSubscription,
            onRetry = onRetry,
            onPlaybackFailed = onPlaybackFailed,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackEnded = onPlaybackEnded,
            onPlaybackProgress = onPlaybackProgress,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onSettingsChange = onSettingsChange,
            onBack = onBack,
            onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
        ),
        controlFocus = PlayerControlFocusBinding(
            restoreId = playerControlFocusToRestoreId,
            keepVisibleAfterReady = keepPlayerControlsVisibleAfterReady,
            onRemember = { controlId -> playerControlFocusToRestoreId = controlId },
            onRestored = {
                if (!keepPlayerControlsVisibleAfterReady) {
                    playerControlFocusToRestoreId = null
                }
            },
            onKeepVisibleRequested = {
                keepPlayerControlsVisibleAfterReady = true
            },
            onKeptVisible = {
                if (!presentation.useRetainedPlayback) {
                    keepPlayerControlsVisibleAfterReady = false
                    playerControlFocusToRestoreId = null
                }
            },
        ),
    )
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
    var clusterStartMs = seed.startMs
    var clusterEndMs = seed.endMs
    var changed: Boolean
    do {
        changed = false
        forEach { candidate ->
            val overlapsCluster = candidate.kind == seed.kind &&
                candidate.startMs <= clusterEndMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS &&
                candidate.endMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS >= clusterStartMs
            if (overlapsCluster) {
                val nextStartMs = minOf(clusterStartMs, candidate.startMs)
                val nextEndMs = maxOf(clusterEndMs, candidate.endMs)
                if (nextStartMs != clusterStartMs || nextEndMs != clusterEndMs) {
                    clusterStartMs = nextStartMs
                    clusterEndMs = nextEndMs
                    changed = true
                }
            }
        }
    } while (changed)

    return filter { candidate ->
        candidate.kind == seed.kind &&
            candidate.startMs <= clusterEndMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS &&
            candidate.endMs + SKIP_SEGMENT_CLUSTER_TOLERANCE_MS >= clusterStartMs
    }.ifEmpty { listOf(seed) }
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
    findViewById<ImageButton>(R.id.yummy_player_voice)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_voice, texts.voice)
        visibility = View.VISIBLE
        setPlayerControlEnabled(groups.size > 1)
        setOnClickListener {
            if (groups.size <= 1) return@setOnClickListener
            showPlayerControls()
            showVoicePopup(
                anchor = this,
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
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindSourceSelector(
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
) {
    findViewById<ImageButton>(R.id.yummy_player_source)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_source, texts.source)
        visibility = View.VISIBLE
        setPlayerControlEnabled(sourceOptions.size > 1)
        setOnClickListener {
            if (sourceOptions.size <= 1) return@setOnClickListener
            showPlayerControls()
            showSourcePopup(
                anchor = this,
                options = sourceOptions,
                selectedSourceKey = selectedSourceKey,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectSource = onSelectSource,
            )
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
@Composable
@OptIn(UnstableApi::class)
internal fun PlayerShellPane(
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
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    message: String? = null,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    val playerControlTexts = rememberPlayerControlTexts()
    val retryFocusRequester = remember(message) { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(message, inputModeManager.inputMode) {
        if (message == null || inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (retryFocusRequester.requestFocusSafely()) return@LaunchedEffect
        }
    }
    Box(
        modifier = modifier.background(Color.Black),
    ) {
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
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        settings = settings,
                        groups = groups,
                        selectedKey = selectedKey,
                        sourceOptions = sourceOptions,
                        selectedSourceKey = selectedSourceKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        canUsePictureInPicture = canUsePictureInPicture,
                        showCenterControls = message == null,
                        texts = playerControlTexts,
                        onToggleSubscription = onToggleSubscription,
                        onSelectGroup = onSelectGroup,
                        onSelectSource = onSelectSource,
                        onPlayVideo = onPlayVideo,
                        onBack = onBack,
                        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                    )
                    view.showPlayerControls()
                    view.restorePlayerControlFocus(playerControlFocusToRestoreId)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (message == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
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
    }
}
