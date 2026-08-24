package me.yummydroid.app.data

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSubscriptionApiMappingTest {
    @Test
    fun mapsEveryServerSubscriptionEntryWithoutResolvingVideos() {
        val dto = SubscriptionDto(
            animeId = 10,
            title = "Anime",
            sub = buildJsonArray {
                add(subscription(player = "Alloha", playerId = 7, dubbing = "Voice", videoId = 101))
                add(subscription(player = "CVH", playerId = 8, dubbing = "Voice", videoId = 102))
            },
        )

        val subscriptions = dto.toVideoSubscriptions()

        assertEquals(listOf(101L, 102L), subscriptions.map { it.videoId })
        assertEquals(listOf("Alloha", "CVH"), subscriptions.map { it.player })
        assertEquals(listOf("Voice", "Voice"), subscriptions.map { it.dubbing })
    }

    @Test
    fun mapsSingleObjectUsingTheSameContract() {
        val dto = SubscriptionDto(
            animeId = 10,
            title = "Anime",
            sub = subscription(player = "Kodik", playerId = 9, dubbing = "Voice", videoId = 103),
        )

        assertEquals(listOf(103L), dto.toVideoSubscriptions().map { it.videoId })
    }

    private fun subscription(
        player: String,
        playerId: Long,
        dubbing: String,
        videoId: Long,
    ) = buildJsonObject {
        put("player", player)
        put("player_id", playerId)
        put("dubbing", dubbing)
        put("video_id", videoId)
    }
}
