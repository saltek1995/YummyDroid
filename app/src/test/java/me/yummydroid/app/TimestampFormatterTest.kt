package me.yummydroid.app

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampFormatterTest {
    private lateinit var originalTimeZone: TimeZone

    @BeforeTest
    fun useUtc() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @AfterTest
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun scheduleTimestampTreatsInputAsEpochSeconds() {
        assertEquals("01.01 00:00", formatScheduleTimestamp(0))
        assertEquals("02.01 01:01", formatScheduleTimestamp(90_061))
    }

    @Test
    fun detailedTimestampsAcceptEpochSecondsAndMilliseconds() {
        val epochSeconds = 1_735_776_061L
        val epochMillis = epochSeconds * 1_000L

        assertEquals("02.01.2025 00:01", formatCommentTimestamp(epochSeconds))
        assertEquals("02.01.2025 00:01", formatNotificationTimestamp(epochMillis))
    }

    @Test
    fun detailedTimestampsRejectNonPositiveValues() {
        assertEquals("", formatCommentTimestamp(0))
        assertEquals("", formatNotificationTimestamp(-1))
    }
}
