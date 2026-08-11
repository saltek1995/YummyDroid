package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.data.VideoVariant

@Composable
internal fun DetailsHeroActionPanel(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    externalPrimaryFocusRequester: FocusRequester? = null,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    if (!detailsHeroShouldShowActions(model.watchVideo, model.hasWatchProgress)) return
    val dialogState = rememberDetailsHeroActionDialogState(actions.onRegisterModalInputActionHandler)
    val focus = rememberDetailsHeroActionFocus(
        watchVideo = model.watchVideo,
        resumeTarget = model.resumeTarget,
        hasWatchProgress = model.hasWatchProgress,
        externalPrimaryFocusRequester = externalPrimaryFocusRequester,
        focusRequestNonce = model.activeFocusRequestNonce,
        heroFocusGridState = heroFocusGridState,
    )
    DetailsHeroActionButtons(model, actions, dialogState, focus)
    DetailsHeroActionDialogs(model, actions, dialogState)
}

internal fun detailsHeroShouldShowActions(
    watchVideo: VideoVariant?,
    hasWatchProgress: Boolean,
): Boolean = watchVideo != null || hasWatchProgress

internal fun detailsHeroPrimaryActionFocusIndex(watchVideo: VideoVariant?): Int =
    if (watchVideo != null) DetailsHeroFocusIndex.PrimaryAction else DetailsHeroFocusIndex.ResetAction

internal fun detailsHeroSelectedDownloadVideo(
    resumeTarget: HeroResumeTarget?,
    watchVideo: VideoVariant?,
): VideoVariant? = resumeTarget?.video ?: watchVideo
