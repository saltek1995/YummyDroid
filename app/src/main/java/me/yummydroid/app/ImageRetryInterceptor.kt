package me.yummydroid.app

import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import java.io.IOException
import java.time.Clock
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import me.yummydroid.app.data.httpRetryAfterEpochMs

internal const val IMAGE_RETRY_ENABLED_PARAMETER = "yummydroid.imageRetryEnabled"

/** Retries one visible image request without restarting its Compose painter or lifecycle. */
internal class ImageRetryInterceptor(
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val clock: Clock = Clock.systemUTC(),
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        currentCoroutineContext().ensureActive()
        var result = chain.proceed(request)
        if (!request.canRetry()) return result

        // Three attempts in total. Prefetch deliberately opts out to avoid another retry cycle.
        for (retry in 1..2) {
            val failure = (result as? ErrorResult)?.throwable ?: return result
            if (!failure.isRetryable()) return result
            pause(maxOf(3_000L * retry, failure.retryAfterMillis()))
            currentCoroutineContext().ensureActive()
            // Failed I/O may have left unusable bytes on disk. Fetch afresh while
            // preserving cache keys and allowing a successful response to repair that entry.
            val retryRequest = request.newBuilder()
                .diskCachePolicy(if (request.diskCachePolicy.writeEnabled) CachePolicy.WRITE_ONLY else CachePolicy.DISABLED)
                .build()
            result = chain.proceed(retryRequest)
        }
        return result
    }

    private fun ImageRequest.canRetry(): Boolean {
        val url = data.toString()
        return (url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)) &&
            networkCachePolicy.readEnabled && parameters.entry(IMAGE_RETRY_ENABLED_PARAMETER)?.value != false
    }

    private fun Throwable.isRetryable(): Boolean = when (this) {
        is HttpException -> response.code in setOf(408, 429) || response.code in 500..599
        is SSLHandshakeException, is SSLPeerUnverifiedException -> false
        is IOException -> true
        else -> false
    }

    private fun Throwable.retryAfterMillis(): Long {
        val response = (this as? HttpException)?.response ?: return 0L
        val now = clock.millis().coerceAtLeast(0L)
        return httpRetryAfterEpochMs(response.headers.toMultimap(), now)?.let { it - now } ?: 0L
    }
}
