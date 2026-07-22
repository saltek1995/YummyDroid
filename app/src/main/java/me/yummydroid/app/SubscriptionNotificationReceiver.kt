package me.yummydroid.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.YummyAnimeRepository

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = AppSettingsStorage(appContext).read()
                val authStorage = AuthStorage(appContext)
                val hasAuth = authStorage.readToken() != null && authStorage.readProfile() != null
                if (settings.notificationsEnabled && hasAuth) {
                    checkAndNotify(appContext, settings.siteDomains, authStorage)
                    SubscriptionNotificationScheduler.schedule(appContext)
                } else {
                    SubscriptionNotificationScheduler.cancel(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkAndNotify(
        context: Context,
        siteDomains: List<String>,
        authStorage: AuthStorage,
    ) {
        if (!context.canPostNotifications()) return
        val store = SubscriptionNotificationStore(context)
        val repository = YummyAnimeRepository(
            context = context,
            siteDomainResolver = SiteDomainResolver(candidates = siteDomains),
            authStorage = authStorage,
        )
        val notifications = runCatching { repository.getNewEpisodeNotifications(limit = 50) }
            .getOrDefault(emptyList())
            .filter { !it.viewed }
            .sortedBy { it.dateSeconds }

        if (!store.isInitialized()) {
            store.markSeen(notifications)
            store.markInitialized()
            return
        }

        val fresh = notifications
            .filterNot(store::isSeen)
            .distinctBy(store::eventKey)
            .takeLast(MAX_NOTIFICATIONS_PER_CHECK)
        if (fresh.isEmpty()) return

        createNotificationChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        fresh.forEach { notification ->
            manager.notify(notification.id.notificationId(), notification.toAndroidNotification(context))
        }
        store.markSeen(fresh)
    }

    private fun SiteNotification.toAndroidNotification(context: Context): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.notificationId(),
            Intent(context, MainActivity::class.java).apply {
                animeIdForOpen()?.let { putExtra("anime_id", it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title.ifBlank { "Новая серия" })
            .setContentText(text.ifBlank { "Вышла новая серия по подписке" })
            .setStyle(Notification.BigTextStyle().bigText(text.ifBlank { title }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun SiteNotification.animeIdForOpen(): Long? {
        val fromUrl = Regex("""-(\d+)(?:[/#?]|$)""")
            .find(clickUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        return fromUrl ?: objectId.takeIf { it > 0L }
    }

    private fun Long.notificationId(): Int = (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "me.yummydroid.app.CHECK_SUBSCRIPTION_NOTIFICATIONS"
        private const val CHANNEL_ID = "anime_episode_notifications"
        private const val CHANNEL_NAME = "Новые серии"
        private const val MAX_NOTIFICATIONS_PER_CHECK = 8

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Уведомления о новых сериях по подпискам на озвучки"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

object SubscriptionNotificationScheduler {
    private const val REQUEST_CODE = 28041
    private const val INTERVAL_MS = 15 * 60 * 1000L

    fun configure(context: Context, enabled: Boolean) {
        if (enabled) {
            SubscriptionNotificationReceiver.createNotificationChannel(context)
            schedule(context)
            context.sendBroadcast(checkIntent(context))
        } else {
            cancel(context)
        }
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            INTERVAL_MS,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            checkIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun checkIntent(context: Context): Intent {
        return Intent(context, SubscriptionNotificationReceiver::class.java)
            .setAction(SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS)
            .setPackage(context.packageName)
    }
}

private fun Context.canPostNotifications(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
