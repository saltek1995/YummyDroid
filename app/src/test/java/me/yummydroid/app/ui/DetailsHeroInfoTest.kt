package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailsHeroInfoTest {
    @Test
    fun presentFactRejectsEmptyAndPlaceholderValues() {
        listOf("", " ", "unknown", "NULL", "-", "\u2014", "\u0432\u0402\u201d").forEach { value ->
            assertFalse(value.isPresentFactValue(), value)
        }
    }

    @Test
    fun presentFactAcceptsActualMetadata() {
        assertTrue("Studio Trigger".isPresentFactValue())
        assertTrue("0".isPresentFactValue())
    }
}
