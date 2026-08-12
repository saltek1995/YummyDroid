package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.content.edit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DownloadSpeedLimiter
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.decodeAppJsonOrNull
import me.yummydroid.app.data.encodeAppJson

// DownloadCenterController
internal class DownloadCenterController : DownloadTaskQueue {
    private val ids = AtomicLong(1L)
    private val stopRequests = DownloadStopRequests()
    private val networkObserver = DownloadNetworkObserver()
    private var appContext: Context? = null

    val state = MutableStateFlow(DownloadQueueSnapshot())

    @Synchronized
    fun initialize(context: Context) {
        val safeContext = context.applicationContext
        if (appContext == null) {
            appContext = safeContext
            restoreTasks(safeContext)
        }
        networkObserver.register(safeContext) { resumeWaitingForAllowedNetwork(safeContext) }
    }

    override fun addTask(request: DownloadTaskRequest): Long {
        request.existingTaskId
            ?.takeIf { id -> state.value.tasks.any { it.id == id } }
            ?.let { id ->
                updateTask(id, request.metadataUpdate())
                return id
            }
        state.value.tasks.findReusableTask(request.identity)?.let { return it.id }

        val task = request.createTask(ids.getAndIncrement())
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = listOf(task) + snapshot.tasks)
        }
        return task.id
    }

    override fun updateTask(id: Long, update: DownloadTaskUpdate) {
        state.updateAndPersist { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.map { task ->
                    if (task.id == id) task.applyUpdate(update) else task
                },
            )
        }
    }

    @Synchronized
    fun requestPause(id: Long) {
        requestStop(
            id = id,
            request = DownloadStopRequest.Pause,
            targetState = DownloadTaskState.Paused,
            message = localizedText(R.string.ui_paused),
        )
    }

    @Synchronized
    fun requestCancel(id: Long) {
        requestStop(
            id = id,
            request = DownloadStopRequest.Cancel,
            targetState = DownloadTaskState.Cancelled,
            message = localizedText(R.string.ui_cancelled),
        )
    }

    override fun isCancelRequested(id: Long): Boolean = stopRequests.isCancelRequested(id)

    override fun isPauseRequested(id: Long): Boolean = stopRequests.isPauseRequested(id)

    override fun isStopRequested(id: Long): Boolean = stopRequests.isStopRequested(id)

    override fun clearStopRequest(id: Long) {
        stopRequests.clear(id)
    }

    override fun task(id: Long): DownloadTaskUi? = state.value.tasks.firstOrNull { it.id == id }

    fun resumeTask(context: Context, id: Long) {
        initialize(context)
        val task = state.value.tasks.firstOrNull { it.id == id } ?: return
        if (!task.canResume) return
        clearStopRequest(id)
        updateTask(
            id,
            DownloadTaskUpdate(
                state = DownloadTaskState.Queued,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = -1L,
                bytesPerSecond = 0L,
                message = localizedText(R.string.ui_queued),
                waitingForUnmetered = false,
                attemptCount = 0,
            ),
        )
        DownloadService.enqueueTask(context, state.value.tasks.first { it.id == id })
    }

    fun resumeWaitingForAllowedNetwork(context: Context) {
        initialize(context)
        val settings = AppSettingsStorage(context).read()
        if (!DownloadNetworkPolicy.canDownloadNow(context, settings)) return
        state.value.tasks
            .filter { it.state == DownloadTaskState.Paused && it.waitingForUnmetered }
            .forEach { resumeTask(context, it.id) }
    }

    fun clearFinished() {
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filter { it.isActive || it.state == DownloadTaskState.Paused })
        }
    }

    override fun removeTask(id: Long) {
        stopRequests.clear(id)
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filterNot { it.id == id })
        }
    }

    fun moveTaskToTop(id: Long) {
        state.updateAndPersist { snapshot ->
            val task = snapshot.tasks.firstOrNull { it.id == id } ?: return@updateAndPersist snapshot
            snapshot.copy(tasks = listOf(task) + snapshot.tasks.filterNot { it.id == id })
        }
    }

    fun clearAll() {
        stopRequests.clearAll()
        state.updateAndPersist { DownloadQueueSnapshot() }
    }

    private fun restoreTasks(context: Context) {
        val restored = DownloadQueueStorage(context)
            .read()
            .restoreInterruptedTasks(
                waitingForNetworkMessage = localizedText(R.string.ui_download_network_waiting_unmetered),
                waitingToResumeMessage = localizedText(R.string.ui_waiting_to_resume),
            )
        if (restored.isEmpty()) return
        ids.set((restored.maxOf { it.id } + 1L).coerceAtLeast(1L))
        state.value = DownloadQueueSnapshot(restored)
    }

    private fun requestStop(
        id: Long,
        request: DownloadStopRequest,
        targetState: DownloadTaskState,
        message: String,
    ) {
        val targetIds = state.value.tasks.stopTargetIds(id)
        stopRequests.request(targetIds, request)
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.updateTaskStates(targetIds, targetState, message))
        }
    }

    private fun MutableStateFlow<DownloadQueueSnapshot>.updateAndPersist(
        transform: (DownloadQueueSnapshot) -> DownloadQueueSnapshot,
    ) {
        update { snapshot ->
            val transformed = transform(snapshot)
            transformed.copy(tasks = transformed.tasks.cappedDownloadTasks())
        }
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        DownloadQueueStorage(context).write(state.value.tasks)
    }

    private fun localizedText(resId: Int): String {
        val context = appContext ?: return ""
        val language = AppSettingsStorage(context).read().contentLanguage
        return context.localizedString(resId, language)
    }
}

// DownloadCenterFacade
object DownloadCenter {
    private val controller = DownloadCenterController()

    val state = controller.state
    internal val taskQueue: DownloadTaskQueue = controller

    fun initialize(context: Context) {
        controller.initialize(context)
    }

    fun addTask(
        animeId: Long,
        videoId: Long?,
        title: String,
        episodeTitle: String,
        qualityTitle: String = PreferredQuality.Auto.title,
        groupKey: String = "",
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        planId: String = "",
        batchKey: String = "",
        batchTotal: Int = 0,
        batchCompleted: Int = 0,
        isBatchSummary: Boolean = false,
        existingTaskId: Long? = null,
    ): Long {
        return controller.addTask(
            DownloadTaskRequest(
                animeId = animeId,
                videoId = videoId,
                title = title,
                episodeTitle = episodeTitle,
                qualityTitle = qualityTitle,
                groupKey = groupKey,
                preferredQualityName = preferredQuality.name,
                planId = planId,
                batchKey = batchKey,
                batchTotal = batchTotal,
                batchCompleted = batchCompleted,
                isBatchSummary = isBatchSummary,
                existingTaskId = existingTaskId,
            ),
        )
    }

    fun updateTask(
        id: Long,
        title: String? = null,
        episodeTitle: String? = null,
        qualityTitle: String? = null,
        groupKey: String? = null,
        preferredQualityName: String? = null,
        planId: String? = null,
        batchKey: String? = null,
        batchTotal: Int? = null,
        batchCompleted: Int? = null,
        isBatchSummary: Boolean? = null,
        progress: Float? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        bytesPerSecond: Long? = null,
        state: DownloadTaskState? = null,
        message: String? = null,
        waitingForUnmetered: Boolean? = null,
        attemptCount: Int? = null,
    ) {
        controller.updateTask(
            id,
            DownloadTaskUpdate(
                title = title,
                episodeTitle = episodeTitle,
                qualityTitle = qualityTitle,
                groupKey = groupKey,
                preferredQualityName = preferredQualityName,
                planId = planId,
                batchKey = batchKey,
                batchTotal = batchTotal,
                batchCompleted = batchCompleted,
                isBatchSummary = isBatchSummary,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond,
                state = state,
                message = message,
                waitingForUnmetered = waitingForUnmetered,
                attemptCount = attemptCount,
            ),
        )
    }

    fun requestPause(id: Long) {
        controller.requestPause(id)
    }

    fun requestCancel(id: Long) {
        controller.requestCancel(id)
    }

    fun isCancelRequested(id: Long): Boolean = controller.isCancelRequested(id)

    fun isPauseRequested(id: Long): Boolean = controller.isPauseRequested(id)

    fun isStopRequested(id: Long): Boolean = controller.isStopRequested(id)

    fun clearStopRequest(id: Long) {
        controller.clearStopRequest(id)
    }

    fun resumeTask(context: Context, id: Long) {
        controller.resumeTask(context, id)
    }

    fun resumeWaitingForAllowedNetwork(context: Context) {
        controller.resumeWaitingForAllowedNetwork(context)
    }

    fun clearFinished() {
        controller.clearFinished()
    }

    fun removeTask(id: Long) {
        controller.removeTask(id)
    }

    fun moveTaskToTop(id: Long) {
        controller.moveTaskToTop(id)
    }

    fun clearHistory() {
        controller.clearFinished()
    }

    fun clearAll() {
        controller.clearAll()
    }
}

// DownloadCenterInfrastructure
internal class DownloadQueueStorage(private val context: Context) {
    fun read(): List<DownloadTaskUi> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, null)
            ?.takeIf { it.isNotBlank() }
            ?.decodeAppJsonOrNull<List<DownloadTaskUi>>()
            .orEmpty()
    }

    fun write(tasks: List<DownloadTaskUi>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TASKS, tasks.cappedDownloadTasks().encodeAppJson())
        }
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_download_queue"
        const val KEY_TASKS = "tasks"
    }
}

internal class DownloadNetworkObserver {
    private var registered = false

    fun register(context: Context, onNetworkAvailable: () -> Unit) {
        if (registered) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkAvailable()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                onNetworkAvailable()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { registered = true }
    }
}

// DownloadIntentProcessor
internal class DownloadIntentProcessor(
    context: Context,
    repository: YummyAnimeRepository,
    settingsStorage: AppSettingsStorage,
    taskRuntime: DownloadTaskRuntime,
    videoProcessor: DownloadVideoProcessor,
) {
    private val taskController = DownloadIntentTaskController(context, settingsStorage, taskRuntime)
    private val requestProcessor = DownloadRequestIntentProcessor(
        repository = repository,
        taskRuntime = taskRuntime,
        videoProcessor = videoProcessor,
        taskController = taskController,
        taskQueue = DownloadCenter.taskQueue,
    )
    private val planProcessor = DownloadPlanIntentProcessor(
        context = context,
        repository = repository,
        taskRuntime = taskRuntime,
        videoProcessor = videoProcessor,
        taskController = taskController,
    )

    suspend fun process(intent: Intent) {
        if (intent.action == DOWNLOAD_ACTION_PLAN) {
            planProcessor.process(intent)
        } else {
            requestProcessor.process(intent)
        }
    }
}

// DownloadIntentTaskController
internal class DownloadIntentTaskController(
    private val context: Context,
    private val settingsStorage: AppSettingsStorage,
    private val taskRuntime: DownloadTaskRuntime,
) : DownloadRequestTaskController {
    fun currentSettings(): AppSettings = settingsStorage.read()

    override fun canStart(taskId: Long): Boolean {
        val settings = currentSettings()
        if (DownloadNetworkPolicy.canDownloadNow(context, settings)) return true
        taskRuntime.pauseForNetwork(taskId, settings)
        return false
    }

    override fun removeFinishedTask(taskId: Long) {
        val task = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }
        if (task?.state == DownloadTaskState.Completed || task?.state == DownloadTaskState.Cancelled) {
            DownloadCenter.removeTask(taskId)
            taskRuntime.notifyChanged()
        }
    }

    override fun handleStartFailure(taskId: Long, throwable: Throwable, fallbackMessageRes: Int) {
        val latestSettings = currentSettings()
        if (!DownloadNetworkPolicy.canDownloadNow(context, latestSettings)) {
            taskRuntime.pauseForNetwork(taskId, latestSettings)
            return
        }
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Failed,
            bytesPerSecond = 0L,
            message = throwable.message?.takeIf { it.isNotBlank() }
                ?: taskRuntime.text(fallbackMessageRes),
            waitingForUnmetered = false,
        )
        taskRuntime.notifyChanged()
    }
}

// DownloadNotificationController
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

// DownloadNotificationSummary
internal data class DownloadNotificationSummary(
    val title: String,
    val text: String,
    val progressMax: Int,
    val progress: Int,
    val indeterminate: Boolean,
    val ongoing: Boolean,
)

internal fun DownloadQueueSnapshot.notificationSummary(
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
        text = downloadNotificationSummaryText(status, speed),
        progressMax = total,
        progress = completed.coerceAtMost(total),
        indeterminate = false,
        ongoing = true,
    )
}

internal fun downloadNotificationSummaryText(status: String, speed: String?): String {
    return listOfNotNull(status, speed).joinToString(" - ")
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

// DownloadServiceRuntime
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
            taskStore = DownloadCenter.taskQueue,
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

// DownloadServiceStarter
internal object DownloadServiceStarter {
    fun enqueueTask(context: Context, task: DownloadTaskUi) {
        startCommand(context, downloadActionForTask(task), initializeCenter = false) {
            putExtra(DOWNLOAD_EXTRA_TASK_ID, task.id)
            putExtra(DOWNLOAD_EXTRA_PLAN_ID, task.planId)
            putExtra(DOWNLOAD_EXTRA_ANIME_ID, task.animeId)
            putExtra(DOWNLOAD_EXTRA_VIDEO_ID, task.videoId ?: 0L)
            putExtra(DOWNLOAD_EXTRA_GROUP_KEY, task.groupKey)
            putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, task.preferredQualityName)
        }
    }

    fun enqueueVideo(
        context: Context,
        animeId: Long,
        videoId: Long,
        groupKey: String? = null,
        quality: PreferredQuality = PreferredQuality.Auto,
    ) {
        startCommand(context, DOWNLOAD_ACTION_VIDEO) {
            putDownloadTargetExtras(animeId, groupKey, quality)
            putExtra(DOWNLOAD_EXTRA_VIDEO_ID, videoId)
        }
    }

    fun enqueueAnime(
        context: Context,
        animeId: Long,
        groupKey: String? = null,
        quality: PreferredQuality = PreferredQuality.Auto,
    ) {
        startCommand(context, DOWNLOAD_ACTION_ANIME) {
            putDownloadTargetExtras(animeId, groupKey, quality)
        }
    }

    fun enqueuePlan(context: Context, planId: String) {
        if (planId.isBlank()) return
        startCommand(context, DOWNLOAD_ACTION_PLAN) {
            putExtra(DOWNLOAD_EXTRA_PLAN_ID, planId)
        }
    }

    private fun startCommand(
        context: Context,
        action: String,
        initializeCenter: Boolean = true,
        configure: Intent.() -> Unit,
    ) {
        if (initializeCenter) DownloadCenter.initialize(context)
        context.startForegroundService(
            Intent(context, DownloadService::class.java)
                .setAction(action)
                .apply(configure),
        )
    }

    private fun Intent.putDownloadTargetExtras(
        animeId: Long,
        groupKey: String?,
        quality: PreferredQuality,
    ) {
        putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
        putExtra(DOWNLOAD_EXTRA_GROUP_KEY, groupKey.orEmpty())
        putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, quality.name)
    }
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

// DownloadStopRequests
internal enum class DownloadStopRequest {
    Pause,
    Cancel,
}

internal class DownloadStopRequests {
    private val cancelRequests = mutableSetOf<Long>()
    private val pauseRequests = mutableSetOf<Long>()

    @Synchronized
    fun request(ids: Set<Long>, request: DownloadStopRequest) {
        when (request) {
            DownloadStopRequest.Pause -> {
                pauseRequests.addAll(ids)
                cancelRequests.removeAll(ids)
            }
            DownloadStopRequest.Cancel -> {
                cancelRequests.addAll(ids)
                pauseRequests.removeAll(ids)
            }
        }
    }

    @Synchronized
    fun isCancelRequested(id: Long): Boolean = id in cancelRequests

    @Synchronized
    fun isPauseRequested(id: Long): Boolean = id in pauseRequests

    @Synchronized
    fun isStopRequested(id: Long): Boolean = id in cancelRequests || id in pauseRequests

    @Synchronized
    fun clear(id: Long) {
        cancelRequests -= id
        pauseRequests -= id
    }

    @Synchronized
    fun clearAll() {
        cancelRequests.clear()
        pauseRequests.clear()
    }
}
