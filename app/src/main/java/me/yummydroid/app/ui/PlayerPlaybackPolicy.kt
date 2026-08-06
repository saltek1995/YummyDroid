package me.yummydroid.app.ui

import androidx.media3.common.C

internal fun resolvedPlaybackDurationMs(
    playerDurationMs: Long,
    contentDurationMs: Long,
    metadataDurationSeconds: Int?,
): Long? {
    return playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: contentDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: metadataDurationSeconds
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(1_000L)
}

internal fun isPlaybackEndCloseOrBuffered(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long?,
    switchFallbackThresholdMs: Long,
): Boolean {
    val duration = durationMs?.takeIf { it > 0L } ?: return false
    val safePositionMs = positionMs.coerceAtLeast(0L)
    val safeBufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L)
    val endIgnoreWindowMs = maxOf(PLAYBACK_BUFFER_END_IGNORE_MS, switchFallbackThresholdMs * 2)
    val remainingMs = (duration - safePositionMs).coerceAtLeast(0L)
    return remainingMs <= endIgnoreWindowMs ||
        safeBufferedPositionMs >= duration - PLAYBACK_BUFFER_END_EPSILON_MS
}
