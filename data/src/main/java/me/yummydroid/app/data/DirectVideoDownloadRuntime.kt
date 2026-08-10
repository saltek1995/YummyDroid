package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.delay
import okhttp3.Request
import okhttp3.Response

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
    val session = DirectDownloadSession(
        target = target,
        qualityTitle = qualityTitle,
        voiceTitle = voiceTitle,
    )
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

private suspend fun YummyAnimeRepository.downloadDirectVideoAttempt(
    session: DirectDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): Boolean {
    check(!isCancelled()) { "Download cancelled" }
    val existingBytes = session.temp.length().coerceAtLeast(0L)
    val request = stream.directDownloadRequest(existingBytes)
    downloadClient.newCall(request).execute().use { response ->
        if (existingBytes > 0L && response.code == 416) {
            session.temp.moveCompleteTo(session.target)
            return false
        }
        response.writeDirectDownloadBody(
            session = session,
            existingBytes = existingBytes,
            onProgress = onProgress,
            isCancelled = isCancelled,
            bandwidthLimiter = bandwidthLimiter,
        )
    }
    session.temp.moveCompleteTo(session.target)
    return true
}

private fun ResolvedVideoStream.directDownloadRequest(existingBytes: Long): Request {
    val builder = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .header("Accept-Encoding", "identity")
    if (existingBytes > 0L) {
        builder.header("Range", "bytes=$existingBytes-")
    }
    return builder.build()
}

private suspend fun Response.writeDirectDownloadBody(
    session: DirectDownloadSession,
    existingBytes: Long,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
) {
    if (!isSuccessful) throw IOException("Download HTTP $code")
    val responseBody = body ?: throw IOException("Empty download body")
    val canAppend = existingBytes > 0L && code == 206
    if (existingBytes > 0L && !canAppend) {
        session.temp.delete()
    }
    val startingBytes = if (canAppend) existingBytes else 0L
    val totalBytes = header("Content-Range")?.parseContentRangeTotal()
        ?: responseBody.contentLength()
            .takeIf { it > 0L }
            ?.let { length -> if (canAppend) startingBytes + length else length }
        ?: -1L
    FileOutputStream(session.temp, canAppend).use { output ->
        responseBody.byteStream().use { input ->
            input.copyDirectDownloadTo(
                output = output,
                session = session,
                startingBytes = startingBytes,
                totalBytes = totalBytes,
                onProgress = onProgress,
                isCancelled = isCancelled,
                bandwidthLimiter = bandwidthLimiter,
            )
        }
    }
    if (totalBytes > 0L && session.temp.length().coerceAtLeast(0L) < totalBytes) {
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

private class DirectDownloadSession(
    val target: File,
    val qualityTitle: String,
    val voiceTitle: String,
    val temp: File = target.partFile(),
    private val startedAtMs: Long = System.currentTimeMillis(),
) {
    private var downloadedBytes = 0L

    fun progressAfterRead(
        readBytes: Long,
        readTotal: Long,
        totalBytes: Long,
    ): DownloadProgressInfo {
        downloadedBytes += readBytes
        val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
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
