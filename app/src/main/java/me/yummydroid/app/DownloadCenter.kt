package me.yummydroid.app

import android.content.Context
import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.PreferredQuality

// DownloadCenterController
internal class DownloadCenterController : DownloadTaskQueue {
    private var nextId = 1L
    private val stopRequests = DownloadStopRequests()
    private val networkObserver = DownloadNetworkObserver()
    private var appContext: Context? = null

    private val mutableState = MutableStateFlow(DownloadQueueSnapshot())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val safeContext = context.applicationContext
        if (appContext == null) {
            appContext = safeContext
            restoreTasks(safeContext)
        }
        networkObserver.register(safeContext) { resumeWaitingForAllowedNetwork(safeContext) }
    }

    @Synchronized
    override fun addTask(request: DownloadTaskRequest): Long {
        request.existingTaskId
            ?.takeIf { id -> state.value.tasks.any { it.id == id } }
            ?.let { id ->
                updateTask(id, request.metadataUpdate())
                return id
            }
        state.value.tasks.findReusableTask(request.identity)?.let { existing ->
            if (request.episodeKey.isNotBlank() && !existing.isActive) reusePlanEpisode(existing.id, request)
            return existing.id
        }

        val task = request.createTask(nextId++)
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = listOf(task) + snapshot.tasks)
        }
        return task.id
    }

    private fun reusePlanEpisode(id: Long, request: DownloadTaskRequest) {
        val redundantIds = state.value.tasks
            .filter { it.id != id && !it.isActive && request.identity.matches(it) }
            .mapTo(mutableSetOf()) { it.id }
        redundantIds.forEach(stopRequests::clear)
        val update = request.metadataUpdate().copy(
            state = DownloadTaskState.Queued,
            message = localizedText(R.string.ui_queued),
            bytesPerSecond = 0L,
            waitingForUnmetered = false,
        )
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filterNot { it.id in redundantIds }.map { task ->
                if (task.id == id) task.applyUpdate(update) else task
            })
        }
    }

    override fun updateTask(id: Long, update: DownloadTaskUpdate) {
        updateAndPersist { snapshot ->
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

    @Synchronized
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
        try {
            DownloadService.enqueueTask(context, state.value.tasks.first { it.id == id })
        } catch (failure: Throwable) {
            updateAndPersist { snapshot ->
                snapshot.copy(tasks = snapshot.tasks.map { if (it.id == id) task else it })
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && failure is ForegroundServiceStartNotAllowedException) {
                AppLog.w("DownloadCenter", "Android deferred background resume until the next visible app session", failure)
                return
            }
            throw failure
        }
    }

    fun resumeWaitingForAllowedNetwork(context: Context, includeInterrupted: Boolean = false) {
        initialize(context)
        val settings = AppSettingsStorage(context).read()
        if (!includeInterrupted && !DownloadNetworkPolicy.canDownloadNow(context, settings)) return
        state.value.tasks
            .automaticResumeTargets(includeInterrupted)
            .forEach { resumeTask(context, it.id) }
    }

    fun clearFinished() {
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filter { it.isActive || it.isWaiting })
        }
    }

    @Synchronized
    override fun removeTask(id: Long) {
        stopRequests.clear(id)
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.filterNot { it.id == id })
        }
    }

    fun moveTaskToTop(id: Long) {
        updateAndPersist { snapshot ->
            val task = snapshot.tasks.firstOrNull { it.id == id } ?: return@updateAndPersist snapshot
            snapshot.copy(tasks = listOf(task) + snapshot.tasks.filterNot { it.id == id })
        }
    }

    @Synchronized
    fun clearAll() {
        stopRequests.clearAll()
        updateAndPersist { DownloadQueueSnapshot() }
    }

    fun interruptActiveTasks() {
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.restoreInterruptedTasks(
                waitingForNetworkMessage = localizedText(R.string.ui_download_network_waiting_unmetered),
                waitingToResumeMessage = localizedText(R.string.ui_download_system_interrupted),
            ))
        }
    }

    @Synchronized
    fun cancelTargets(target: DownloadRemoval, removedPlanIds: Set<String> = emptySet()) {
        val ids = state.value.tasks.filter { target.matches(it.animeId, it.videoId) || it.planId in removedPlanIds }
            .filterNot { it.state == DownloadTaskState.Completed || it.state == DownloadTaskState.Cancelled }
            .mapTo(mutableSetOf()) { it.id }
        stopRequests.request(ids, DownloadStopRequest.Cancel)
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.updateTaskStates(ids, DownloadTaskState.Cancelled, localizedText(R.string.ui_cancelled)))
        }
    }

    private fun restoreTasks(context: Context) {
        val restored = DownloadQueueStorage(context)
            .read()
            .restoreInterruptedTasks(
                waitingForNetworkMessage = localizedText(R.string.ui_download_network_waiting_unmetered),
                waitingToResumeMessage = localizedText(R.string.ui_waiting_to_resume),
            )
        if (restored.isEmpty()) return
        nextId = (restored.maxOf { it.id } + 1L).coerceAtLeast(1L)
        mutableState.value = DownloadQueueSnapshot(restored)
    }

    private fun requestStop(
        id: Long,
        request: DownloadStopRequest,
        targetState: DownloadTaskState,
        message: String,
    ) {
        val targetIds = state.value.tasks.stopTargetIds(id)
        stopRequests.request(targetIds, request)
        updateAndPersist { snapshot ->
            snapshot.copy(tasks = snapshot.tasks.updateTaskStates(targetIds, targetState, message))
        }
    }

    @Synchronized
    private fun updateAndPersist(
        transform: (DownloadQueueSnapshot) -> DownloadQueueSnapshot,
    ) {
        val transformed = transform(mutableState.value)
        mutableState.value = transformed.copy(tasks = transformed.tasks.cappedDownloadTasks())
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

    internal fun cancelTargets(target: DownloadRemoval, removedPlanIds: Set<String> = emptySet()) = controller.cancelTargets(target, removedPlanIds)

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

    fun resumeWaitingForAllowedNetwork(context: Context, includeInterrupted: Boolean = false) {
        controller.resumeWaitingForAllowedNetwork(context, includeInterrupted)
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

    internal fun interruptActiveTasks() = controller.interruptActiveTasks()
}
