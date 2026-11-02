package me.yummydroid.app

import android.app.Application
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.PreferredQuality
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DownloadTaskInterruptionRuntimeTest {
    @Test fun terminalHandlersAndLateMetadataFailuresCannotUndoPauseOrCancel() {
        val context = RuntimeEnvironment.getApplication()
        val settings = AppSettingsStorage(context)
        for (cancel in listOf(false, true)) {
            val tasks = DownloadCenterController()
            val runtime = DownloadTaskRuntime(context, settings, {}, tasks)
            val controller = DownloadIntentTaskController(context, settings, runtime)
            val id = tasks.addTask(DownloadTaskRequest(1, 1, "Anime", "Episode"))
            tasks.updateTask(id, DownloadTaskUpdate(state = DownloadTaskState.Running, downloadedBytes = 123))
            if (cancel) tasks.requestCancel(id) else tasks.requestPause(id)
            val stopped = tasks.task(id)

            runtime.markTaskCompleted(id, cvhSourceVideo(), PreferredQuality.Auto, 1)
            runtime.markTaskFailed(id, "late failure", 1)
            assertEquals(stopped, tasks.task(id))
            assertTrue(tasks.isStopRequested(id))
            assertFalse(tasks.updateTaskIfUnstopped(id, DownloadTaskUpdate(state = DownloadTaskState.Completed)))

            controller.handleStartFailure(id, IllegalStateException("late metadata"), R.string.ui_download_start_failed)
            assertEquals(stopped?.state, tasks.task(id)?.state)
            assertFalse(tasks.task(id)?.waitingForUnmetered ?: true)
            assertFalse(tasks.isStopRequested(id))
        }
    }
}
