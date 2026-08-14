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
    fun skippedProbeKeepsFallbackCandidatesWithoutTestingThem() {
        val client = client { error("Network must not be used for skipped fallback candidates") }
        val fallbackUrls = listOf(
            "https://cdn.example.test/video/720p/master.m3u8",
            "https://cdn.example.test/video/480p/master.m3u8",
        )

        val result = processor(client).process(
            ResolvedVideoStream(
                url = "https://cdn.example.test/video/1080p/master.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                fallbackUrls = fallbackUrls,
                skipPlaybackProbe = true,
            ),
        )

        assertEquals("https://cdn.example.test/video/1080p/master.m3u8", result.url)
        assertEquals(fallbackUrls, result.fallbackUrls)
        assertEquals(listOf(1080), result.availableQualities.mapNotNull { it.height })
    }

    @Test
    fun adaptiveProbeCompletesPartialRuntimeQualityListFromManifest() {
        val masterUrl = "https://cdn.example.test/video/master.m3u8"
        val requestedUrls = mutableListOf<String>()
        val client = client { request ->
            requestedUrls += request.url.toString()
            response(request, body = HLS_ALL_QUALITIES, contentType = "application/x-mpegURL")
        }

        val result = processor(client).process(
            ResolvedVideoStream(
                url = masterUrl,
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                availableQualities = listOf(SourceQuality(height = 1080), SourceQuality(height = 480)),
                maxVideoHeight = 1080,
                skipPlaybackProbe = false,
            ),
        )

        assertEquals(listOf(1080, 720, 480, 360), result.availableQualities.mapNotNull { it.height })
        assertEquals(1080, result.maxVideoHeight)
        assertEquals(listOf(masterUrl, masterUrl), requestedUrls)
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

        val HLS_ALL_QUALITIES = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=700000,RESOLUTION=640x360
            chunklist_360.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=854x480
            chunklist_480.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            chunklist_720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4200000,RESOLUTION=1920x1080
            chunklist_1080.m3u8
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
