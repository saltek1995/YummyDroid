package me.yummydroid.app.data

import java.io.File
import kotlinx.coroutines.delay

internal suspend fun YummyAnimeRepository.downloadDirectVideoRuntime(
    target: File,
    stream: ResolvedVideoStream,
    qualityTitle: String,
    voiceTitle: String,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    val session = DirectDownloadSession(target, qualityTitle, voiceTitle)
    val shouldReportCompletion = downloadDirectVideoWithRetries(
        session = session,
        stream = stream,
        onProgress = onProgress,
        isCancelled = isCancelled,
        deletePartialOnCancel = deletePartialOnCancel,
        bandwidthLimiter = bandwidthLimiter,
    )
    if (shouldReportCompletion) {
        onProgress(target.completedDownloadProgress(qualityTitle, voiceTitle))
    }
    return target
}

private suspend fun YummyAnimeRepository.downloadDirectVideoWithRetries(
    session: DirectDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): Boolean {
    var attempt = 0
    while (true) {
        try {
            return downloadDirectVideoAttempt(session, stream, onProgress, isCancelled, bandwidthLimiter)
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
                if (deletePartialOnCancel()) session.temp.delete()
                throw throwable
            }
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
}
