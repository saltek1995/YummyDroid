package me.yummydroid.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Scoped to the hidden Alloha discovery view, never installed on other providers or media clients. */
internal fun isAllohaAdvertisingRequest(url: String, providerPage: String): Boolean {
    val request = url.toHttpUrlOrNull() ?: return false
    val provider = providerPage.toHttpUrlOrNull() ?: return false
    if (request.host == "pc.alloviewroll.com") return true
    if (request.host == "imasdk.googleapis.com" && request.encodedPath == "/cekh8i") return true
    return request.scheme == provider.scheme && request.host == provider.host && request.port == provider.port &&
        request.encodedPath == "/js/rmp-vast.min.js"
}
