package me.yummydroid.app

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Ordered queue checkpoints. UI publication and accepted Pause/Cancel never wait for JSON encoding. */
internal class DownloadQueuePersistence(
    private val schedule: (Long, () -> Unit) -> Unit = { delay, task -> executor.schedule(task, delay, TimeUnit.MILLISECONDS); Unit },
    private val write: (List<DownloadTaskUi>) -> Unit,
) {
    private val lock = Any()
    private var latest: List<DownloadTaskUi>? = null
    private var revision = 0L
    private var scheduled = false
    private var urgent = false

    fun submit(tasks: List<DownloadTaskUi>, progressOnly: Boolean) = synchronized(lock) {
        latest = tasks
        if (scheduled && (progressOnly || urgent)) return@synchronized
        urgent = !progressOnly
        scheduled = true
        val ticket = ++revision
        schedule(if (urgent) 0L else 1_000L) { drain(ticket) }
    }

    private fun drain(ticket: Long) {
        val snapshot = synchronized(lock) {
            if (ticket != revision) return
            val snapshot = latest ?: return
            latest = null
            scheduled = false
            urgent = false
            snapshot
        }
        try {
            write(snapshot)
        } catch (failure: Exception) {
            AppLog.w("YummyDroidDownloads", "Could not persist the download queue checkpoint", failure)
        }
    }

    private companion object {
        val executor = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "yummy-download-queue-writer").apply { isDaemon = true }
        }
    }
}
