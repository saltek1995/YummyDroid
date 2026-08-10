package me.yummydroid.app.data

import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import okhttp3.Response

internal suspend fun Response.writeDirectDownloadBody(
    session: DirectDownloadSession,
    existingBytes: Long,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
) {
    if (!isSuccessful) throw IOException("Download HTTP $code")
    val responseBody = body ?: throw IOException("Empty download body")
    val plan = directDownloadBodyPlan(
        existingBytes = existingBytes,
        responseCode = code,
        contentRangeTotal = header("Content-Range")?.parseContentRangeTotal(),
        contentLength = responseBody.contentLength(),
    )
    if (existingBytes > 0L && !plan.canAppend) session.temp.delete()
    FileOutputStream(session.temp, plan.canAppend).use { output ->
        responseBody.byteStream().use { input ->
            input.copyDirectDownloadTo(
                output = output,
                session = session,
                startingBytes = plan.startingBytes,
                totalBytes = plan.totalBytes,
                onProgress = onProgress,
                isCancelled = isCancelled,
                bandwidthLimiter = bandwidthLimiter,
            )
        }
    }
    if (plan.totalBytes > 0L && session.temp.length().coerceAtLeast(0L) < plan.totalBytes) {
        throw IOException("Download incomplete")
    }
}

private suspend fun InputStream.copyDirectDownloadTo(
    output: OutputStream,
    session: DirectDownloadSession,
    startingBytes: Long,
    totalBytes: Long,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var readTotal = startingBytes
    while (true) {
        check(!isCancelled()) { "Download cancelled" }
        val read = read(buffer)
        if (read <= 0) return
        bandwidthLimiter.throttle(read.toLong())
        output.write(buffer, 0, read)
        readTotal += read
        onProgress(session.progressAfterRead(read.toLong(), readTotal, totalBytes))
    }
}
