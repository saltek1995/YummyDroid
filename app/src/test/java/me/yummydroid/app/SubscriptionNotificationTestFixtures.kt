package me.yummydroid.app

import me.yummydroid.app.data.SiteNotification

internal fun siteNotification(
    id: Long,
    title: String = "Title $id",
    text: String = "Text $id",
    clickUrl: String = "event-$id",
    objectId: Long = id,
    dateSeconds: Long = id,
    viewed: Boolean = false,
): SiteNotification {
    return SiteNotification(
        id = id,
        title = title,
        text = text,
        clickUrl = clickUrl,
        type = "anime_episode",
        subType = "new_episode",
        objectId = objectId,
        dateSeconds = dateSeconds,
        viewed = viewed,
    )
}
