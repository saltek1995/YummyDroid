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
import java.util.concurrent.atomic.AtomicInteger
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
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DownloadSpeedLimiter
import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.downloadVoiceSlotKey
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.isCompletedDownload
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
private const val NOTIFICATION_UPDATE_INTERVAL_MS = 2_000L

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: YummyAnimeRepository
    private lateinit var settingsStorage: AppSettingsStorage
    private lateinit var downloadSlots: Semaphore
    private lateinit var downloadSpeedLimiter: DownloadSpeedLimiter
    @Volatile
    private var downloadSpeedLimitBytesPerSecond: Long = AppSettings().downloadSpeedLimitBytesPerSecond
    private val downloadSpeedSettingsLock = Any()
    private var lastDownloadSpeedSettingsReadMs: Long = 0L
    @Volatile
    private var foregroundStarted: Boolean = false
    @Volatile
    private var downloadNotificationStartedAtMs: Long = 0L
    private val notificationUpdateGate = NotificationUpdateGate(NOTIFICATION_UPDATE_INTERVAL_MS)

    override fun onCreate() {
        super.onCreate()
        settingsStorage = AppSettingsStorage(applicationContext)
        val settings = settingsStorage.read()
        downloadSpeedLimitBytesPerSecond = settings.downloadSpeedLimitBytesPerSecond
        lastDownloadSpeedSettingsReadMs = System.currentTimeMillis()
        downloadSpeedLimiter = DownloadSpeedLimiter(::currentDownloadSpeedLimitBytesPerSecond)
        DownloadCenter.initialize(applicationContext)
        downloadSlots = Semaphore(settings.downloadParallelism.coerceIn(1, 4))
        val domainResolver = SiteDomainResolver(candidates = settings.siteDomains)
        repository = YummyAnimeRepository(
            context = applicationContext,
            siteDomainResolver = domainResolver,
            authStorage = AuthStorage(applicationContext),
            downloadBandwidthLimiter = downloadSpeedLimiter,
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

    private fun serviceString(resId: Int, vararg formatArgs: Any): String {
        val language = settingsStorage.read().contentLanguage
        return if (formatArgs.isEmpty()) {
            applicationContext.localizedString(resId, language)
        } else {
            applicationContext.localizedString(resId, language, *formatArgs)
        }
    }

    private suspend fun processIntent(intent: Intent) {
        if (intent.action == ACTION_DOWNLOAD_PLAN) {
            processPlanIntent(intent)
            return
        }

        val animeId = intent.getLongExtra(EXTRA_ANIME_ID, 0L)
        if (animeId <= 0L) return

        val existingTaskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val requestedVideoId = intent.getLongExtra(EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
        val preferredGroupKey = intent.getStringExtra(EXTRA_GROUP_KEY).orEmpty()
        val preferredPlanId = intent.getStringExtra(EXTRA_PLAN_ID).orEmpty()
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
            title = serviceString(R.string.ui_loading),
            episodeTitle = if (requestedVideoId == null) {
                serviceString(R.string.ui_all_episodes)
            } else {
                serviceString(R.string.ui_preparing)
            },
            qualityTitle = preferredQuality.title,
            groupKey = preferredGroupKey,
            preferredQuality = preferredQuality,
            planId = preferredPlanId,
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
            message = serviceString(R.string.ui_preparing),
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
                        alreadyDownloadedSingle -> videos.firstOrNull { it.id == requestedVideoId }?.episodeTitle
                            ?: serviceString(R.string.ui_episode)
                        hasVideos -> serviceString(R.string.ui_all_episodes)
                        else -> serviceString(R.string.ui_no_episodes)
                    },
                    progress = if (hasVideos) 1f else 0f,
                    state = if (hasVideos) DownloadTaskState.Completed else DownloadTaskState.Failed,
                    message = when {
                        alreadyDownloadedSingle -> serviceString(R.string.ui_episode_already_downloaded)
                        hasVideos -> serviceString(R.string.ui_all_available_episodes_are_already_downloaded)
                        else -> serviceString(R.string.ui_no_episodes_to_download)
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
                            val completedTask = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }
                            if (completedTask?.state == DownloadTaskState.Completed || completedTask?.state == DownloadTaskState.Cancelled) {
                                DownloadCenter.removeTask(taskId)
                                updateNotification()
                            }
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
                val completedTask = DownloadCenter.state.value.tasks.firstOrNull { it.id == prepareTaskId }
                if (completedTask?.state == DownloadTaskState.Completed || completedTask?.state == DownloadTaskState.Cancelled) {
                    DownloadCenter.removeTask(prepareTaskId)
                    updateNotification()
                }
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
                    message = throwable.message?.takeIf { it.isNotBlank() }
                        ?: serviceString(R.string.ui_download_start_failed),
                    waitingForUnmetered = false,
                )
                updateNotification()
            }
        }
    }

    private suspend fun processPlanIntent(intent: Intent) {
        val planId = intent.getStringExtra(EXTRA_PLAN_ID).orEmpty()
        val planStorage = DownloadPlanStorage(applicationContext)
        val plan = planStorage.read(planId) ?: return
        val existingTaskId = intent.getLongExtra(EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val summaryTaskId = DownloadCenter.addTask(
            animeId = plan.animeId,
            videoId = null,
            title = plan.animeTitle.ifBlank { serviceString(R.string.ui_loading) },
            episodeTitle = serviceString(R.string.ui_download_plan),
            qualityTitle = plan.qualityTitle,
            preferredQuality = plan.preferredQuality,
            planId = plan.id,
            batchKey = plan.id,
            batchTotal = plan.items.size,
            batchCompleted = 0,
            isBatchSummary = true,
            existingTaskId = existingTaskId,
        )

        val initialSettings = settingsStorage.read()
        if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, initialSettings)) {
            pauseForNetwork(summaryTaskId, initialSettings)
            return
        }

        DownloadCenter.updateTask(
            id = summaryTaskId,
            state = DownloadTaskState.Running,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = -1L,
            bytesPerSecond = 0L,
            message = serviceString(R.string.ui_preparing_download_plan),
            waitingForUnmetered = false,
            batchCompleted = 0,
        )
        updateNotification()

        runCatching {
            val (details, videos) = repository.getAnimeWithVideos(plan.animeId)
            val targets = plan.items
                .mapNotNull { item ->
                    item.resolveVideo(videos)?.let { video -> item to video }
                }
                .filterNot { (item, _) ->
                    plan.onlyMissing && videos.hasDownloadedEpisodeForPlan(item.episodeKey, item.preferredQuality)
                }
                .distinctBy { (item, _) -> item.episodeKey }

            if (targets.isEmpty()) {
                DownloadCenter.updateTask(
                    id = summaryTaskId,
                    title = details.title,
                    episodeTitle = serviceString(R.string.ui_download_plan),
                    progress = 1f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    bytesPerSecond = 0L,
                    state = DownloadTaskState.Completed,
                    message = serviceString(R.string.ui_all_selected_episodes_are_already_downloaded),
                    waitingForUnmetered = false,
                    batchTotal = 0,
                    batchCompleted = 0,
                )
                planStorage.delete(plan.id)
                updateNotification()
                return@runCatching
            }

            val total = targets.size
            DownloadCenter.moveTaskToTop(summaryTaskId)
            val completed = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val nextIndex = AtomicInteger(0)
            DownloadCenter.updateTask(
                id = summaryTaskId,
                title = details.title,
                episodeTitle = serviceString(R.string.ui_download_notification_progress, 0, total),
                progress = 0f,
                state = DownloadTaskState.Running,
                message = serviceString(R.string.ui_download_plan_loading),
                batchTotal = total,
                batchCompleted = 0,
            )
            updateNotification()

            coroutineScope {
                repeat(settingsStorage.read().downloadParallelism.coerceIn(1, 4)) {
                    launch {
                        while (true) {
                            if (DownloadCenter.isStopRequested(summaryTaskId)) return@launch
                            val index = nextIndex.getAndIncrement()
                            if (index >= targets.size) return@launch
                            val (item, video) = targets[index]
                            val taskId = DownloadCenter.addTask(
                                animeId = details.id,
                                videoId = video.id,
                                title = details.title,
                                episodeTitle = item.episodeTitle.ifBlank { video.episodeTitle },
                                qualityTitle = video.downloadTaskSubtitle(item.preferredQuality.title, item.voiceTitle),
                                groupKey = video.groupKey,
                                preferredQuality = item.preferredQuality,
                                planId = plan.id,
                                batchKey = plan.id,
                                batchTotal = total,
                                batchCompleted = completed.get(),
                            )
                            processVideoTarget(
                                taskId = taskId,
                                detailsTitle = details.title,
                                details = details,
                                videos = videos,
                                video = video,
                                preferredQuality = item.preferredQuality,
                                parentTaskId = summaryTaskId,
                            )
                            val child = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }
                            when (child?.state) {
                                DownloadTaskState.Completed -> {
                                    DownloadCenter.removeTask(taskId)
                                    val done = completed.incrementAndGet()
                                    DownloadCenter.updateTask(
                                        id = summaryTaskId,
                                        episodeTitle = serviceString(R.string.ui_download_notification_progress, done, total),
                                        progress = done.toFloat() / total.toFloat(),
                                        message = serviceString(R.string.ui_download_notification_progress, done, total),
                                        batchCompleted = done,
                                    )
                                    updateNotification()
                                }
                                DownloadTaskState.Cancelled -> {
                                    DownloadCenter.removeTask(taskId)
                                    failed.incrementAndGet()
                                }
                                DownloadTaskState.Failed -> {
                                    failed.incrementAndGet()
                                }
                                DownloadTaskState.Paused -> return@launch
                                else -> Unit
                            }
                        }
                    }
                }
            }

            val done = completed.get()
            val errors = failed.get()
            when {
                DownloadCenter.isCancelRequested(summaryTaskId) -> {
                    DownloadCenter.updateTask(
                        id = summaryTaskId,
                        state = DownloadTaskState.Cancelled,
                        bytesPerSecond = 0L,
                        message = serviceString(R.string.ui_cancelled),
                        batchCompleted = done,
                    )
                }
                DownloadCenter.isPauseRequested(summaryTaskId) -> {
                    DownloadCenter.updateTask(
                        id = summaryTaskId,
                        state = DownloadTaskState.Paused,
                        bytesPerSecond = 0L,
                        message = serviceString(R.string.ui_paused),
                        batchCompleted = done,
                    )
                }
                errors > 0 -> {
                    DownloadCenter.updateTask(
                        id = summaryTaskId,
                        state = DownloadTaskState.Failed,
                        bytesPerSecond = 0L,
                        message = serviceString(R.string.ui_download_plan_completed_with_errors, done, total, errors),
                        batchCompleted = done,
                    )
                }
                else -> {
                    DownloadCenter.updateTask(
                        id = summaryTaskId,
                        progress = 1f,
                        downloadedBytes = 0L,
                        totalBytes = 0L,
                        bytesPerSecond = 0L,
                        state = DownloadTaskState.Completed,
                        message = serviceString(R.string.ui_download_plan_completed, done, total),
                        batchCompleted = done,
                    )
                    planStorage.delete(plan.id)
                }
            }
            updateNotification()
        }.onFailure { throwable ->
            val latestSettings = settingsStorage.read()
            if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, latestSettings)) {
                pauseForNetwork(summaryTaskId, latestSettings)
            } else {
                DownloadCenter.updateTask(
                    id = summaryTaskId,
                    state = DownloadTaskState.Failed,
                    bytesPerSecond = 0L,
                    message = throwable.message?.takeIf { it.isNotBlank() }
                        ?: serviceString(R.string.ui_download_plan_failed),
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
        parentTaskId: Long? = null,
    ) {
        downloadSlots.withPermit {
            fun isParentStopped(): Boolean = parentTaskId?.let(DownloadCenter::isStopRequested) == true
            val settings = settingsStorage.read()
            if (!DownloadNetworkPolicy.canDownloadNow(applicationContext, settings)) {
                pauseForNetwork(taskId, settings)
                return
            }
            if (DownloadCenter.isCancelRequested(taskId) || parentTaskId?.let(DownloadCenter::isCancelRequested) == true) {
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Cancelled,
                    bytesPerSecond = 0L,
                    message = serviceString(R.string.ui_cancelled),
                )
                DownloadCenter.clearStopRequest(taskId)
                updateNotification()
                return
            }
            if (DownloadCenter.isPauseRequested(taskId) || parentTaskId?.let(DownloadCenter::isPauseRequested) == true) {
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Paused,
                    bytesPerSecond = 0L,
                    message = serviceString(R.string.ui_paused),
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
                message = serviceString(R.string.ui_loading),
                waitingForUnmetered = false,
            )
            updateNotification()

            val retryCandidates = videos.downloadRetryCandidatesFor(video, preferredQuality)
            var attempt = 0
            while (attempt < DOWNLOAD_TASK_MAX_ATTEMPTS) {
                if (DownloadCenter.isCancelRequested(taskId) || parentTaskId?.let(DownloadCenter::isCancelRequested) == true) {
                    DownloadCenter.updateTask(
                        id = taskId,
                        state = DownloadTaskState.Cancelled,
                        bytesPerSecond = 0L,
                        message = serviceString(R.string.ui_cancelled),
                        waitingForUnmetered = false,
                    )
                    DownloadCenter.clearStopRequest(taskId)
                    updateNotification()
                    return
                }
                if (DownloadCenter.isPauseRequested(taskId) || parentTaskId?.let(DownloadCenter::isPauseRequested) == true) {
                    DownloadCenter.updateTask(
                        id = taskId,
                        state = DownloadTaskState.Paused,
                        bytesPerSecond = 0L,
                        message = serviceString(R.string.ui_paused),
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
                val attemptVideo = retryCandidates.downloadRetryCandidateForAttempt(attempt) ?: video
                DownloadCenter.updateTask(
                    id = taskId,
                    state = DownloadTaskState.Running,
                    bytesPerSecond = 0L,
                    episodeTitle = attemptVideo.episodeTitle,
                    qualityTitle = attemptVideo.downloadTaskSubtitle(preferredQuality.title),
                    message = if (attempt == 1) {
                        serviceString(R.string.ui_loading)
                    } else {
                        serviceString(R.string.ui_download_retry_message, attempt, DOWNLOAD_TASK_MAX_ATTEMPTS, "")
                            .trimEnd(':', ' ')
                    },
                    waitingForUnmetered = false,
                    attemptCount = attempt,
                )

                val result = runCatching {
                    repository.downloadVideo(
                        details = details,
                        videos = videos,
                        video = attemptVideo,
                        preferredQuality = preferredQuality,
                        onProgress = { progress ->
                            if (DownloadCenter.isStopRequested(taskId) || isParentStopped()) {
                                throw IllegalStateException(serviceString(R.string.ui_download_stopped))
                            }
                            val clamped = progress.fraction.coerceIn(0f, 1f)
                            val taskSubtitle = attemptVideo.downloadTaskSubtitle(
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
                                message = serviceString(R.string.ui_loading),
                                waitingForUnmetered = false,
                                attemptCount = attempt,
                            )
                            updateNotification()
                        },
                        isCancelled = { DownloadCenter.isStopRequested(taskId) || isParentStopped() },
                        deletePartialOnCancel = {
                            DownloadCenter.isCancelRequested(taskId) ||
                                parentTaskId?.let(DownloadCenter::isCancelRequested) == true
                        },
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
                        episodeTitle = downloaded.episodeTitle,
                        qualityTitle = downloaded.downloadTaskSubtitle(
                            quality = completedFile?.qualityTitle?.takeIf { it.isNotBlank() } ?: preferredQuality.title,
                            voice = completedFile?.voiceTitle.orEmpty(),
                        ),
                        state = DownloadTaskState.Completed,
                        message = serviceString(R.string.ui_downloaded_bc4f6a),
                        waitingForUnmetered = false,
                        attemptCount = attempt,
                    )
                    updateNotification()
                    return
                }

                val throwable = result.exceptionOrNull() ?: continue
                val cancelled = DownloadCenter.isCancelRequested(taskId) ||
                    parentTaskId?.let(DownloadCenter::isCancelRequested) == true
                val paused = DownloadCenter.isPauseRequested(taskId) ||
                    parentTaskId?.let(DownloadCenter::isPauseRequested) == true
                val settingsAfterFailure = settingsStorage.read()
                if (cancelled || paused || !DownloadNetworkPolicy.canDownloadNow(applicationContext, settingsAfterFailure)) {
                    DownloadCenter.clearStopRequest(taskId)
                    when {
                        cancelled -> DownloadCenter.updateTask(
                            id = taskId,
                            bytesPerSecond = 0L,
                            state = DownloadTaskState.Cancelled,
                            message = serviceString(R.string.ui_cancelled),
                            waitingForUnmetered = false,
                        )
                        paused -> DownloadCenter.updateTask(
                            id = taskId,
                            bytesPerSecond = 0L,
                            state = DownloadTaskState.Paused,
                            message = serviceString(R.string.ui_paused),
                            waitingForUnmetered = false,
                        )
                        else -> pauseForNetwork(taskId, settingsAfterFailure)
                    }
                    updateNotification()
                    return
                }

                val errorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: serviceString(R.string.ui_error)
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
                    message = serviceString(
                        R.string.ui_download_retry_message,
                        attempt + 1,
                        DOWNLOAD_TASK_MAX_ATTEMPTS,
                        errorMessage,
                    ),
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
            message = DownloadNetworkPolicy.waitingMessage(applicationContext, settings),
            waitingForUnmetered = true,
        )
        updateNotification()
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

    private fun updateNotification(force: Boolean = false) {
        if (!foregroundStarted) {
            startDownloadForeground(notification())
            return
        }
        if (!notificationUpdateGate.shouldPost(force)) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification())
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
        val builder = Notification.Builder(this, CHANNEL_ID)
        val language = settingsStorage.read().contentLanguage
        val summary = DownloadCenter.state.value.notificationSummary(applicationContext, language)

        return builder
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(summary.title)
            .setContentText(summary.text)
            .setContentIntent(pendingIntent)
            .setOngoing(summary.ongoing)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setDefaults(0)
            .setShowWhen(false)
            .setWhen(ensureDownloadNotificationStartedAtMs())
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setLocalOnly(true)
            .setProgress(
                summary.progressMax,
                summary.progress,
                summary.indeterminate,
            )
            .build()
    }

    private fun createNotificationChannel() {
        val language = settingsStorage.read().contentLanguage
        val channel = NotificationChannel(
            CHANNEL_ID,
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
        private const val CHANNEL_ID = "offline_downloads"
        private const val NOTIFICATION_ID = 9104
        private const val SPEED_LIMIT_SETTINGS_REFRESH_MS = 1_000L
        private const val ACTION_DOWNLOAD_VIDEO = "me.yummydroid.app.DOWNLOAD_VIDEO"
        private const val ACTION_DOWNLOAD_ANIME = "me.yummydroid.app.DOWNLOAD_ANIME"
        private const val ACTION_DOWNLOAD_PLAN = "me.yummydroid.app.DOWNLOAD_PLAN"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_PLAN_ID = "plan_id"
        private const val EXTRA_ANIME_ID = "anime_id"
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_GROUP_KEY = "group_key"
        private const val EXTRA_QUALITY_NAME = "quality_name"

        fun enqueueTask(context: Context, task: DownloadTaskUi) {
            context.startDownloadService(
                Intent(context, DownloadService::class.java)
                    .setAction(
                        when {
                            task.isBatchSummary && task.planId.isNotBlank() -> ACTION_DOWNLOAD_PLAN
                            task.videoId == null -> ACTION_DOWNLOAD_ANIME
                            else -> ACTION_DOWNLOAD_VIDEO
                        },
                    )
                    .putExtra(EXTRA_TASK_ID, task.id)
                    .putExtra(EXTRA_PLAN_ID, task.planId)
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

        fun enqueuePlan(context: Context, planId: String) {
            if (planId.isBlank()) return
            DownloadCenter.initialize(context)
            context.startDownloadService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_DOWNLOAD_PLAN)
                    .putExtra(EXTRA_PLAN_ID, planId),
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

private fun DownloadQueueSnapshot.notificationSummary(
    context: Context,
    language: ContentLanguage,
): DownloadNotificationSummary {
    val active = activeTasks
    if (active.isEmpty()) {
        return DownloadNotificationSummary(
            title = "YummyDroid",
            text = context.localizedString(R.string.ui_download_notification_idle_text, language),
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
    val batchTotal = groupedTasks.maxOfOrNull { it.batchTotal }?.takeIf { it > 0 }
    val batchCompleted = groupedTasks.maxOfOrNull { it.batchCompleted }?.takeIf { it > 0 }
    val total = batchTotal ?: groupedTasks.size.coerceAtLeast(1)
    val completed = batchCompleted ?: groupedTasks.count { it.state == DownloadTaskState.Completed }
    val speedBytesPerSecond = groupedTasks
        .filter { it.state == DownloadTaskState.Running }
        .sumOf { it.bytesPerSecond.coerceAtLeast(0L) }
    val status = context.localizedString(R.string.ui_download_notification_progress, language, completed, total)
    val speed = speedBytesPerSecond
        .takeIf { it > 0L }
        ?.let { "${context.localizedByteSize(it, language)}/${context.localizedString(R.string.ui_s, language)}" }
    return DownloadNotificationSummary(
        title = context.localizedString(R.string.ui_download_notification_title, language),
        text = listOfNotNull(status, speed).joinToString(" • "),
        progressMax = total,
        progress = completed.coerceAtMost(total),
        indeterminate = false,
        ongoing = true,
    )
}

private fun Context.localizedByteSize(bytes: Long, language: ContentLanguage): String {
    return formatByteSize(
        bytes = bytes,
        byteUnit = localizedString(R.string.ui_unit_byte, language),
        kilobyteUnit = localizedString(R.string.ui_unit_kilobyte, language),
        megabyteUnit = localizedString(R.string.ui_unit_megabyte, language),
        gigabyteUnit = localizedString(R.string.ui_unit_gigabyte, language),
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
        candidate.downloadVoiceSlotKey == key &&
            candidate.hasDownloadedQuality(preferredQuality)
    }
}

private fun VideoVariant.completedDownloadFile(preferredQuality: PreferredQuality): OfflineVideoFile? {
    return offlineFiles.firstOrNull { it.isCompletedDownload(preferredQuality) }
        ?: offlineFiles.firstOrNull()
}

private fun VideoVariant.downloadTaskSubtitle(
    quality: String,
    voice: String = "",
): String {
    val voiceTitle = voice.ifBlank {
        matchingDisplayVoiceTitle
    }.ifBlank { "Voice" }
    val qualityTitle = quality.ifBlank { "Auto" }
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
