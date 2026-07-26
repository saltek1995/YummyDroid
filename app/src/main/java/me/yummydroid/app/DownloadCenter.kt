package me.yummydroid.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.edit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.decodeAppJsonOrNull
import me.yummydroid.app.data.encodeAppJson
import me.yummydroid.app.data.PreferredQuality

private const val DOWNLOAD_TASK_HISTORY_LIMIT = 120

enum class DownloadTaskState {
    Queued,
    Running,
    Paused,
    Added,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
data class DownloadTaskUi(
    val id: Long,
    val animeId: Long,
    val videoId: Long?,
    val title: String,
    val episodeTitle: String,
    val qualityTitle: String = "Авто",
    val groupKey: String = "",
    val preferredQualityName: String = PreferredQuality.Auto.name,
    val planId: String = "",
    val batchKey: String = "",
    val batchTotal: Int = 0,
    val batchCompleted: Int = 0,
    val isBatchSummary: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val state: DownloadTaskState = DownloadTaskState.Queued,
    val message: String = "",
    val waitingForUnmetered: Boolean = false,
    val attemptCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean
        get() = state == DownloadTaskState.Queued || state == DownloadTaskState.Running

    val canResume: Boolean
        get() = state == DownloadTaskState.Paused || state == DownloadTaskState.Failed
}

data class DownloadQueueSnapshot(
    val tasks: List<DownloadTaskUi> = emptyList(),
) {
    val activeTasks: List<DownloadTaskUi>
        get() = tasks.filter { it.isActive }

    val finishedTasks: List<DownloadTaskUi>
        get() = tasks.filter { !it.isActive && it.state != DownloadTaskState.Paused }
}

object DownloadCenter {
    private val ids = AtomicLong(1L)
    private val cancelRequests = mutableSetOf<Long>()
    private val pauseRequests = mutableSetOf<Long>()
    private var appContext: Context? = null
    private var networkCallbackRegistered = false

    val state = MutableStateFlow(DownloadQueueSnapshot())

    @Synchronized
    fun initialize(context: Context) {
        val safeContext = context.applicationContext
        if (appContext == null) {
            appContext = safeContext
            val restored = readPersistedTasks(safeContext)
                .map { task ->
                    if (task.state == DownloadTaskState.Running || task.state == DownloadTaskState.Queued) {
                        task.copy(
                            state = DownloadTaskState.Paused,
                            bytesPerSecond = 0L,
                            message = if (task.waitingForUnmetered) {
                                "Ожидание Wi-Fi или Ethernet"
                            } else {
                                "Ожидает возобновления"
                            },
                        )
                    } else {
                        task
                    }
                }
                .take(DOWNLOAD_TASK_HISTORY_LIMIT)
            if (restored.isNotEmpty()) {
                ids.set((restored.maxOf { it.id } + 1L).coerceAtLeast(1L))
                state.value = DownloadQueueSnapshot(restored)
            }
        }
        registerNetworkCallback(safeContext)
    }

    fun addTask(
        animeId: Long,
        videoId: Long?,
        title: String,
        episodeTitle: String,
        qualityTitle: String = "Авто",
        groupKey: String = "",
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        planId: String = "",
        batchKey: String = "",
        batchTotal: Int = 0,
        batchCompleted: Int = 0,
        isBatchSummary: Boolean = false,
        existingTaskId: Long? = null,
    ): Long {
        if (existingTaskId != null && state.value.tasks.any { it.id == existingTaskId }) {
            updateTask(
                id = existingTaskId,
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
            )
            return existingTaskId
        }

        val existing = state.value.tasks.firstOrNull {
            (it.isActive || it.state == DownloadTaskState.Paused) &&
                it.animeId == animeId &&
                it.videoId == videoId &&
                it.groupKey == groupKey &&
                it.preferredQualityName == preferredQuality.name
        }
        if (existing != null) return existing.id

        val task = DownloadTaskUi(
            id = ids.getAndIncrement(),
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
        )
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = (listOf(task) + snapshot.tasks).cappedDownloadTasks())
        }
        return task.id
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
        this.state.updateAndPersist { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.map { task ->
                    if (task.id == id) {
                        task.copy(
                            title = title ?: task.title,
                            episodeTitle = episodeTitle ?: task.episodeTitle,
                            qualityTitle = qualityTitle ?: task.qualityTitle,
                            groupKey = groupKey ?: task.groupKey,
                            preferredQualityName = preferredQualityName ?: task.preferredQualityName,
                            planId = planId ?: task.planId,
                            batchKey = batchKey ?: task.batchKey,
                            batchTotal = batchTotal ?: task.batchTotal,
                            batchCompleted = batchCompleted ?: task.batchCompleted,
                            isBatchSummary = isBatchSummary ?: task.isBatchSummary,
                            progress = progress?.coerceIn(0f, 1f) ?: task.progress,
                            downloadedBytes = downloadedBytes ?: task.downloadedBytes,
                            totalBytes = totalBytes ?: task.totalBytes,
                            bytesPerSecond = bytesPerSecond ?: task.bytesPerSecond,
                            state = state ?: task.state,
                            message = message ?: task.message,
                            waitingForUnmetered = waitingForUnmetered ?: task.waitingForUnmetered,
                            attemptCount = attemptCount ?: task.attemptCount,
                            updatedAtMs = System.currentTimeMillis(),
                        )
                    } else {
                        task
                    }
                },
            )
        }
    }

    @Synchronized
    fun requestPause(id: Long) {
        pauseRequests += id
        cancelRequests -= id
        updateTask(
            id = id,
            state = DownloadTaskState.Paused,
            bytesPerSecond = 0L,
            message = "Пауза",
            waitingForUnmetered = false,
        )
    }

    @Synchronized
    fun requestCancel(id: Long) {
        cancelRequests += id
        pauseRequests -= id
        updateTask(
            id = id,
            state = DownloadTaskState.Cancelled,
            bytesPerSecond = 0L,
            message = "Отменено",
            waitingForUnmetered = false,
        )
    }

    @Synchronized
    fun isCancelRequested(id: Long): Boolean = id in cancelRequests

    @Synchronized
    fun isPauseRequested(id: Long): Boolean = id in pauseRequests

    @Synchronized
    fun isStopRequested(id: Long): Boolean = id in cancelRequests || id in pauseRequests

    @Synchronized
    fun clearStopRequest(id: Long) {
        cancelRequests -= id
        pauseRequests -= id
    }

    fun resumeTask(context: Context, id: Long) {
        initialize(context)
        val task = state.value.tasks.firstOrNull { it.id == id } ?: return
        if (!task.canResume) return
        clearStopRequest(id)
        updateTask(
            id = id,
            state = DownloadTaskState.Queued,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = -1L,
            bytesPerSecond = 0L,
            message = "В очереди",
            waitingForUnmetered = false,
            attemptCount = 0,
        )
        DownloadService.enqueueTask(context, state.value.tasks.first { it.id == id })
    }

    fun resumeWaitingForAllowedNetwork(context: Context) {
        initialize(context)
        val settings = me.yummydroid.app.data.AppSettingsStorage(context).read()
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

    fun removeTask(id: Long) {
        cancelRequests -= id
        pauseRequests -= id
        state.updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filterNot { it.id == id })
        }
    }

    fun clearHistory() {
        clearFinished()
    }

    fun clearAll() {
        cancelRequests.clear()
        pauseRequests.clear()
        state.updateAndPersist { DownloadQueueSnapshot() }
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
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TASKS, state.value.tasks.take(DOWNLOAD_TASK_HISTORY_LIMIT).encodeAppJson())
        }
    }

    private fun readPersistedTasks(context: Context): List<DownloadTaskUi> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, null)
            ?.takeIf { it.isNotBlank() }
            ?.decodeAppJsonOrNull<List<DownloadTaskUi>>()
            .orEmpty()
    }

    private fun registerNetworkCallback(context: Context) {
        if (networkCallbackRegistered) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                resumeWaitingForAllowedNetwork(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                resumeWaitingForAllowedNetwork(context)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallbackRegistered = true }
    }

    private const val PREFS_NAME = "yummydroid_download_queue"
    private const val KEY_TASKS = "tasks"
}

private fun List<DownloadTaskUi>.cappedDownloadTasks(): List<DownloadTaskUi> {
    if (size <= DOWNLOAD_TASK_HISTORY_LIMIT) return this
    val protectedTasks = filter { it.isBatchSummary && it.state != DownloadTaskState.Cancelled }
    return (protectedTasks + this)
        .distinctBy { it.id }
        .take(DOWNLOAD_TASK_HISTORY_LIMIT)
}
