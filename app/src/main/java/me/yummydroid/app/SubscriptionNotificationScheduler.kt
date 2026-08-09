package me.yummydroid.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage

object SubscriptionNotificationScheduler {
    private val schedulerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YummyDroidNotifications").apply {
            isDaemon = true
        }
    }

    fun configure(context: Context, enabled: Boolean, runImmediately: Boolean = true) {
        val appContext = context.applicationContext
        if (!enabled) {
            cancel(appContext)
            return
        }
        SubscriptionNotificationChannels.create(appContext)
        val unreadCount = AuthStorage(appContext).readProfile()?.unreadNotifications ?: 0
        SubscriptionNotificationBadge.update(appContext, unreadCount)
        schedule(appContext)
        if (runImmediately) {
            runOnce(appContext)
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
        configure(
            context = appContext,
            enabled = storedNotificationsEnabled(appContext),
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
            PERIODIC_WORK_INTERVAL_MINUTES,
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

    fun handleAlarm(context: Context) {
        val appContext = context.applicationContext
        if (!storedNotificationsEnabled(appContext)) {
            cancel(appContext)
            return
        }
        runOnce(appContext)
        scheduleNextAlarm(appContext)
    }

    fun handleAlarmAsync(context: Context) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            handleAlarm(appContext)
        }
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
        val triggerAt = System.currentTimeMillis() + ALARM_INTERVAL_MS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            createAlarmPendingIntent(appContext),
        )
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
            notificationAlarmIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmPendingIntent(context: Context, flags: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            notificationAlarmIntent(context),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationAlarmIntent(context: Context): Intent {
        return Intent(context.applicationContext, SubscriptionNotificationReceiver::class.java).apply {
            action = SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS
        }
    }

    private fun notificationWorkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private fun storedNotificationsEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        return SubscriptionNotificationPolicy.canSchedule(
            notificationsEnabled = settings.notificationsEnabled,
            hasToken = authStorage.readToken() != null,
            hasProfile = authStorage.readProfile() != null,
        )
    }
}

private const val PERIODIC_WORK_NAME = "subscription_notification_periodic_check"
private const val IMMEDIATE_WORK_NAME = "subscription_notification_immediate_check"
private const val PERIODIC_WORK_INTERVAL_MINUTES = 15L
private const val ALARM_INTERVAL_MINUTES = 5L
private const val ALARM_INTERVAL_MS = ALARM_INTERVAL_MINUTES * 60 * 1000L
private const val BACKOFF_MINUTES = 30L
private const val ALARM_REQUEST_CODE = 28043
