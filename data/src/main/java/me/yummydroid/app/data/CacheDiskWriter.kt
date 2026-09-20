package me.yummydroid.app.data

import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred

/** Process-owned, ordered persistence for disposable caches. Never awaited by loading UI. */
internal fun interface CacheDiskWriter {
    fun enqueue(write: () -> Unit)

    companion object {
        val Default: CacheDiskWriter = object : CacheDiskWriter {
            private val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "yummy-cache-writer").apply { isDaemon = true }
            }

            override fun enqueue(write: () -> Unit) {
                executor.execute {
                    // A full/unavailable disk must not turn a successful network response into an error.
                    try { write() } catch (_: Exception) { }
                }
            }
        }
    }
}

/** Maintenance only; ordinary cache consumers must never wait for persistence. */
internal suspend fun CacheDiskWriter.awaitIdle() {
    val completed = CompletableDeferred<Unit>()
    enqueue { completed.complete(Unit) }
    completed.await()
}
