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
