package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.ui.profileSubscriptionMetaText
import me.yummydroid.app.ui.profileSubscriptionsForManagement

class VideoSubscriptionResolutionTest {
    @Test
    fun profileManagementSubscriptionsKeepServerEntriesAndSortThem() {
        val subscriptions = listOf(
            subscription(animeId = 2, player = "Alloha", dubbing = "MiraiDUB", playerId = 7)
                .copy(title = "Beta", videoId = 1),
            subscription(animeId = 2, player = "CVH", dubbing = "MiraiDUB", playerId = 9)
                .copy(title = "Beta", videoId = 2),
            subscription(animeId = 1, player = "CVH", dubbing = "AniDUB", playerId = 9)
                .copy(title = "alpha", videoId = 3),
            subscription(animeId = 3, player = "Alloha", dubbing = "Alloha", playerId = 7)
                .copy(title = "Hidden", videoId = 4),
        )

        val visible = subscriptions.profileSubscriptionsForManagement()

        assertEquals(listOf(3L, 1L, 2L, 4L), visible.map { it.videoId })
    }

    @Test
    fun profileCardMetaShowsVoiceAndSource() {
        val subscription = subscription(
            animeId = 2,
            player = "Player CVH",
            dubbing = "Dubbing AniLibria",
            playerId = 9,
        )

        assertEquals("AniLibria \u2022 CVH", subscription.profileSubscriptionMetaText())
    }

    private fun subscription(
        animeId: Long,
        player: String,
        dubbing: String,
        playerId: Long,
    ): VideoSubscription {
        return VideoSubscription(
            animeId = animeId,
            title = "",
            posterUrl = "",
            player = player,
            dubbing = dubbing,
            playerId = playerId,
        )
    }

}
