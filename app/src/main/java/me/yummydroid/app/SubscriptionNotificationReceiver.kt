package me.yummydroid.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.ApiHttpException
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.isUnauthorizedApiError

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK_SUBSCRIPTIONS) return
        SubscriptionNotificationScheduler.runOnce(context.applicationContext)
    }

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "me.yummydroid.app.CHECK_SUBSCRIPTION_NOTIFICATIONS"
    }
}

class SubscriptionNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in RESCHEDULE_ACTIONS) return
        SubscriptionNotificationScheduler.configureFromStoredState(
            context = context.applicationContext,
            runImmediately = false,
        )
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

class SubscriptionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            SubscriptionNotificationSync.check(applicationContext)
            Result.success()
        } catch (throwable: Throwable) {
            when {
                throwable is CancellationException -> throw throwable
                throwable is CaptchaRequiredException -> Result.success()
                throwable.isUnauthorizedApiError() -> {
                    AuthStorage(applicationContext).clear()
                    SubscriptionNotificationBadge.clear(applicationContext)
                    Result.success()
                }
                throwable is ApiHttpException && throwable.statusCode in 400..499 -> Result.success()
                throwable is IOException -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}

object SubscriptionNotificationScheduler {
    private const val PERIODIC_WORK_NAME = "subscription_notification_periodic_check"
    private const val IMMEDIATE_WORK_NAME = "subscription_notification_immediate_check"
    private const val INTERVAL_MINUTES = 15L
    private const val BACKOFF_MINUTES = 30L

    fun configure(context: Context, enabled: Boolean, runImmediately: Boolean = true) {
        val appContext = context.applicationContext
        if (enabled) {
            SubscriptionNotificationChannels.create(appContext)
            val unreadCount = AuthStorage(appContext).readProfile()?.unreadNotifications ?: 0
            SubscriptionNotificationBadge.update(appContext, unreadCount)
            schedule(appContext)
            if (runImmediately) {
                runOnce(appContext)
            }
        } else {
            cancel(appContext)
        }
    }

    fun configureFromStoredState(context: Context, runImmediately: Boolean = false) {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        val hasAuth = authStorage.readToken() != null && authStorage.readProfile() != null
        configure(
            context = appContext,
            enabled = settings.notificationsEnabled && hasAuth,
            runImmediately = runImmediately,
        )
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionNotificationWorker>(
            INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(notificationWorkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SubscriptionNotificationWorker>()
            .setConstraints(notificationWorkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        SubscriptionNotificationBadge.clear(context)
    }

    private fun notificationWorkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

private object SubscriptionNotificationSync {
    private const val PROFILE_NOTIFICATION_LIMIT = 50
    private const val MAX_NOTIFICATIONS_PER_CHECK = 8
    private const val MIN_CHECK_SPACING_MS = 10 * 60 * 1000L
    private const val EPISODE_TYPE = "anime_episode"
    private const val NEW_EPISODE_SUB_TYPE = "new_episode"

    suspend fun check(context: Context) {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        val token = authStorage.readToken()
        val profile = authStorage.readProfile()
        if (!settings.notificationsEnabled || token == null || profile == null) {
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
        val notifications = repository.getProfileNotifications(limit = PROFILE_NOTIFICATION_LIMIT)
            .sortedBy { it.dateSeconds }
        store.markCheckRun()

        val unreadCount = notifications.count { !it.viewed }
        authStorage.saveProfile(profile.copy(unreadNotifications = unreadCount))
        SubscriptionNotificationBadge.update(appContext, unreadCount)

        val episodeNotifications = notifications
            .filter { !it.viewed && it.isNewEpisodeNotification() }

        if (!store.isInitialized()) {
            store.markSeen(episodeNotifications)
            store.markInitialized()
            return
        }

        val fresh = episodeNotifications
            .filterNot(store::isSeen)
            .distinctBy(store::eventKey)
            .takeLast(MAX_NOTIFICATIONS_PER_CHECK)
        if (fresh.isEmpty()) return

        if (appContext.canPostNotifications()) {
            SubscriptionNotificationChannels.create(appContext)
            val manager = appContext.getSystemService(NotificationManager::class.java)
            fresh.forEach { notification ->
                manager.notify(
                    notification.id.notificationId(),
                    notification.toAndroidNotification(appContext),
                )
            }
        }
        store.markSeen(fresh)
    }

    private fun SiteNotification.isNewEpisodeNotification(): Boolean {
        return type == EPISODE_TYPE && subType == NEW_EPISODE_SUB_TYPE
    }

    private fun SiteNotification.toAndroidNotification(context: Context): Notification {
        val titleText = title.ifBlank { context.getString(R.string.notification_new_episode_title) }
        val bodyText = text.ifBlank { context.getString(R.string.notification_new_episode_text) }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.notificationId(),
            Intent(context, MainActivity::class.java).apply {
                animeIdForOpen()?.let { putExtra("anime_id", it) }
            },
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

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

internal object SubscriptionNotificationBadge {
    private const val BADGE_NOTIFICATION_ID = 28042

    fun update(context: Context, unreadCount: Int) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val count = unreadCount.coerceAtLeast(0)
        if (count == 0 || !appContext.canPostNotifications()) {
            manager.cancel(BADGE_NOTIFICATION_ID)
            return
        }

        SubscriptionNotificationChannels.create(appContext)
        val notification = Notification.Builder(appContext, SubscriptionNotificationChannels.BADGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(appContext.getString(R.string.notification_unread_title))
            .setContentText(
                appContext.resources.getQuantityString(
                    R.plurals.notification_unread_count,
                    count,
                    count,
                ),
            )
            .setContentIntent(profilePendingIntent(appContext))
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

    private fun profilePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            BADGE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

private object SubscriptionNotificationChannels {
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

private fun Context.canPostNotifications(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
