package me.yummydroid.app.data

import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeferredPlaybackSubtitleTest {
    @Test
    fun lazySingleFlightMemoizesSuccessAndFailure() = runBlocking {
        for (value in listOf("file:/subtitle.vtt", null)) {
            var calls = 0
            val subtitle = DeferredPlaybackSubtitle { calls++; value }
            assertEquals(0, calls)
            assertEquals(List(8) { value }, List(8) { async { subtitle.resolve() } }.awaitAll())
            assertEquals(value, subtitle.resolve())
            assertEquals(1, calls)
        }
    }

    @Test
    fun cancelledLoadCanBeRetried() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val subtitle = DeferredPlaybackSubtitle {
            if (++calls == 1) {
                started.complete(Unit)
                CompletableDeferred<Unit>().await()
                throw CancellationException()
            }
            "file:/subtitle.vtt"
        }
        val first = async { subtitle.resolve() }
        started.await()
        first.cancelAndJoin()
        assertEquals("file:/subtitle.vtt", subtitle.resolve())
        assertEquals(2, calls)
    }

    @Test
    fun postprocessingDoesNotFetchUntilLoadAndUsesCapturedHeadersAndValidatedLocalFile() = runBlocking {
        val directory = Files.createTempDirectory("deferred-subtitles").toFile()
        val headers = mapOf("Referer" to "https://player.example.test/", "User-Agent" to "test-agent", "Cookie" to "session=test")
        var requests = 0
        val client = client { request ->
            requests++
            headers.forEach { (name, value) -> assertEquals(value, request.header(name)) }
            response(request, 200, VTT)
        }
        try {
            val materializer = SubtitleTrackMaterializer(null, client, cacheDir = directory, cacheFileUri = { it.toURI().toString() })
            val processor = ResolvedStreamPostProcessor(client, SubtitleMetadataParser({ "https://example.test" }, VIDEO_RESOLVER_JSON), materializer)
            val stream = ResolvedVideoStream(
                url = "https://cdn.example.test/media.m3u8", mimeType = "application/x-mpegURL", headers = headers,
                skipPlaybackProbe = true, runtimeMetadataResolved = true,
                subtitles = listOf(ResolvedSubtitleTrack("https://cdn.example.test/captions.vtt", mimeType = "text/vtt")),
            )
            val processed = withContext(PlaybackResolveRequestPolicy()) { processor.process(stream, deferSubtitles = true) }
            assertEquals(0, requests)
            val track = processed.subtitles.single()
            assertEquals(stream.subtitles.single().uri, track.uri)
            assertEquals("text/vtt", track.mimeType)
            val loader = assertNotNull(track.deferredLoad)
            val local = assertNotNull(loader.resolve())
            assertTrue(local.startsWith("file:"))
            assertTrue(File(URI(local)).readText().contains("Hello"))
            assertEquals(local, loader.resolve())
            assertEquals(1, requests)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun invalidSubtitleIsMemoizedAndNeverExposesRemoteUri() = runBlocking {
        val directory = Files.createTempDirectory("invalid-deferred-subtitles").toFile()
        try {
            for (body in listOf("<html>not subtitles</html>", "WEBVTT\n\n", "{\"error\":\"unavailable\"}")) {
                var requests = 0
                val materializer = SubtitleTrackMaterializer(null,
                    client { request -> requests++; response(request, 200, body) },
                    cacheDir = directory, cacheFileUri = { it.toURI().toString() })
                val track = withContext(PlaybackResolveRequestPolicy()) {
                    materializer.deferPlainVttTracks(
                        listOf(ResolvedSubtitleTrack("https://cdn.example.test/subtitles.vtt", mimeType = "text/vtt")), emptyMap(),
                    ).single()
                }
                repeat(2) { assertNull(track.deferredLoad!!.resolve()) }
                assertEquals(1, requests)
                assertTrue(directory.walkTopDown().none { it.isFile })
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun restrictionStopsOtherDeferredTracksWithoutRestrictingMedia() = runBlocking {
        for (status in listOf(403, 429)) {
            var requests = 0
            val client = client { request -> requests++; response(request, status, "blocked") }
            val policy = PlaybackResolveRequestPolicy()
            val tracks = withContext(policy) {
                SubtitleTrackMaterializer(null, client).deferPlainVttTracks(
                    listOf("one", "two").map { ResolvedSubtitleTrack("https://cdn.example.test/$it.vtt", mimeType = "text/vtt") },
                    emptyMap(),
                )
            }
            assertEquals(0, requests)
            tracks.map { async { assertNull(it.deferredLoad!!.resolve()) } }.awaitAll()
            assertEquals(1, requests)
            policy.beforeRequest()
        }
    }

    private fun client(respond: (okhttp3.Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { respond(it.request()) }.build()

    private fun response(request: okhttp3.Request, status: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
        .body(body.toResponseBody()).build()

    private companion object {
        const val VTT = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello.\n"
    }
}
