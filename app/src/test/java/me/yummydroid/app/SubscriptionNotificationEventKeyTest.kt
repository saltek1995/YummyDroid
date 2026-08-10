package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionNotificationEventKeyTest {
    @Test
    fun objectAndEpisodeProduceStableSemanticKey() {
        val notification = siteNotification(
            id = 1,
            objectId = 42,
            text = "\u0421\u0435\u0440\u0438\u044f 7",
        )

        assertEquals("anime:42|episode:7", subscriptionNotificationEventKey(notification))
    }

    @Test
    fun urlAnimeIdAndDecimalEpisodeAreRecognized() {
        val notification = siteNotification(
            id = 1,
            objectId = 0,
            clickUrl = "https://example.test/anime-title-99/",
            text = "Episode #3,5",
        )

        assertEquals("anime:99|episode:3.5", subscriptionNotificationEventKey(notification))
    }

    @Test
    fun fallbackRemovesProviderAndBulletWithoutDroppingCyrillicLetters() {
        val notification = siteNotification(
            id = 1,
            objectId = 0,
            title = "\u0414\u0432\u0435 \u2022 \u0441\u0435\u0440\u0438\u0438",
            text = "Kodik \u043e\u0437\u0432\u0443\u0447\u043a\u0430",
        )

        assertEquals(
            "\u0434\u0432\u0435 \u0441\u0435\u0440\u0438\u0438",
            subscriptionNotificationEventKey(notification),
        )
    }
}
