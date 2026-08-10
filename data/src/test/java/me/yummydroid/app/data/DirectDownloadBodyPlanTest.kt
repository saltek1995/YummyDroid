package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectDownloadBodyPlanTest {
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
