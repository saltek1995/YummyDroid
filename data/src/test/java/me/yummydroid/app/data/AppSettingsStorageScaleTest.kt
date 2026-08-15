package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStorageScaleTest {
    @Test
    fun persistedLegacyScaleValuesRemainReadable() {
        val preferences = InMemoryAppSettingsPreferences()
        val storage = AppSettingsStorage(preferences)

        preferences.values["interface_scale"] = "Percent120"
        assertEquals(InterfaceScale(120), storage.readInterfaceScale())

        preferences.values["interface_scale"] = "150%"
        assertEquals(InterfaceScale(130), storage.readInterfaceScale())

        preferences.values["interface_scale"] = "Unknown"
        assertEquals(InterfaceScale.Default, storage.readInterfaceScale())
    }

    @Test
    fun interfaceScaleCanBePersistedBeforeActivityRecreation() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        storage.saveInterfaceScale(InterfaceScale(126))

        assertEquals(InterfaceScale(130), storage.readInterfaceScale())
    }

    @Test
    fun contentLanguageCanBePersistedBeforeActivityRecreation() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        storage.saveContentLanguage(ContentLanguage.Ukrainian)

        assertEquals(ContentLanguage.Ukrainian, storage.readContentLanguage())
    }
}
