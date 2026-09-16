package me.yummydroid.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal class SourceHttpRestricted(statusCode: Int, retryAtEpochMs: Long? = null) :
    PlaybackHttpException(statusCode, retryAtEpochMs)

/** Once restricted, stop the rest of this discovery session, including WebView subrequests. */
internal class PlaybackResolveRequestPolicy(
    private val parent: PlaybackResolveRequestPolicy? = null,
    private val stopOnForbidden: Boolean = true,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : HttpRequestPolicy() {
    @Volatile private var restriction: SourceHttpRestricted? = null
    private val subtitles by lazy { PlaybackResolveRequestPolicy(this, clockMs = clockMs) }
    val mediaPolicy: PlaybackResolveRequestPolicy get() = parent ?: this
    fun subtitlePolicy(): PlaybackResolveRequestPolicy = if (parent == null) subtitles else this

    override fun beforeRequest() {
        parent?.beforeRequest()
        restriction?.let { throw it }
    }

    override fun onResponse(statusCode: Int) {
        onResponse(statusCode, emptyMap())
    }

    override fun onResponse(statusCode: Int, headers: Map<String, List<String>>) {
        val now = clockMs()
        val retryAt = httpRetryAfterEpochMs(headers, now)
        val hasFutureDeadline = retryAt != null && retryAt > now
        if ((statusCode == 403 && (stopOnForbidden || hasFutureDeadline)) || statusCode == 429 ||
            (statusCode == 503 && hasFutureDeadline)) {
            val failure = SourceHttpRestricted(statusCode, retryAt)
            restriction = failure
            throw failure
        }
    }
}

/** A rejected optional subtitle must not invalidate a captured media URL or trigger another fetch. */
internal suspend fun <T> withOptionalPlaybackSubtitles(
    fallback: T,
    networkRequired: Boolean = true,
    action: suspend () -> T,
): T {
    val policy = currentCoroutineContext()[HttpRequestPolicy] as? PlaybackResolveRequestPolicy
        ?: return action() // Download restrictions retain their existing behavior.
    policy.mediaPolicy.beforeRequest()
    if (!networkRequired) return action()
    val result = try {
        withContext(policy.subtitlePolicy()) {
            currentCoroutineContext()[HttpRequestPolicy]!!.beforeRequest()
            action()
        }
    } catch (_: SourceHttpRestricted) {
        fallback
    }
    policy.mediaPolicy.beforeRequest()
    return result
}
