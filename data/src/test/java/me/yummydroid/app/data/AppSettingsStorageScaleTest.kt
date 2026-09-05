package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStorageScaleTest {
    @Test
    fun persistedLegacyScaleValuesRemainReadable() {
        val preferences = InMemoryAppSettingsPreferences()
        val storage = AppSettingsStorage(preferences)

        preferences.values["interface_scale"] = "Percent120"
        assertEquals(InterfaceScale(120), storage.read().interfaceScale)

        preferences.values["interface_scale"] = "150%"
        assertEquals(InterfaceScale(130), storage.read().interfaceScale)

        preferences.values["interface_scale"] = "Unknown"
        assertEquals(InterfaceScale.Default, storage.read().interfaceScale)

        preferences.values["interface_scale"] = Long.MAX_VALUE
        assertEquals(InterfaceScale(130), storage.read().interfaceScale)
        preferences.values["interface_scale"] = Long.MIN_VALUE
        assertEquals(InterfaceScale(50), storage.read().interfaceScale)
    }

}
