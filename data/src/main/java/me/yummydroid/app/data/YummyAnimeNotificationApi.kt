package me.yummydroid.app.data

internal class YummyAnimeNotificationApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun subscribeVideo(videoId: Long, token: String): Boolean {
        return transport.putEmptySuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean {
        return transport.deleteSuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> {
        return transport.get<List<SubscriptionDto>>(
            path = "/users/$userId/lists/subs",
            authToken = token,
        ).mapNotNull { it.toVideoSubscription() }
    }

    suspend fun getProfileNotifications(
        token: String,
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<SiteNotification> {
        return transport.get<List<NotificationDto>>(
            path = "/profile/notifications",
            params = notificationParams(types, subTypes, offset, limit),
            authToken = token,
        ).mapNotNull { it.toSiteNotification() }
    }

    suspend fun markProfileNotificationsRead(token: String): Boolean {
        return transport.postEmptySuccess(
            path = "/profile/notifications/read",
            authToken = token,
        )
    }

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.postEmptySuccess(
            path = "/profile/notifications/$notificationId/read",
            authToken = token,
        )
    }

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.deleteSuccess(
            path = "/profile/notifications/$notificationId",
            authToken = token,
        )
    }

    private fun notificationParams(
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<Pair<String, String>> = buildList {
        types.forEach { add("type" to it) }
        subTypes.forEach { add("sub_type" to it) }
        add("offset" to offset.coerceAtLeast(0).toString())
        add("limit" to limit.coerceIn(1, 100).toString())
    }
}
