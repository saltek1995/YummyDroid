package me.yummydroid.app.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import java.io.IOException

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
    fun storageRevisionsReachOtherInstancesAndLateCollectorsAfterRapidChanges() = runBlocking {
        val reader = OfflineAnimeStorage(rootDir)
        val writer = OfflineAnimeStorage(File(rootDir, "."))
        assertEquals(0L, reader.changes.value)
        writer.deleteAnime(10)
        writer.deleteVideo(10, 20)
        writer.clearOfflineCache()
        assertEquals(3L, reader.changes.first())
        assertEquals(reader.changes.value, writer.changes.value)
        val otherRoot = File(rootDir, "other").apply { mkdirs() }
        assertEquals(0L, OfflineAnimeStorage(otherRoot).changes.value)
    }

    @Test
    fun publishingCompletedMediaKeepsThePreviousFileWhenReplacementFails() {
        val target = File(rootDir, "episode.mp4").apply { writeText("previous complete media") }
        val missing = File(rootDir, "missing.part")
        assertFailsWith<IOException> { missing.moveCompleteTo(target) }
        assertEquals("previous complete media", target.readText())
        val replacement = File(rootDir, "episode.part").apply { writeText("replacement complete media") }
        replacement.moveCompleteTo(target)
        assertEquals("replacement complete media", target.readText())
        assertFalse(replacement.exists())
    }

    @Test
    fun completedArtifactSurvivesReadsUntilItsWriterRegistersIt() = runBlocking {
        val video = video()
        val target = video.offlineTargetFile(rootDir, "mp4", "720p")
        OfflineStorageAccess.withDownload(File(rootDir, video.animeId.toString())) {
            RandomAccessFile(target, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }

            assertTrue(OfflineDownloadRegistry(rootDir).completedFilesBySlot(video.animeId).isEmpty())
            assertTrue(target.exists())
            OfflineDownloadRegistry(rootDir).upsert(
                video,
                OfflineVideoFile(target.toURI().toString(), "video/mp4", target.length(), "720p"),
            )
        }

        assertEquals(1, OfflineDownloadRegistry(rootDir).completedFilesBySlot(video.animeId).size)
        assertTrue(target.exists())
    }

    @Test
    fun independentRegistryInstancesDoNotLoseConcurrentEpisodeRegistrations() {
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val work = (1..32).map { episode ->
                executor.submit {
                    val video = video().copy(id = episode.toLong(), episode = episode.toString())
                    val target = video.offlineTargetFile(rootDir, "mp4", "720p")
                    RandomAccessFile(target, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }
                    start.await()
                    OfflineDownloadRegistry(rootDir).upsert(
                        video,
                        OfflineVideoFile(target.toURI().toString(), "video/mp4", target.length(), "720p"),
                    )
                }
            }
            start.countDown()
            work.forEach { it.get() }

            assertEquals(32, OfflineDownloadRegistry(rootDir).completedFilesBySlot(1L).size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun readersAlwaysSeeOneCompleteJsonVersionWhileCacheIsReplaced() {
        val file = File(rootDir, "cache.json")
        val first = "a".repeat(64_000)
        val second = "b".repeat(64_000)
        file.writeJson(first)
        val executor = Executors.newSingleThreadExecutor()
        val reading = CountDownLatch(1)
        val done = AtomicBoolean(false)
        val reader = executor.submit {
            reading.countDown()
            do {
                val snapshot = file.readJsonOrNull<String>()
                assertTrue(snapshot == first || snapshot == second)
            } while (!done.get())
        }
        try {
            reading.await()
            repeat(60) { file.writeJson(if (it % 2 == 0) second else first) }
        } finally {
            done.set(true)
            executor.shutdown()
        }
        reader.get()
        assertEquals(listOf("cache.json"), rootDir.listFiles().orEmpty().map { it.name })
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

    @Test
    fun unavailableRegistryNeverDeletesCompletedMediaEvenAfterAnotherRegistration() {
        for (missing in listOf(false, true)) {
            val video = video()
            val registry = OfflineDownloadRegistry(rootDir)
            val target = video.offlineTargetFile(rootDir, "mp4", "720p")
            RandomAccessFile(target, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }
            registry.upsert(video, OfflineVideoFile(target.toURI().toString(), "video/mp4", target.length(), "720p"))
            val index = File(rootDir, "1/downloads_index.json")
            assertTrue(index.exists())
            if (missing) assertTrue(index.delete()) else index.writeText("{broken")

            registry.completedFilesBySlot(1L)
            assertTrue(target.exists(), "Completed media must survive an unreadable index")

            val nextVideo = video.copy(id = 43, episode = "4", index = 4)
            val nextTarget = nextVideo.offlineTargetFile(rootDir, "mp4", "720p")
            RandomAccessFile(nextTarget, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }
            registry.upsert(nextVideo, OfflineVideoFile(nextTarget.toURI().toString(), "video/mp4", nextTarget.length(), "720p"))
            registry.completedFilesBySlot(1L)
            assertTrue(target.exists(), "A subsequent registration must not authorize deleting older media")
            assertTrue(nextTarget.exists())
        }
    }

    @Test
    fun replacementDeletesOnlyTheKnownSupersededArtifact() {
        val registry = OfflineDownloadRegistry(rootDir)
        val video = video()
        val target = video.offlineTargetFile(rootDir, "mp4", "720p")
        val replacement = File(target.parentFile, "replacement.mp4")
        val orphan = File(target.parentFile, "unregistered.mp4")
        for (file in listOf(target, replacement, orphan)) {
            RandomAccessFile(file, "rw").use { it.setLength(MIN_COMPLETED_VIDEO_BYTES + 1024L) }
        }
        fun record(file: File) = OfflineVideoFile(file.toURI().toString(), "video/mp4", file.length(), "720p")
        registry.upsert(video, record(target))
        registry.upsert(video, record(replacement))
        assertFalse(target.exists())
        assertTrue(replacement.exists())
        registry.completedFilesBySlot(1L)
        assertTrue(orphan.exists())
        assertEquals(replacement.toURI().toString(), registry.completedFilesBySlot(1L).values.single().single().playbackUrl)
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
