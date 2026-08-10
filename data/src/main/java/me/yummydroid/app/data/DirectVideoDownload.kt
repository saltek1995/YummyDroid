package me.yummydroid.app.data

import java.io.File
import java.io.IOException

internal fun ResolvedVideoStream.qualityScore(preferredQuality: PreferredQuality): Int {
    return selectedVideoHeight
        ?.qualityPreferenceScore(preferredQuality)
        ?: sourceResolutionHeight().qualityPreferenceScore(preferredQuality)
}

internal fun ResolvedVideoStream.hasExactDownloadQuality(height: Int): Boolean {
    selectedVideoHeight?.let { return it == height }
    return maxVideoHeight == height ||
        availableQualities.any { it.height == height } ||
        url.detectDownloadQualityHeight() == height
}

internal fun ResolvedVideoStream.requireExactDownloadQuality(preferredQuality: PreferredQuality) {
    val height = preferredQuality.height ?: return
    if (!hasExactDownloadQuality(height)) {
        throw IOException("Source does not contain selected quality ${preferredQuality.title}")
    }
}

internal fun String.detectDownloadQualityHeight(): Int? {
    return Regex("""(?i)(?:^|[^\d])(\d{3,4})p(?:[^\d]|$)""")
        .find(substringBefore('?').substringBefore('#'))
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        .validVideoQualityHeight()
}

internal fun VideoVariant.downloadVoiceTitle(): String {
    return matchingDisplayVoiceTitle
}

internal fun VideoVariant.primaryOfflineFile(): OfflineVideoFile? {
    val preferredUrl = localPlaybackUrl.takeIf { it.isNotBlank() }
    return offlineFiles.firstOrNull { it.playbackUrl == preferredUrl }
        ?: offlineFiles.maxWithOrNull(compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes })
}

internal fun File.isCompletedDownloadFile(): Boolean {
    return exists() && length() >= 256L * 1024L && !extension.equals("m3u8", ignoreCase = true)
}

internal fun ResolvedVideoStream.isHlsStream(): Boolean {
    return mimeType?.contains("mpegurl", ignoreCase = true) == true ||
        url.contains(".m3u8", ignoreCase = true)
}

internal fun ResolvedVideoStream.isDashStream(): Boolean {
    return mimeType?.contains("dash", ignoreCase = true) == true ||
        url.contains(".mpd", ignoreCase = true)
}

internal suspend fun YummyAnimeRepository.downloadDirectVideo(
    storage: OfflineAnimeStorage,
    video: VideoVariant,
    stream: ResolvedVideoStream,
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): File {
    stream.requireExactDownloadQuality(preferredQuality)
    val qualityTitle = stream.qualityTitle()
    val target = storage.targetFile(video, stream.url.fileExtensionForDownload(), qualityTitle.ifBlank { "auto" })
    val voiceTitle = video.downloadVoiceTitle()
    if (target.isCompletedDownloadFile()) {
        onProgress(target.completedDownloadProgress(target.downloadQualityTitle(), voiceTitle))
        return target
    }
    return downloadDirectVideoRuntime(
        target = target,
        stream = stream,
        qualityTitle = qualityTitle,
        voiceTitle = voiceTitle,
        onProgress = onProgress,
        isCancelled = isCancelled,
        deletePartialOnCancel = deletePartialOnCancel,
        bandwidthLimiter = bandwidthLimiter,
    )
}

internal fun File.completedDownloadProgress(
    qualityTitle: String,
    voiceTitle: String,
): DownloadProgressInfo {
    val completedBytes = length().coerceAtLeast(0L)
    return DownloadProgressInfo(
        fraction = 1f,
        downloadedBytes = completedBytes,
        totalBytes = completedBytes,
        bytesPerSecond = 0L,
        qualityTitle = qualityTitle,
        voiceTitle = voiceTitle,
    )
}
