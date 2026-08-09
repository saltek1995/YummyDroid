package me.yummydroid.app

import java.io.IOException
import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.ApiHttpException
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.isUnauthorizedApiError

internal enum class NotificationWorkerFailure {
    Rethrow,
    ClearAuth,
    Success,
    Retry,
    Failure,
}

internal object SubscriptionNotificationPolicy {
    fun classifyWorkerFailure(throwable: Throwable): NotificationWorkerFailure {
        return when {
            throwable is CancellationException -> NotificationWorkerFailure.Rethrow
            throwable is CaptchaRequiredException -> NotificationWorkerFailure.Success
            throwable.isUnauthorizedApiError() -> NotificationWorkerFailure.ClearAuth
            throwable is ApiHttpException && throwable.statusCode in 400..499 -> {
                NotificationWorkerFailure.Success
            }
            throwable is IOException -> NotificationWorkerFailure.Retry
            else -> NotificationWorkerFailure.Failure
        }
    }

    fun canSchedule(
        notificationsEnabled: Boolean,
        hasToken: Boolean,
        hasProfile: Boolean,
    ): Boolean {
        return notificationsEnabled && hasToken && hasProfile
    }

    fun newEpisodeNotifications(notifications: List<SiteNotification>): List<SiteNotification> {
        return notifications.filter { notification ->
            !notification.viewed &&
                notification.type == EPISODE_TYPE &&
                notification.subType == NEW_EPISODE_SUB_TYPE
        }
    }

    fun freshNotifications(
        notifications: List<SiteNotification>,
        isSeen: (SiteNotification) -> Boolean,
        eventKey: (SiteNotification) -> String,
    ): List<SiteNotification> {
        return notifications
            .filterNot(isSeen)
            .distinctBy(eventKey)
            .takeLast(MAX_NOTIFICATIONS_PER_CHECK)
    }

    fun notificationId(id: Long): Int {
        return (id % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

private const val MAX_NOTIFICATIONS_PER_CHECK = 8
private const val EPISODE_TYPE = "anime_episode"
private const val NEW_EPISODE_SUB_TYPE = "new_episode"
