package me.yummydroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private val SubscriptionNotificationRescheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)

class SubscriptionNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSubscriptionNotificationRescheduleAction(intent?.action)) return
        SubscriptionNotificationScheduler.configureFromStoredStateAsync(
            context = context.applicationContext,
            runImmediately = false,
        )
    }
}

internal fun isSubscriptionNotificationRescheduleAction(action: String?): Boolean =
    action in SubscriptionNotificationRescheduleActions
