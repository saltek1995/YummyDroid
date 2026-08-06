package me.yummydroid.app

import me.yummydroid.app.data.SiteNotification

internal fun AuthUiState.withUnreadNotifications(count: Int): AuthUiState {
    val currentProfile = profile ?: return this
    return copy(profile = currentProfile.copy(unreadNotifications = count.coerceAtLeast(0)))
}

internal fun AuthUiState.withUnreadNotificationDelta(delta: Int): AuthUiState {
    val currentProfile = profile ?: return this
    return withUnreadNotifications(currentProfile.unreadNotifications + delta)
}

internal fun List<SiteNotification>.unreadCount(): Int {
    return count { !it.viewed }
}

internal fun YummyDroidUiState.withProfileNotifications(
    notifications: List<SiteNotification>,
): YummyDroidUiState {
    return copy(
        profileNotifications = LoadState.Ready(notifications),
        auth = auth.withUnreadNotifications(notifications.unreadCount()),
    )
}

internal fun YummyDroidUiState.withProfileNotificationRead(notificationId: Long): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull() ?: return this
    val updatedNotifications = notifications.map { notification ->
        if (notification.id == notificationId) notification.copy(viewed = true) else notification
    }
    return if (updatedNotifications == notifications) this else withProfileNotifications(updatedNotifications)
}

internal fun YummyDroidUiState.withAllProfileNotificationsRead(): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull()
    return copy(
        profileNotifications = notifications
            ?.map { notification -> notification.copy(viewed = true) }
            ?.let { updated -> LoadState.Ready(updated) }
            ?: profileNotifications,
        auth = auth.withUnreadNotifications(0),
    )
}

internal fun YummyDroidUiState.withoutProfileNotification(
    notification: SiteNotification,
): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull()
    if (notifications == null) {
        return copy(
            auth = auth.withUnreadNotificationDelta(if (notification.viewed) 0 else -1),
        )
    }
    return withProfileNotifications(notifications.filterNot { it.id == notification.id })
}
