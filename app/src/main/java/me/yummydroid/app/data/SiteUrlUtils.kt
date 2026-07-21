package me.yummydroid.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val DEFAULT_SITE_BASE_URL = "https://old.yummyani.me/"
internal const val APP_USER_AGENT = "YummyDroid Android TV"
internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

internal val DEFAULT_YUMMY_SITE_DOMAINS: List<String> = listOf(
    DEFAULT_SITE_BASE_URL,
    "https://ru.yummyani.me/",
    "https://yummyani.me/",
    "https://yummy-ani.me/",
    "https://old.yummy-ani.me/",
    "https://yummyani.meme/",
    "https://site.yummyani.me/",
    "https://en.yummyani.me/",
    "https://uk.yummyani.me/",
    "https://yummy-anime.ru/",
)

internal fun String.urlOrigin(): String? {
    val url = toHttpUrlOrNull() ?: return null
    val defaultPort = when (url.scheme) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
    val port = url.port
        .takeIf { it > 0 && it != defaultPort }
        ?.let { ":$it" }
        .orEmpty()
    return "${url.scheme}://${url.host}$port"
}

internal fun String.withTrailingSlash(): String {
    return if (endsWith("/")) this else "$this/"
}

internal fun String.toRootSiteBaseUrl(): String {
    return toHttpUrlOrNull()
        ?.newBuilder()
        ?.encodedPath("/")
        ?.query(null)
        ?.fragment(null)
        ?.build()
        ?.toString()
        ?: this
}

internal fun String.sameUrlOrigin(other: String?): Boolean {
    if (other.isNullOrBlank()) return false
    val first = toRootSiteBaseUrl().trimEnd('/')
    val second = other.toRootSiteBaseUrl().trimEnd('/')
    return first.equals(second, ignoreCase = true)
}

internal fun String.resolveUrlAgainst(baseUrl: String): String {
    val clean = trim().trim('"', '\'')
    return when {
        clean.startsWith("//") -> "https:$clean"
        clean.startsWith("http://", ignoreCase = true) ||
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("blob:", ignoreCase = true) -> clean
        clean.startsWith("/") -> "${baseUrl.urlOrigin() ?: baseUrl.trimEnd('/')}$clean"
        else -> baseUrl.toHttpUrlOrNull()?.resolve(clean)?.toString() ?: clean
    }
}

