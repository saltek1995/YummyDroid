package me.yummydroid.app

import java.io.IOException
import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.ApiHttpException
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.SiteNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionNotificationPolicyTest {
    @Test
    fun `worker failures preserve retry and terminal behavior`() {
        assertEquals(
            NotificationWorkerFailure.Rethrow,
            SubscriptionNotificationPolicy.classifyWorkerFailure(CancellationException()),
        )
        assertEquals(
            NotificationWorkerFailure.Success,
            SubscriptionNotificationPolicy.classifyWorkerFailure(CaptchaRequiredException("captcha")),
        )
        assertEquals(
            NotificationWorkerFailure.ClearAuth,
            SubscriptionNotificationPolicy.classifyWorkerFailure(ApiHttpException(401, "expired")),
        )
        assertEquals(
            NotificationWorkerFailure.Success,
            SubscriptionNotificationPolicy.classifyWorkerFailure(ApiHttpException(404, "missing")),
        )
        assertEquals(
            NotificationWorkerFailure.Retry,
            SubscriptionNotificationPolicy.classifyWorkerFailure(IOException("offline")),
        )
        assertEquals(
            NotificationWorkerFailure.Failure,
            SubscriptionNotificationPolicy.classifyWorkerFailure(IllegalStateException("broken")),
        )
    }

    @Test
    fun `scheduling requires setting token and profile`() {
        assertTrue(
            SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = true,
                hasToken = true,
                hasProfile = true,
            ),
        )
        assertFalse(
            SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = false,
                hasToken = true,
                hasProfile = true,
            ),
        )
        assertFalse(
            SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = true,
                hasToken = false,
                hasProfile = true,
            ),
        )
        assertFalse(
            SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = true,
                hasToken = true,
                hasProfile = false,
            ),
        )
    }

    @Test
    fun `new episode filter excludes viewed and unrelated notifications`() {
        val freshEpisode = notification(id = 1, type = "anime_episode", subType = "new_episode")
        val viewedEpisode = notification(
            id = 2,
            type = "anime_episode",
            subType = "new_episode",
            viewed = true,
        )
        val unrelated = notification(id = 3, type = "comment", subType = "new_episode")

        assertEquals(
            listOf(freshEpisode),
            SubscriptionNotificationPolicy.newEpisodeNotifications(
                listOf(freshEpisode, viewedEpisode, unrelated),
            ),
        )
    }

    @Test
    fun `fresh selection skips seen events deduplicates and keeps newest eight`() {
        val notifications = (1L..10L).map { id -> notification(id = id) }
        val selected = SubscriptionNotificationPolicy.freshNotifications(
            notifications = notifications + notification(id = 11, eventKey = "event-10"),
            isSeen = { it.id == 1L },
            eventKey = SiteNotification::clickUrl,
        )

        assertEquals((3L..10L).toList(), selected.map(SiteNotification::id))
    }

    @Test
    fun `android notification ids remain positive and bounded`() {
        assertEquals(1, SubscriptionNotificationPolicy.notificationId(0L))
        assertEquals(1, SubscriptionNotificationPolicy.notificationId(-5L))
        assertEquals(1, SubscriptionNotificationPolicy.notificationId(Int.MAX_VALUE.toLong()))
        assertEquals(42, SubscriptionNotificationPolicy.notificationId(42L))
    }

    private fun notification(
        id: Long,
        type: String = "anime_episode",
        subType: String = "new_episode",
        viewed: Boolean = false,
        eventKey: String = "event-$id",
    ): SiteNotification {
        return SiteNotification(
            id = id,
            title = "Title $id",
            text = "Episode $id",
            clickUrl = eventKey,
            type = type,
            subType = subType,
            objectId = id,
            dateSeconds = id,
            viewed = viewed,
        )
    }
}
