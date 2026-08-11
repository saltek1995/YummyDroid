package me.yummydroid.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun DetailsHeroActionDialogs(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    state: DetailsHeroActionDialogState,
) {
    val selectedDownloadVideo = detailsHeroSelectedDownloadVideo(model.resumeTarget, model.watchVideo)
    if (state.downloadOpen && selectedDownloadVideo != null) {
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
    if (state.resetOpen) {
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
