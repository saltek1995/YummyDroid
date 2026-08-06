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
