package me.yummydroid.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import me.yummydroid.app.animeIdForOpen
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.LoadState

@Composable
internal fun ProfileChildDialogs(
    profileAvailable: Boolean,
    subscriptionsOpen: Boolean,
    notificationsOpen: Boolean,
    subscriptionsState: LoadState<List<VideoSubscription>>,
    notificationsState: LoadState<List<SiteNotification>>,
    context: Context,
    openSiteError: String,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onMarkNotificationRead: (SiteNotification) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onDeleteNotification: (SiteNotification) -> Unit,
    onRefreshNotifications: () -> Unit,
    onCloseSubscriptions: () -> Unit,
    onCloseNotifications: () -> Unit,
    onDismissProfile: () -> Unit,
) {
    if (subscriptionsOpen && profileAvailable) {
        ProfileSubscriptionsDialog(
            subscriptionsState = subscriptionsState,
            onOpenAnime = { animeId ->
                onCloseSubscriptions()
                onDismissProfile()
                onOpenAnime(animeId)
            },
            onUnsubscribe = onUnsubscribe,
            onDismiss = onCloseSubscriptions,
        )
    }
    if (notificationsOpen && profileAvailable) {
        ProfileNotificationsDialog(
            notificationsState = notificationsState,
            onOpenNotification = { notification ->
                onMarkNotificationRead(notification)
                val animeId = notification.animeIdForOpen()
                if (animeId != null) {
                    onCloseNotifications()
                    onDismissProfile()
                    onOpenAnime(animeId)
                } else if (notification.clickUrl.isNotBlank()) {
                    openExternalUrl(context, notification.clickUrl, openSiteError)
                }
            },
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = onMarkAllNotificationsRead,
            onDelete = onDeleteNotification,
            onRefresh = onRefreshNotifications,
            onDismiss = onCloseNotifications,
        )
    }
}

internal fun openExternalUrl(
    context: Context,
    url: String,
    errorMessage: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}
