package me.yummydroid.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yummydroid.app.data.SiteNotification

internal const val PROFILE_NOTIFICATION_FETCH_LIMIT = 80

internal class ProfileNotificationCoordinator(
    private val runtime: ProfileNotificationRuntime,
    private val fetchNotifications: suspend (Int) -> List<SiteNotification>,
    private val markNotificationRead: suspend (Long) -> Unit,
    private val markAllNotificationsRead: suspend () -> Unit,
    private val deleteNotification: suspend (Long) -> Unit,
) {
    private val operationMutex = Mutex()

    suspend fun load(profileId: Long): List<SiteNotification> = operationMutex.withLock {
        fetchNotifications(PROFILE_NOTIFICATION_FETCH_LIMIT)
            .sortedByDescending(SiteNotification::dateSeconds)
            .also { notifications ->
                runtime.synchronize(profileId, notifications)
            }
    }

    suspend fun markRead(
        profileId: Long,
        notificationId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        runtime.synchronize(
            profileId = profileId,
            notifications = notifications,
            cancelledNotificationIds = listOf(notificationId),
        )
        markNotificationRead(notificationId)
    }

    suspend fun markAllRead(
        profileId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        runtime.synchronize(
            profileId = profileId,
            notifications = notifications,
            cancelledNotificationIds = notifications.map(SiteNotification::id),
        )
        markAllNotificationsRead()
    }

    suspend fun delete(
        profileId: Long,
        notificationId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        runtime.synchronize(
            profileId = profileId,
            notifications = notifications,
            cancelledNotificationIds = listOf(notificationId),
        )
        deleteNotification(notificationId)
    }
}
