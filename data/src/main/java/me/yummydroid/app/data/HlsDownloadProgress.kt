package me.yummydroid.app.data

internal fun hlsSegmentDownloadProgress(
    nextSegmentIndex: Int,
    segmentCount: Int,
    downloadedBytes: Long,
    sessionDownloadedBytes: Long,
    elapsedMs: Long,
    qualityTitle: String,
    voiceTitle: String,
): DownloadProgressInfo {
    val speed = (sessionDownloadedBytes * 1000L / elapsedMs.coerceAtLeast(1L)).coerceAtLeast(0L)
    val fraction = if (segmentCount > 0) {
        (nextSegmentIndex.toFloat() / segmentCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return DownloadProgressInfo(
        fraction = fraction,
        downloadedBytes = downloadedBytes,
        totalBytes = -1L,
        bytesPerSecond = speed,
        qualityTitle = qualityTitle,
        voiceTitle = voiceTitle,
    )
}
