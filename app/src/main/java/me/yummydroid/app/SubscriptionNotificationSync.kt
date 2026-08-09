package me.yummydroid.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.YummyAnimeRepository

internal object SubscriptionNotificationSync {
    private const val MIN_CHECK_SPACING_MS = 5 * 60 * 1000L

    suspend fun check(context: Context) {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        val token = authStorage.readToken()
        val profile = authStorage.readProfile()
        if (
            !SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = settings.notificationsEnabled,
                hasToken = token != null,
                hasProfile = profile != null,
            )
        ) {
            SubscriptionNotificationBadge.clear(appContext)
            return
        }

        val store = SubscriptionNotificationStore(appContext)
        if (!store.shouldRunCheck(MIN_CHECK_SPACING_MS)) return

        val repository = YummyAnimeRepository(
            context = appContext,
            siteDomainResolver = SiteDomainResolver(candidates = settings.siteDomains),
            authStorage = authStorage,
        )
        val notifications = repository.getProfileNotifications(limit = PROFILE_NOTIFICATION_FETCH_LIMIT)
            .sortedBy { it.dateSeconds }
        store.markCheckRun()

        AndroidProfileNotificationRuntime(appContext, authStorage).synchronize(
            profileId = requireNotNull(profile).id,
            notifications = notifications,
        )

        val episodeNotifications = SubscriptionNotificationPolicy
            .newEpisodeNotifications(notifications)
        if (!store.isInitialized()) {
            store.markSeen(episodeNotifications)
            store.markInitialized()
            return
        }

        val fresh = SubscriptionNotificationPolicy.freshNotifications(
            notifications = episodeNotifications,
            isSeen = store::isSeen,
            eventKey = store::eventKey,
        )
        if (fresh.isEmpty()) return

        postNotifications(appContext, fresh)
        store.markSeen(fresh)
    }

    private fun postNotifications(context: Context, notifications: List<SiteNotification>) {
        if (!context.canPostNotifications()) return
        SubscriptionNotificationChannels.create(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        notifications.forEach { notification ->
            manager.notify(
                SubscriptionNotificationPolicy.notificationId(notification.id),
                notification.toAndroidNotification(context),
            )
        }
    }

    private fun SiteNotification.toAndroidNotification(context: Context): Notification {
        val titleText = title.ifBlank { context.getString(R.string.notification_new_episode_title) }
        val bodyText = text.ifBlank { context.getString(R.string.notification_new_episode_text) }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SubscriptionNotificationPolicy.notificationId(id),
            profileNotificationsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, SubscriptionNotificationChannels.EPISODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(Notification.BigTextStyle().bigText(bodyText.ifBlank { titleText }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
            .build()
    }
}
