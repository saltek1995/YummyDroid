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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profilePlayerTitle
import me.yummydroid.app.data.profileVoiceTitle
import me.yummydroid.app.formatNotificationTimestamp
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import java.util.Locale

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
                hasError = notificationsState is LoadState.Error,
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
    hasError: Boolean,
    canMarkAllRead: Boolean,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogActionRow {
        DialogActionButton(
            text = uiText(UiStringKey.Refresh),
            primary = hasError,
            onClick = onRefresh,
            compact = true,
        )
        if (canMarkAllRead) {
            DialogActionButton(
                text = uiText(UiStringKey.MarkAllRead),
                onClick = onMarkAllRead,
                compact = true,
            )
        }
        DialogActionButton(
            text = uiText(UiStringKey.Close),
            primary = !hasError,
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
        ProfileNotificationMessageBox(contentAlignment = Alignment.Center) {
            ProfileEmptyState(
                title = uiText(UiStringKey.NoNotifications),
                icon = Icons.Default.Notifications,
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

@Composable
internal fun ProfileSubscriptionsDialog(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasSubscriptionItems = subscriptionsState.readyDataOrNull()
        ?.profileSubscriptionsForManagement()
        ?.isNotEmpty() == true
    AlertDialog(
        modifier = Modifier
            .profileSubscriptionsDialogSize(hasSubscriptionItems)
            .yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Subscriptions)) },
        text = {
            ProfileSubscriptionsContent(subscriptionsState, onOpenAnime, onUnsubscribe)
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Refresh),
                    primary = subscriptionsState is LoadState.Error,
                    onClick = onRefresh,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    primary = subscriptionsState !is LoadState.Error,
                    onClick = onDismiss,
                )
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}

private fun Modifier.profileSubscriptionsDialogSize(hasSubscriptionItems: Boolean): Modifier {
    val widthFraction = if (hasSubscriptionItems) 0.94f else 0.86f
    val sized = fillMaxWidth(widthFraction)
    return if (hasSubscriptionItems) sized.fillMaxHeight(0.9f) else sized
}

@Composable
private fun ProfileSubscriptionsContent(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
) {
    when (subscriptionsState) {
        LoadState.Loading -> ProfileSubscriptionsStatusPane(Alignment.Center) {
            LoadingPane(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }
        is LoadState.Error -> ProfileSubscriptionsStatusPane(Alignment.CenterStart) {
            InlineErrorMessage(message = subscriptionsState.message)
        }
        is LoadState.Ready -> ProfileSubscriptionsReadyContent(
            subscriptions = subscriptionsState.data.profileSubscriptionsForManagement(),
            onOpenAnime = onOpenAnime,
            onUnsubscribe = onUnsubscribe,
        )
    }
}

@Composable
private fun ProfileSubscriptionsStatusPane(
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 420.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
private fun ProfileSubscriptionsReadyContent(
    subscriptions: List<VideoSubscription>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
) {
    if (subscriptions.isEmpty()) {
        ProfileSubscriptionsStatusPane(Alignment.Center) {
            ProfileEmptyState(
                title = uiText(UiStringKey.NoSubscriptions),
                icon = Icons.Default.Subscriptions,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            subscriptions,
            key = { index, subscription ->
                "profile-subscription:${subscription.profileDisplayKey}:$index"
            },
        ) { _, subscription ->
            ProfileSubscriptionCard(
                subscription = subscription,
                onOpenAnime = { onOpenAnime(subscription.animeId) },
                onUnsubscribe = { onUnsubscribe(subscription) },
            )
        }
    }
}

internal fun List<VideoSubscription>.profileSubscriptionsForManagement(): List<VideoSubscription> =
    distinctBy { it.profileDisplayKey }
        .sortedWith(
            compareBy<VideoSubscription> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.profileVoiceTitle.lowercase(Locale.ROOT) },
        )

@Composable
private fun ProfileSubscriptionCard(
    subscription: VideoSubscription,
    onOpenAnime: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    AnimeCard(
        anime = subscription.profileAnimeCardData(uiText(UiStringKey.Anime)),
        metaText = subscription.profileSubscriptionMetaText(),
        onClick = onOpenAnime,
        topEndContent = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            ) {
                IconButton(
                    onClick = onUnsubscribe,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = uiText(UiStringKey.Disable),
                    )
                }
            }
        },
    )
}

internal fun VideoSubscription.profileSubscriptionMetaText(): String =
    listOf(profileVoiceTitle, profilePlayerTitle)
        .filter(String::isNotBlank)
        .joinToString(" \u2022 ")

private fun VideoSubscription.profileAnimeCardData(fallbackTitle: String): Anime = Anime(
    id = animeId,
    title = title.ifBlank { fallbackTitle },
    description = "",
    posterUrl = posterUrl,
    animeUrl = "",
    year = null,
    rating = null,
    views = 0L,
    status = "",
    type = "",
    genres = emptyList(),
    blockedIn = emptyList(),
)

@Composable
private fun ProfileEmptyState(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(YummyRadii.mediumShape)
            .background(yummySurfaceColor(YummySurfaceRole.Panel))
            .border(yummySurfaceBorder(YummySurfaceRole.Panel), YummyRadii.mediumShape)
            .padding(horizontal = YummySpacing.xl, vertical = YummySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YummySpacing.md),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
