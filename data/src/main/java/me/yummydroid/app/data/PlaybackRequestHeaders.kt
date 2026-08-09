package me.yummydroid.app.data

import android.webkit.CookieManager

internal fun interface PlaybackCookieProvider {
    fun cookieFor(url: String): String?
}

private object AndroidPlaybackCookieProvider : PlaybackCookieProvider {
    override fun cookieFor(url: String): String? {
        return runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
    }
}

internal class PlaybackRequestHeaders(
    private val fallbackSiteBaseUrl: () -> String,
    private val cookieProvider: PlaybackCookieProvider = AndroidPlaybackCookieProvider,
) {
    fun iframe(
        url: String,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            put("Accept", "*/*")
            put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
            put("Referer", siteBaseUrl.withTrailingSlash())
            put("User-Agent", BROWSER_USER_AGENT)
            if (url.contains("alloha.yani.tv", ignoreCase = true)) {
                put("Sec-Fetch-Dest", "iframe")
                put("Sec-Fetch-Mode", "navigate")
            }
        }
    }

    fun aksorApi(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: AKSOR_PLAYER_ORIGIN
        return buildMap {
            put("Accept", "application/json")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun kodikApi(sourceUrl: String): Map<String, String> {
        return buildMap {
            put("Accept", "application/json, text/javascript, */*; q=0.01")
            put("Origin", sourceUrl.urlOrigin() ?: KODIK_PLAYER_ORIGIN)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
            put("X-Requested-With", "XMLHttpRequest")
        }
    }

    fun kodikPlayback(url: String): Map<String, String> {
        return buildMap {
            putAll(playback(url, "$KODIK_PLAYER_ORIGIN/"))
            put("Accept", "*/*")
            put("Origin", KODIK_PLAYER_ORIGIN)
            put("Referer", "$KODIK_PLAYER_ORIGIN/")
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun playback(
        url: String,
        refererUrl: String? = null,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        val referer = refererUrl?.takeIf { it.isNotBlank() }
        val origin = referer?.urlOrigin()

        return buildMap {
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            put("Accept-Language", PLAYBACK_ACCEPT_LANGUAGE)
            put("User-Agent", BROWSER_USER_AGENT)
            put("Sec-Fetch-Dest", "empty")
            put("Sec-Fetch-Mode", "cors")
            put("Sec-Fetch-Site", "cross-site")
            if (url.contains("vkvideo.cloud", ignoreCase = true)) {
                put("Origin", origin ?: ALLOHA_PLAYER_ORIGIN)
                put("Referer", referer ?: "$ALLOHA_PLAYER_ORIGIN/")
            } else if (referer != null && origin != null) {
                put("Origin", origin)
                put("Referer", referer)
            } else {
                put("Origin", siteBaseUrl.urlOrigin() ?: siteBaseUrl.trimEnd('/'))
                put("Referer", siteBaseUrl.withTrailingSlash())
            }
        }
    }

    fun cvhApi(sourceUrl: String): Map<String, String> {
        val origin = sourceUrl.urlOrigin() ?: DEFAULT_CVH_SITE_ORIGIN
        return buildMap {
            put("Accept", "application/json, text/plain, */*")
            put("Origin", origin)
            put("Referer", sourceUrl)
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun cvhPlayback(
        url: String,
        sourceUrl: String,
        siteBaseUrl: String,
    ): Map<String, String> {
        return buildMap {
            putAll(playback(url, sourceUrl, siteBaseUrl))
            put("Accept", "*/*")
            put("Origin", CVH_PLAYER_ORIGIN)
            put("Referer", "$CVH_PLAYER_ORIGIN/")
            put("User-Agent", BROWSER_USER_AGENT)
        }
    }

    fun forwardedPlayback(
        sourceHeaders: Map<String, String>,
        streamUrl: String,
        sourceUrl: String,
        siteBaseUrl: String = fallbackSiteBaseUrl(),
    ): Map<String, String> {
        return buildMap {
            putAll(playback(streamUrl, sourceUrl, siteBaseUrl))
            sourceHeaders.forEach { (name, value) ->
                if (name.isForwardablePlaybackHeader() && value.isNotBlank()) {
                    put(name, value)
                }
            }
            putIfAbsent("Referer", sourceUrl)
            putIfAbsent("Origin", sourceUrl.urlOrigin() ?: siteBaseUrl.urlOrigin().orEmpty())
            putIfAbsent("User-Agent", BROWSER_USER_AGENT)
            putIfAbsent("Accept-Encoding", "identity")
            putIfAbsent("Accept-Language", PLAYBACK_ACCEPT_LANGUAGE)
            putIfAbsent("Sec-Fetch-Dest", "empty")
            putIfAbsent("Sec-Fetch-Mode", "cors")
            putIfAbsent("Sec-Fetch-Site", "cross-site")
            playbackCookies(streamUrl, sourceUrl)?.let { put("Cookie", it) }
        }
    }

    private fun String.isForwardablePlaybackHeader(): Boolean {
        return lowercase() !in BLOCKED_PLAYBACK_HEADERS
    }

    private fun playbackCookies(streamUrl: String, sourceUrl: String): String? {
        val streamOrigin = streamUrl.urlOrigin()
        val sourceOrigin = sourceUrl.urlOrigin()
        val cookieUrls = buildList {
            add(streamUrl)
            add(streamOrigin)
            if (streamOrigin != null && streamOrigin == sourceOrigin) {
                add(sourceUrl)
                add(sourceOrigin)
            }
        }
        return cookieUrls
            .asSequence()
            .filterNotNull()
            .mapNotNull(cookieProvider::cookieFor)
            .firstOrNull { it.isNotBlank() }
    }

    private companion object {
        const val AKSOR_PLAYER_ORIGIN = "https://player.aksor.tv"
        const val KODIK_PLAYER_ORIGIN = "https://kodikplayer.com"
        const val ALLOHA_PLAYER_ORIGIN = "https://alloha.yani.tv"
        const val CVH_PLAYER_ORIGIN = "https://player.cdnvideohub.com"
        const val DEFAULT_CVH_SITE_ORIGIN = "https://ru.yummyani.me"
        const val PLAYBACK_ACCEPT_LANGUAGE = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"

        val BLOCKED_PLAYBACK_HEADERS = setOf(
            "accept-encoding",
            "access-control-request-headers",
            "access-control-request-method",
            "connection",
            "host",
            "range",
        )
    }
}
