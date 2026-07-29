package me.yummydroid.app

import android.Manifest
import android.app.AlarmManager
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
import java.util.concurrent.Executors
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
        val appContext = context.applicationContext
        SubscriptionNotificationScheduler.runOnceAsync(appContext)
        SubscriptionNotificationScheduler.scheduleNextAlarm(appContext)
    }

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "me.yummydroid.app.CHECK_SUBSCRIPTION_NOTIFICATIONS"
    }
}

class SubscriptionNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in RESCHEDULE_ACTIONS) return
        SubscriptionNotificationScheduler.configureFromStoredStateAsync(
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
    private const val INTERVAL_MS = INTERVAL_MINUTES * 60 * 1000L
    private const val BACKOFF_MINUTES = 30L
    private const val ALARM_REQUEST_CODE = 28043
    private val schedulerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YummyDroidNotifications").apply {
            isDaemon = true
        }
    }

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

    fun configureAsync(context: Context, enabled: Boolean, runImmediately: Boolean = true) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            configure(
                context = appContext,
                enabled = enabled,
                runImmediately = runImmediately,
            )
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

    fun configureFromStoredStateAsync(context: Context, runImmediately: Boolean = false) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            configureFromStoredState(
                context = appContext,
                runImmediately = runImmediately,
            )
        }
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
        scheduleNextAlarm(context)
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

    fun runOnceAsync(context: Context) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            runOnce(appContext)
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        cancelAlarm(context)
        SubscriptionNotificationBadge.clear(context)
    }

    fun scheduleNextAlarm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + INTERVAL_MS
        val pendingIntent = createAlarmPendingIntent(appContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context) {
        val appContext = context.applicationContext
        val pendingIntent = alarmPendingIntent(appContext, PendingIntent.FLAG_NO_CREATE) ?: return
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createAlarmPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            Intent(context.applicationContext, SubscriptionNotificationReceiver::class.java).apply {
                action = SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmPendingIntent(context: Context, flags: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            Intent(context.applicationContext, SubscriptionNotificationReceiver::class.java).apply {
                action = SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
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

        val unreadNotifications = notifications.filterNot { it.viewed }
        val unreadCount = unreadNotifications.size
        authStorage.saveProfile(profile.copy(unreadNotifications = unreadCount))
        store.saveUnreadShadeItems(unreadNotifications)
        SubscriptionNotificationBadge.update(appContext, unreadNotifications)

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

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
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
        val unread = unreadNotifications.filterNot { it.viewed }
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

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
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
            else -> shadeItems.firstOrNull()?.text?.takeIf { it.isNotBlank() } ?: countText
        }
        return NotificationShadeContent(
            title = title,
            text = text,
            inboxLines = titles.ifEmpty { listOf(countText) },
        )
    }

    internal fun List<NotificationShadeItem>.notificationShadeTitles(fallbackTitle: String): List<String> {
        return sortedByDescending { it.dateSeconds }
            .map { item -> item.title.ifBlank { item.text }.ifBlank { fallbackTitle } }
            .distinct()
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
