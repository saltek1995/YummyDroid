package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.formatNotificationTimestamp
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

// ProfileNotificationDialogs
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

// ProfileNotificationRow
@Composable
internal fun ProfileNotificationRow(
    notification: SiteNotification,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val unread = !notification.viewed
    val unreadAccent = MaterialTheme.colorScheme.secondary
    Surface(
        modifier = Modifier
            .dpadClickable(shape, onOpen)
            .then(notificationUnreadBorder(unread, unreadAccent, shape)),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ProfileNotificationUnreadIndicator(unread, unreadAccent)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ProfileNotificationHeader(notification, unread, unreadAccent)
                ProfileNotificationBody(notification, unread)
                ProfileNotificationActions(unread, onMarkRead, onDelete)
            }
        }
    }
}

private fun notificationUnreadBorder(
    unread: Boolean,
    accent: Color,
    shape: RoundedCornerShape,
): Modifier {
    return if (unread) Modifier.border(1.dp, accent.copy(alpha = 0.28f), shape) else Modifier
}

@Composable
private fun ProfileNotificationUnreadIndicator(unread: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .width(3.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (unread) accent.copy(alpha = 0.85f) else Color.Transparent),
    )
}

@Composable
private fun ProfileNotificationHeader(
    notification: SiteNotification,
    unread: Boolean,
    unreadAccent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatNotificationTimestamp(notification.dateSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (unread) ProfileNotificationUnreadBadge()
    }
}

@Composable
private fun ProfileNotificationUnreadBadge() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = uiText(UiStringKey.New),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProfileNotificationBody(notification: SiteNotification, unread: Boolean) {
    Text(
        text = notification.title.ifBlank { uiText(UiStringKey.Notifications) },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (unread) FontWeight.Black else FontWeight.Bold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    if (notification.text.isNotBlank()) {
        Text(
            text = notification.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileNotificationActions(
    unread: Boolean,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (unread) {
            ProfileNotificationActionChip(
                text = uiText(UiStringKey.MarkRead),
                onClick = onMarkRead,
            )
        }
        ProfileNotificationActionChip(
            text = uiText(UiStringKey.Delete),
            onClick = onDelete,
            destructive = true,
        )
    }
}

@Composable
private fun ProfileNotificationActionChip(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier.dpadClickable(shape, onClick),
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(destructive = destructive),
        border = yummyActionBorder(),
        shape = shape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

// ProfileNotificationsDialogContent
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
