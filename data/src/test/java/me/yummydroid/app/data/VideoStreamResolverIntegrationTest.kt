package me.yummydroid.app.data

import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class VideoStreamResolverIntegrationTest {
    @Test
    fun failedSiteDomainFallsBackToNextCandidateAndPromotesIt() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val siteDomainResolver = siteDomainResolver()
        val resolver = VideoStreamResolver(
            context = null,
            siteDomainResolver = siteDomainResolver,
            client = client { request ->
                val url = request.url.toString()
                requestedUrls += url
                when (url) {
                    FIRST_PLAYER_URL -> response(request, "failed", "text/plain", code = 500)
                    SECOND_PLAYER_URL -> response(request, STATIC_PLAYER_PAGE, "text/html")
                    STREAM_URL -> response(request, HLS_MANIFEST, "application/x-mpegURL")
                    else -> response(request, "missing", "text/plain", code = 404)
                }
            },
        )

        val stream = resolver.resolve(video("/player/14"))

        assertEquals(STREAM_URL, stream.url)
        assertEquals(720, stream.maxVideoHeight)
        assertEquals(listOf(FIRST_PLAYER_URL, SECOND_PLAYER_URL), requestedUrls.take(2))
        assertEquals("https://two.example.test/", siteDomainResolver.cachedOrDefaultBaseUrl())
    }

    @Test
    fun cancellationStopsDomainFailoverAndDoesNotPoisonDomainState() {
        val requestedUrls = mutableListOf<String>()
        val cancellation = CancellationException("cancelled")
        val siteDomainResolver = siteDomainResolver()
        val resolver = VideoStreamResolver(
            context = null,
            siteDomainResolver = siteDomainResolver,
            client = client { request ->
                val url = request.url.toString()
                requestedUrls += url
                if (url == FIRST_PLAYER_URL) throw cancellation
                response(request, STATIC_PLAYER_PAGE, "text/html")
            },
        )

        assertFailsWith<CancellationException> {
            runBlocking { resolver.resolve(video("/player/14")) }
        }

        assertTrue(FIRST_PLAYER_URL in requestedUrls)
        assertFalse(SECOND_PLAYER_URL in requestedUrls)
        assertEquals("https://one.example.test/", siteDomainResolver.cachedOrDefaultBaseUrl())
    }

    private fun siteDomainResolver(): SiteDomainResolver {
        return SiteDomainResolver(
            client = client { request -> response(request, "", "text/plain") },
            candidates = listOf("https://one.example.test/", "https://two.example.test/"),
        )
    }

    private fun client(responseFor: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responseFor(chain.request()) })
            .build()
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

    private fun video(url: String): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 5500,
            player = "Generic",
            dubbing = "AniLibria",
            episode = "14",
            url = url,
            index = 14,
            durationSeconds = 1_400,
            views = 1,
        )
    }

    private companion object {
        const val FIRST_PLAYER_URL = "https://one.example.test/player/14"
        const val SECOND_PLAYER_URL = "https://two.example.test/player/14"
        const val STREAM_URL = "https://cdn.example.test/video/720p/master.m3u8"
        const val STATIC_PLAYER_PAGE =
            """{"file":"https://cdn.example.test/video/720p/master.m3u8"}"""
        val HLS_MANIFEST = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720
            chunklist_720.m3u8
        """.trimIndent()
    }
}
