package me.yummydroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AuthStorage

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK_SUBSCRIPTIONS) return
        SubscriptionNotificationScheduler.handleAlarmAsync(context.applicationContext)
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
            when (SubscriptionNotificationPolicy.classifyWorkerFailure(throwable)) {
                NotificationWorkerFailure.Rethrow -> throw throwable
                NotificationWorkerFailure.ClearAuth -> clearAuthentication()
                NotificationWorkerFailure.Success -> Result.success()
                NotificationWorkerFailure.Retry -> Result.retry()
                NotificationWorkerFailure.Failure -> Result.failure()
            }
        }
    }

    private fun clearAuthentication(): Result {
        AuthStorage(applicationContext).clear()
        SubscriptionNotificationBadge.clear(applicationContext)
        return Result.success()
    }
}
