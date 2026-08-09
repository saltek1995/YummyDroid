package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.DownloadSpeedLimiter
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.YummyAnimeRepository

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsStorage: AppSettingsStorage
    private lateinit var intentProcessor: DownloadIntentProcessor
    @Volatile
    private var downloadSpeedLimitBytesPerSecond = AppSettings().downloadSpeedLimitBytesPerSecond
    private val downloadSpeedSettingsLock = Any()
    private var lastDownloadSpeedSettingsReadMs = 0L
    @Volatile
    private var foregroundStarted = false
    @Volatile
    private var downloadNotificationStartedAtMs = 0L
    private val notificationUpdateGate = NotificationUpdateGate(NOTIFICATION_UPDATE_INTERVAL_MS)

    override fun onCreate() {
        super.onCreate()
        settingsStorage = AppSettingsStorage(applicationContext)
        val settings = settingsStorage.read()
        downloadSpeedLimitBytesPerSecond = settings.downloadSpeedLimitBytesPerSecond
        lastDownloadSpeedSettingsReadMs = System.currentTimeMillis()
        val speedLimiter = DownloadSpeedLimiter(::currentDownloadSpeedLimitBytesPerSecond)
        DownloadCenter.initialize(applicationContext)
        val repository = YummyAnimeRepository(
            context = applicationContext,
            siteDomainResolver = SiteDomainResolver(candidates = settings.siteDomains),
            authStorage = AuthStorage(applicationContext),
            downloadBandwidthLimiter = speedLimiter,
        )
        val taskRuntime = DownloadTaskRuntime(
            context = applicationContext,
            settingsStorage = settingsStorage,
            updateNotification = ::updateNotification,
        )
        val videoProcessor = DownloadVideoProcessor(
            context = applicationContext,
            repository = repository,
            settingsStorage = settingsStorage,
            downloadSlots = Semaphore(settings.downloadParallelism.coerceIn(1, 4)),
            taskRuntime = taskRuntime,
        )
        intentProcessor = DownloadIntentProcessor(
            context = applicationContext,
            repository = repository,
            settingsStorage = settingsStorage,
            taskRuntime = taskRuntime,
            videoProcessor = videoProcessor,
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startDownloadForeground(notification())
        if (intent == null) {
            finishForeground()
            return START_NOT_STICKY
        }
        scope.launch {
            intentProcessor.process(intent)
            if (DownloadCenter.state.value.activeTasks.isEmpty()) {
                finishForeground()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun currentDownloadSpeedLimitBytesPerSecond(): Long {
        val now = System.currentTimeMillis()
        synchronized(downloadSpeedSettingsLock) {
            if (now - lastDownloadSpeedSettingsReadMs >= SPEED_LIMIT_SETTINGS_REFRESH_MS) {
                downloadSpeedLimitBytesPerSecond = settingsStorage.read().downloadSpeedLimitBytesPerSecond
                lastDownloadSpeedSettingsReadMs = now
            }
            return downloadSpeedLimitBytesPerSecond
        }
    }

    private fun startDownloadForeground(notification: Notification) {
        ensureDownloadNotificationStartedAtMs()
        notificationUpdateGate.shouldPost(force = true)
        if (foregroundStarted) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification() {
        if (!foregroundStarted) {
            startDownloadForeground(notification())
            return
        }
        if (!notificationUpdateGate.shouldPost(force = false)) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun ensureDownloadNotificationStartedAtMs(): Long {
        val startedAt = downloadNotificationStartedAtMs
        if (startedAt > 0L) return startedAt
        val now = System.currentTimeMillis()
        downloadNotificationStartedAtMs = now
        return now
    }

    private fun notification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val language = settingsStorage.read().contentLanguage
        val summary = DownloadCenter.state.value.notificationSummary(applicationContext, language)
        return Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setContentIntent(pendingIntent)
            .setOngoing(summary.ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setWhen(ensureDownloadNotificationStartedAtMs())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setLocalOnly(true)
            .setProgress(summary.progressMax, summary.progress, summary.indeterminate)
            .build()
    }

    private fun createNotificationChannel() {
        val language = settingsStorage.read().contentLanguage
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            applicationContext.localizedString(R.string.ui_download_channel_name, language),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = applicationContext.localizedString(R.string.ui_download_channel_description, language)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun finishForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        downloadNotificationStartedAtMs = 0L
        notificationUpdateGate.reset()
        stopSelf()
    }

    companion object {
        fun enqueueTask(context: Context, task: DownloadTaskUi) {
            context.startDownloadService(
                downloadServiceIntent(context)
                    .setAction(downloadActionForTask(task))
                    .putExtra(DOWNLOAD_EXTRA_TASK_ID, task.id)
                    .putExtra(DOWNLOAD_EXTRA_PLAN_ID, task.planId)
                    .putExtra(DOWNLOAD_EXTRA_ANIME_ID, task.animeId)
                    .putExtra(DOWNLOAD_EXTRA_VIDEO_ID, task.videoId ?: 0L)
                    .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, task.groupKey)
                    .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, task.preferredQualityName),
            )
        }

        fun enqueueVideo(
            context: Context,
            animeId: Long,
            videoId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            DownloadCenter.initialize(context)
            context.startDownloadService(
                downloadServiceIntent(context)
                    .setAction(DOWNLOAD_ACTION_VIDEO)
                    .putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
                    .putExtra(DOWNLOAD_EXTRA_VIDEO_ID, videoId)
                    .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, groupKey.orEmpty())
                    .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, quality.name),
            )
        }

        fun enqueueAnime(
            context: Context,
            animeId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            DownloadCenter.initialize(context)
            context.startDownloadService(
                downloadServiceIntent(context)
                    .setAction(DOWNLOAD_ACTION_ANIME)
                    .putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
                    .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, groupKey.orEmpty())
                    .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, quality.name),
            )
        }

        fun enqueuePlan(context: Context, planId: String) {
            if (planId.isBlank()) return
            DownloadCenter.initialize(context)
            context.startDownloadService(
                downloadServiceIntent(context)
                    .setAction(DOWNLOAD_ACTION_PLAN)
                    .putExtra(DOWNLOAD_EXTRA_PLAN_ID, planId),
            )
        }

        private fun downloadServiceIntent(context: Context): Intent {
            return Intent(context, DownloadService::class.java)
        }
    }
}

private fun Context.startDownloadService(intent: Intent) {
    startForegroundService(intent)
}

internal fun downloadActionForTask(task: DownloadTaskUi): String {
    return when {
        task.isBatchSummary && task.planId.isNotBlank() -> DOWNLOAD_ACTION_PLAN
        task.videoId == null -> DOWNLOAD_ACTION_ANIME
        else -> DOWNLOAD_ACTION_VIDEO
    }
}

internal const val DOWNLOAD_ACTION_VIDEO = "me.yummydroid.app.DOWNLOAD_VIDEO"
internal const val DOWNLOAD_ACTION_ANIME = "me.yummydroid.app.DOWNLOAD_ANIME"
internal const val DOWNLOAD_ACTION_PLAN = "me.yummydroid.app.DOWNLOAD_PLAN"
internal const val DOWNLOAD_EXTRA_TASK_ID = "task_id"
internal const val DOWNLOAD_EXTRA_PLAN_ID = "plan_id"
internal const val DOWNLOAD_EXTRA_ANIME_ID = "anime_id"
internal const val DOWNLOAD_EXTRA_VIDEO_ID = "video_id"
internal const val DOWNLOAD_EXTRA_GROUP_KEY = "group_key"
internal const val DOWNLOAD_EXTRA_QUALITY_NAME = "quality_name"

private const val DOWNLOAD_CHANNEL_ID = "offline_downloads"
private const val NOTIFICATION_ID = 9104
private const val SPEED_LIMIT_SETTINGS_REFRESH_MS = 1_000L
private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L
