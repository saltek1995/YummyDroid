package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

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
