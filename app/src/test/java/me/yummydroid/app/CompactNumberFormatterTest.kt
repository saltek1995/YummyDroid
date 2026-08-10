package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactNumberFormatterTest {
    @Test
    fun viewsUseExpectedThresholdPrecision() {
        assertEquals("999", formatViews(999))
        assertEquals("1.2 K", formatViews(1_234))
        assertEquals("448 K", formatViews(448_000))
        assertEquals("1.3 M", formatViews(1_300_000))
        assertEquals("12 M", formatViews(12_000_000))
    }

    @Test
    fun compactCountAcceptsLocalizedSuffixes() {
        assertEquals("1.2 thousand", formatCompactCount(1_234, "thousand", "million"))
        assertEquals("1.3 million", formatCompactCount(1_300_000, "thousand", "million"))
    }

    @Test
    fun ratingAlwaysUsesOneDecimalWithDotSeparator() {
        assertEquals("9.6", formatRating(9.56))
    }
}
