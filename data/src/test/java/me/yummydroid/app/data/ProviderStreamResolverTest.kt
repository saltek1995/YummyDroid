package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

class ProviderStreamResolverTest {
    @Test
    fun kodikUsesIframeParametersAndFtorResponse() {
        val requests = mutableListOf<Request>()
        val resolver = resolver { request ->
            requests += request
            when (request.url.toString()) {
                KODIK_SOURCE_URL -> response(request, KODIK_IFRAME_HTML, "text/html")
                "https://kodikplayer.com/ftor" -> response(request, KODIK_FTOR_RESPONSE, "application/json")
                else -> response(request, "missing", "text/plain", code = 404)
            }
        }

        val stream = resolver.resolveKodik(
            sourceUrl = KODIK_SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.P720,
        )

        assertEquals("https://cdn.example.test/video/720p/master.m3u8", stream.url)
        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(720, stream.maxVideoHeight)
        assertEquals(listOf(720), stream.availableQualities.mapNotNull(SourceQuality::height))
        assertTrue(stream.skipPlaybackProbe)
        assertEquals(listOf("GET", "POST"), requests.map { it.method })
        assertTrue(requests.last().bodyText().contains("id=42"))
        assertTrue(requests.last().bodyText().contains("hash=episode-hash"))
        assertEquals("XMLHttpRequest", requests.last().header("X-Requested-With"))
    }

    @Test
    fun aksorSelectsRequestedQualityFromApiResponse() {
        val requests = mutableListOf<Request>()
        val resolver = resolver { request ->
            requests += request
            response(request, AKSOR_RESPONSE, "application/json")
        }

        val stream = resolver.resolveAksor(
            sourceUrl = AKSOR_SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.P720,
        )

        assertEquals("https://cdn.example.test/aksor/720p.mp4", stream.url)
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(720, stream.maxVideoHeight)
        assertEquals(listOf(1080, 720), stream.availableQualities.mapNotNull(SourceQuality::height))
        assertTrue(stream.skipPlaybackProbe)
        assertEquals("https://player.aksor.tv/api/video/episode-14", requests.single().url.toString())
        assertEquals("https://player.aksor.tv", requests.single().header("Origin"))
    }

    @Test
    fun sibnetResolvesRelativePlayerSourceAgainstIframe() {
        val resolver = resolver { request -> response(request, SIBNET_IFRAME_HTML, "text/html") }

        val stream = resolver.resolveSibnet(
            sourceUrl = SIBNET_SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("https://video.sibnet.ru/v/14/video_1080p.mp4?token=abc", stream.url)
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(1080, stream.maxVideoHeight)
        assertTrue(stream.skipPlaybackProbe)
    }

    @Test
    fun cvhSelectsRequestedVoiceAndBuildsPlaybackContext() {
        val requests = mutableListOf<Request>()
        val resolver = resolver { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/playlist") -> response(
                    request,
                    CVH_PLAYLIST_RESPONSE,
                    "application/json",
                )
                request.url.encodedPath.endsWith("/selected-vk-id") -> response(
                    request,
                    CVH_VIDEO_RESPONSE,
                    "application/json",
                )
                else -> response(request, "missing", "text/plain", code = 404)
            }
        }

        val stream = resolver.resolveCvh(
            sourceUrl = CVH_SOURCE_URL,
            video = video(player = "CVH", dubbing = "MiraiDUB"),
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.P720,
        )

        assertEquals("https://cdn.example.test/cvh/master.m3u8", stream.url)
        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(1080, stream.maxVideoHeight)
        assertEquals("https://player.cdnvideohub.com", stream.headers["Origin"])
        assertEquals("https://player.cdnvideohub.com/", stream.headers["Referer"])
        assertTrue(stream.skipPlaybackProbe)
        assertEquals(2, requests.size)
        assertEquals("745", requests.first().url.queryParameter("pub"))
        assertEquals("5500", requests.first().url.queryParameter("id"))
        assertTrue(requests.last().url.encodedPath.endsWith("/selected-vk-id"))
    }

    private fun resolver(responseFor: (Request) -> Response): ProviderStreamResolver {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responseFor(chain.request()) })
            .build()
        val headers = PlaybackRequestHeaders(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            cookieProvider = PlaybackCookieProvider { null },
        )
        return ProviderStreamResolver(
            client = client,
            playbackRequestHeaders = headers,
            subtitleMetadataParser = SubtitleMetadataParser(
                fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
                json = VIDEO_RESOLVER_JSON,
            ),
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            json = VIDEO_RESOLVER_JSON,
        )
    }

    private fun response(
        request: Request,
        body: String,
        contentType: String,
        code: Int = 200,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
            .build()
    }

    private fun Request.bodyText(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun video(player: String, dubbing: String): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 5500,
            player = player,
            dubbing = dubbing,
            episode = "14",
            url = CVH_SOURCE_URL,
            index = 14,
            durationSeconds = 1_400,
            views = 1,
        )
    }

    private companion object {
        const val TEST_SITE_BASE_URL = "https://ru.yummyani.me"
        const val KODIK_SOURCE_URL = "https://kodikplayer.com/video/episode-14"
        const val AKSOR_SOURCE_URL = "https://player.aksor.tv/embed/episode-14/"
        const val SIBNET_SOURCE_URL = "https://video.sibnet.ru/shell.php?videoid=14"
        const val CVH_SOURCE_URL =
            "https://ru.yummyani.me/iframeCVH?anime_id=5500&episode=14&season=1&dubbing=MiraiDUB"

        val KODIK_IFRAME_HTML = """
            <script>
                var domain = "kodikplayer.com";
                var d_sign = "domain-sign";
                var pd = "kodikplayer.com";
                var pd_sign = "player-sign";
                var ref = "https://ru.yummyani.me/";
                var ref_sign = "referer-sign";
                vInfo.type = 'seria';
                vInfo.id = '42';
                vInfo.hash = 'episode-hash';
            </script>
        """.trimIndent()
        const val KODIK_FTOR_RESPONSE =
            """{"link":"https://cdn.example.test/video/720p/master.m3u8"}"""
        const val AKSOR_RESPONSE =
            """{"qualities":{"q1080":"https://cdn.example.test/aksor/1080p.mp4","q720":"https://cdn.example.test/aksor/720p.mp4"}}"""
        const val SIBNET_IFRAME_HTML =
            """player = { src: "/v/14/video_1080p.mp4?token=abc" };"""
        val CVH_PLAYLIST_RESPONSE = """
            {
              "items": [
                {"vkId":"wrong-vk-id","voiceStudio":"AniDUB","season":1,"episode":14},
                {"vkId":"selected-vk-id","voiceStudio":"MiraiDUB","season":1,"episode":14}
              ]
            }
        """.trimIndent()
        val CVH_VIDEO_RESPONSE = """
            {
              "sources": {
                "hlsUrl": "https://cdn.example.test/cvh/master.m3u8",
                "mpegFullHdUrl": "https://cdn.example.test/cvh/1080p.mp4",
                "mpegHighUrl": "https://cdn.example.test/cvh/720p.mp4"
              }
            }
        """.trimIndent()
    }
}
