package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateDownloadServiceTest {
    @Test
    fun `apk file name trims version and removes lowercase prefix`() {
        assertEquals("YummyDroid-1.2.3.apk", updateApkFileName(" v1.2.3 "))
        assertEquals("YummyDroid-update.apk", updateApkFileName("update"))
    }

    @Test
    fun `download progress handles known unknown and oversized totals`() {
        assertEquals(50, updateDownloadProgress(downloadedBytes = 500, totalBytes = 1_000))
        assertEquals(0, updateDownloadProgress(downloadedBytes = 500, totalBytes = -1))
        assertEquals(100, updateDownloadProgress(downloadedBytes = 1_500, totalBytes = 1_000))
    }

    @Test
    fun `pending update install store saves reads and clears apk request`() {
        val preferences = InMemorySharedPreferences()

        PendingUpdateInstallStore.save(preferences, apkPath = "/tmp/YummyDroid.apk", version = "1.4.1")

        assertEquals(
            PendingUpdateInstall(apkPath = "/tmp/YummyDroid.apk", version = "1.4.1"),
            PendingUpdateInstallStore.read(preferences),
        )

        PendingUpdateInstallStore.clear(preferences)

        assertNull(PendingUpdateInstallStore.read(preferences))
    }

    @Test
    fun `pending update install store ignores blank apk path`() {
        val preferences = InMemorySharedPreferences()

        PendingUpdateInstallStore.save(preferences, apkPath = " ", version = "1.4.1")

        assertNull(PendingUpdateInstallStore.read(preferences))
    }
}
