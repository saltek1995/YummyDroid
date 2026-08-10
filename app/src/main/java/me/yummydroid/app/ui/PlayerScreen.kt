package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure

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
