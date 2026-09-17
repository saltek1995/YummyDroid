package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.PlaybackHttpException
import me.yummydroid.app.data.PlaybackSessionExpiredException
import me.yummydroid.app.data.PlaybackSessionRestrictedException
import me.yummydroid.app.data.httpRetryAfterEpochMs

internal data class PlaybackHttpDetails(val statusCode: Int, val retryAtEpochMs: Long?)

@OptIn(UnstableApi::class)
internal fun Throwable.isPlaybackHttpRestricted(nowEpochMs: Long = System.currentTimeMillis()): Boolean = playbackHttpDetails(nowEpochMs)?.let {
    it.statusCode == 429 || (it.retryAtEpochMs != null && it.retryAtEpochMs > nowEpochMs)
} == true

@OptIn(UnstableApi::class)
internal fun Throwable.playbackHttpDetails(nowEpochMs: Long = System.currentTimeMillis()): PlaybackHttpDetails? {
    var error: Throwable? = this
    val visited = HashSet<Throwable>()
    var found: PlaybackHttpDetails? = null
    while (error != null && visited.add(error)) {
        val details = when (error) {
            is HttpDataSource.InvalidResponseCodeException -> PlaybackHttpDetails(error.responseCode,
                httpRetryAfterEpochMs(error.headerFields, nowEpochMs))
            is PlaybackHttpException -> PlaybackHttpDetails(error.statusCode, error.retryAtEpochMs)
            is PlaybackSessionRestrictedException -> PlaybackHttpDetails(error.statusCode, error.retryAtEpochMs)
            else -> null
        }
        if (details != null) {
            val strongestDeadline = listOfNotNull(found?.retryAtEpochMs, details.retryAtEpochMs).maxOrNull()
            val status = when {
                found?.statusCode == 429 || details.statusCode == 429 -> 429
                found == null || (details.retryAtEpochMs ?: Long.MIN_VALUE) >
                    (found.retryAtEpochMs ?: Long.MIN_VALUE) -> details.statusCode
                else -> found.statusCode
            }
            found = PlaybackHttpDetails(status, strongestDeadline)
        }
        error = error.cause
    }
    return found
}

internal fun Throwable.isTerminalPlaybackSessionFailure(): Boolean =
    generateSequence(this) { it.cause }.take(16).any {
        it is PlaybackSessionExpiredException || it is PlaybackSessionRestrictedException ||
            it is me.yummydroid.app.data.CvhMediaAccessException
    }

/** Keep buffered loads recoverable with spaced retries; never retry an explicit rate limit. */
@OptIn(UnstableApi::class)
internal class PlaybackLoadErrorHandlingPolicy(
    private val provider: PlaybackProvider = PlaybackProvider.Unknown,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        if (loadErrorInfo.exception.isTerminalPlaybackSessionFailure()) return C.TIME_UNSET
        val now = clockMs()
        val details = loadErrorInfo.exception.playbackHttpDetails(now)
        if (details?.statusCode == 429) return C.TIME_UNSET
        val deadline = details?.retryAtEpochMs
        if (deadline != null && deadline > now) {
            // Compare before subtracting: untrusted absolute deadlines may span the Long range.
            if (loadErrorInfo.errorCount !in 1..2 ||
                (now <= Long.MAX_VALUE - 30_000L && deadline > now + 30_000L)) return C.TIME_UNSET
            return maxOf(deadline - now, super.getRetryDelayMsFor(loadErrorInfo))
        }
        if (details?.statusCode == 403 && provider != PlaybackProvider.Unknown) {
            // TIME_UNSET permanently stops the loader even while playable samples remain.
            // Let Media3 propagate persistent failures when those samples run out instead.
            // Preserve its non-retryable errors and avoid immediate repeated CDN requests.
            val delay = super.getRetryDelayMsFor(loadErrorInfo)
            return if (delay == C.TIME_UNSET) C.TIME_UNSET else maxOf(2_000L, delay)
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: FallbackOptions,
        loadErrorInfo: LoadErrorInfo,
    ): FallbackSelection? {
        val now = clockMs()
        val details = loadErrorInfo.exception.playbackHttpDetails(now)
        if (loadErrorInfo.exception.isTerminalPlaybackSessionFailure() || details?.statusCode == 429 ||
            (details?.retryAtEpochMs != null && details.retryAtEpochMs > now) ||
            (details?.statusCode == 403 && provider != PlaybackProvider.Unknown)) return null
        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
    }
}
