package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackOptions
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.FallbackSelection
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo

@OptIn(UnstableApi::class)
internal fun Throwable.isPlaybackHttpRestricted(): Boolean {
    var error: Throwable? = this
    val visited = HashSet<Throwable>()
    while (error != null && visited.add(error)) {
        if (error is HttpDataSource.InvalidResponseCodeException &&
            (error.responseCode == 403 || error.responseCode == 429)
        ) return true
        error = error.cause
    }
    return false
}

/** Do not multiply a provider restriction into retries, alternate tracks or CDN locations. */
@OptIn(UnstableApi::class)
internal class PlaybackLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        if (loadErrorInfo.exception.isPlaybackHttpRestricted()) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: FallbackOptions,
        loadErrorInfo: LoadErrorInfo,
    ): FallbackSelection? {
        if (loadErrorInfo.exception.isPlaybackHttpRestricted()) return null
        return super.getFallbackSelectionFor(fallbackOptions, loadErrorInfo)
    }
}
