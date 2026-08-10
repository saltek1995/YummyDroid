package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeFormatterTest {
    @Test
    fun defaultUnitsCoverEveryMagnitude() {
        assertEquals("0 B", formatByteSize(-1))
        assertEquals("1023 B", formatByteSize(1023))
        assertEquals("1 KB", formatByteSize(1024))
        assertEquals("1.5 MB", formatByteSize(1_572_864))
        assertEquals("2.0 GB", formatByteSize(2_147_483_648))
    }

    @Test
    fun customUnitsPreserveLocaleIndependentDecimalPoint() {
        assertEquals(
            "1.5 MiB",
            formatByteSize(
                bytes = 1_572_864,
                byteUnit = "byte",
                kilobyteUnit = "KiB",
                megabyteUnit = "MiB",
                gigabyteUnit = "GiB",
            ),
        )
    }
}
