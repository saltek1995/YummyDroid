package me.yummydroid.app.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineAnimeStorageTest {
    private lateinit var rootDir: File

    @BeforeTest
    fun setUp() {
        rootDir = Files.createTempDirectory("offline-anime-storage").toFile()
    }

    @AfterTest
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    @Test
    fun registryRestoresAndRemovesCompletedDownload() {
        val video = video()
        val target = video.offlineTargetFile(rootDir, extension = ".mp4", qualityTitle = "1080p")
        RandomAccessFile(target, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }
        val offlineFile = OfflineVideoFile(
            playbackUrl = target.toURI().toString(),
            mimeType = "video/mp4",
            bytes = 1L,
            qualityTitle = "1080p",
            voiceTitle = "Main Voice",
            player = video.player,
        )

        OfflineDownloadRegistry(rootDir).upsert(video, offlineFile)
        val restored = OfflineDownloadRegistry(rootDir)
            .completedFilesBySlot(video.animeId)[video.downloadRecordSlotKey()]
            .orEmpty()

        assertEquals(1, restored.size)
        assertEquals(target.length(), restored.single().bytes)
        assertEquals("1080p", target.downloadQualityTitle())

        OfflineDownloadRegistry(rootDir).remove(video.animeId, video.id, playbackUrl = null)

        assertFalse(target.exists())
        assertTrue(OfflineDownloadRegistry(rootDir).completedFilesBySlot(video.animeId).isEmpty())
    }

    @Test
    fun cleanupKeepsFreshPartialFileAndRemovesStaleOne() {
        val partial = video().offlineTargetFile(rootDir, "part", "720p")
        partial.writeText("partial")
        val registry = OfflineDownloadRegistry(rootDir)

        registry.completedFilesBySlot(1L)
        assertTrue(partial.exists())

        partial.setLastModified(System.currentTimeMillis() - 7L * 60L * 60L * 1000L)
        registry.completedFilesBySlot(1L)
        assertFalse(partial.exists())
    }

    private fun video(): VideoVariant {
        return VideoVariant(
            id = 42L,
            animeId = 1L,
            player = "Kodik",
            dubbing = "Main Voice",
            episode = "3",
            url = "https://example.test/video",
            index = 3,
            durationSeconds = null,
            views = 0,
        )
    }
}
