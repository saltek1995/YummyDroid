package me.yummydroid.app.ui

internal fun Int.notificationBadgeText(): String? {
    return takeIf { it > 0 }?.let { count ->
        if (count > 99) "99+" else count.toString()
    }
}
