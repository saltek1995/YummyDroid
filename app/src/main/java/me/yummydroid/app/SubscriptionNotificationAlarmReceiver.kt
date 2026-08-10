package me.yummydroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSubscriptionNotificationAlarmAction(intent?.action)) return
        SubscriptionNotificationScheduler.handleAlarmAsync(context.applicationContext)
    }

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "me.yummydroid.app.CHECK_SUBSCRIPTION_NOTIFICATIONS"
    }
}

internal fun isSubscriptionNotificationAlarmAction(action: String?): Boolean =
    action == SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS
