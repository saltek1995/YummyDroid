package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeRatingStateStorageTest {
    @Test
    fun saveRoundTripsExplicitRemovalAcrossInstancesAndKeepsAccountsSeparate() {
        val preferences = InMemoryPlaybackPreferences()
        AnimeRatingStateStorage(preferences).save(7, mapOf(10L to 8, 20L to null))
        AnimeRatingStateStorage(preferences).save(8, mapOf(10L to 4))

        val restored = AnimeRatingStateStorage(preferences)
        assertEquals(mapOf(10L to 8, 20L to null), restored.read(7))
        assertEquals(mapOf(10L to 4), restored.read(8))
    }

    @Test
    fun legacyJsonAndInvalidEntriesAreHandledWithoutDroppingValidTombstones() {
        val preferences = InMemoryPlaybackPreferences()
        preferences.edit().putString("ratings_7", "{\"items\":[{\"animeId\":10,\"rating\":8}]}").apply()
        preferences.edit().putString(
            "ratings_8",
            "{\"items\":[{\"animeId\":10,\"rating\":0},{\"animeId\":0,\"rating\":8},{\"animeId\":20,\"rating\":11},{\"animeId\":30,\"rating\":null}]}",
        ).apply()

        val storage = AnimeRatingStateStorage(preferences)
        assertEquals(mapOf(10L to 8), storage.read(7))
        assertEquals(mapOf(30L to null), storage.read(8))

        storage.save(9, mapOf(-1L to 8, 10L to 0, 20L to 11, 30L to null))
        assertEquals(mapOf(30L to null), AnimeRatingStateStorage(preferences).read(9))
    }
}
