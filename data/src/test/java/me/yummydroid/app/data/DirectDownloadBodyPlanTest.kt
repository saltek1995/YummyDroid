package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.IOException

class DirectDownloadBodyPlanTest {
    @Test
    fun rangeRejectionOnlyCompletesAnExactlyMatchingFile() {
        assertTrue(validateDirectDownloadRange(416, "bytes */400", 400))
        for (header in listOf(null, "bytes */300", "bytes */500", "bytes */*", "garbage/400")) {
            assertFailsWith<IOException> { validateDirectDownloadRange(416, header, 400) }
        }
        assertFailsWith<IOException> { validateDirectDownloadRange(416, "bytes */0", 0) }
    }

    @Test
    fun partialContentCannotAppendAMissingOrMismatchedRange() {
        assertFalse(validateDirectDownloadRange(206, "bytes 400-999/1000", 400))
        assertFalse(validateDirectDownloadRange(206, "bytes 400-999/*", 400))
        assertFalse(validateDirectDownloadRange(200, null, 400))
        assertFailsWith<IOException> { validateDirectDownloadRange(206, "bytes 400-999/99999999999999999999999999", 400) }
        for (header in listOf(null, "bytes 0-999/1000", "bytes 500-999/1000", "bytes 400-300/1000", "bytes 400-999/999")) {
            assertFailsWith<IOException> { validateDirectDownloadRange(206, header, 400) }
        }
    }

    @Test
    fun partialContentAppendsAndIncludesExistingBytesInTotal() {
        assertEquals(
            DirectDownloadBodyPlan(canAppend = true, startingBytes = 400L, totalBytes = 1_000L),
            directDownloadBodyPlan(400L, 206, contentRangeTotal = null, contentLength = 600L),
        )
    }

    @Test
    fun fullResponseRestartsExistingPartialDownload() {
        assertEquals(
            DirectDownloadBodyPlan(canAppend = false, startingBytes = 0L, totalBytes = 1_000L),
            directDownloadBodyPlan(400L, 200, contentRangeTotal = null, contentLength = 1_000L),
        )
    }

    @Test
    fun contentRangeTotalTakesPriorityAndUnknownLengthStaysUnknown() {
        assertEquals(2_000L, directDownloadBodyPlan(400L, 206, 2_000L, 600L).totalBytes)
        assertEquals(-1L, directDownloadBodyPlan(0L, 200, null, -1L).totalBytes)
    }
}
