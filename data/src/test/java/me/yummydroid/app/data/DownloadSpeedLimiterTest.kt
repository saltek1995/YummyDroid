package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class DownloadSpeedLimiterTest {
    @Test
    fun limiterSharesOneBudgetAcrossSequentialConsumers() = runBlocking {
        var nowMs = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = DownloadSpeedLimiter(
            bytesPerSecondProvider = { 100L },
            clockMs = { nowMs },
            sleepMs = { delayMs ->
                sleeps += delayMs
                nowMs += delayMs
            },
        )

        limiter.throttle(60L)
        limiter.throttle(60L)

        assertEquals(listOf(1_000L), sleeps)
    }

    @Test
    fun zeroLimitIsUnlimited() = runBlocking {
        var nowMs = 0L
        val sleeps = mutableListOf<Long>()
        val limiter = DownloadSpeedLimiter(
            bytesPerSecondProvider = { 0L },
            clockMs = { nowMs },
            sleepMs = { delayMs ->
                sleeps += delayMs
                nowMs += delayMs
            },
        )

        limiter.throttle(1_000_000L)

        assertEquals(emptyList(), sleeps)
    }
}
