package me.yummydroid.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Size
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = android.app.Application::class)
@OptIn(coil.annotation.ExperimentalCoilApi::class)
class ImageRetryInterceptorTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun firstSuccessDoesNotPauseOrRetry() = runBlocking {
        val pauses = mutableListOf<Long>()
        val request = request("https://images.example/cover.png")
        val success = success(request)
        val chain = FakeChain(request, success)

        val result = ImageRetryInterceptor(pause = pauses::add).intercept(chain)

        assertIs<SuccessResult>(result)
        assertEquals(1, chain.requests.size)
        assertTrue(pauses.isEmpty())
    }

    @Test
    fun ioFailuresUseBoundedExponentialRetries() = runBlocking {
        val pauses = mutableListOf<Long>()
        val request = request("https://images.example/cover.png")
        val chain = FakeChain(request, error(request, IOException("offline")), error(request, IOException("offline")), success(request))

        val result = ImageRetryInterceptor(pause = pauses::add).intercept(chain)

        assertIs<SuccessResult>(result)
        assertEquals(3, chain.requests.size)
        assertEquals(listOf(3_000L, 6_000L), pauses)
    }

    @Test
    fun totalFailureStopsAfterThreeAttemptsAndSecondSuccessStopsAfterTwo() = runBlocking {
        val request = request("https://images.example/cover.png")
        val failed = FakeChain(request, error(request, IOException()), error(request, IOException()), error(request, IOException()))
        ImageRetryInterceptor(pause = {}).intercept(failed)
        assertEquals(3, failed.requests.size)

        val recovered = FakeChain(request, error(request, IOException()), success(request), success(request))
        assertIs<SuccessResult>(ImageRetryInterceptor(pause = {}).intercept(recovered))
        assertEquals(2, recovered.requests.size)
    }

    @Test
    fun permanentAndTlsFailuresDoNotRetry() = runBlocking {
        val request = request("https://images.example/cover.png")
        listOf(
            error(request, http(404)), error(request, http(403)), error(request, SSLHandshakeException("tls")),
            error(request, SSLPeerUnverifiedException("peer")), error(request, IllegalArgumentException("bad data")),
        ).forEach { failure ->
            val chain = FakeChain(request, failure, success(request))
            ImageRetryInterceptor(pause = {}).intercept(chain)
            assertEquals(1, chain.requests.size, "${failure.throwable} must not retry")
        }
    }

    @Test
    fun transientHttpFailuresRetryAndHonorRetryAfter() = runBlocking {
        val request = request("https://images.example/cover.png")
        for (code in listOf(408, 429, 503)) {
            val chain = FakeChain(request, error(request, http(code)), success(request))
            ImageRetryInterceptor(pause = {}).intercept(chain)
            assertEquals(2, chain.requests.size, "HTTP $code should retry")
        }

        val pauses = mutableListOf<Long>()
        val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        val retryAfter = FakeChain(request, error(request, http(429, "12")), success(request))
        ImageRetryInterceptor(pause = pauses::add, clock = clock).intercept(retryAfter)
        assertEquals(listOf(12_000L), pauses)

        pauses.clear()
        val date = "Thu, 01 Jan 2026 00:00:09 GMT"
        val datedRetry = FakeChain(request, error(request, http(503, date)), success(request))
        ImageRetryInterceptor(pause = pauses::add, clock = clock).intercept(datedRetry)
        assertEquals(listOf(9_000L), pauses)

        pauses.clear()
        val malformed = FakeChain(request, error(request, http(503, "not a date")), success(request))
        ImageRetryInterceptor(pause = pauses::add, clock = clock).intercept(malformed)
        assertEquals(listOf(3_000L), pauses)
    }

    @Test
    fun disabledNetworkLocalDataAndPrefetchOptOutDoNotRetry() = runBlocking {
        val requests = listOf(
            request("https://images.example/cache-only.png", networkPolicy = CachePolicy.DISABLED),
            request("file:///storage/emulated/0/cover.png"),
            request("data:image/png;base64,AA=="),
            request("https://images.example/prefetch.png", retryEnabled = false),
        )
        requests.forEach { request ->
            val chain = FakeChain(request, error(request, IOException()), success(request))
            ImageRetryInterceptor(pause = {}).intercept(chain)
            assertEquals(1, chain.requests.size, "${request.data} must not retry")
        }
    }

    @Test
    fun cancellationDuringSuspendedPausePreventsTheNextAttempt() = runBlocking {
        val request = request("https://images.example/cover.png")
        val chain = FakeChain(request, error(request, IOException()), success(request))
        val pauseStarted = CompletableDeferred<Unit>()
        val job = launch {
            ImageRetryInterceptor(pause = {
                pauseStarted.complete(Unit)
                suspendCancellableCoroutine { }
            }).intercept(chain)
        }

        pauseStarted.await()
        job.cancel(CancellationException("test cancellation"))
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, chain.requests.size)
    }

    @Test
    fun retryRequestsPreserveIdentityAndAvoidDiskReads() = runBlocking {
        for (policy in CachePolicy.entries) {
            val request = request("https://images.example/cover.png", diskPolicy = policy)
            val chain = FakeChain(request, error(request, IOException()), success(request))
            ImageRetryInterceptor(pause = {}).intercept(chain)
            val retried = chain.requests[1]
            assertEquals(request.data, retried.data)
            assertEquals(request.memoryCacheKey, retried.memoryCacheKey)
            assertEquals(request.networkCachePolicy, retried.networkCachePolicy)
            assertFalse(retried.diskCachePolicy.readEnabled)
            assertEquals(policy.writeEnabled, retried.diskCachePolicy.writeEnabled)
        }
    }

    @Test
    fun realCoilLoaderRetriesTwo503ResponsesThenDecodesPng() {
        val pauses = mutableListOf<Long>()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody(Buffer().write(png())))
            server.start()
            val loader = ImageLoader.Builder(context)
                .dispatcher(Dispatchers.IO)
                .interceptorDispatcher(Dispatchers.IO)
                .components { add(ImageRetryInterceptor(pause = pauses::add)) }
                .build()
            try {
                val request = ImageRequest.Builder(context).data(server.url("/cover.png").toString())
                    .memoryCacheKey("integration-cover")
                    .build()
                val result = loadWhilePumpingMainLooper(loader, request)
                if (result is ErrorResult) throw AssertionError("Image load failed after ${server.requestCount} HTTP requests", result.throwable)
                assertIs<SuccessResult>(result)
                assertEquals(3, server.requestCount)
                assertEquals(listOf(3_000L, 6_000L), pauses)
                assertIs<SuccessResult>(loadWhilePumpingMainLooper(loader, request))
                assertEquals(3, server.requestCount, "Cached success must not make a fourth request")
                assertEquals(listOf(3_000L, 6_000L), pauses, "Cached success must not pause")
                val offline = ImageRequest.Builder(context).data(server.url("/offline.png").toString())
                    .networkCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                assertIs<ErrorResult>(loadWhilePumpingMainLooper(loader, offline))
                assertEquals(3, server.requestCount, "An offline cache miss must not contact the server")
                assertEquals(listOf(3_000L, 6_000L), pauses)
            } finally {
                loader.shutdown()
            }
        }
    }

    private fun loadWhilePumpingMainLooper(loader: ImageLoader, request: ImageRequest): ImageResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val result = scope.async { loader.execute(request) }
        val deadline = System.nanoTime() + 10_000_000_000L
        try {
            // Coil delivers lifecycle events on Android's main looper; blocking that looper
            // with runBlocking would prevent a real network request from completing.
            while (!result.isCompleted && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertTrue(result.isCompleted, "Image load must complete within the test deadline")
            return runBlocking { result.await() }
        } finally {
            scope.cancel()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun request(
        data: String,
        networkPolicy: CachePolicy = CachePolicy.ENABLED,
        diskPolicy: CachePolicy = CachePolicy.ENABLED,
        retryEnabled: Boolean = true,
    ): ImageRequest = ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey("cover-key")
        .networkCachePolicy(networkPolicy)
        .diskCachePolicy(diskPolicy)
        .setParameter(IMAGE_RETRY_ENABLED_PARAMETER, retryEnabled, memoryCacheKey = null)
        .build()

    private fun success(request: ImageRequest) = SuccessResult(
        drawable = ColorDrawable(Color.BLACK),
        request = request,
        dataSource = DataSource.MEMORY_CACHE,
    )

    private fun error(request: ImageRequest, throwable: Throwable) = ErrorResult(null, request, throwable)

    private fun http(code: Int, retryAfter: String? = null): HttpException {
        val response = Response.Builder()
            .request(Request.Builder().url("https://images.example/cover.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
            .build()
        return HttpException(response)
    }

    private fun png(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLACK)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        } finally {
            bitmap.recycle()
        }
        output.toByteArray()
    }

    private class FakeChain(
        override val request: ImageRequest,
        vararg results: ImageResult,
    ) : Interceptor.Chain {
        private val results = ArrayDeque(results.asList())
        val requests = mutableListOf<ImageRequest>()
        override val size: Size = Size.ORIGINAL

        override suspend fun proceed(request: ImageRequest): ImageResult {
            requests += request
            return results.removeFirst()
        }

        override fun withRequest(request: ImageRequest): Interceptor.Chain = FakeChain(request, *results.toTypedArray())

        override fun withSize(size: Size): Interceptor.Chain = this
    }
}
