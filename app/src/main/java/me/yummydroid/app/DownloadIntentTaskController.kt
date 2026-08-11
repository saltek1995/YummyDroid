package me.yummydroid.app

import android.content.Context
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage

internal class DownloadIntentTaskController(
    private val context: Context,
    private val settingsStorage: AppSettingsStorage,
    private val taskRuntime: DownloadTaskRuntime,
) {
    fun currentSettings(): AppSettings = settingsStorage.read()

    fun canStart(taskId: Long): Boolean {
        val settings = currentSettings()
        if (DownloadNetworkPolicy.canDownloadNow(context, settings)) return true
        taskRuntime.pauseForNetwork(taskId, settings)
        return false
    }

    fun removeFinishedTask(taskId: Long) {
        val task = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }
        if (task?.state == DownloadTaskState.Completed || task?.state == DownloadTaskState.Cancelled) {
            DownloadCenter.removeTask(taskId)
            taskRuntime.notifyChanged()
        }
    }

    fun handleStartFailure(taskId: Long, throwable: Throwable, fallbackMessageRes: Int) {
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
