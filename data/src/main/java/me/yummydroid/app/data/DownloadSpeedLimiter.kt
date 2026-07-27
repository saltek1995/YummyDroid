package me.yummydroid.app.data

import kotlinx.coroutines.delay

interface DownloadBandwidthLimiter {
    suspend fun throttle(bytes: Long)
}

object NoOpDownloadBandwidthLimiter : DownloadBandwidthLimiter {
    override suspend fun throttle(bytes: Long) = Unit
}

class DownloadSpeedLimiter(
    private val bytesPerSecondProvider: () -> Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleepMs: suspend (Long) -> Unit = { delay(it) },
) : DownloadBandwidthLimiter {
    private val lock = Any()
    private var windowStartMs = clockMs()
    private var windowBytes = 0L

    override suspend fun throttle(bytes: Long) {
        var remaining = bytes.coerceAtLeast(0L)
        while (remaining > 0L) {
            var unlimited = false
            val waitMs = synchronized(lock) {
                val limit = bytesPerSecondProvider().coerceAtLeast(0L)
                if (limit == 0L) {
                    unlimited = true
                    return@synchronized 0L
                }

                val now = clockMs()
                val elapsed = now - windowStartMs
                if (elapsed >= WINDOW_MS || elapsed < 0L) {
                    windowStartMs = now
                    windowBytes = 0L
                }

                val available = (limit - windowBytes).coerceAtLeast(0L)
                if (available > 0L) {
                    val granted = remaining.coerceAtMost(available)
                    windowBytes += granted
                    remaining -= granted
                    0L
                } else {
                    (WINDOW_MS - elapsed).coerceAtLeast(1L)
                }
            }

            if (unlimited) return
            if (waitMs > 0L) {
                sleepMs(waitMs)
            }
        }
    }

    private companion object {
        const val WINDOW_MS = 1_000L
    }
}
