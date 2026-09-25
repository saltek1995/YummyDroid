package me.yummydroid.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedProviderMediaLoadsTest {
    @Test fun canceledAndRetiredLoadsNeverContributeToNewSession() {
        val loads = CompletedProviderMediaLoads()
        val first = Any()
        loads.select(first)
        loads.started(1, first)
        loads.started(2, first)
        loads.canceled(1)
        loads.completed(1, 100)
        loads.completed(2, 200)
        assertEquals(200L, loads.bytes)
        loads.started(3, first)
        val next = Any()
        loads.select(next)
        loads.completed(3, 300)
        loads.started(4, first)
        loads.completed(4, 400)
        assertEquals(0L, loads.bytes)
        loads.started(5, next)
        loads.completed(5, 500)
        loads.completed(5, 500)
        assertEquals(500L, loads.bytes)
    }

    @Test fun qualityReloadWithinSameSessionPreservesCompletedBytes() {
        val loads = CompletedProviderMediaLoads()
        val session = Any()
        loads.select(session)
        loads.started(1, session)
        loads.completed(1, 700)
        loads.select(session)
        loads.started(3, session)
        loads.retireLoads()
        loads.completed(3, 5000)
        loads.started(2, session)
        loads.completed(2, 300)
        assertEquals(1000L, loads.bytes)
        loads.select(null)
        assertEquals(0L, loads.bytes)
    }
}
