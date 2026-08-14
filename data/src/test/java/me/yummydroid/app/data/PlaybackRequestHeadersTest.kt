package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackRequestHeadersTest {
    @Test
    fun allohaIframeHeadersKeepSiteNavigationContext() {
        val headers = headers().iframe(
            url = "https://alloha.yani.tv/?translation=210",
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("https://ru.yummyani.me", headers["Origin"])
        assertEquals("https://ru.yummyani.me/", headers["Referer"])
        assertEquals("iframe", headers["Sec-Fetch-Dest"])
        assertEquals("navigate", headers["Sec-Fetch-Mode"])
    }

    @Test
    fun vkVideoPlaybackWithoutRefererUsesAllohaContext() {
        val headers = headers().playback(
            url = "https://edge.vkvideo.cloud/video/master.m3u8",
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("https://alloha.yani.tv", headers["Origin"])
        assertEquals("https://alloha.yani.tv/", headers["Referer"])
        assertEquals("identity", headers["Accept-Encoding"])
    }

    @Test
    fun forwardedPlaybackFiltersTransportHeadersAndPrefersRuntimeCookie() {
        val headers = headers(
            cookieProvider = PlaybackCookieProvider { url ->
                "runtime=active".takeIf { url == STREAM_URL }
            },
        ).forwardedPlayback(
            sourceHeaders = mapOf(
                "Accept-Encoding" to "gzip",
                "Connection" to "keep-alive",
                "Host" to "forged.example.test",
                "Range" to "bytes=500-",
                "Authorization" to "Bearer token",
                "Cookie" to "request=old",
                "X-Playback-Token" to "abc",
            ),
            streamUrl = STREAM_URL,
            sourceUrl = SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("identity", headers["Accept-Encoding"])
        assertFalse("Connection" in headers)
        assertFalse("Host" in headers)
        assertFalse("Range" in headers)
        assertEquals("Bearer token", headers["Authorization"])
        assertEquals("abc", headers["X-Playback-Token"])
        assertEquals("runtime=active", headers["Cookie"])
    }

    @Test
    fun forwardedSignedAllohaPlaybackDoesNotForceAcceptEncoding() {
        val headers = headers().forwardedPlayback(
            sourceHeaders = mapOf(
                "Accept-Encoding" to "gzip, deflate, br",
                "Authorizations" to "signed-stream",
                "Accepts-Controls" to "stream-control",
            ),
            streamUrl = "https://edge.vkvideo.cloud/video/master.m3u8",
            sourceUrl = SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertFalse("Accept-Encoding" in headers)
        assertEquals("signed-stream", headers["Authorizations"])
        assertEquals("stream-control", headers["Accepts-Controls"])
    }

    @Test
    fun forwardedPlaybackDeduplicatesHeadersCaseInsensitively() {
        val headers = headers().forwardedPlayback(
            sourceHeaders = mapOf(
                "user-agent" to "Runtime browser",
                "referer" to "https://alloha.yani.tv/runtime",
                "X-Playback-Token" to "abc",
            ),
            streamUrl = STREAM_URL,
            sourceUrl = SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals(1, headers.keys.count { it.equals("User-Agent", ignoreCase = true) })
        assertEquals(1, headers.keys.count { it.equals("Referer", ignoreCase = true) })
        assertEquals("Runtime browser", headers.valueForHeader("User-Agent"))
        assertEquals("https://alloha.yani.tv/runtime", headers.valueForHeader("Referer"))
        assertEquals("abc", headers["X-Playback-Token"])
    }

    @Test
    fun crossOriginPlaybackDoesNotReadSourcePageCookies() {
        val inspectedUrls = mutableListOf<String>()
        val headers = headers(
            cookieProvider = PlaybackCookieProvider { url ->
                inspectedUrls += url
                "source=private".takeIf { url == SOURCE_URL }
            },
        ).forwardedPlayback(
            sourceHeaders = emptyMap(),
            streamUrl = STREAM_URL,
            sourceUrl = SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertNull(headers["Cookie"])
        assertFalse(SOURCE_URL in inspectedUrls)
        assertTrue(inspectedUrls.all { it.startsWith("https://cdn.example.test") })
    }

    @Test
    fun sameOriginPlaybackCanReuseSourcePageCookie() {
        val sourceUrl = "https://cdn.example.test/player/14"
        val headers = headers(
            cookieProvider = PlaybackCookieProvider { url ->
                "source=shared".takeIf { url == sourceUrl }
            },
        ).forwardedPlayback(
            sourceHeaders = emptyMap(),
            streamUrl = STREAM_URL,
            sourceUrl = sourceUrl,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("source=shared", headers["Cookie"])
    }

    private fun headers(
        cookieProvider: PlaybackCookieProvider = PlaybackCookieProvider { null },
    ): PlaybackRequestHeaders {
        return PlaybackRequestHeaders(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            cookieProvider = cookieProvider,
        )
    }

    private companion object {
        const val TEST_SITE_BASE_URL = "https://ru.yummyani.me"
        const val SOURCE_URL = "https://alloha.yani.tv/?translation=210&season=1&episode=14"
        const val STREAM_URL = "https://cdn.example.test/video/master.m3u8"
    }
}

private fun Map<String, String>.valueForHeader(name: String): String? {
    return entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
}
