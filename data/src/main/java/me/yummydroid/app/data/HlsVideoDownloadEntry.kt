package me.yummydroid.app.data

import java.io.File

internal suspend fun YummyAnimeRepository.downloadHlsAsSingleVideoFile(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    val resolved = resolveHlsDownloadPlan(stream, preferredQuality)
    val startedAtMs = System.currentTimeMillis()
    val voiceTitle = video.downloadVoiceTitle()
    val target = storage.targetFile(
        video,
        resolved.plan.outputExtension,
        resolved.qualityTitle.ifBlank { "auto" },
    )
    if (target.isCompletedDownloadFile()) {
        onProgress(target.completedDownloadProgress(target.downloadQualityTitle(), voiceTitle))
        return target
    }

    val session = HlsDownloadSession(
        target = target,
        plan = resolved.plan,
        qualityTitle = resolved.qualityTitle,
        voiceTitle = voiceTitle,
        startedAtMs = startedAtMs,
    )
    session.prepareResume()
    try {
        writeHlsDownload(session, stream, onProgress, isCancelled, bandwidthLimiter)
        session.complete()
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
            if (deletePartialOnCancel()) session.deletePartial()
        }
        throw throwable
    }

    onProgress(target.completedDownloadProgress(resolved.qualityTitle, voiceTitle))
    return target
}
