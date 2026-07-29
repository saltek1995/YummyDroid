package me.yummydroid.app

import android.app.Application

class YummyDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SubscriptionNotificationScheduler.configureFromStoredState(
            context = this,
            runImmediately = false,
        )
    }
}
