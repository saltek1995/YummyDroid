package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsBringIntoViewPolicyTest {
    @Test
    fun focusedContentStaysInsideGuardedEdges() {
        assertEquals(-36f, DetailsBringIntoViewSpec.calculateScrollDistance(20f, 100f, 1000f))
        assertEquals(56f, DetailsBringIntoViewSpec.calculateScrollDistance(900f, 100f, 1000f))
        assertEquals(0f, DetailsBringIntoViewSpec.calculateScrollDistance(100f, 100f, 1000f))
    }
}
