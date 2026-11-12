package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OfflineSubscriptionBatchAuditTest {
    @Test
    fun batchOverlayMatchesIndividualOverlayAndReadsOneSnapshot() {
        val preferences = InMemoryPlaybackPreferences()
        val storage = AccountVideoSubscriptionStorage(preferences)
        val stored = video(id = 11, animeId = 1, player = "Player Kodik", dubbing = "Voice AniLibria", subscribed = true)
        storage.rememberVideos(7, listOf(stored))
        storage.setSubscribed(7, 99, false)
        val groups = listOf(
            listOf(
                stored.copy(player = "Kodik", dubbing = "AniLibria"),
                stored.copy(id = 99, player = "Kodik", dubbing = "AniLibria"),
                stored.copy(id = 11, player = "Kodik", dubbing = "AniLibria"),
            ),
            listOf(video(id = 22, animeId = 2, player = "Kodik", dubbing = "AniLibria")),
        )

        val readsBefore = preferences.stringReads
        val batch = storage.overlayAll(7, groups)

        assertEquals(readsBefore + 1, preferences.stringReads)
        assertEquals(groups.map { storage.overlay(7, it) }, batch)
        assertEquals(listOf(true, false, true), batch.first().map { it.subscribed })
        assertFalse(batch[1].single().subscribed)
    }

    @Test
    fun batchOverlayDoesNotCrossAccounts() {
        val storage = AccountVideoSubscriptionStorage(InMemoryPlaybackPreferences())
        val video = video(id = 11, animeId = 1, player = "Kodik", dubbing = "AniLibria")
        storage.rememberVideos(7, listOf(video.copy(subscribed = true)))

        assertTrue(storage.overlayAll(7, listOf(listOf(video))).single().single().subscribed)
        assertFalse(storage.overlayAll(8, listOf(listOf(video))).single().single().subscribed)
        assertFalse(storage.overlayAll(null, listOf(listOf(video))).single().single().subscribed)
    }

    private fun video(
        id: Long,
        animeId: Long,
        player: String,
        dubbing: String,
        subscribed: Boolean = false,
    ) = VideoVariant(
        id = id,
        animeId = animeId,
        player = player,
        dubbing = dubbing,
        episode = "1",
        url = "https://example.test/$id",
        index = 1,
        durationSeconds = null,
        views = 0,
        subscribed = subscribed,
    )
}
