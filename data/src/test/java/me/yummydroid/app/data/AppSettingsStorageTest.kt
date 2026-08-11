package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStorageTest {
    @Test
    fun emptyPreferencesReturnApplicationDefaults() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        assertEquals(AppSettings(), storage.read())
    }
}
