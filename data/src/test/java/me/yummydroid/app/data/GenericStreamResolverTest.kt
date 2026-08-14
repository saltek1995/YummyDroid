package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class GenericStreamResolverTest {
    @Test
    fun directStreamDoesNotUseNetworkOrRuntimeDiscovery() = runBlocking {
        val resolver = resolver(
            responseFor = { error("Network must not be used for a direct stream") },
        )

        val stream = resolver.resolve(
            sourceUrl = "https://cdn.example.test/video/1080p/master.m3u8",
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(1080, stream.maxVideoHeight)
        assertEquals(listOf(1080), stream.availableQualities.mapNotNull(SourceQuality::height))
        assertTrue(stream.skipPlaybackProbe)
    }

    @Test
    fun extensionlessHlsResponseKeepsManifestSubtitleMetadata() = runBlocking {
        val resolver = resolver(
            responseFor = { request -> response(request, HLS_WITH_SUBTITLES, "application/x-mpegURL") },
        )

        val stream = resolver.resolve(
            sourceUrl = "https://player.example.test/manifest?id=14",
            siteBaseUrl = TEST_SITE_BASE_URL,
        )

        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(720, stream.maxVideoHeight)
        assertEquals("https://player.example.test/subs/signs.m3u8", stream.subtitles.single().uri)
        assertEquals("Signs", stream.subtitles.single().label)
        assertTrue(stream.skipPlaybackProbe)
    }

    @Test
    fun unknownHtmlWithoutDirectStreamFailsInsteadOfUsingRuntimeFallback() {
        val resolver = resolver(
            responseFor = { request -> response(request, "<html></html>", "text/html") },
        )

        val failure = assertFailsWith<java.io.IOException> {
            runBlocking {
                resolver.resolve(
                    sourceUrl = "https://player.example.test/no-stream",
                    siteBaseUrl = TEST_SITE_BASE_URL,
                )
            }
        }

        assertEquals("Generic: HLS/MP4/DASH stream was not found", failure.message)
    }

    private fun resolver(
        responseFor: (Request) -> Response,
    ): GenericStreamResolver {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responseFor(chain.request()) })
            .build()
        val parser = SubtitleMetadataParser(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            json = VIDEO_RESOLVER_JSON,
        )
        val headers = PlaybackRequestHeaders(
            fallbackSiteBaseUrl = { TEST_SITE_BASE_URL },
            cookieProvider = PlaybackCookieProvider { null },
        )
        return GenericStreamResolver(
            client = client,
            playbackRequestHeaders = headers,
            subtitleMetadataParser = parser,
        )
    }

    private fun response(
        request: Request,
        body: String,
        contentType: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
            .build()
    }

    private companion object {
        const val TEST_SITE_BASE_URL = "https://ru.yummyani.me"
        val HLS_WITH_SUBTITLES = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Signs",LANGUAGE="ru",URI="subs/signs.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,SUBTITLES="subs"
            chunklist_720.m3u8
        """.trimIndent()
    }
}
