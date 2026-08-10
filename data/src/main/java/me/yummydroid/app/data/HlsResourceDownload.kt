package me.yummydroid.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.delay
import okhttp3.Request

internal suspend fun YummyAnimeRepository.downloadUrlBytes(
    url: String,
    headers: Map<String, String>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    var attempt = 0
    while (true) {
        try {
            return downloadUrlBytesOnce(url, headers, bandwidthLimiter)
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
}

private suspend fun YummyAnimeRepository.downloadUrlBytesOnce(
    url: String,
    headers: Map<String, String>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        val body = response.body ?: throw IOException("Empty HLS resource")
        body.byteStream().use { input ->
            input.readBytes(bandwidthLimiter)
        }
    }
}

private suspend fun InputStream.readBytes(
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray = ByteArrayOutputStream().use { output ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        bandwidthLimiter.throttle(read.toLong())
        output.write(buffer, 0, read)
    }
    output.toByteArray()
}
