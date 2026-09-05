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
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        planStorage = DownloadPlanStorage(context),
    )
    private val planProcessor = DownloadPlanIntentProcessor(
        context = context,
        repository = repository,
        taskRuntime = taskRuntime,
        videoProcessor = videoProcessor,
        taskController = taskController,
    )

    suspend fun process(intent: Intent) {
        val taskId = intent.getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L)
        if (taskId > 0L) {
            val task = DownloadCenter.taskQueue.task(taskId) ?: return
            if (!task.isActive && task.state != DownloadTaskState.Interrupted) return
        }
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

    fun finish(startId: Int) {
        if (!service.stopSelfResult(startId)) return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        notificationStartedAtMs = 0L
        updateGate.reset()
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
            .setSmallIcon(R.drawable.ic_notification_yummydroid)
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

internal data class DownloadRemoval(
    val animeId: Long,
    val videoIds: Set<Long>? = null,
    val episodeKeys: Set<String> = emptySet(),
) {
    fun matches(animeId: Long, videoId: Long?): Boolean {
        return this.animeId == animeId && (videoIds == null || videoId in videoIds)
    }
}

// Owns command admission, individual writers, and file-maintenance barriers.
internal class DownloadCommandCoordinator {
    private data class VideoTarget(val id: Long, val episodeKey: String)

    private class Command(
        val animeId: Long,
        val videoId: Long?,
        val barrier: CompletableDeferred<Unit>?,
    ) : AbstractCoroutineContextElement(Key) {
        var job: Job? = null
        val excludedVideos = mutableSetOf<Long>()
        val excludedEpisodes = mutableSetOf<String>()
        val writers = mutableMapOf<Job, VideoTarget>()
        fun excludes(video: VideoTarget): Boolean = video.id in excludedVideos || video.episodeKey in excludedEpisodes
        companion object Key : CoroutineContext.Key<Command>
    }

    private val operations = SerialStateOperationCoordinator()
    private val maintenance = Mutex()
    private val lock = Any()
    private val processId = UUID.randomUUID().toString()
    private var nextId = 0L
    private val commands = mutableMapOf<String, Command>()
    private var maintenanceBarrier: CompletableDeferred<Unit>? = null
    private var removal: DownloadRemoval? = null

    fun reserve(animeId: Long, videoId: Long?, replacePending: Boolean = false): String = synchronized(lock) {
        val replaced = if (replacePending) interruptCommands(null) else emptyList()
        val barrier = maintenanceBarrier?.takeIf { removal == null || removal?.animeId == animeId }
        val id = "$processId:${++nextId}"
        commands[id] = Command(animeId, videoId, barrier)
        replaced.forEach(Job::cancel)
        id
    }

    fun discard(id: String) = synchronized(lock) { commands.remove(id); Unit }

    fun launch(
        scope: CoroutineScope,
        animeId: Long = 0L,
        videoId: Long? = null,
        id: String = reserve(animeId, videoId),
        action: suspend (StateOperationLease) -> Unit,
    ): Job = synchronized(lock) {
        // An old-process intent can be restored; an erased current-process request was cancelled.
        val acceptedId = if (id.startsWith("$processId:")) id else reserve(animeId, videoId)
        val command = commands[acceptedId] ?: return Job().apply { complete() }
        operations.launch(scope) { lease ->
            command.barrier?.await()
            withContext(command) { action(lease) }
        }.also { job ->
            command.job = job
            job.invokeOnCompletion { synchronized(lock) { commands.remove(acceptedId) } }
        }
    }

    fun isIdle(): Boolean = synchronized(lock) { commands.isEmpty() }

    suspend fun runVideo(videoId: Long, episodeKey: String = "", action: suspend () -> Unit): Boolean {
        val video = VideoTarget(videoId, episodeKey)
        val command = currentCoroutineContext()[Command] ?: run { action(); return true }
        return supervisorScope {
            val writer = synchronized(lock) {
                if (command.excludes(video)) return@supervisorScope false
                async(start = CoroutineStart.LAZY) { action() }.also { command.writers[it] = video }
            }
            try {
                writer.start()
                writer.await()
                true
            } catch (cancelled: CancellationException) {
                currentCoroutineContext().ensureActive()
                if (synchronized(lock) { !command.excludes(video) }) throw cancelled
                false
            } finally {
                synchronized(lock) { command.writers.remove(writer) }
            }
        }
    }

    suspend fun withMaintenance(target: DownloadRemoval? = null, action: suspend () -> Unit) = maintenance.withLock {
        val barrier = CompletableDeferred<Unit>()
        val interrupted = synchronized(lock) {
            maintenanceBarrier = barrier
            removal = target
            interruptCommands(target)
        }
        try {
            interrupted.forEach(Job::cancel)
            // Once downloads are interrupted, finish resetting their queue and files as one transaction.
            withContext(NonCancellable) {
                interrupted.joinAll()
                action()
            }
        } finally {
            synchronized(lock) {
                maintenanceBarrier = null
                removal = null
            }
            barrier.complete(Unit)
        }
    }

    private fun interruptCommands(target: DownloadRemoval?): List<Job> {
        val interrupted = mutableListOf<Job>()
        val iterator = commands.iterator()
        while (iterator.hasNext()) {
            val (_, command) = iterator.next()
            when {
                target == null || target.matches(command.animeId, command.videoId) -> {
                    command.job?.let(interrupted::add)
                    iterator.remove()
                }
                target.animeId == command.animeId -> {
                    command.excludedVideos += target.videoIds.orEmpty()
                    command.excludedEpisodes += target.episodeKeys
                    interrupted += command.writers.filterValues(command::excludes).keys
                }
            }
        }
        return interrupted
    }
}

// DownloadServiceRuntime
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var latestStartId = 0
    private lateinit var settingsStorage: AppSettingsStorage
    private lateinit var intentProcessor: DownloadIntentProcessor
    private lateinit var executionLimits: DownloadExecutionLimits
    private lateinit var notificationController: DownloadNotificationController

    override fun onCreate() {
        super.onCreate()
        settingsStorage = AppSettingsStorage(applicationContext)
        val settings = settingsStorage.read()
        executionLimits = DownloadExecutionLimits(settings)
        scope.launch { settingsStorage.observe().collect(executionLimits::update) }
        notificationController = DownloadNotificationController(this, settingsStorage)
        val speedLimiter = DownloadSpeedLimiter(bytesPerSecondProvider = { executionLimits.speedBytesPerSecond })
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
            executionLimits = executionLimits,
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
        currentService = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        notificationController.start()
        if (intent == null) {
            notificationController.finish(startId)
            return START_NOT_STICKY
        }
        commands.launch(
            scope,
            animeId = intent.getLongExtra(DOWNLOAD_EXTRA_ANIME_ID, 0L),
            videoId = intent.getLongExtra(DOWNLOAD_EXTRA_VIDEO_ID, 0L).takeIf { it > 0L },
            id = intent.getStringExtra(DOWNLOAD_EXTRA_COMMAND_ID).orEmpty(),
        ) {
            intentProcessor.process(intent)
        }.invokeOnCompletion {
            scope.launch(Dispatchers.Main.immediate) {
                finishIfIdle(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android requires an immediate stop. The existing maintenance barrier drains
        // old writers before restoring their queue, including when onDestroy cancels scope.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                commands.withMaintenance { DownloadCenter.interruptActiveTasks() }
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (currentService === this) currentService = null
        scope.cancel()
        super.onDestroy()
    }

    private fun finishIfIdle(startId: Int) {
        if (commands.isIdle() && DownloadCenter.state.value.activeTasks.isEmpty()) {
            notificationController.finish(startId)
        }
    }

    companion object {
        internal val commands = DownloadCommandCoordinator()
        private var currentService: DownloadService? = null

        internal suspend fun withCacheMaintenance(target: DownloadRemoval? = null, action: suspend () -> Unit) {
            commands.withMaintenance(target) {
                withContext(Dispatchers.Main.immediate) {
                    if (target == null) DownloadCenter.clearAll() else DownloadCenter.cancelTargets(target)
                    currentService?.let { it.finishIfIdle(it.latestStartId) }
                }
                action()
            }
        }

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

        fun enqueuePlan(context: Context, planId: String, animeId: Long) {
            DownloadServiceStarter.enqueuePlan(context, planId, animeId)
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

    fun enqueuePlan(context: Context, planId: String, animeId: Long) {
        if (planId.isBlank()) return
        startCommand(context, DOWNLOAD_ACTION_PLAN) {
            putExtra(DOWNLOAD_EXTRA_PLAN_ID, planId)
            putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
        }
    }

    private fun startCommand(
        context: Context,
        action: String,
        initializeCenter: Boolean = true,
        configure: Intent.() -> Unit,
    ) {
        if (initializeCenter) DownloadCenter.initialize(context)
        val intent = Intent(context, DownloadService::class.java).setAction(action).apply(configure)
        val commands = DownloadService.commands
        val commandId = commands.reserve(
            intent.getLongExtra(DOWNLOAD_EXTRA_ANIME_ID, 0L),
            intent.getLongExtra(DOWNLOAD_EXTRA_VIDEO_ID, 0L).takeIf { it > 0L },
        )
        try {
            context.startForegroundService(intent.putExtra(DOWNLOAD_EXTRA_COMMAND_ID, commandId))
        } catch (failure: Throwable) {
            commands.discard(commandId)
            throw failure
        }
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
internal const val DOWNLOAD_EXTRA_COMMAND_ID = "command_id"
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
