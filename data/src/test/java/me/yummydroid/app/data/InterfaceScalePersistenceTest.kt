package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class InterfaceScalePersistenceTest {
    @Test
    fun scaleIsClampedAndRoundedToTenPercentSteps() {
        assertEquals(MIN_INTERFACE_SCALE_PERCENT, AppSettings(interfaceScale = InterfaceScale(1)).normalized().interfaceScale.percent)
        assertEquals(MAX_INTERFACE_SCALE_PERCENT, AppSettings(interfaceScale = InterfaceScale(1_000)).normalized().interfaceScale.percent)
        assertEquals(80, InterfaceScale.fromPercent(84).percent)
        assertEquals(90, InterfaceScale.fromPercent(85).percent)
        assertEquals(130, InterfaceScale.fromPercent(134).percent)
    }

    @Test
    fun legacyNamesAndNumericValuesRemainReadable() {
        assertEquals(InterfaceScale(80), InterfaceScale.fromPersistedValue("Percent80"))
        assertEquals(InterfaceScale(120), InterfaceScale.fromPersistedValue("Percent120"))
        assertEquals(InterfaceScale(130), InterfaceScale.fromPersistedValue("150%"))
        assertEquals(InterfaceScale(90), InterfaceScale.fromPersistedValue("87%"))
        assertEquals(InterfaceScale.Default, InterfaceScale.fromPersistedValue(DEFAULT_INTERFACE_SCALE_PERCENT))
        assertEquals(null, InterfaceScale.fromPersistedValue("Unknown"))
    }
}
