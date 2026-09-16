package me.yummydroid.app.data

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

internal class SourceHttpRestricted(val statusCode: Int) : IOException("Source returned HTTP $statusCode")

/** Once restricted, stop the rest of this discovery session, including WebView subrequests. */
internal class PlaybackResolveRequestPolicy(
    private val parent: PlaybackResolveRequestPolicy? = null,
) : HttpRequestPolicy() {
    @Volatile private var restriction: SourceHttpRestricted? = null
    private val subtitles by lazy { PlaybackResolveRequestPolicy(this) }
    val mediaPolicy: PlaybackResolveRequestPolicy get() = parent ?: this
    fun subtitlePolicy(): PlaybackResolveRequestPolicy = if (parent == null) subtitles else this

    override fun beforeRequest() {
        parent?.beforeRequest()
        restriction?.let { throw it }
    }

    override fun onResponse(statusCode: Int) {
        if (statusCode == 403 || statusCode == 429) {
            val failure = SourceHttpRestricted(statusCode)
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
