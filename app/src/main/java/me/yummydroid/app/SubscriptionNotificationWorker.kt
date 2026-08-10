package me.yummydroid.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AuthStorage

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
