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
