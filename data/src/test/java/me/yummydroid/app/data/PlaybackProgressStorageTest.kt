package me.yummydroid.app.data

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class PlaybackProgressStorageTest {
    @Test
    fun animeSnapshotReplacementRejectsInterveningSaveAndReset() {
        val storage = PlaybackProgressStorage(InMemoryPlaybackPreferences())
        val old = progress(1, 100).copy(positionMs = 100)
        val other = progress(2, 200)
        val newer = old.copy(positionMs = 900, updatedAtMs = 900)
        storage.replaceAll(listOf(old, other))
        val beforeSave = storage.readHistoryRevision()
        storage.save(newer)
        assertNull(storage.replaceAnimeIfRevision(1, listOf(old), beforeSave))
        assertEquals(listOf(newer), storage.readAnimeHistory(1))
        val beforeReset = storage.readHistoryRevision()
        storage.clearAnime(1)
        assertNull(storage.replaceAnimeIfRevision(1, listOf(old), beforeReset))
        assertEquals(emptyList(), storage.readAnimeHistory(1))
        assertNotNull(storage.replaceAnimeIfRevision(1, listOf(old), storage.readHistoryRevision()))
        assertEquals(listOf(old), storage.readAnimeHistory(1))
        assertEquals(listOf(other), storage.readAnimeHistory(2))
    }

    @Test
    fun snapshotReplacementRejectsInterveningPlayerSaveAndReset() {
        val storage = PlaybackProgressStorage(InMemoryPlaybackPreferences())
        val old = progress(1, 100).copy(positionMs = 100)
        val newer = old.copy(positionMs = 900, updatedAtMs = 900)
        storage.save(old)
        val beforeSave = storage.readHistoryRevision()
        storage.save(newer)
        assertNull(storage.replaceAllIfRevision(listOf(old), beforeSave))
        assertEquals(listOf(newer), storage.readAll())

        val beforeReset = storage.readHistoryRevision()
        storage.clearAnime(1)
        assertNull(storage.replaceAllIfRevision(listOf(old), beforeReset))
        assertEquals(emptyList(), storage.readAll())

        assertNotNull(storage.replaceAllIfRevision(listOf(old), storage.readHistoryRevision()))
        assertEquals(listOf(old), storage.readAll())
    }

    @Test
    fun batchReplacementMatchesSequentialSavesIncludingStableTies() {
        val random = kotlin.random.Random(41)
        val episodes = listOf("1", "1.0", "2", "", "Special", "NaN", "-0.0", "0.0")
        repeat(30) {
            val entries = List(60) {
                progress(animeId = random.nextLong(1, 5), updatedAtMs = random.nextLong(4)).copy(
                    videoId = random.nextLong(1, 5),
                    groupKey = "CVH|Voice ${random.nextInt(3)}",
                    episode = episodes.random(random),
                    positionMs = random.nextLong(-100, 10_000),
                    durationMs = random.nextLong(-100, 20_000),
                )
            }
            val sequential = PlaybackProgressStorage(InMemoryPlaybackPreferences())
            entries.forEach(sequential::save)
            val batched = PlaybackProgressStorage(InMemoryPlaybackPreferences())
            batched.replaceAll(entries)
            (1L..4L).forEach { animeId ->
                assertEquals(sequential.readAnimeHistory(animeId), batched.readAnimeHistory(animeId))
            }
        }

        val first = progress(10, 1).copy(videoId = 20, groupKey = "CVH|A")
        val second = first.copy(videoId = 10, groupKey = "CVH|B")
        val winner = first.copy(videoId = 10, updatedAtMs = 2)
        val storage = PlaybackProgressStorage(InMemoryPlaybackPreferences())
        storage.replaceAll(listOf(first, second, winner))
        assertEquals(listOf(second, winner), storage.readAnimeHistory(10))
    }

    @Test
    fun batchReplacementWritesOnceWithoutReadingIndividualHistories() {
        val preferences = InMemoryPlaybackPreferences()
        val storage = PlaybackProgressStorage(preferences)
        storage.saveSelection(selection())
        storage.save(progress(99, 1))
        val editsBefore = preferences.editCalls
        val readsBefore = preferences.stringReads

        storage.replaceAll(List(500) { index ->
            progress((index % 5 + 1).toLong(), index.toLong()).copy(episode = index.toString())
        })

        assertEquals(1, preferences.editCalls - editsBefore)
        assertEquals(0, preferences.stringReads - readsBefore)
        assertEquals(500, storage.readAll().size)
        assertEquals(emptyList(), storage.readAnimeHistory(99))
        assertEquals(selection(), storage.readSelection(10))
    }

    @Test
    fun replacingOneAnimeKeepsOtherAnimeAndSelectionAndCanClearTheTarget() {
        val preferences = InMemoryPlaybackPreferences()
        val storage = PlaybackProgressStorage(preferences)
        val other = progress(20, 1)
        val replacement = progress(10, 2).copy(positionMs = -1, durationMs = -1)
        storage.saveSelection(selection())
        storage.replaceAll(listOf(progress(10, 1), other))
        storage.replaceAnime(10, listOf(replacement, progress(20, 3)))
        val restored = PlaybackProgressStorage(preferences)
        assertEquals(listOf(replacement.copy(positionMs = 0, durationMs = 0)), restored.readAnimeHistory(10))
        assertEquals(listOf(other), restored.readAnimeHistory(20))
        assertEquals(selection(), restored.readSelection(10))
        restored.replaceAnime(10, listOf(other))
        assertEquals(emptyList(), restored.readAnimeHistory(10))
        assertEquals(listOf(other), restored.readAnimeHistory(20))
    }

    @Test
    fun saveIfNewerReadsOnceAndPreservesPositionVersusTimestampPolicy() {
        val preferences = InMemoryPlaybackPreferences()
        val storage = PlaybackProgressStorage(preferences)
        val original = progress(10, 100)
        storage.save(original)
        val editsBefore = preferences.editCalls
        val readsBefore = preferences.stringReads
        assertEquals(original, storage.saveIfNewer(original.copy(positionMs = 500)))
        assertEquals(1, preferences.stringReads - readsBefore)
        assertEquals(editsBefore, preferences.editCalls)

        val advancedButOlder = original.copy(positionMs = 1_500, updatedAtMs = 50)
        assertEquals(advancedButOlder, storage.saveIfNewer(advancedButOlder))
        assertEquals(listOf(original), storage.readAnimeHistory(10))
        assertEquals(editsBefore, preferences.editCalls)
        val advanced = advancedButOlder.copy(updatedAtMs = 200)
        assertEquals(advanced, storage.saveIfNewer(advanced))
        assertEquals(listOf(advanced), storage.readAnimeHistory(10))
    }

    @Test
    fun selectionSurvivesStorageRecreationAndHistoryReplacement() {
        val preferences = InMemoryPlaybackPreferences()
        val selection = selection()
        PlaybackProgressStorage(preferences).apply {
            saveSelection(selection)
            save(progress(animeId = 10, updatedAtMs = 100L))
            replaceAll(listOf(progress(animeId = 20, updatedAtMs = 200L)))
        }

        val restored = PlaybackProgressStorage(preferences)

        assertEquals(selection, restored.readSelection(animeId = 10))
        assertEquals(listOf(20L), restored.readAll().map(PlaybackProgress::animeId))
    }

    @Test
    fun clearingHistoryKeepsLongTermPlaybackSelection() {
        val preferences = InMemoryPlaybackPreferences()
        val storage = PlaybackProgressStorage(preferences)
        val selection = selection()
        storage.saveSelection(selection)
        storage.save(progress(animeId = 10, updatedAtMs = 100L))

        storage.clearAnime(animeId = 10)

        assertEquals(selection, storage.readSelection(animeId = 10))
        assertEquals(emptyList(), storage.readAnimeHistory(animeId = 10))

        storage.save(progress(animeId = 10, updatedAtMs = 200L))
        storage.clear()

        assertEquals(selection, storage.readSelection(animeId = 10))
        assertEquals(emptyList(), storage.readAll())
    }

    @Test
    fun savingTheSameSelectionDoesNotRewriteItsTimestamp() {
        val storage = PlaybackProgressStorage(InMemoryPlaybackPreferences())
        val original = selection()
        storage.saveSelection(original)

        storage.saveSelection(original.copy(updatedAtMs = original.updatedAtMs + 1_000L))

        assertEquals(original, storage.readSelection(original.animeId))
    }

    private fun selection(): PlaybackSelection {
        return PlaybackSelection(
            animeId = 10,
            groupKey = "Kodik|Voice",
            voiceKey = "voice",
            sourceKey = "kodik|kodik.test",
            updatedAtMs = 300L,
        )
    }

    private fun progress(animeId: Long, updatedAtMs: Long): PlaybackProgress {
        return PlaybackProgress(
            animeId = animeId,
            videoId = animeId,
            animeTitle = "Anime $animeId",
            posterUrl = "",
            groupKey = "CVH|Voice",
            episode = "1",
            positionMs = 1_000L,
            durationMs = 2_000L,
            updatedAtMs = updatedAtMs,
        )
    }
}

internal class InMemoryPlaybackPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    var editCalls = 0
        private set
    var stringReads = 0
        private set

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? {
        stringReads++
        return values[key] as? String ?: defValue
    }
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? Set<String>)?.toSet() ?: defValues
    }
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun edit(): SharedPreferences.Editor {
        editCalls++
        return Editor(values)
    }
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private class Editor(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = update(key, value)
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            return update(key, values?.toSet())
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = update(key, value)
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = update(key, value)
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = update(key, value)
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = update(key, value)
        override fun remove(key: String): SharedPreferences.Editor = apply { removals += key }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        override fun commit(): Boolean {
            applyChanges()
            return true
        }
        override fun apply() = applyChanges()

        private fun update(key: String, value: Any?): SharedPreferences.Editor = apply {
            updates[key] = value
            removals -= key
        }

        private fun applyChanges() {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            updates.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }
    }
}
