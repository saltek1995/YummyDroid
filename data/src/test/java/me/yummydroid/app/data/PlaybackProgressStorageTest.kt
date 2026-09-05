package me.yummydroid.app.data

import android.content.SharedPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressStorageTest {
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

private class InMemoryPlaybackPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return (values[key] as? Set<String>)?.toSet() ?: defValues
    }
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun edit(): SharedPreferences.Editor = Editor(values)
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
