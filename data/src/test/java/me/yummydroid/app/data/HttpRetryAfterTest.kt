package me.yummydroid.app.data

import kotlin.test.*

class HttpRetryAfterTest {
    @Test fun numericDateInvalidAndOverflowDeadlines() {
        assertEquals(61_000L, httpRetryAfterEpochMs(mapOf("retry-after" to listOf("60")), 1_000L))
        assertEquals(1445412480000, httpRetryAfterEpochMs(mapOf("Retry-After" to listOf("Wed, 21 Oct 2015 07:28:00 GMT")), 0))
        assertEquals(Long.MAX_VALUE, httpRetryAfterEpochMs(mapOf("Retry-After" to listOf(Long.MAX_VALUE.toString())), 1_000L))
        assertNull(httpRetryAfterEpochMs(mapOf("Retry-After" to listOf("-1")), 1_000L))
        assertNull(httpRetryAfterEpochMs(mapOf("Retry-After" to listOf("invalid")), 1_000L))
    }
}
