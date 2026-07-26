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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.downloadVoiceSlotKey
import me.yummydroid.app.data.matchesPreferredQuality
import me.yummydroid.app.data.matchingDisplayVoiceTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

private const val DOWNLOAD_TASK_MAX_ATTEMPTS = 5
private const val DOWNLOAD_TASK_RETRY_DELAY_MS = 1_500L

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: YummyAnimeRepository
    private lateinit var settingsStorage: AppSettingsStorage
    private lateinit var downloadSlots: Semaphore

    override fun onCreate() {
        super.onCreate()
        settingsStorage = AppSettingsStorage(applicationContext)
        val settings = settingsStorage.read()
        DownloadCenter.initialize(applicationContext)
        downloadSlots = Semaphore(settings.downloadParallelism.coerceIn(1, 4))
        val domainResolver = SiteDomainResolver(candidates = settings.siteDomains)
        repository = YummyAnimeRepository(
            context = applicationContext,
            siteDomainResolver = domainResolver,
            authStorage = AuthStorage(applicationContext),
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
            processIntent(intent)
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

        val existingTaskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val requestedVideoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
        val preferredGroupKey = intent.getStringExtra(EXTRA_GROUP_KEY).orEmpty()
        val preferredQuality = intent.getStringExtra(EXTRA_QUALITY_NAME)
            ?.let(PreferredQuality::fromName)
            ?: PreferredQuality.Auto
        val batchKey = existingTaskId
            ?.let { id -> DownloadCenter.state.value.tasks.firstOrNull { it.id == id }?.batchKey }
            ?.takeIf { it.isNotBlank() }
            ?: downloadBatchKey(animeId, requestedVideoId, preferredGroupKey, preferredQuality)
        val prepareTaskId = DownloadCenter.addTask(
            animeId = animeId,
            videoId = requestedVideoId,
            title = "Загрузка",
            episodeTitle = if (requestedVideoId == null) "Все серии" else "Подготовка",
            qualityTitle = preferredQuality.title,
            groupKey = preferredGroupKey,
            preferredQuality = preferredQuality,
            batchKey = batchKey,
            existingTaskId = existingTaskId,
        )

        val settings = settingsStorage.read()
        if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, settings)) {
            pauseForNetwork(prepareTaskId, settings)
            return
        }

        DownloadCenter.updateTask(
            id = prepareTaskId,
            state = DownloadTaskState.Running,
            message = "Подготовка",
            waitingForUnmetered = false,
        )
        updateNotification()

        runCatching {
            val (details, videos) = repository.getAnimeWithVideos(animeId)
            val targets = if (requestedVideoId != null) {
                videos
                    .firstOrNull { it.id == requestedVideoId }
                    ?.takeUnless { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
                    ?.let(::listOf)
                    .orEmpty()
            } else {
                videos.selectDownloadAllTargets(preferredGroupKey)
                    .filterNot { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
            }

            if (targets.isEmpty()) {
                val hasVideos = videos.isNotEmpty()
                val alreadyDownloadedSingle = requestedVideoId != null && hasVideos
                DownloadCenter.updateTask(
                    id = prepareTaskId,
                    title = details.title,
                    episodeTitle = when {
                        alreadyDownloadedSingle -> videos.firstOrNull { it.id == requestedVideoId }?.episodeTitle ?: "Серия"
                        hasVideos -> "Все серии"
                        else -> "Нет серий"
                    },
                    progress = if (hasVideos) 1f else 0f,
                    state = if (hasVideos) DownloadTaskState.Completed else DownloadTaskState.Failed,
                    message = when {
                        alreadyDownloadedSingle -> "Серия уже скачана"
                        hasVideos -> "Все доступные серии уже скачаны"
                        else -> "Нет серий для загрузки"
                    },
                    waitingForUnmetered = false,
                    bytesPerSecond = 0L,
                )
                updateNotification()
                return@runCatching
            }

            if (requestedVideoId == null) {
                DownloadCenter.removeTask(prepareTaskId)
                coroutineScope {
                    targets.map { video ->
                        launch {
                            val taskId = DownloadCenter.addTask(
                                animeId = details.id,
                                videoId = video.id,
                                title = details.title,
                                episodeTitle = video.episodeTitle,
                                qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
                                groupKey = preferredGroupKey,
                                preferredQuality = preferredQuality,
                                batchKey = batchKey,
                            )
                            processVideoTarget(
                                taskId = taskId,
                                detailsTitle = details.title,
                                details = details,
                                videos = videos,
                                video = video,
                                preferredQuality = preferredQuality,
                            )
                        }
                    }.joinAll()
                }
            } else {
                val video = targets.first()
                processVideoTarget(
                    taskId = prepareTaskId,
                    detailsTitle = details.title,
                    details = details,
                    videos = videos,
                    video = video,
                    preferredQuality = preferredQuality,
                )
            }
        }.onFailure { throwable ->
            val latestSettings = settingsStorage.read()
            if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, latestSettings)) {
                pauseForNetwork(prepareTaskId, latestSettings)
            } else {
                DownloadCenter.updateTask(
                    id = prepareTaskId,
                    state = DownloadTaskState.Failed,
                    bytesPerSecond = 0L,
                    message = throwable.message?.takeIf { it.isNotBlank() } ?: "Не удалось начать загрузку",
                    waitingForUnmetered = false,
                )
                updateNotification()
            }
        }
    }

    private suspend fun processVideoTarget(
        taskId: Long,
        detailsTitle: String,
        details: me.yummydroid.app.data.AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
    ) {
        downloadSlots.withPermit {
            val settings = settingsStorage.read()
            if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, settings)) {
                pauseForNetwork(taskId, settings)
                return
            }
            if (DownloadCenter.isCancelRequested(taskId)) {
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Cancelled,
                    bytesPerSecond = 0L,
                    message = "Отменено",
                )
                DownloadCenter.clearStopRequest(taskId)
                updateNotification()
                return
            }
            if (DownloadCenter.isPauseRequested(taskId)) {
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Paused,
                    bytesPerSecond = 0L,
                    message = "Пауза",
                )
                updateNotification()
                return
            }

            DownloadCenter.updateTask(
                id = taskId,
                title = detailsTitle,
                episodeTitle = video.episodeTitle,
                qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
                state = DownloadTaskState.Running,
                message = "Загрузка",
                waitingForUnmetered = false,
            )
            updateNotification()

            var attempt = 0
            while (attempt < DOWNLOAD_TASK_MAX_ATTEMPTS) {
                if (DownloadCenter.isCancelRequested(taskId)) {
                    DownloadCenter.updateTask(
                        id = taskId,
                        state = DownloadTaskState.Cancelled,
                        bytesPerSecond = 0L,
                        message = "Отменено",
                        waitingForUnmetered = false,
                    )
                    DownloadCenter.clearStopRequest(taskId)
                    updateNotification()
                    return
                }
                if (DownloadCenter.isPauseRequested(taskId)) {
                    DownloadCenter.updateTask(
                        id = taskId,
                        state = DownloadTaskState.Paused,
                        bytesPerSecond = 0L,
                        message = "Пауза",
                        waitingForUnmetered = false,
                    )
                    DownloadCenter.clearStopRequest(taskId)
                    updateNotification()
                    return
                }
                val latestSettings = settingsStorage.read()
                if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, latestSettings)) {
                    pauseForNetwork(taskId, latestSettings)
                    return
                }

                attempt += 1
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Running,
                    bytesPerSecond = 0L,
                    message = if (attempt == 1) "Загрузка" else "Повтор $attempt из $DOWNLOAD_TASK_MAX_ATTEMPTS",
                    waitingForUnmetered = false,
                    attemptCount = attempt,
                )

                val result = runCatching {
                    repository.downloadVideo(
                        details = details,
                        videos = videos,
                        video = video,
                        preferredQuality = preferredQuality,
                        onProgress = { progress ->
                            if (DownloadCenter.isStopRequested(taskId)) {
                                throw IllegalStateException("Загрузка остановлена")
                            }
                            val clamped = progress.fraction.coerceIn(0f, 1f)
                            val taskSubtitle = video.downloadTaskSubtitle(
                                quality = progress.qualityTitle.ifBlank { preferredQuality.title },
                                voice = progress.voiceTitle,
                            )
                            DownloadCenter.updateTask(
                                id = taskId,
                                progress = clamped,
                                downloadedBytes = progress.downloadedBytes,
                                totalBytes = progress.totalBytes,
                                bytesPerSecond = progress.bytesPerSecond,
                                qualityTitle = taskSubtitle,
                                message = "Загрузка",
                                waitingForUnmetered = false,
                                attemptCount = attempt,
                            )
                            updateNotification()
                        },
                        isCancelled = { DownloadCenter.isStopRequested(taskId) },
                        deletePartialOnCancel = { DownloadCenter.isCancelRequested(taskId) },
                    )
                }

                result.onSuccess { downloaded ->
                    val completedFile = downloaded.completedDownloadFile(preferredQuality)
                    val completedBytes = completedFile?.bytes?.coerceAtLeast(0L) ?: 0L
                    DownloadCenter.clearStopRequest(taskId)
                    DownloadCenter.updateTask(
                        id = taskId,
                        progress = 1f,
                        downloadedBytes = completedBytes,
                        totalBytes = completedBytes,
                        bytesPerSecond = 0L,
                        qualityTitle = video.downloadTaskSubtitle(
                            quality = completedFile?.qualityTitle?.takeIf { it.isNotBlank() } ?: preferredQuality.title,
                            voice = completedFile?.voiceTitle.orEmpty(),
                        ),
                        state = DownloadTaskState.Completed,
                        message = "Скачано",
                        waitingForUnmetered = false,
                        attemptCount = attempt,
                    )
                    updateNotification()
                    return
                }

                val throwable = result.exceptionOrNull() ?: continue
                val cancelled = DownloadCenter.isCancelRequested(taskId)
                val paused = DownloadCenter.isPauseRequested(taskId)
                val settingsAfterFailure = settingsStorage.read()
                if (cancelled || paused || !DownloadNetworkPolicy.canDownloadNow(applicationContext, settingsAfterFailure)) {
                    DownloadCenter.clearStopRequest(taskId)
                    when {
                        cancelled -> DownloadCenter.updateTask(
                            id = taskId,
                            bytesPerSecond = 0L,
                            state = DownloadTaskState.Cancelled,
                            message = "Отменено",
                            waitingForUnmetered = false,
                        )
                        paused -> DownloadCenter.updateTask(
                            id = taskId,
                            bytesPerSecond = 0L,
                            state = DownloadTaskState.Paused,
                            message = "Пауза",
                            waitingForUnmetered = false,
                        )
                        else -> pauseForNetwork(taskId, settingsAfterFailure)
                    }
                    updateNotification()
                    return
                }

                val errorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: "Ошибка загрузки"
                if (attempt >= DOWNLOAD_TASK_MAX_ATTEMPTS) {
                    DownloadCenter.clearStopRequest(taskId)
                    DownloadCenter.updateTask(
                        id = taskId,
                        bytesPerSecond = 0L,
                        state = DownloadTaskState.Failed,
                        message = errorMessage,
                        waitingForUnmetered = false,
                        attemptCount = attempt,
                    )
                    updateNotification()
                    return
                }

                DownloadCenter.updateTask(
                    id = taskId,
                    bytesPerSecond = 0L,
                    message = "Повтор ${attempt + 1} из $DOWNLOAD_TASK_MAX_ATTEMPTS: $errorMessage",
                    waitingForUnmetered = false,
                    attemptCount = attempt,
                )
                updateNotification()
                delay(DOWNLOAD_TASK_RETRY_DELAY_MS * attempt)
            }
        }
    }

    private fun pauseForNetwork(taskId: Long, settings: AppSettings) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Paused,
            bytesPerSecond = 0L,
            message = DownloadNetworkPolicy.waitingMessage(settings),
            waitingForUnmetered = true,
        )
        updateNotification()
    }

    private fun startDownloadForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
        val summary = DownloadCenter.state.value.notificationSummary()

        return builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setContentIntent(pendingIntent)
            .setOngoing(summary.ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(
                summary.progressMax,
                summary.progress,
                summary.indeterminate,
            )
            .build()
    }

    private fun createNotificationChannel() {
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "offline_downloads"
        private const val NOTIFICATION_ID = 9104
        private const val ACTION_DOWNLOAD_VIDEO = "me.yummydroid.app.DOWNLOAD_VIDEO"
        private const val ACTION_DOWNLOAD_ANIME = "me.yummydroid.app.DOWNLOAD_ANIME"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_ANIME_ID = "anime_id"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_GROUP_KEY = "group_key"
        private const val EXTRA_QUALITY_NAME = "quality_name"

        fun enqueueTask(context: Context, task: DownloadTaskUi) {
            context.startDownloadService(
                Intent(context, DownloadService::class.java)
                    .setAction(if (task.videoId == null) ACTION_DOWNLOAD_ANIME else ACTION_DOWNLOAD_VIDEO)
                    .putExtra(EXTRA_TASK_ID, task.id)
                    .putExtra(EXTRA_ANIME_ID, task.animeId)
                    .putExtra(EXTRA_VIDEO_ID, task.videoId ?: 0L)
                    .putExtra(EXTRA_GROUP_KEY, task.groupKey)
                    .putExtra(EXTRA_QUALITY_NAME, task.preferredQualityName),
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
            DownloadCenter.initialize(context)
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

private data class DownloadNotificationSummary(
    val title: String,
    val text: String,
    val progressMax: Int,
    val progress: Int,
    val indeterminate: Boolean,
    val ongoing: Boolean,
)

private fun DownloadQueueSnapshot.notificationSummary(): DownloadNotificationSummary {
    val active = activeTasks
    if (active.isEmpty()) {
        return DownloadNotificationSummary(
            title = "YummyDroid",
            text = "Очередь загрузок",
            progressMax = 0,
            progress = 0,
            indeterminate = true,
            ongoing = false,
        )
    }

    val activeBatchKeys = active.mapTo(mutableSetOf()) { it.notificationBatchKey() }
    val groupedTasks = tasks
        .filter { it.notificationBatchKey() in activeBatchKeys }
        .filterNot { it.state == DownloadTaskState.Cancelled }
        .ifEmpty { active }
    val total = groupedTasks.size.coerceAtLeast(1)
    val completed = groupedTasks.count { it.state == DownloadTaskState.Completed }
    val speedBytesPerSecond = groupedTasks
        .filter { it.state == DownloadTaskState.Running }
        .sumOf { it.bytesPerSecond.coerceAtLeast(0L) }
    val status = "$completed из $total"
    val speed = speedBytesPerSecond
        .takeIf { it > 0L }
        ?.let { "${formatByteSize(it)}/с" }
    return DownloadNotificationSummary(
        title = "Загрузка серий",
        text = listOfNotNull(status, speed).joinToString(" • "),
        progressMax = total,
        progress = completed.coerceAtMost(total),
        indeterminate = false,
        ongoing = true,
    )
}

private fun DownloadTaskUi.notificationBatchKey(): String {
    return batchKey.takeIf { it.isNotBlank() } ?: "task:$id"
}

private fun downloadBatchKey(
    animeId: Long,
    videoId: Long?,
    groupKey: String,
    quality: PreferredQuality,
): String {
    return listOf(
        animeId.toString(),
        videoId?.toString() ?: "all",
        groupKey,
        quality.name,
        System.currentTimeMillis().toString(),
    ).joinToString(":")
}

private fun Context.startDownloadService(intent: Intent) {
    startForegroundService(intent)
}

private fun List<VideoVariant>.selectDownloadAllTargets(preferredGroupKey: String): List<VideoVariant> {
    val preferredVoiceKey = firstOrNull { it.groupKey == preferredGroupKey }
        ?.matchingVoiceKey
    return groupBy { it.downloadEpisodeSlotKey }
        .toSortedMap(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
        .values
        .mapNotNull { episodeVideos ->
            if (preferredVoiceKey != null) {
                episodeVideos
                    .filter { it.matchingVoiceKey == preferredVoiceKey }
                    .sortedWith(downloadTargetComparator(preferredGroupKey))
                    .firstOrNull()
            } else {
                episodeVideos.sortedWith(downloadTargetComparator()).firstOrNull()
            }
        }
}

private fun List<VideoVariant>.hasDownloadedRequestedSlot(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val key = video.downloadVoiceSlotKey
    return any { candidate ->
        candidate.isOfflineAvailable &&
            candidate.downloadVoiceSlotKey == key &&
            candidate.offlineFiles.any { it.matchesPreferredQuality(preferredQuality) }
    }
}

private fun VideoVariant.completedDownloadFile(preferredQuality: PreferredQuality): OfflineVideoFile? {
    return offlineFiles.firstOrNull { it.matchesPreferredQuality(preferredQuality) }
        ?: offlineFiles.firstOrNull()
}

private fun VideoVariant.downloadTaskSubtitle(
    quality: String,
    voice: String = "",
): String {
    val voiceTitle = voice.ifBlank {
        matchingDisplayVoiceTitle
    }.ifBlank { "Озвучка" }
    val qualityTitle = quality.ifBlank { "Авто" }
    return listOf(voiceTitle, qualityTitle)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

private fun downloadTargetComparator(preferredGroupKey: String = ""): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { if (preferredGroupKey.isNotBlank() && it.groupKey == preferredGroupKey) 0 else 1 }
        .thenBy { sourceProviderRank(it.player) }
        .thenBy { it.index }
}
