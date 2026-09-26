package me.yummydroid.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A playback-owned, single-flight subtitle load. Construction performs no work. */
class DeferredPlaybackSubtitle(private val loader: suspend () -> String?) {
    private val mutex = Mutex()
    private var completed = false
    private var result: String? = null

    suspend fun resolve(): String? = mutex.withLock {
        currentCoroutineContext().ensureActive()
        if (!completed) {
            val loaded = try {
                loader()
            } catch (failure: Exception) {
                failure.throwIfCancellation()
                null
            }
            currentCoroutineContext().ensureActive()
            result = loaded
            completed = true
        }
        result
    }
}
