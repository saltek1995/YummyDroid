package me.yummydroid.app.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class AsyncContentCacheTest {
    @Test
    fun explicitMaintenanceBarrierWaitsForDeletion() = runBlocking {
        val directory = Files.createTempDirectory("async-clear-barrier").toFile()
        val writer = ManualWriter()
        val cache = AnimeContentCacheStorage(directory, writer)
        try {
            cache.saveVideos(language, null, animeId, listOf(video(1)))
            writer.runAll()
            cache.clear()
            val barrier = async(start = CoroutineStart.UNDISPATCHED) { cache.awaitPersistence() }
            assertFalse(barrier.isCompleted)
            assertTrue(directory.exists())
            writer.runAll()
            barrier.await()
            assertFalse(directory.exists())
        } finally {
            writer.runAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun historySummaryIsReadableBeforePersistenceAndClearCancelsQueuedSave() {
        val writer = ManualWriter()
        val preferences = InMemoryPlaybackPreferences()
        val cache = HistoryAnimeCacheStorage(preferences, writer)
        val anime = Anime(id = animeId, title = "Test", posterUrl = "", description = "", year = null, rating = null,
            genres = emptyList(), status = "", type = "", animeUrl = "", views = 0L, blockedIn = emptyList())
        cache.save(anime)
        assertEquals(anime, cache.read(animeId))
        assertTrue(preferences.all.isEmpty())
        cache.clear()
        writer.runAll()
        assertNull(cache.read(animeId))
        assertTrue(preferences.all.isEmpty())
        cache.save(anime)
        writer.runAll()
        assertEquals(anime, cache.read(animeId))
        assertTrue(preferences.all.isNotEmpty())
    }

    @Test
    fun sourceQualityClearIsNotCoalescedAwayByALaterSave() {
        val file = Files.createTempFile("quality-clear-failure", ".json").toFile()
        val writer = ManualWriter()
        val cache = SourceQualityCacheStorage(file, nowMs = { 1L }, diskWriter = writer)
        try {
            cache.save(video(1), stream(480))
            writer.runAll()
            cache.clear()
            cache.save(video(1), stream(720))
            writer.runNext()
            assertTrue(!file.exists(), "Clear must run even when a replacement save is queued")
            // Emulate an unwritable replacement target after deletion.
            assertTrue(file.mkdir())
            File(file, "blocker").writeText("cannot replace a nonempty directory")
            writer.runAll()
            assertTrue(file.isDirectory)
            assertEquals(listOf(SourceQuality(720)), cache.applyTo(listOf(video(1))).single().sourceQualities)
        } finally {
            writer.runAll()
            file.deleteRecursively()
        }
    }

    @Test
    fun saveAnimeWithVideosPublishesMemoryBeforeQueuedDiskWritesRun() {
        val directory = Files.createTempDirectory("async-anime-cache").toFile()
        val writer = ManualWriter()
        val first = AnimeContentCacheStorage(directory, writer)
        val second = AnimeContentCacheStorage(directory)
        val video = video(1)
        try {
            first.saveAnimeWithVideos(language, null, animeId, CachedAnimeWithVideos(details(), listOf(video)))

            assertEquals(listOf(video), second.readVideos(language, null, animeId))
            assertEquals(listOf(video), second.readAnimeWithVideos(language, null, animeId)?.videos)
            assertEquals(0, directory.listFiles()?.size ?: 0)

            writer.runAll()

            assertEquals(
                setOf(combinedFile(null)),
                directory.listFiles().orEmpty().map(File::getName).toSet(),
            )
        } finally {
            writer.runAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun clearDiscardsOlderQueuedWritesBeforePersistingNewContent() {
        val directory = Files.createTempDirectory("async-cache-clear").toFile()
        val writer = ManualWriter()
        val cache = AnimeContentCacheStorage(directory, writer)
        try {
            cache.saveVideos(language, null, animeId, listOf(video(1)))
            cache.saveVideos(language, null, animeId + 1, listOf(video(2)))
            cache.clear()
            cache.saveVideos(language, null, animeId, listOf(video(3)))

            assertNull(cache.readVideos(language, null, animeId + 1))
            assertEquals(listOf(video(3)), cache.readVideos(language, null, animeId))
            writer.runAll()

            assertEquals(setOf(videosFile(null)), directory.listFiles().orEmpty().map(File::getName).toSet())
            val diskVideo = AppJson.parseToJsonElement(File(directory, videosFile(null)).readText())
                .jsonObject.getValue("value").jsonArray.single().jsonObject
            assertEquals(video(3).url, diskVideo.getValue("url").jsonPrimitive.content)
            assertEquals(listOf(video(3)), cache.readVideos(language, null, animeId))
        } finally {
            writer.runAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun videosOnlyRefreshInvalidatesCombinedSnapshotBeforeAndAfterDrain() {
        val directory = Files.createTempDirectory("async-video-refresh").toFile()
        val writer = ManualWriter()
        val cache = AnimeContentCacheStorage(directory, writer)
        val newer = video(2)
        try {
            cache.saveAnimeWithVideos(language, null, animeId, CachedAnimeWithVideos(details(), listOf(video(1))))
            cache.saveVideos(language, null, animeId, listOf(newer))

            assertNull(cache.readAnimeWithVideos(language, null, animeId))
            assertEquals(listOf(newer), cache.readVideos(language, null, animeId))
            writer.runAll()

            assertNull(cache.readAnimeWithVideos(language, null, animeId))
            assertEquals(listOf(newer), cache.readVideos(language, null, animeId))
        } finally {
            writer.runAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidateAccountContentDropsQueuedPrivateWritesAndKeepsReadyPublicSchedule() {
        val directory = Files.createTempDirectory("async-account-cache").toFile()
        val writer = ManualWriter()
        val cache = AnimeContentCacheStorage(directory, writer)
        val other = AnimeContentCacheStorage(directory)
        try {
            cache.saveSchedule(language, emptyList())
            writer.runAll()
            cache.saveVideos(language, userId, animeId, listOf(video(1)))

            other.invalidateAccountContent()

            assertEquals(emptyList(), cache.readSchedule(language))
            assertNull(cache.readVideos(language, userId, animeId))
            writer.runAll()

            assertEquals(setOf(scheduleFile()), directory.listFiles().orEmpty().map(File::getName).toSet())
            assertEquals(emptyList(), other.readSchedule(language))
            assertNull(other.readVideos(language, userId, animeId))
        } finally {
            writer.runAll()
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedDiskWriteKeepsPublishedMemoryValue() {
        val rootFile = Files.createTempFile("async-cache-write-failure", ".tmp").toFile()
        val writer = ManualWriter()
        val cache = AnimeContentCacheStorage(rootFile, writer)
        val saved = listOf(video(1))
        try {
            cache.saveVideos(language, null, animeId, saved)
            writer.runAll()

            assertTrue(rootFile.isFile)
            assertEquals(saved, cache.readVideos(language, null, animeId))
        } finally {
            writer.runAll()
            rootFile.delete()
        }
    }

    @Test
    fun sourceQualitySavePublishesMemoryBeforeQueuedPersistence() {
        val file = Files.createTempFile("async-source-quality", ".json").toFile()
        file.delete()
        val writer = ManualWriter()
        val cache = SourceQualityCacheStorage(file, nowMs = { 1L }, diskWriter = writer)
        val video = video(1)
        try {
            cache.save(video, stream(720))

            assertEquals(listOf(SourceQuality(height = 720)), cache.applyTo(listOf(video)).single().sourceQualities)
            assertTrue(!file.exists())
            writer.runAll()

            assertTrue(file.isFile)
        } finally {
            writer.runAll()
            file.delete()
        }
    }

    @Test
    fun sourceQualityClearSupersedesQueuedSaveBeforeNewSnapshotPersists() {
        val file = Files.createTempFile("async-source-quality-clear", ".json").toFile()
        file.delete()
        val writer = ManualWriter()
        val cache = SourceQualityCacheStorage(file, nowMs = { 1L }, diskWriter = writer)
        val video = video(1)
        try {
            cache.save(video, stream(480))
            cache.clear()
            cache.save(video, stream(720))
            writer.runAll()

            assertTrue(file.isFile)
            assertEquals(listOf(SourceQuality(height = 720)), cache.applyTo(listOf(video)).single().sourceQualities)
            assertEquals(listOf(SourceQuality(720)), file.readJsonOrNull<Map<Long, SourceQualityCacheEntry>>()!!.getValue(video.id).qualities)
        } finally {
            writer.runAll()
            file.delete()
        }
    }

    private fun combinedFile(accountId: Long?): String =
        "${animeContentCacheName("anime_with_videos", language.apiCode, accountId.animeContentCacheUserPart(), animeId)}.json"

    private fun videosFile(accountId: Long?): String =
        "${animeContentCacheName("videos", language.apiCode, accountId.animeContentCacheUserPart(), animeId)}.json"

    private fun scheduleFile(): String =
        "${animeContentCacheName("schedule", language.apiCode)}.json"

    private fun video(id: Long) = VideoVariant(
        id = id, animeId = animeId, player = "CVH", dubbing = "Voice", episode = id.toString(),
        url = "https://example.test/$id", index = id.toInt(), durationSeconds = null, views = 0L,
    )

    private fun stream(height: Int) = ResolvedVideoStream(
        url = "https://example.test/stream",
        mimeType = null,
        headers = emptyMap(),
        availableQualities = listOf(SourceQuality(height = height)),
    )

    private fun details() = AnimeDetails(
        id = animeId, title = "Anime", otherTitles = emptyList(), description = "", posterUrl = "",
        backdropUrl = null, year = null, rating = null, views = 0L, status = "ongoing", type = "TV",
        minAge = "", genreTags = emptyList(), genres = emptyList(), episodeSummary = "", episodeAired = 1,
        episodeCount = 12, nextEpisodeText = "", durationSeconds = 0, ratingDetails = RatingDetails(),
        studios = emptyList(), creators = emptyList(), original = "", commentsCount = 0L, listsCount = 0L,
        translations = emptyList(), relatedAnime = emptyList(), screenshots = emptyList(), blockedIn = emptyList(),
    )

    private class ManualWriter : CacheDiskWriter {
        private val pending = ArrayDeque<() -> Unit>()

        override fun enqueue(write: () -> Unit) {
            pending.addLast(write)
        }

        fun runAll() {
            while (pending.isNotEmpty()) {
                runNext()
            }
        }

        fun runNext() {
            try { pending.removeFirst().invoke() } catch (_: Exception) { }
        }
    }

    private companion object {
        val language = ContentLanguage.Russian
        const val animeId = 1L
        const val userId = 42L
    }
}
