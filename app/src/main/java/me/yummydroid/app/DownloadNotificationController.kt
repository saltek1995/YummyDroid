package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import me.yummydroid.app.data.AppSettingsStorage

internal class DownloadNotificationController(
    private val service: Service,
    private val settingsStorage: AppSettingsStorage,
) {
    @Volatile
    private var foregroundStarted = false
    @Volatile
    private var notificationStartedAtMs = 0L
    private val updateGate = NotificationUpdateGate(NOTIFICATION_UPDATE_INTERVAL_MS)

    fun start() {
        startForeground(notification())
    }

    fun update() {
        if (!foregroundStarted) {
            start()
            return
        }
        if (!updateGate.shouldPost(force = false)) return
        notificationManager.notify(NOTIFICATION_ID, notification())
    }

    fun createChannel() {
        val language = settingsStorage.read().contentLanguage
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            service.applicationContext.localizedString(R.string.ui_download_channel_name, language),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = service.applicationContext.localizedString(
                R.string.ui_download_channel_description,
                language,
            )
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun finish() {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        notificationStartedAtMs = 0L
        updateGate.reset()
        service.stopSelf()
    }

    private fun startForeground(notification: Notification) {
        ensureNotificationStartedAtMs()
        updateGate.shouldPost(force = true)
        if (foregroundStarted) {
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            service.startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun notification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val language = settingsStorage.read().contentLanguage
        val summary = DownloadCenter.state.value.notificationSummary(service.applicationContext, language)
        return Notification.Builder(service, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setContentIntent(pendingIntent)
            .setOngoing(summary.ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setWhen(ensureNotificationStartedAtMs())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setLocalOnly(true)
            .setProgress(summary.progressMax, summary.progress, summary.indeterminate)
            .build()
    }

    private fun ensureNotificationStartedAtMs(): Long {
        val startedAt = notificationStartedAtMs
        if (startedAt > 0L) return startedAt
        val now = System.currentTimeMillis()
        notificationStartedAtMs = now
        return now
    }

    private val notificationManager: NotificationManager
        get() = service.getSystemService(NotificationManager::class.java)
}

private const val DOWNLOAD_CHANNEL_ID = "offline_downloads"
private const val NOTIFICATION_ID = 9104
private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L
