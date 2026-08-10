package me.yummydroid.app.data

import okhttp3.Request

internal suspend fun YummyAnimeRepository.downloadDirectVideoAttempt(
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

internal fun ResolvedVideoStream.directDownloadRequest(existingBytes: Long): Request {
    val builder = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .header("Accept-Encoding", "identity")
    if (existingBytes > 0L) {
        builder.header("Range", "bytes=$existingBytes-")
    }
    return builder.build()
}
