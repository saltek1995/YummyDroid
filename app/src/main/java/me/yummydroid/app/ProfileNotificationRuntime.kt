package me.yummydroid.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.SiteNotification

internal interface ProfileNotificationRuntime {
    suspend fun synchronize(
        profileId: Long,
        notifications: List<SiteNotification>,
        cancelledNotificationIds: List<Long> = emptyList(),
    )
}

internal class AndroidProfileNotificationRuntime(
    context: Context,
    private val authStorage: AuthStorage = AuthStorage(context.applicationContext),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfileNotificationRuntime {
    private val appContext = context.applicationContext

    override suspend fun synchronize(
        profileId: Long,
        notifications: List<SiteNotification>,
        cancelledNotificationIds: List<Long>,
    ) = updateMutex.withLock {
        withContext(ioDispatcher) {
            cancelledNotificationIds.distinct().forEach { notificationId ->
                SubscriptionNotificationBadge.cancelNotification(appContext, notificationId)
            }

            val profile = authStorage.readProfile()
                ?.takeIf { it.id == profileId }
                ?: return@withContext
            val unreadNotifications = notifications.filterNot(SiteNotification::viewed)
            authStorage.saveProfile(profile.copy(unreadNotifications = unreadNotifications.size))
            SubscriptionNotificationBadge.update(appContext, unreadNotifications)
        }
    }

    private companion object {
        val updateMutex = Mutex()
    }
}

internal object SubscriptionNotificationBadge {
    private const val BADGE_NOTIFICATION_ID = 28042
    private const val MAX_INBOX_LINES = 5

    fun update(context: Context, unreadCount: Int) {
        val appContext = context.applicationContext
        if (unreadCount <= 0) {
            SubscriptionNotificationStore(appContext).clearUnreadShadeItems()
            appContext.getSystemService(NotificationManager::class.java).cancel(BADGE_NOTIFICATION_ID)
            return
        }
        update(
            context = appContext,
            unreadCount = unreadCount,
            shadeItems = SubscriptionNotificationStore(appContext).unreadShadeItems(),
        )
    }

    fun update(context: Context, unreadNotifications: List<SiteNotification>) {
        val appContext = context.applicationContext
        val unread = unreadNotifications.filterNot(SiteNotification::viewed)
        val store = SubscriptionNotificationStore(appContext)
        if (unread.isEmpty()) {
            store.clearUnreadShadeItems()
            appContext.getSystemService(NotificationManager::class.java).cancel(BADGE_NOTIFICATION_ID)
            return
        }
        store.saveUnreadShadeItems(unread)
        update(
            context = appContext,
            unreadCount = unread.size,
            shadeItems = unread.toNotificationShadeItems(),
        )
    }

    private fun update(
        context: Context,
        unreadCount: Int,
        shadeItems: List<NotificationShadeItem>,
    ) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val count = unreadCount.coerceAtLeast(0)
        if (count == 0 || !appContext.canPostNotifications()) {
            manager.cancel(BADGE_NOTIFICATION_ID)
            return
        }

        SubscriptionNotificationChannels.create(appContext)
        val countText = appContext.resources.getQuantityString(
            R.plurals.notification_unread_count,
            count,
            count,
        )
        val fallbackTitle = appContext.getString(R.string.notification_unread_title)
        val content = notificationShadeContent(
            unreadCount = count,
            shadeItems = shadeItems,
            fallbackTitle = fallbackTitle,
            countText = countText,
        )
        val inboxStyle = Notification.InboxStyle()
            .setBigContentTitle(countText)
            .setSummaryText(countText)
        content.inboxLines.forEach(inboxStyle::addLine)
        val notification = Notification.Builder(appContext, SubscriptionNotificationChannels.BADGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(inboxStyle)
            .setContentIntent(profileNotificationsPendingIntent(appContext))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setNumber(count)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
            .build()
        manager.notify(BADGE_NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(BADGE_NOTIFICATION_ID)
    }

    fun cancelNotification(context: Context, notificationId: Long) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(notificationId.notificationId())
    }

    private fun profileNotificationsPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            BADGE_NOTIFICATION_ID,
            profileNotificationsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun notificationShadeContent(
        unreadCount: Int,
        shadeItems: List<NotificationShadeItem>,
        fallbackTitle: String,
        countText: String,
    ): NotificationShadeContent {
        val titles = shadeItems.notificationShadeTitles(fallbackTitle).take(MAX_INBOX_LINES)
        val title = titles.firstOrNull() ?: fallbackTitle
        val text = when {
            titles.size >= 2 -> titles[1]
            unreadCount > 1 -> countText
            else -> shadeItems.firstOrNull()?.text?.takeIf(String::isNotBlank) ?: countText
        }
        return NotificationShadeContent(
            title = title,
            text = text,
            inboxLines = titles.ifEmpty { listOf(countText) },
        )
    }

    internal fun List<NotificationShadeItem>.notificationShadeTitles(fallbackTitle: String): List<String> {
        return sortedByDescending(NotificationShadeItem::dateSeconds)
            .map { item -> item.title.ifBlank { item.text }.ifBlank { fallbackTitle } }
            .distinct()
    }

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

internal data class NotificationShadeContent(
    val title: String,
    val text: String,
    val inboxLines: List<String>,
)

internal fun profileNotificationsIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_PROFILE_NOTIFICATIONS)
        .putExtra(EXTRA_OPEN_PROFILE_NOTIFICATIONS, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

internal fun Intent.requestsProfileNotifications(): Boolean {
    return requestsProfileNotifications(
        action = action,
        openExtra = getBooleanExtra(EXTRA_OPEN_PROFILE_NOTIFICATIONS, false),
    )
}

internal fun requestsProfileNotifications(action: String?, openExtra: Boolean): Boolean {
    return action == ACTION_OPEN_PROFILE_NOTIFICATIONS || openExtra
}

internal const val ACTION_OPEN_PROFILE_NOTIFICATIONS =
    "me.yummydroid.app.action.OPEN_PROFILE_NOTIFICATIONS"
internal const val EXTRA_OPEN_PROFILE_NOTIFICATIONS = "open_profile_notifications"

internal object SubscriptionNotificationChannels {
    const val EPISODE_CHANNEL_ID = "anime_episode_notifications"
    const val BADGE_CHANNEL_ID = "profile_notification_badge"

    fun create(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                EPISODE_CHANNEL_ID,
                context.getString(R.string.notification_episode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_episode_channel_description)
                setShowBadge(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                BADGE_CHANNEL_ID,
                context.getString(R.string.notification_badge_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_badge_channel_description)
                setShowBadge(true)
            },
        )
    }
}

internal fun Context.canPostNotifications(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
