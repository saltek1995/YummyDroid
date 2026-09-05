package me.yummydroid.app.data

import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNull
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class SubtitleTrackMaterializerValidationTest {
    @Test
    fun subtitleResponseStartedBeforeCleanupCannotRecreateTheCache() = runBlocking {
        val directory = Files.createTempDirectory("subtitle-publication").toFile()
        val body = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello.\n"
        val generation = SubtitleCacheAccess.generation()
        val materializer = SubtitleTrackMaterializer(
            context = null,
            cacheDir = directory,
            client = OkHttpClient.Builder().addInterceptor { chain ->
                clearSubtitleCache(directory)
                response(chain.request(), body)
            }.build(),
        )
        try {
            assertTrue(materializer.validateTracks(listOf(ResolvedSubtitleTrack("https://example.test/subtitle.vtt")), emptyMap()).isEmpty())
            assertNull(materializer.materializeCapturedBody("https://example.test/subtitle.vtt", "text/vtt", body, generation))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            clearSubtitleCache(directory)
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancelledSubtitleRequestPropagatesInsteadOfReportingMissingTracks() = runBlocking {
        val materializer = SubtitleTrackMaterializer(
            context = null,
            client = OkHttpClient.Builder().addInterceptor { throw CancellationException("cancelled") }.build(),
        )
        assertFailsWith<CancellationException> {
            materializer.validateTracks(
                listOf(ResolvedSubtitleTrack(uri = "https://cdn.example.test/subtitles.vtt")),
                emptyMap(),
            )
        }
        Unit
    }

    @Test
    fun unnamedTrackWithCueTextIsKept() = runBlocking {
        val trackUrl = "https://cdn.example.test/track?id=ru"
        val materializer = materializer(
            responseBody = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                Hello.
            """.trimIndent(),
        )

        val tracks = materializer.validateTracks(
            tracks = listOf(ResolvedSubtitleTrack(uri = trackUrl)),
            headers = emptyMap(),
        )

        assertEquals(1, tracks.size)
        assertEquals(trackUrl, tracks.single().uri)
        assertEquals("", tracks.single().label)
        assertEquals("text/vtt", tracks.single().mimeType)
    }

    @Test
    fun placeholderTrackWithoutCueTextIsRejected() = runBlocking {
        val materializer = materializer(
            responseBody = """
                WEBVTT

                NOTE subtitles are available
            """.trimIndent(),
        )

        val tracks = materializer.validateTracks(
            tracks = listOf(ResolvedSubtitleTrack(uri = "https://cdn.example.test/track?id=empty")),
            headers = emptyMap(),
        )

        assertTrue(tracks.isEmpty())
    }

    private fun materializer(responseBody: String): SubtitleTrackMaterializer {
        return SubtitleTrackMaterializer(
            context = null,
            client = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    response(chain.request(), responseBody)
                })
                .build(),
        )
    }

    private fun response(request: Request, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/vtt".toMediaTypeOrNull()))
            .build()
    }
}
