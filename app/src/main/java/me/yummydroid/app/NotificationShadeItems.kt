package me.yummydroid.app

import kotlinx.serialization.Serializable
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.decodeAppJsonOrNull
import me.yummydroid.app.data.encodeAppJson

internal data class NotificationShadeItem(
    val id: Long,
    val title: String,
    val text: String,
    val dateSeconds: Long,
)

internal fun List<SiteNotification>.toNotificationShadeItems(): List<NotificationShadeItem> {
    return sortedByDescending { it.dateSeconds }
        .map { notification ->
            NotificationShadeItem(
                id = notification.id,
                title = notification.title.trim(),
                text = notification.text.trim(),
                dateSeconds = notification.dateSeconds,
            )
        }
}

internal fun List<SiteNotification>.unreadNotificationShadeItemsJson(maxItems: Int): String? {
    val storedItems = filterNot(SiteNotification::viewed)
        .toNotificationShadeItems()
        .take(maxItems)
        .map(StoredNotificationShadeItem::fromShadeItem)
    return storedItems.takeIf(List<StoredNotificationShadeItem>::isNotEmpty)?.encodeAppJson()
}

internal fun decodeNotificationShadeItems(json: String?): List<NotificationShadeItem> {
    return json?.decodeAppJsonOrNull<List<StoredNotificationShadeItem>>()
        .orEmpty()
        .map(StoredNotificationShadeItem::toShadeItem)
}

@Serializable
private data class StoredNotificationShadeItem(
    val id: Long,
    val title: String,
    val text: String,
    val dateSeconds: Long,
) {
    fun toShadeItem() = NotificationShadeItem(id, title, text, dateSeconds)

    companion object {
        fun fromShadeItem(item: NotificationShadeItem) = StoredNotificationShadeItem(
            id = item.id,
            title = item.title,
            text = item.text,
            dateSeconds = item.dateSeconds,
        )
    }
}
