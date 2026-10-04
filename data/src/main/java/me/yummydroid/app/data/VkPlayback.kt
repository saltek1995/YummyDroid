package me.yummydroid.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val vkVideoCdnHost = Regex("vkvd[0-9]+\\.okcdn\\.ru")

/** Only VK's type=1 CDN manifest requests are inspected; segments stay with the player. */
internal fun String.isVkDashManifestUrl(): Boolean {
    val url = toHttpUrlOrNull() ?: return false
    return vkVideoCdnHost.matches(url.host) && url.encodedPath == "/" &&
        url.queryParameter("type") == "1"
}
