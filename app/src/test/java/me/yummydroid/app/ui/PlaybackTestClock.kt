package me.yummydroid.app.ui

import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.RobolectricUtil

/** Pace simulated playback without depending on the OS rounding a 1ms sleep. */
internal fun advancePlaybackTestClockUntil(
    clock: FakeClock,
    stepMs: Long,
    condition: () -> Boolean,
) {
    var nextTickNs = System.nanoTime()
    RobolectricUtil.runMainLooperUntil {
        if (condition()) true else {
            val nowNs = System.nanoTime()
            if (nowNs - nextTickNs >= 0L) {
                clock.advanceTime(stepMs)
                // Do not catch up missed ticks: loaders still need a real I/O turn.
                nextTickNs = nowNs + 1_000_000L
            }
            Thread.yield()
            false
        }
    }
}
