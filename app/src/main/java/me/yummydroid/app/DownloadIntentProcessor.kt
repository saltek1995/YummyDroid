package me.yummydroid.app

import android.content.Context
import android.content.Intent
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.YummyAnimeRepository

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
