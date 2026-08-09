package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsSliderPolicyTest {
    @Test
    fun valueIsClampedAndRoundedToNearestStep() {
        val range = 50..130

        assertEquals(50, normalizeSliderValue(1, range, valueStep = 10))
        assertEquals(80, normalizeSliderValue(84, range, valueStep = 10))
        assertEquals(90, normalizeSliderValue(85, range, valueStep = 10))
        assertEquals(130, normalizeSliderValue(1_000, range, valueStep = 10))
    }

    @Test
    fun materialSliderReceivesOnlyIntermediateStepCount() {
        assertEquals(7, sliderStepCount(50..130, valueStep = 10))
        assertEquals(2, sliderStepCount(1..4, valueStep = 1))
    }

    @Test
    fun invalidStepConfigurationIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            normalizeSliderValue(50, 50..130, valueStep = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            normalizeSliderValue(50, 50..125, valueStep = 10)
        }
    }
}
