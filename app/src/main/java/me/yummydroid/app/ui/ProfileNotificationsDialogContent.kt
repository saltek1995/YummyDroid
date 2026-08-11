package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.SiteNotification

@Composable
internal fun ProfileNotificationsDialogContent(
    notificationsState: LoadState<List<SiteNotification>>,
    onOpenNotification: (SiteNotification) -> Unit,
    onMarkRead: (SiteNotification) -> Unit,
    onDelete: (SiteNotification) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        when (notificationsState) {
            LoadState.Loading -> ProfileNotificationMessageBox(contentAlignment = Alignment.Center) {
                LoadingPane(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
            }
            is LoadState.Error -> ProfileNotificationMessageBox {
                InlineErrorMessage(message = notificationsState.message)
            }
            is LoadState.Ready -> ProfileNotificationsReadyContent(
                notifications = notificationsState.data,
                onOpenNotification = onOpenNotification,
                onMarkRead = onMarkRead,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun ProfileNotificationsReadyContent(
    notifications: List<SiteNotification>,
    onOpenNotification: (SiteNotification) -> Unit,
    onMarkRead: (SiteNotification) -> Unit,
    onDelete: (SiteNotification) -> Unit,
) {
    if (notifications.isEmpty()) {
        ProfileNotificationMessageBox {
            Text(
                text = uiText(UiStringKey.NoNotifications),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            notifications,
            key = { notification -> "profile-notification:${notification.id}" },
        ) { notification ->
            ProfileNotificationRow(
                notification = notification,
                onOpen = { onOpenNotification(notification) },
                onMarkRead = { onMarkRead(notification) },
                onDelete = { onDelete(notification) },
            )
        }
    }
}

@Composable
private fun ProfileNotificationMessageBox(
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 460.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = contentAlignment,
        content = content,
    )
}
