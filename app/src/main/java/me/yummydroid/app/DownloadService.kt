package me.yummydroid.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
    private lateinit var speedSettings: DownloadSpeedSettings
    private lateinit var notificationController: DownloadNotificationController

    override fun onCreate() {
        super.onCreate()
        settingsStorage = AppSettingsStorage(applicationContext)
        val settings = settingsStorage.read()
        speedSettings = DownloadSpeedSettings(
            settingsStorage = settingsStorage,
            initialLimitBytesPerSecond = settings.downloadSpeedLimitBytesPerSecond,
            initialReadMs = System.currentTimeMillis(),
        )
        notificationController = DownloadNotificationController(this, settingsStorage)
        val speedLimiter = DownloadSpeedLimiter(speedSettings::currentLimitBytesPerSecond)
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
            updateNotification = notificationController::update,
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
        notificationController.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationController.start()
        if (intent == null) {
            notificationController.finish()
            return START_NOT_STICKY
        }
        scope.launch {
            intentProcessor.process(intent)
            if (DownloadCenter.state.value.activeTasks.isEmpty()) {
                notificationController.finish()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun enqueueTask(context: Context, task: DownloadTaskUi) {
            DownloadServiceStarter.enqueueTask(context, task)
        }

        fun enqueueVideo(
            context: Context,
            animeId: Long,
            videoId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            DownloadServiceStarter.enqueueVideo(context, animeId, videoId, groupKey, quality)
        }

        fun enqueueAnime(
            context: Context,
            animeId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            DownloadServiceStarter.enqueueAnime(context, animeId, groupKey, quality)
        }

        fun enqueuePlan(context: Context, planId: String) {
            DownloadServiceStarter.enqueuePlan(context, planId)
        }
    }
}
