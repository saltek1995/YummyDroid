package me.yummydroid.app.data

import java.io.File

internal class DirectDownloadSession(
    val target: File,
    val qualityTitle: String,
    val voiceTitle: String,
    val temp: File = target.partFile(),
    private val startedAtMs: Long = System.currentTimeMillis(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private var downloadedBytes = 0L

    fun progressAfterRead(
        readBytes: Long,
        readTotal: Long,
        totalBytes: Long,
    ): DownloadProgressInfo {
        downloadedBytes += readBytes
        val elapsedMs = (nowMs() - startedAtMs).coerceAtLeast(1L)
        val speed = (downloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
        val fraction = if (totalBytes > 0L) {
            (readTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        return DownloadProgressInfo(
            fraction = fraction,
            downloadedBytes = readTotal,
            totalBytes = totalBytes,
            bytesPerSecond = speed,
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        )
    }
}
