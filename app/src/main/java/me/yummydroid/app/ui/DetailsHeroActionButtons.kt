package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActionButtons(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    dialogState: DetailsHeroActionDialogState,
    focus: DetailsHeroActionFocus,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        model.watchVideo?.let { watchVideo ->
            DetailsHeroPrimaryAction(model, actions, focus, watchVideo)
        }
        if (model.watchVideo != null && model.canDownload && model.downloadVideos.isNotEmpty()) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = focus.actionModifier(DetailsHeroFocusIndex.DownloadAction),
                onClick = { dialogState.downloadOpen = true },
            )
        }
        if (model.hasWatchProgress) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = focus.resetModifier(model.watchVideo),
                onClick = { dialogState.resetOpen = true },
            )
        }
    }
}

@Composable
private fun DetailsHeroPrimaryAction(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focus: DetailsHeroActionFocus,
    watchVideo: me.yummydroid.app.data.VideoVariant,
) {
    val resumeTarget = model.resumeTarget
    DialogActionButton(
        text = if (resumeTarget != null) uiText(UiStringKey.Continue) else uiText(UiStringKey.Watch5af041),
        primary = true,
        modifier = focus.primaryModifier(),
        onClick = if (resumeTarget != null) {
            { actions.onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) }
        } else {
            { actions.onPlayVideo(watchVideo) }
        },
    )
}
