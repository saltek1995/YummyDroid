package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchHistoryRefreshStateTest {
    @Test
    fun oneCacheClockPreventsCompetingLoadsAndReopensWhenDue() {
        var nowMs = 1_000L
        val state = WatchHistoryRefreshState(
            monotonicClockMs = { nowMs },
            refreshIntervalMs = 60_000L,
        )

        assertEquals(true, state.beginRefresh(false, false, true, false)?.showCachedSnapshot)
        state.markRemoteSynchronized()
        assertNull(state.beginRefresh(false, true, true, false))

        nowMs += 60_001L
        assertEquals(false, state.beginRefresh(false, true, true, false)?.showCachedSnapshot)

        state.reset()
        assertNull(state.beginRefresh(false, false, true, true))
        assertEquals(true, state.beginRefresh(true, true, true, true)?.showCachedSnapshot)
    }
}
