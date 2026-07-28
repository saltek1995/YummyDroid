package me.yummydroid.app

import me.yummydroid.app.data.SiteNotification

internal fun SiteNotification.animeIdForOpen(): Long? {
    val fromUrl = Regex("""-(\d+)(?:[/#?]|$)""")
        .find(clickUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
    return fromUrl ?: objectId.takeIf { it > 0L }
}
