package me.yummydroid.app.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolvedStreamPostProcessorTest {
    @Test
    fun firstPlayableFallbackWinsWithoutLosingRemainingCandidates() {
        val failedUrl = "https://cdn.example.test/failed/master.m3u8"
        val playableUrl = "https://cdn.example.test/playable/master.m3u8"
        val standbyUrl = "https://cdn.example.test/standby/master.m3u8"
        val requestedUrls = mutableListOf<String>()
        val client = client { request ->
            requestedUrls += request.url.toString()
            if (request.url.toString() == failedUrl) {
                response(request, code = 404, body = "missing", contentType = "text/plain")
            } else {
                response(request, body = HLS_720, contentType = "application/x-mpegURL")
            }
        }

        val result = processor(client).process(
            ResolvedVideoStream(
                url = failedUrl,
                mimeType = "application/x-mpegURL",
                headers = mapOf("Referer" to "https://player.example.test/"),
                fallbackUrls = listOf(playableUrl, standbyUrl),
            ),
        )

        assertEquals(playableUrl, result.url)
        assertEquals(listOf(failedUrl, standbyUrl), result.fallbackUrls)
        assertEquals(listOf(720), result.availableQualities.mapNotNull { it.height })
        assertEquals(listOf(failedUrl, playableUrl, playableUrl), requestedUrls)
    }

    @Test
    fun skippedProbePerformsNoNetworkRequestAndKeepsUrlQuality() {
        val client = client { error("Network must not be used for a skipped probe") }

        val result = processor(client).process(
            ResolvedVideoStream(
                url = "https://cdn.example.test/video/1080p/master.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                skipPlaybackProbe = true,
            ),
        )

        assertEquals(1080, result.maxVideoHeight)
        assertEquals(listOf(1080), result.availableQualities.mapNotNull { it.height })
    }

    @Test
    fun manifestSubtitleIsNamedValidatedAndKeptAsPlayableTrack() {
        val masterUrl = "https://cdn.example.test/video/master.m3u8"
        val subtitleUrl = "https://cdn.example.test/video/subs/signs.m3u8"
        val client = client { request ->
            when (request.url.toString()) {
                masterUrl -> response(request, body = HLS_WITH_SUBTITLES, contentType = "application/x-mpegURL")
                subtitleUrl -> response(request, body = WEBVTT_SUBTITLES, contentType = "text/vtt")
                else -> response(request, code = 404, body = "missing", contentType = "text/plain")
            }
        }

        val result = processor(client).process(
            ResolvedVideoStream(
                url = masterUrl,
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
            ),
        )

        val subtitle = result.subtitles.single()
        assertEquals(subtitleUrl, subtitle.uri)
        assertEquals("Signs", subtitle.label)
        assertEquals("ru", subtitle.language)
        assertEquals("text/vtt", subtitle.mimeType)
        assertTrue(result.hasSubtitles)
    }

    private fun processor(client: OkHttpClient): ResolvedStreamPostProcessor {
        val parser = SubtitleMetadataParser(
            fallbackSiteBaseUrl = { DEFAULT_SITE_BASE_URL },
            json = VIDEO_RESOLVER_JSON,
        )
        return ResolvedStreamPostProcessor(
            client = client,
            subtitleMetadataParser = parser,
            subtitleTrackMaterializer = SubtitleTrackMaterializer(context = null, client = client),
        )
    }

    private fun client(responseFor: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> responseFor(chain.request()) })
            .build()
    }

    private fun response(
        request: Request,
        code: Int = 200,
        body: String,
        contentType: String,
    ): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody(contentType.toMediaTypeOrNull()))
            .build()
    }

    private companion object {
        val HLS_720 = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720
            chunklist_720.m3u8
        """.trimIndent()

        val HLS_WITH_SUBTITLES = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Signs",LANGUAGE="ru",URI="subs/signs.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,SUBTITLES="subs"
            chunklist_720.m3u8
        """.trimIndent()

        val WEBVTT_SUBTITLES = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            Sign text
        """.trimIndent()
    }
}
