package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource

class UpdateDownloadServiceTest {
    @Test
    fun cancelledApkWritePreservesThePreviousFileAndRemovesItsPartial() = runBlocking {
        val directory = Files.createTempDirectory("update-apk-cancel").toFile()
        val apk = directory.resolve("update.apk").apply { writeText("previous") }
        try {
            val client = updateClient(ByteArray(32_768) { 7 }.toResponseBody())
            val download = launch {
                val job = currentCoroutineContext()[Job]!!
                downloadUpdateApk(client, "https://example.test/update.apk", apk) { _, _ -> job.cancel() }
            }
            download.join()

            assertTrue(download.isCancelled)
            assertEquals("previous", apk.readText())
            assertFalse(directory.resolve("update.apk.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun incompleteApkResponseCannotReplaceAnExistingInstallableFile() = runBlocking {
        val directory = Files.createTempDirectory("update-apk-truncated").toFile()
        val apk = directory.resolve("update.apk").apply { writeText("previous") }
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 100L
            override fun source(): BufferedSource = Buffer().writeUtf8("truncated")
        }
        try {
            val result = runCatching { downloadUpdateApk(updateClient(body), "https://example.test/update.apk", apk) { _, _ -> } }
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("previous", apk.readText())
            assertFalse(directory.resolve("update.apk.part").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun completeApkResponseAtomicallyReplacesThePreviousFile() = runBlocking {
        val directory = Files.createTempDirectory("update-apk-complete").toFile()
        val apk = directory.resolve("update.apk").apply { writeText("previous") }
        try {
            downloadUpdateApk(updateClient("new payload".toResponseBody()), "https://example.test/update.apk", apk) { _, _ -> }
            assertEquals("new payload", apk.readText())
            assertEquals(listOf("update.apk"), directory.listFiles().orEmpty().map { it.name })
            assertEquals("YummyDroid-.._.._outside.apk", updateApkFileName("../../outside"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun updateClient(body: ResponseBody): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()

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
