package me.yummydroid.app.data

import java.util.concurrent.CancellationException
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
            runtimeResolver = failingRuntime("Runtime discovery must not be used for a direct stream"),
        )

        val stream = resolver.resolve(
            sourceUrl = "https://cdn.example.test/video/1080p/master.m3u8",
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.Auto,
            waitForRuntimeSubtitles = true,
        )

        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(1080, stream.maxVideoHeight)
        assertEquals(listOf(1080), stream.availableQualities.mapNotNull(SourceQuality::height))
    }

    @Test
    fun extensionlessHlsResponseKeepsManifestSubtitleMetadata() = runBlocking {
        val resolver = resolver(
            responseFor = { request -> response(request, HLS_WITH_SUBTITLES, "application/x-mpegURL") },
            runtimeResolver = failingRuntime("Runtime discovery must not be used for an HLS response"),
        )

        val stream = resolver.resolve(
            sourceUrl = "https://player.example.test/manifest?id=14",
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.Auto,
            waitForRuntimeSubtitles = true,
        )

        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals(720, stream.maxVideoHeight)
        assertEquals("https://player.example.test/subs/signs.m3u8", stream.subtitles.single().uri)
        assertEquals("Signs", stream.subtitles.single().label)
    }

    @Test
    fun runtimeProviderUsesWebViewWithoutPreflightHttpRequest() = runBlocking {
        var runtimeCalls = 0
        val runtimeStream = ResolvedVideoStream(
            url = "https://cdn.example.test/runtime/720p.m3u8",
            mimeType = "application/x-mpegURL",
            headers = emptyMap(),
            maxVideoHeight = 720,
            availableQualities = listOf(SourceQuality(height = 720)),
        )
        val resolver = resolver(
            responseFor = { error("Runtime provider must not use a preflight HTTP request") },
            runtimeResolver = object : RuntimeStreamResolver {
                override suspend fun resolve(
                    sourceUrl: String,
                    siteBaseUrl: String,
                    preferredQuality: PreferredQuality,
                    waitForRuntimeSubtitles: Boolean,
                ): ResolvedVideoStream {
                    runtimeCalls += 1
                    return runtimeStream
                }
            },
        )

        val stream = resolver.resolve(
            sourceUrl = ALLOHA_SOURCE_URL,
            siteBaseUrl = TEST_SITE_BASE_URL,
            preferredQuality = PreferredQuality.P720,
            waitForRuntimeSubtitles = true,
        )

        assertEquals(1, runtimeCalls)
        assertEquals(runtimeStream.url, stream.url)
        assertEquals(listOf(720), stream.availableQualities.mapNotNull(SourceQuality::height))
    }

    @Test
    fun runtimeProviderFailureIsNotHiddenByUnsafeHttpFallback() {
        val resolver = resolver(
            responseFor = { error("Runtime provider must not use a preflight HTTP request") },
            runtimeResolver = failingRuntime("runtime unavailable"),
        )

        val failure = assertFailsWith<IllegalStateException> {
            runBlocking {
                resolver.resolve(
                    sourceUrl = ALLOHA_SOURCE_URL,
                    siteBaseUrl = TEST_SITE_BASE_URL,
                    preferredQuality = PreferredQuality.Auto,
                    waitForRuntimeSubtitles = true,
                )
            }
        }

        assertEquals("runtime unavailable", failure.message)
    }

    @Test
    fun cancellationIsNeverConvertedIntoStaticFallback() {
        val cancellation = CancellationException("cancelled")
        val resolver = resolver(
            responseFor = { error("Runtime provider must not use a preflight HTTP request") },
            runtimeResolver = object : RuntimeStreamResolver {
                override suspend fun resolve(
                    sourceUrl: String,
                    siteBaseUrl: String,
                    preferredQuality: PreferredQuality,
                    waitForRuntimeSubtitles: Boolean,
                ): ResolvedVideoStream = throw cancellation
            },
        )

        val thrown = assertFailsWith<CancellationException> {
            runBlocking {
                resolver.resolve(
                    sourceUrl = ALLOHA_SOURCE_URL,
                    siteBaseUrl = TEST_SITE_BASE_URL,
                    preferredQuality = PreferredQuality.Auto,
                    waitForRuntimeSubtitles = true,
                )
            }
        }

        assertTrue(thrown === cancellation)
    }

    private fun resolver(
        responseFor: (Request) -> Response,
        runtimeResolver: RuntimeStreamResolver,
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
            runtimeStreamResolver = runtimeResolver,
        )
    }

    private fun failingRuntime(message: String): RuntimeStreamResolver {
        return object : RuntimeStreamResolver {
            override suspend fun resolve(
                sourceUrl: String,
                siteBaseUrl: String,
                preferredQuality: PreferredQuality,
                waitForRuntimeSubtitles: Boolean,
            ): ResolvedVideoStream = error(message)
        }
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
        const val ALLOHA_SOURCE_URL = "https://alloha.yani.tv/embed/14"
        val HLS_WITH_SUBTITLES = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Signs",LANGUAGE="ru",URI="subs/signs.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,SUBTITLES="subs"
            chunklist_720.m3u8
        """.trimIndent()
    }
}
