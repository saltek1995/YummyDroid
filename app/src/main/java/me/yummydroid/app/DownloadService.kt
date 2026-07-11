package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private lateinit var repository: YummyAnimeRepository

    override fun onCreate() {
        super.onCreate()
        val settings = AppSettingsStorage(applicationContext).read()
        val domainResolver = SiteDomainResolver(candidates = settings.siteDomains)
        repository = YummyAnimeRepository(
            context = applicationContext,
            siteDomainResolver = domainResolver,
            authStorage = AuthStorage(applicationContext),
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Подготовка загрузки", 0, 0))
        if (intent == null) return START_NOT_STICKY

        scope.launch {
            downloadMutex.withLock {
                processIntent(intent)
            }
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

    private suspend fun processIntent(intent: Intent) {
        val animeId = intent.getLongExtra(EXTRA_ANIME_ID, 0L)
        if (animeId <= 0L) return

        runCatching {
            val requestedVideoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
            val preferredGroupKey = intent.getStringExtra(EXTRA_GROUP_KEY).orEmpty()
            val preferredQuality = intent.getStringExtra(EXTRA_QUALITY_NAME)
                ?.let(PreferredQuality::fromName)
                ?: PreferredQuality.Auto
            val (details, videos) = repository.getAnimeWithVideos(animeId)
            val targets = if (requestedVideoId != null) {
                videos.firstOrNull { it.id == requestedVideoId }?.let(::listOf).orEmpty()
            } else {
                videos.selectDownloadAllTargets(preferredGroupKey)
            }

            targets.forEachIndexed { index, video ->
                val taskId = DownloadCenter.addTask(
                    animeId = details.id,
                    videoId = video.id,
                    title = details.title,
                    episodeTitle = video.episodeTitle,
                    qualityTitle = preferredQuality.title,
                )

                if (video.isOfflineAvailable) {
                    DownloadCenter.updateTask(
                        id = taskId,
                        progress = 1f,
                        state = DownloadTaskState.Completed,
                        message = "Уже скачано",
                    )
                    return@forEachIndexed
                }

                DownloadCenter.updateTask(
                    id = taskId,
                    progress = 0f,
                    state = DownloadTaskState.Running,
                    message = "Загрузка",
                )
                updateNotification(details.title, index + 1, targets.size, preferredQuality.title)

                runCatching {
                    repository.downloadVideo(details, videos, video, preferredQuality) { progress ->
                        val clamped = progress.coerceIn(0f, 1f)
                        DownloadCenter.updateTask(taskId, progress = clamped)
                        updateNotification(
                            title = "${details.title} • ${video.episodeTitle}",
                            current = index + 1,
                            total = targets.size,
                            quality = preferredQuality.title,
                            progress = clamped,
                        )
                    }
                }
                    .onSuccess {
                        DownloadCenter.updateTask(
                            id = taskId,
                            progress = 1f,
                            state = DownloadTaskState.Completed,
                            message = "Скачано",
                        )
                    }
                    .onFailure { throwable ->
                        DownloadCenter.updateTask(
                            id = taskId,
                            state = DownloadTaskState.Failed,
                            message = throwable.message?.takeIf { it.isNotBlank() } ?: "Ошибка загрузки",
                        )
                    }
            }
        }.onFailure { throwable ->
            DownloadCenter.updateTask(
                id = DownloadCenter.addTask(animeId, null, "Загрузка", "Ошибка"),
                state = DownloadTaskState.Failed,
                message = throwable.message?.takeIf { it.isNotBlank() } ?: "Не удалось начать загрузку",
            )
        }
    }

    private fun updateNotification(
        title: String,
        current: Int,
        total: Int,
        quality: String = "",
        progress: Float? = null,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(title, current, total, quality, progress))
    }

    private fun notification(
        title: String,
        current: Int,
        total: Int,
        quality: String = "",
        progress: Float? = null,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val text = when {
            total > 1 && progress != null -> "$current из $total • ${(progress * 100).toInt()}%"
            total > 1 -> "$current из $total"
            progress != null -> "${(progress * 100).toInt()}%"
            else -> "Очередь загрузок"
        }.let { status -> if (quality.isBlank()) status else "$status • $quality" }

        return builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("YummyDroid")
            .setContentText(title)
            .setSubText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                100,
                ((progress ?: 0f).coerceIn(0f, 1f) * 100).toInt(),
                progress == null,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Загрузки",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Статус скачивания серий"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun finishForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "offline_downloads"
        private const val NOTIFICATION_ID = 9104
        private const val ACTION_DOWNLOAD_VIDEO = "me.yummydroid.app.DOWNLOAD_VIDEO"
        private const val ACTION_DOWNLOAD_ANIME = "me.yummydroid.app.DOWNLOAD_ANIME"
        private const val EXTRA_ANIME_ID = "anime_id"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_GROUP_KEY = "group_key"
        private const val EXTRA_QUALITY_NAME = "quality_name"

        fun enqueueVideo(
            context: Context,
            animeId: Long,
            videoId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            context.startDownloadService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_DOWNLOAD_VIDEO)
                    .putExtra(EXTRA_ANIME_ID, animeId)
                    .putExtra(EXTRA_VIDEO_ID, videoId)
                    .putExtra(EXTRA_GROUP_KEY, groupKey.orEmpty())
                    .putExtra(EXTRA_QUALITY_NAME, quality.name),
            )
        }

        fun enqueueAnime(
            context: Context,
            animeId: Long,
            groupKey: String? = null,
            quality: PreferredQuality = PreferredQuality.Auto,
        ) {
            context.startDownloadService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_DOWNLOAD_ANIME)
                    .putExtra(EXTRA_ANIME_ID, animeId)
                    .putExtra(EXTRA_GROUP_KEY, groupKey.orEmpty())
                    .putExtra(EXTRA_QUALITY_NAME, quality.name),
            )
        }
    }
}

private fun Context.startDownloadService(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

private fun List<VideoVariant>.selectDownloadAllTargets(preferredGroupKey: String): List<VideoVariant> {
    return groupBy { it.episode.ifBlank { it.index.toString() } }
        .toSortedMap(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
        .values
        .mapNotNull { episodeVideos ->
            episodeVideos.firstOrNull { it.groupKey == preferredGroupKey }
                ?: episodeVideos.sortedWith(downloadTargetComparator()).firstOrNull()
        }
}

private fun downloadTargetComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { providerRank(it.player) }
        .thenBy { it.index }
}

private fun providerRank(player: String): Int {
    val normalized = player.lowercase()
    return when {
        "cvh" in normalized -> 0
        "alloha" in normalized -> 1
        "kodik" in normalized -> 2
        "aksor" in normalized -> 3
        "sibnet" in normalized -> 4
        else -> 10
    }
}
