package me.yummydroid.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.readyDataOrNull

@Composable
internal fun ProfileNotificationsDialog(
    notificationsState: LoadState<List<SiteNotification>>,
    onOpenNotification: (SiteNotification) -> Unit,
    onMarkRead: (SiteNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (SiteNotification) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Notifications)) },
        text = {
            ProfileNotificationsDialogContent(
                notificationsState = notificationsState,
                onOpenNotification = onOpenNotification,
                onMarkRead = onMarkRead,
                onDelete = onDelete,
            )
        },
        confirmButton = {
            ProfileNotificationsDialogActions(
                canMarkAllRead = notificationsState.readyDataOrNull()?.any { !it.viewed } == true,
                onRefresh = onRefresh,
                onMarkAllRead = onMarkAllRead,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
private fun ProfileNotificationsDialogActions(
    canMarkAllRead: Boolean,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogActionRow {
        DialogActionButton(
            text = uiText(UiStringKey.Refresh),
            onClick = onRefresh,
            compact = true,
        )
        DialogActionButton(
            text = uiText(UiStringKey.MarkAllRead),
            onClick = onMarkAllRead,
            enabled = canMarkAllRead,
            compact = true,
        )
        DialogActionButton(
            text = uiText(UiStringKey.Close),
            primary = true,
            onClick = onDismiss,
            compact = true,
        )
    }
}
