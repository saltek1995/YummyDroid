package me.yummydroid.app.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.R
import me.yummydroid.app.sourceSelectionKey
import me.yummydroid.app.ui.theme.YummySpacing

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

private data class RetainedReadyPlayback(
    val stream: ResolvedVideoStream,
    val video: VideoVariant,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
)

@Composable
internal fun PlayerScreen(
    animeTitle: String,
    video: VideoVariant,
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
    val retainedPlayback = retainedReadyPlayback
    val useRetainedPlayback = streamState == LoadState.Loading &&
        resumeChoicePosition == null &&
        retainedPlayback != null &&
        retainedPlayback.video.animeId == video.animeId
    val playbackStream = readyStream ?: retainedPlayback?.stream?.takeIf { useRetainedPlayback }
    val playbackVideo = retainedPlayback?.video?.takeIf { useRetainedPlayback } ?: video
    val playbackStartPositionMs = retainedPlayback?.startPositionMs?.takeIf { useRetainedPlayback } ?: startPositionMs
    val playbackPreferredQuality = retainedPlayback?.preferredQuality?.takeIf { useRetainedPlayback } ?: preferredQuality
    val sourceVideos = allVideos.ifEmpty { listOf(playbackVideo) }
    val videos = if (forcedOfflineMode) {
        sourceVideos.filter { it.isOfflineAvailable }
            .ifEmpty { listOf(playbackVideo).filter { it.isOfflineAvailable } }
    } else {
        sourceVideos
    }
    val groups = remember(videos) { videos.groupBy { it.matchingVoiceKey } }
    val selectedKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
        ?: playbackVideo.matchingVoiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: playbackVideo.groupKey
    val sourceSubtitleSourceKeys = playbackStream
        ?.let { stream ->
            stream.sourceSubtitleSourceKeys + listOfNotNull(
                playbackVideo.matchingSourceKey.takeIf { key -> key.isNotBlank() && stream.hasSubtitles },
            )
        }
        .orEmpty()
    val sourceSubtitleSelectionKeys = playbackStream
        ?.let { stream ->
            listOfNotNull(playbackVideo.sourceSelectionKey.takeIf { key -> key.isNotBlank() && stream.hasSubtitles })
                .toSet()
        }
        .orEmpty()
    val sourceSubtitleLabel = uiText(UiStringKey.HasSubtitles)
    val sourceOptions = remember(
        videos,
        playbackVideo,
        selectedKey,
        sourceSubtitleSourceKeys,
        sourceSubtitleSelectionKeys,
        sourceSubtitleLabel,
    ) {
        videos.sourceOptionsFor(
            currentVideo = playbackVideo,
            selectedVoiceKey = selectedKey,
            sourceSubtitleSourceKeys = sourceSubtitleSourceKeys,
            sourceSubtitleSelectionKeys = sourceSubtitleSelectionKeys,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
    val visibleSourceOptions = if (forcedOfflineMode) emptyList() else sourceOptions
    val selectedSourceKey = playbackVideo.sourceSelectionKey
    val previousVideo = remember(playbackVideo, videos, selectedGroup) {
        findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        )
    }
    val nextVideo = remember(playbackVideo, videos, selectedGroup) {
        findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        )
    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (playbackStream != null && resumeChoicePosition == null) {
            NativeVideoPlayer(
                stream = playbackStream,
                animeTitle = animeTitle,
                currentVideo = playbackVideo,
                settings = settings,
                startPositionMs = playbackStartPositionMs,
                playbackPreferredQuality = playbackPreferredQuality,
                playbackMetadataLoading = playbackMetadataLoading,
                groups = groups,
                selectedKey = selectedKey,
                sourceOptions = visibleSourceOptions,
                selectedSourceKey = selectedSourceKey,
                previousVideo = previousVideo,
                nextVideo = nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(playbackVideo),
                onToggleSubscription = { onToggleVideoSubscription(playbackVideo) },
                onSelectGroup = { groupKey, replacement, positionMs ->
                    if (replacement != null) {
                        onSelectGroup(replacement.groupKey)
                        onPlayVideoAtQuality(replacement, positionMs, playbackPreferredQuality)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source, positionMs ->
                    onSelectGroup(source.groupKey)
                    onSelectPlaybackSource(source, positionMs)
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, 0L, playbackPreferredQuality)
                },
                onPlayVideoAt = { next, positionMs ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, positionMs, playbackPreferredQuality)
                },
                onPlayVideoAtQuality = { next, positionMs, nextPreferredQuality ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, positionMs, nextPreferredQuality)
                },
                onPlaybackFailed = onPlaybackFailed,
                onPlaybackStarted = onPlaybackStarted,
                onPlaybackEnded = onPlaybackEnded,
                onPlaybackProgress = onPlaybackProgress,
                canUsePictureInPicture = canUsePictureInPicture,
                isInPictureInPicture = isInPictureInPicture,
                onEnterPictureInPicture = onEnterPictureInPicture,
                onSettingsChange = onSettingsChange,
                onBack = onBack,
                onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
                offlineMode = forcedOfflineMode,
                modifier = Modifier.fillMaxSize(),
            )
            if (useRetainedPlayback) {
                PlayerLoadingOverlay()
            }
        } else {
            PlayerShellPane(
                animeTitle = animeTitle,
                currentVideo = playbackVideo,
                settings = settings,
                groups = groups,
                selectedKey = selectedKey,
                sourceOptions = visibleSourceOptions,
                selectedSourceKey = selectedSourceKey,
                previousVideo = previousVideo,
                nextVideo = nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(playbackVideo),
                canUsePictureInPicture = canUsePictureInPicture,
                onToggleSubscription = { onToggleVideoSubscription(playbackVideo) },
                onSelectGroup = { groupKey, replacement ->
                    if (replacement != null) {
                        onSelectGroup(replacement.groupKey)
                        onPlayVideoAtQuality(replacement, playbackStartPositionMs, playbackPreferredQuality)
                    } else {
                        onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source ->
                    onSelectGroup(source.groupKey)
                    onSelectPlaybackSource(source, playbackStartPositionMs)
                },
                onPlayVideo = { next ->
                    onSelectGroup(next.groupKey)
                    onPlayVideoAtQuality(next, 0L, playbackPreferredQuality)
                },
                message = (streamState as? LoadState.Error)?.message,
                onRetry = onRetry,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (resumeChoicePosition != null) {
            PlayerResumeChoiceDialog(
                video = video,
                positionMs = resumeChoicePosition,
                onStartOver = { onChooseResumePosition(0L) },
                onResume = { onChooseResumePosition(resumeChoicePosition) },
                onDismiss = onBack,
            )
        }
    }
}

@Composable
private fun PlayerLoadingOverlay() {
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
private fun PlayerResumeChoiceDialog(
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
