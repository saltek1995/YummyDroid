package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class AppSettingsStorageTest {
    @Test
    fun aServiceObserverReceivesSettingsSavedByAnotherStorageInstance() = runBlocking {
        val preferences = InMemoryAppSettingsPreferences()
        val serviceStorage = AppSettingsStorage(preferences)
        val screenStorage = AppSettingsStorage(preferences)
        val observed = async(start = CoroutineStart.UNDISPATCHED) { serviceStorage.observe().take(2).toList() }
        val changed = AppSettings(
            downloadParallelism = 3, downloadSpeedLimitMegabytesPerSecond = 2,
            interfaceScale = InterfaceScale(130), contentLanguage = ContentLanguage.English,
        )

        screenStorage.save(changed)

        assertEquals(listOf(AppSettings(), changed), observed.await())
    }

    @Test
    fun emptyPreferencesReturnApplicationDefaults() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        assertEquals(AppSettings(), storage.read())
    }
}
