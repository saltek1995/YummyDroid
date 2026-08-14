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

class SubtitleTrackMaterializerValidationTest {
    @Test
    fun unnamedTrackWithCueTextIsKept() {
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
    fun placeholderTrackWithoutCueTextIsRejected() {
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
