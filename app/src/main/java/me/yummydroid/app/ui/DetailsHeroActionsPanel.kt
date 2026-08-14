package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import me.yummydroid.app.InputAction
import me.yummydroid.app.data.VideoVariant

// DetailsHeroActionButtons
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActionButtons(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    dialogState: DetailsHeroActionDialogState,
    focus: DetailsHeroActionFocus,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        policy.primaryVideo?.let {
            DetailsHeroPrimaryAction(policy, actions, focus)
        }
        if (policy.showDownload) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = focus.actionModifier(DetailsHeroFocusIndex.DownloadAction),
                onClick = dialogState::openDownload,
            )
        }
        if (policy.showReset) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = focus.resetModifier(policy.primaryVideo),
                onClick = dialogState::openReset,
            )
        }
    }
}
@Composable
private fun DetailsHeroPrimaryAction(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    focus: DetailsHeroActionFocus,
) {
    val primaryVideo = policy.primaryVideo ?: return
    val resumeTarget = policy.resumeTarget
    DialogActionButton(
        text = when {
            policy.primaryLoading -> uiText(UiStringKey.Loading)
            resumeTarget != null -> uiText(UiStringKey.Continue)
            else -> uiText(UiStringKey.Watch5af041)
        },
        primary = true,
        loading = policy.primaryLoading,
        modifier = focus.primaryModifier(),
        onClick = if (resumeTarget != null) {
            { actions.onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) }
        } else {
            { actions.onPlayVideo(primaryVideo) }
        },
    )
}
// DetailsHeroActionDialogs
@Composable
internal fun DetailsHeroActionDialogs(
    model: DetailsHeroModel,
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    state: DetailsHeroActionDialogState,
) {
    val selectedDownloadVideo = policy.selectedDownloadVideo
    if (model.interactive && state.downloadOpen && selectedDownloadVideo != null) {
        DownloadPlanDialog(
            animeId = model.details.id,
            animeTitle = model.details.title,
            videos = model.downloadVideos,
            selectedVideo = selectedDownloadVideo,
            selected = model.defaultDownloadQuality,
            onResolveSampledQualities = actions.onResolveSampledDownloadQualities,
            onConfirm = { plan ->
                state.downloadOpen = false
                actions.onDownloadAllVideos(plan)
            },
            onDismiss = { state.downloadOpen = false },
        )
    }
    if (model.interactive && state.resetOpen) {
        ResetWatchProgressDialog(
            onConfirm = {
                state.resetOpen = false
                actions.onResetWatchProgress()
            },
            onDismiss = { state.resetOpen = false },
        )
    }
}

@Composable
private fun ResetWatchProgressDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ResetWatchProgress)) },
        text = { Text(uiText(UiStringKey.DeleteWatchProgressForAllEpisodesOfThisAnime)) },
        confirmButton = {
            DialogActionButton(
                text = uiText(UiStringKey.Reset),
                primary = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = onDismiss)
        },
    )
}
// DetailsHeroActionDialogState
internal class DetailsHeroActionDialogState {
    var downloadOpen by mutableStateOf(false)
    var resetOpen by mutableStateOf(false)

    fun openDownload() {
        resetOpen = false
        downloadOpen = true
    }

    fun openReset() {
        downloadOpen = false
        resetOpen = true
    }

    fun closeAll() {
        downloadOpen = false
        resetOpen = false
    }

    fun handleInput(action: InputAction): Boolean {
        if (action != InputAction.Back) return false
        return when {
            downloadOpen -> {
                downloadOpen = false
                true
            }
            resetOpen -> {
                resetOpen = false
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun rememberDetailsHeroActionDialogState(
    interactive: Boolean,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): DetailsHeroActionDialogState {
    val state = remember { DetailsHeroActionDialogState() }
    LaunchedEffect(interactive) {
        if (!interactive) state.closeAll()
    }
    val inputActionHandler by rememberUpdatedState { action: InputAction -> state.handleInput(action) }
    DisposableEffect(interactive, state.downloadOpen, state.resetOpen, onRegisterModalInputActionHandler) {
        if (interactive && (state.downloadOpen || state.resetOpen)) {
            onRegisterModalInputActionHandler { action -> inputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return state
}
// DetailsHeroActionFocus
internal class DetailsHeroActionFocus(
    private val primaryRequester: FocusRequester,
    private val gridState: VisualFocusGridState?,
) {
    fun primaryModifier(): Modifier {
        return if (gridState == null) {
            Modifier.focusRequester(primaryRequester)
        } else {
            actionModifier(DetailsHeroFocusIndex.PrimaryAction)
        }
    }

    fun actionModifier(index: Int): Modifier {
        val state = gridState ?: return Modifier
        return Modifier.visualFocusGridItem(
            state = state,
            index = index,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroActions,
            blockEntryIndex = index,
        )
    }

    fun resetModifier(watchVideo: VideoVariant?): Modifier = when {
        gridState != null -> actionModifier(DetailsHeroFocusIndex.ResetAction)
        watchVideo == null -> Modifier.focusRequester(primaryRequester)
        else -> Modifier
    }
}

@Composable
internal fun rememberDetailsHeroActionFocus(
    policy: DetailsHeroActionPolicy,
    externalPrimaryFocusRequester: FocusRequester?,
    focusRequestNonce: Long,
    heroFocusGridState: VisualFocusGridState?,
): DetailsHeroActionFocus {
    val primaryVideoId = policy.primaryVideo?.id ?: -1L
    val resumeVideoId = policy.resumeTarget?.video?.id ?: -1L
    val internalRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(policy.primaryFocusIndex)
        ?: internalRequester
    val inputModeManager = LocalInputModeManager.current

    UiControlEffect(
        focusRequestNonce,
        primaryVideoId,
        resumeVideoId,
        policy.showReset,
        inputModeManager.inputMode,
        enabled = focusRequestNonce > 0L && inputModeManager.inputMode != InputMode.Touch,
    ) {
        repeat(4) {
            withFrameNanos { }
            if (primaryRequester.requestFocusSafely()) return@UiControlEffect
        }
    }
    return DetailsHeroActionFocus(primaryRequester, heroFocusGridState)
}
// DetailsHeroActions
@Composable
internal fun DetailsHeroActionPanel(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    externalPrimaryFocusRequester: FocusRequester? = null,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    val policy = resolveDetailsHeroActionPolicy(
        watchVideo = model.watchVideo,
        resumeTarget = model.resumeTarget,
        canDownload = model.canDownload,
        hasDownloadVideos = model.downloadVideos.isNotEmpty(),
        hasWatchProgress = model.hasWatchProgress,
        playbackHistoryLoading = model.playbackHistoryLoading,
    )
    if (!policy.showPanel) return
    val dialogState = rememberDetailsHeroActionDialogState(
        interactive = model.interactive,
        onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
    )
    val focus = rememberDetailsHeroActionFocus(
        policy = policy,
        externalPrimaryFocusRequester = externalPrimaryFocusRequester,
        focusRequestNonce = model.activeFocusRequestNonce,
        heroFocusGridState = heroFocusGridState,
    )
    DetailsHeroActionButtons(policy, actions, dialogState, focus)
    DetailsHeroActionDialogs(model, policy, actions, dialogState)
}

internal data class DetailsHeroActionPolicy(
    val primaryVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val selectedDownloadVideo: VideoVariant?,
    val showDownload: Boolean,
    val showReset: Boolean,
    val primaryLoading: Boolean,
) {
    val showPanel: Boolean
        get() = primaryVideo != null || showReset

    val primaryFocusIndex: Int
        get() = if (primaryVideo != null) {
            DetailsHeroFocusIndex.PrimaryAction
        } else {
            DetailsHeroFocusIndex.ResetAction
        }
}

internal fun resolveDetailsHeroActionPolicy(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    canDownload: Boolean,
    hasDownloadVideos: Boolean,
    hasWatchProgress: Boolean,
    playbackHistoryLoading: Boolean = false,
): DetailsHeroActionPolicy = DetailsHeroActionPolicy(
    primaryVideo = watchVideo,
    resumeTarget = resumeTarget,
    selectedDownloadVideo = resumeTarget?.video ?: watchVideo,
    showDownload = watchVideo != null && canDownload && hasDownloadVideos,
    showReset = hasWatchProgress,
    primaryLoading = watchVideo != null && resumeTarget == null && playbackHistoryLoading,
)
