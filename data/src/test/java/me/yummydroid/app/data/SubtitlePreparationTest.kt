package me.yummydroid.app.data

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.util.Collections
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient

class SubtitlePreparationTest {
    @Test
    fun prefetchStartsBeforePostprocessingAndMaterializerUsesItsSingleHeldResponse() = runBlocking {
        val directory = Files.createTempDirectory("subtitle-preparation").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<HttpResponseSnapshot>()
        val requests = Collections.synchronizedList(mutableListOf<Pair<String, Map<String, String>>>())
        val headers = mapOf("X-Caption" to "fixture", "Authorization" to "caption-only")
        val track = ResolvedSubtitleTrack("https://captions.example.test/fixture.vtt", headers = headers)
        val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { uri, requestHeaders ->
            requests += uri to requestHeaders
            started.complete(Unit)
            release.await()
        }
        val materializer = materializer(directory)
        try {
            preparation.prefetch(listOf(track))
            withTimeout(5_000) { started.await() }
            assertEquals(listOf(track.uri to headers), requests)

            val validating = async(preparation) { materializer.validateTracks(listOf(track), emptyMap()) }
            release.complete(vttResponse())
            val validated = withTimeout(5_000) { validating.await() }

            assertEquals(1, requests.size)
            assertEquals(1, validated.size)
            val cached = validated.single()
            assertTrue(cached.uri.startsWith("file:"))
            assertEquals("text/vtt", cached.mimeType)
            assertTrue(File(URI(cached.uri)).readText().contains("Prepared caption."))
        } finally {
            clearSubtitleCache(directory)
            directory.deleteRecursively()
        }
    }

    @Test
    fun forbiddenPrefetchIsMemoizedStopsQueuedCaptionsAndLeavesEarlierCaptionUsable() = runBlocking {
        val directory = Files.createTempDirectory("subtitle-preparation-forbidden").toFile()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<HttpResponseSnapshot>()
        val forbiddenStarted = CompletableDeferred<Unit>()
        val requests = Collections.synchronizedList(mutableListOf<String>())
        val headers = mapOf("X-Caption" to "fixture")
        val first = ResolvedSubtitleTrack("https://captions.example.test/first.vtt", headers = headers)
        val forbidden = ResolvedSubtitleTrack("https://captions.example.test/forbidden.vtt", headers = headers)
        val skipped = ResolvedSubtitleTrack("https://captions.example.test/skipped.vtt", headers = headers)
        val policy = PlaybackResolveRequestPolicy()
        val materializer = materializer(directory)
        try {
            withContext(policy) {
                val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { uri, _ ->
                    requests += uri
                    when (uri) {
                        first.uri -> {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                        }
                        forbidden.uri -> {
                            forbiddenStarted.complete(Unit)
                            kotlinx.coroutines.currentCoroutineContext()[HttpRequestPolicy]!!.onResponse(403)
                            error("403 must throw")
                        }
                        else -> error("A forbidden subtitle must stop queued requests: $uri")
                    }
                }
                preparation.prefetch(listOf(first))
                withTimeout(5_000) { firstStarted.await() }
                preparation.prefetch(listOf(forbidden, skipped))
                releaseFirst.complete(vttResponse("First caption."))
                withTimeout(5_000) { forbiddenStarted.await() }

                val tracks = withContext(preparation) {
                    materializer.validateTracks(listOf(first, forbidden, skipped), emptyMap())
                }
                assertEquals(listOf(first.uri, forbidden.uri), requests)
                assertEquals(1, tracks.size)
                assertTrue(File(URI(tracks.single().uri)).readText().contains("First caption."))
                policy.beforeRequest()
            }
        } finally {
            clearSubtitleCache(directory)
            directory.deleteRecursively()
        }
    }

    @Test
    fun cancellingResolutionCancelsInflightPrefetchWithoutHidingCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val track = ResolvedSubtitleTrack("https://captions.example.test/cancel.vtt", headers = mapOf("X-Caption" to "fixture"))
        val resolution = async {
            val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { _, _ ->
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            preparation.prefetch(listOf(track))
            started.await()
            preparation.response(track.uri, track.headers)
        }
        withTimeout(5_000) { started.await() }
        resolution.cancel()
        assertFailsWith<kotlinx.coroutines.CancellationException> { resolution.await() }
        withTimeout(5_000) { cancelled.await() }
    }

    @Test
    fun cacheClearWhilePrefetchIsHeldPreventsItsLaterMaterializationFromPublishing() = runBlocking {
        val directory = Files.createTempDirectory("subtitle-preparation-clear").toFile()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<HttpResponseSnapshot>()
        val track = ResolvedSubtitleTrack(
            "https://captions.example.test/cleared.vtt",
            headers = mapOf("X-Caption" to "fixture"),
        )
        val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { _, _ ->
            started.complete(Unit)
            release.await()
        }
        val materializer = materializer(directory)
        try {
            preparation.prefetch(listOf(track))
            withTimeout(5_000) { started.await() }
            clearSubtitleCache(directory)
            release.complete(vttResponse())

            val tracks = withContext(preparation) { materializer.validateTracks(listOf(track), emptyMap()) }
            assertTrue(tracks.isEmpty())
            assertTrue(directory.walkTopDown().none { it.isFile })
        } finally {
            clearSubtitleCache(directory)
            directory.deleteRecursively()
        }
    }

    @Test
    fun terminatingWebViewRequestDoesNotCancelSharedPrefetchNeededByPostprocessing() = runBlocking {
        val directory = Files.createTempDirectory("subtitle-preparation-termination").toFile()
        val fetchStarted = CompletableDeferred<Unit>()
        val requestAwaiting = CompletableDeferred<Unit>()
        val release = CompletableDeferred<HttpResponseSnapshot>()
        val headers = mapOf("X-Caption" to "fixture")
        val track = ResolvedSubtitleTrack("https://captions.example.test/termination.vtt", headers = headers)
        val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { _, _ ->
            fetchStarted.complete(Unit)
            release.await()
        }
        val termination = WebViewSessionTermination(coroutineContext + preparation)
        val materializer = materializer(directory)
        try {
            preparation.prefetch(listOf(track))
            withTimeout(5_000) { fetchStarted.await() }
            val interceptor = async(Dispatchers.IO) {
                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    termination.runRequest {
                        requestAwaiting.complete(Unit)
                        withContext(preparation) { preparation.response(track.uri, headers) }
                    }
                }
            }
            withTimeout(5_000) { requestAwaiting.await() }
            assertTrue(termination.tryTerminate())
            withTimeout(5_000) { interceptor.await() }

            release.complete(vttResponse("Surviving caption."))
            val tracks = withContext(preparation) { materializer.validateTracks(listOf(track), emptyMap()) }
            assertEquals(1, tracks.size)
            assertTrue(File(URI(tracks.single().uri)).readText().contains("Surviving caption."))
        } finally {
            termination.tryTerminate()
            clearSubtitleCache(directory)
            directory.deleteRecursively()
        }
    }

    @Test
    fun resolutionFailureCancelsItsHeldPrefetchAndPropagatesTheOriginalException() = runBlocking {
        val fetchStarted = CompletableDeferred<Unit>()
        val fetchCancelled = CompletableDeferred<Unit>()
        val policy = PlaybackResolveRequestPolicy()
        val track = ResolvedSubtitleTrack(
            "https://captions.example.test/resolution-failure.vtt",
            headers = mapOf("X-Caption" to "fixture"),
        )

        val failure = assertFailsWith<IOException> {
            withContext(Dispatchers.IO + policy) {
                val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { _, _ ->
                    fetchStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        fetchCancelled.complete(Unit)
                    }
                }
                preparation.prefetch(listOf(track))
                withContext(preparation) { withTimeout(5_000) { fetchStarted.await() } }
                throw IOException("resolution failed")
            }
        }
        assertEquals("resolution failed", failure.message)
        withTimeout(5_000) { fetchCancelled.await() }
    }

    @Test
    fun requestIdentityUsesHeaderValuesButIgnoresHeaderNameCase() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<Map<String, String>>())
        val preparation = SubtitlePreparation(CoroutineScope(coroutineContext)) { _, headers ->
            requests += headers
            vttResponse()
        }
        val uri = "https://captions.example.test/identity.vtt"
        val first = async { preparation.response(uri, mapOf("X-Caption" to "one")) }
        val sameHeaders = async { preparation.response(uri, mapOf("x-caption" to "one")) }
        val differentHeaders = async { preparation.response(uri, mapOf("X-Caption" to "two")) }

        withTimeout(5_000) { first.await(); sameHeaders.await(); differentHeaders.await() }
        assertEquals(2, requests.size)
        assertTrue(requests.any { it.values.single() == "one" })
        assertTrue(requests.any { it.values.single() == "two" })
    }

    private fun materializer(directory: File) = SubtitleTrackMaterializer(
        context = null,
        client = OkHttpClient(),
        cacheDir = directory,
        cacheFileUri = { it.toURI().toString() },
    )

    private fun vttResponse(text: String = "Prepared caption.") = HttpResponseSnapshot(
        code = 200,
        message = "OK",
        headers = emptyMap(),
        mimeType = "text/vtt",
        encoding = "UTF-8",
        body = "WEBVTT\n\n00:00:00.000 --> 00:02:00.000\n$text\n".toByteArray(),
    )
}
