package me.yummydroid.app.data

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSubscriptionApiMappingTest {
    @Test
    fun mapsNestedServerSubscriptionEntriesWithoutVideoIds() {
        val dto = SubscriptionDto(
            animeId = 10,
            title = "Anime",
            sub = buildJsonArray {
                add(
                    buildJsonArray {
                        add(subscription(player = "Alloha", playerId = 7, dubbing = "Voice"))
                    },
                )
                add(
                    buildJsonArray {
                        add(subscription(player = "CVH", playerId = 8, dubbing = "Voice"))
                    },
                )
            },
        )

        val subscriptions = dto.toVideoSubscriptions()

        assertEquals(listOf(0L, 0L), subscriptions.map { it.videoId })
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
        videoId: Long = 0,
    ) = buildJsonObject {
        put("player", player)
        put("player_id", playerId)
        put("dubbing", dubbing)
        if (videoId > 0) put("video_id", videoId)
    }
}
