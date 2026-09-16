package me.yummydroid.app.data

import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

/** Inert provider metadata. Create one interceptor for each playback generation and share it. */
data class CvhMediaRequestRecovery(val primaryHost: String, val failoverHost: String) {
    fun createInterceptor(): Interceptor = CvhRecoveryInterceptor(primaryHost, failoverHost)
}

/** CVH's numeric CDN response distinguishes expiry from rate limits and context changes. */
class CvhMediaAccessException(val responseCode: Int, val reasonCode: Int, retryAtEpochMs: Long?) :
    PlaybackHttpException(if (reasonCode == 8) 429 else responseCode, retryAtEpochMs) {
    override val message: String get() = "CVH: ${when (reasonCode) {
        1 -> "media link expired"
        8 -> "request rate limited"
        10 -> "User-Agent changed"
        11 -> "Referer rejected"
        18 -> "IP address changed"
        21 -> "media host changed"
        else -> "media access denied"
    }} (HTTP $responseCode, code $reasonCode)"
}

private class CvhRecoveryInterceptor(
    private val primaryHost: String,
    private val failoverHost: String,
) : Interceptor {
    private val useFailover = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.method != "GET" || (!original.url.host.equals(primaryHost, ignoreCase = true) &&
                !original.url.host.equals(failoverHost, ignoreCase = true))) return chain.proceed(original)
        if (original.url.host.equals(failoverHost, ignoreCase = true)) {
            return checkAccessResponse(chain.proceed(original), chain)
        }
        // HttpUrl changes only the host; signed query, escaping, scheme, port and Range remain intact.
        val fallback = original.newBuilder().url(original.url.newBuilder().host(failoverHost).build()).build()
        if (useFailover.get()) return checkAccessResponse(chain.proceed(fallback), chain)
        val response = try {
            chain.proceed(original)
        } catch (failure: IOException) {
            if (!failure.canRecover(chain)) throw failure
            useFailover.set(true)
            return checkAccessResponse(chain.proceed(fallback), chain)
        }
        val now = System.currentTimeMillis()
        checkAccessResponse(response, chain)
        val hasCooldown = response.code == 429 ||
            (httpRetryAfterEpochMs(response.headers.toMultimap(), now)?.let { it > now } == true)
        if (hasCooldown || chain.call().isCanceled()) return response
        if (response.code in recoverableStatusCodes) {
            response.close()
            useFailover.set(true)
            return checkAccessResponse(chain.proceed(fallback), chain)
        }
        val body = response.body ?: return response
        return response.newBuilder().body(object : ResponseBody() {
            private val monitoredSource: BufferedSource by lazy {
                object : ForwardingSource(body.source()) {
                    private var received = 0L
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        try {
                            val count = super.read(sink, byteCount)
                            if (count == -1L && body.contentLength() >= 0L && received < body.contentLength()) {
                                throw EOFException("Truncated CVH response body")
                            }
                            if (count > 0L) received += count
                            return count
                        } catch (failure: IOException) {
                            // The consumer owns partial bytes and its next Range request. Do not replay a body.
                            val truncated = failure is ProtocolException &&
                                failure.message == "unexpected end of stream"
                            if (failure.canRecover(chain, truncatedBody = truncated)) useFailover.set(true)
                            throw failure
                        }
                    }
                }.buffer()
            }

            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source(): BufferedSource = monitoredSource
        }).build()
    }

    private fun checkAccessResponse(response: Response, chain: Interceptor.Chain): Response {
        val now = System.currentTimeMillis()
        if (!response.isSuccessful && !chain.call().isCanceled()) {
            val reason = runCatching {
                response.peekBody(65).bytes().takeIf { it.size <= 64 }?.decodeToString()?.trim()
                    ?.takeIf { it in accessFailureTokens }?.toIntOrNull()
            }.getOrNull()
            if (reason in accessFailureCodes) {
                val deadline = httpRetryAfterEpochMs(response.headers.toMultimap(), now)
                response.close()
                throw CvhMediaAccessException(response.code, reason!!, deadline)
            }
        }
        return response
    }

    private fun IOException.canRecover(chain: Interceptor.Chain, truncatedBody: Boolean = false): Boolean {
        if (chain.call().isCanceled() || Thread.currentThread().isInterrupted) return false
        return generateSequence<Throwable>(this) { it.cause }.take(16).none {
            it is SSLException || it is CertificateException || it is CancellationException || it is InterruptedException ||
                (it is ProtocolException && !truncatedBody) ||
                (it is InterruptedIOException && it !is SocketTimeoutException)
        }
    }

    private companion object {
        val recoverableStatusCodes = setOf(403, 404, 408, 500, 502, 503, 504)
        val accessFailureCodes = setOf(1, 8, 10, 11, 18, 21)
        val accessFailureTokens = accessFailureCodes.map(Int::toString).toSet()
    }
}
