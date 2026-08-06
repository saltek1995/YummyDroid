package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.InputAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActions(
    animeId: Long,
    animeTitle: String,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    hasWatchProgress: Boolean,
    onResetWatchProgress: () -> Unit,
    externalPrimaryFocusRequester: FocusRequester? = null,
    focusRequestNonce: Long = 0L,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    if (!detailsHeroShouldShowActions(watchVideo = watchVideo, hasWatchProgress = hasWatchProgress)) return
    var downloadDialogOpen by remember { mutableStateOf(false) }
    var resetDialogOpen by remember { mutableStateOf(false) }
    val dialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                downloadDialogOpen -> {
                    downloadDialogOpen = false
                    true
                }
                resetDialogOpen -> {
                    resetDialogOpen = false
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(downloadDialogOpen, resetDialogOpen, onRegisterModalInputActionHandler) {
        if (downloadDialogOpen || resetDialogOpen) {
            onRegisterModalInputActionHandler { action -> dialogInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    val primaryVideoId = watchVideo?.id ?: -1L
    val resumeVideoId = resumeTarget?.video?.id ?: -1L
    val internalPrimaryActionFocusRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryActionFocusIndex = detailsHeroPrimaryActionFocusIndex(watchVideo)
    val primaryActionFocusRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(primaryActionFocusIndex)
        ?: internalPrimaryActionFocusRequester
    val inputModeManager = LocalInputModeManager.current

    fun Modifier.heroActionFocus(index: Int): Modifier {
        val state = heroFocusGridState ?: return this
        return then(
            Modifier.visualFocusGridItem(
                state = state,
                index = index,
                horizontal = true,
                vertical = true,
                blockKey = DetailsFocusBlockKey.HeroActions,
                blockEntryIndex = index,
            ),
        )
    }

    suspend fun requestPrimaryActionFocus() {
        repeat(4) {
            withFrameNanos { }
            if (primaryActionFocusRequester.requestFocusSafely()) {
                return
            }
        }
    }

    LaunchedEffect(focusRequestNonce, primaryVideoId, resumeVideoId, hasWatchProgress) {
        if (focusRequestNonce <= 0L) return@LaunchedEffect
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        requestPrimaryActionFocus()
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (watchVideo != null) {
            if (resumeTarget != null) {
                DialogActionButton(
                    text = uiText(UiStringKey.Continue),
                    primary = true,
                    modifier = if (heroFocusGridState == null) {
                        Modifier.focusRequester(primaryActionFocusRequester)
                    } else {
                        Modifier.heroActionFocus(DetailsHeroFocusIndex.PrimaryAction)
                    },
                    onClick = { onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) },
                )
            } else {
                DialogActionButton(
                    text = uiText(UiStringKey.Watch5af041),
                    primary = true,
                    modifier = if (heroFocusGridState == null) {
                        Modifier.focusRequester(primaryActionFocusRequester)
                    } else {
                        Modifier.heroActionFocus(DetailsHeroFocusIndex.PrimaryAction)
                    },
                    onClick = { onPlayVideo(watchVideo) },
                )
            }
        }
        if (watchVideo != null && canDownload && downloadVideos.isNotEmpty()) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = Modifier.heroActionFocus(DetailsHeroFocusIndex.DownloadAction),
                onClick = { downloadDialogOpen = true },
            )
        }
        if (hasWatchProgress) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = when {
                    heroFocusGridState != null -> Modifier.heroActionFocus(DetailsHeroFocusIndex.ResetAction)
                    watchVideo == null -> Modifier.focusRequester(primaryActionFocusRequester)
                    else -> Modifier
                },
                onClick = { resetDialogOpen = true },
            )
        }
    }

    val selectedDownloadVideo = detailsHeroSelectedDownloadVideo(
        resumeTarget = resumeTarget,
        watchVideo = watchVideo,
    )
    if (downloadDialogOpen && selectedDownloadVideo != null) {
        DownloadPlanDialog(
            animeId = animeId,
            animeTitle = animeTitle,
            videos = downloadVideos,
            selectedVideo = selectedDownloadVideo,
            selected = defaultDownloadQuality,
            onResolveSampledQualities = onResolveSampledDownloadQualities,
            onConfirm = { plan ->
                downloadDialogOpen = false
                onDownloadAllVideos(plan)
            },
            onDismiss = { downloadDialogOpen = false },
        )
    }

    if (resetDialogOpen) {
        AlertDialog(
            modifier = Modifier.yummyDialogMotion(),
            onDismissRequest = { resetDialogOpen = false },
            title = { Text(uiText(UiStringKey.ResetWatchProgress)) },
            text = { Text(uiText(UiStringKey.DeleteWatchProgressForAllEpisodesOfThisAnime)) },
            confirmButton = {
                DialogActionButton(
                    text = uiText(UiStringKey.Reset),
                    primary = true,
                    onClick = {
                        resetDialogOpen = false
                        onResetWatchProgress()
                    },
                )
            },
            dismissButton = {
                DialogActionButton(
                    text = uiText(UiStringKey.Cancel),
                    onClick = { resetDialogOpen = false },
                )
            },
        )
    }
}

internal fun detailsHeroShouldShowActions(
    watchVideo: VideoVariant?,
    hasWatchProgress: Boolean,
): Boolean {
    return watchVideo != null || hasWatchProgress
}

internal fun detailsHeroPrimaryActionFocusIndex(watchVideo: VideoVariant?): Int {
    return if (watchVideo != null) {
        DetailsHeroFocusIndex.PrimaryAction
    } else {
        DetailsHeroFocusIndex.ResetAction
    }
}

internal fun detailsHeroSelectedDownloadVideo(
    resumeTarget: HeroResumeTarget?,
    watchVideo: VideoVariant?,
): VideoVariant? {
    return resumeTarget?.video ?: watchVideo
}
