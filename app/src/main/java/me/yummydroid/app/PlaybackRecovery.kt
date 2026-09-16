package me.yummydroid.app

import me.yummydroid.app.data.PlaybackHttpException
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.PlaybackProvider
import me.yummydroid.app.data.playbackProvider
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey

/** One fresh resolution per failure burst. Healthy advancing playback replenishes the budget. */
internal class PlaybackRecovery(private val clockMs: () -> Long) {
    private val attempted = mutableSetOf<String>()
    private val retryDeadlines = mutableMapOf<String, Long>()

    private data class Progress(val atMs: Long, val positionMs: Long, val healthyMs: Long)
    private val progress = mutableMapOf<String, Progress>()

    fun resetAttempts() {
        attempted.clear()
        progress.clear()
    }

    fun observeProgress(video: VideoVariant, positionMs: Long) {
        val key = key(video)
        if (key !in attempted) return
        val now = clockMs()
        val previous = progress[key]
        val elapsed = previous?.let { now - it.atMs } ?: 0L
        val advanced = previous?.let { positionMs - it.positionMs } ?: 0L
        // Saves arrive every 15 seconds. Gaps, pauses and seeks do not prove health.
        val advancing = elapsed in 1L..30_000L && advanced > 0L && advanced <= elapsed * 3L + 2_000L
        val healthyMs = if (advancing) previous!!.healthyMs + minOf(elapsed, advanced) else 0L
        if (healthyMs >= 60_000L) {
            attempted.remove(key)
            progress.remove(key)
        } else {
            progress[key] = Progress(now, positionMs, healthyMs)
        }
    }

    fun remainingCooldownMs(video: VideoVariant): Long =
        ((retryDeadlines[video.sourceSelectionKey] ?: 0L) - clockMs()).coerceAtLeast(0L)

    fun cooldownMessage(video: VideoVariant): String? =
        remainingCooldownMs(video).takeIf { it > 0L }?.let {
            "Источник временно ограничил запросы. Повторите через ${it / 1_000L + if (it % 1_000L == 0L) 0L else 1L} с."
        }

    fun consumeAttempt(video: VideoVariant, failure: PlaybackFailure): Long? {
        if (video.isOfflineAvailable) return null
        val key = key(video)
        progress.remove(key)
        val cooldownKey = video.sourceSelectionKey
        if (failure.httpStatusCode == 429 || failure.retryAtEpochMs != null) {
            val deadline = failure.retryAtEpochMs
                ?: if (failure.httpStatusCode == 429) clockMs() + 30_000L else null
            if (deadline != null) retryDeadlines[cooldownKey] = maxOf(retryDeadlines[cooldownKey] ?: 0L, deadline)
        }
        if (failure.httpStatusCode == 429) return null
        if (video.playbackProvider() == PlaybackProvider.Unknown) return null
        if (failure.kind != PlaybackFailureKind.SourceUnavailable &&
            failure.kind != PlaybackFailureKind.BufferingTimeout) return null
        val waitMs = maxOf(2_000L, remainingCooldownMs(video))
        if (waitMs > 30_000L || !attempted.add(key)) return null
        return waitMs
    }

    private fun key(video: VideoVariant): String =
        listOf(video.animeId, video.matchingEpisodeKey, video.matchingVoiceKey, video.sourceSelectionKey).joinToString("|")

}

internal fun Throwable.playbackHttpFailure(): PlaybackFailure? {
    val http = generateSequence(this) { it.cause }.take(16)
        .filterIsInstance<PlaybackHttpException>().firstOrNull() ?: return null
    return PlaybackFailure(PlaybackFailureKind.SourceUnavailable,
        message = http.message, httpStatusCode = http.statusCode, retryAtEpochMs = http.retryAtEpochMs)
}
