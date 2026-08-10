package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.delay
import okhttp3.Request

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
    if (target.isCompletedDownloadFile()) {
        val voiceTitle = video.downloadVoiceTitle()
        onProgress(
            DownloadProgressInfo(
                fraction = 1f,
                downloadedBytes = target.length().coerceAtLeast(0L),
                totalBytes = target.length().coerceAtLeast(0L),
                bytesPerSecond = 0L,
                qualityTitle = target.downloadQualityTitle(),
                voiceTitle = voiceTitle,
            ),
        )
        return target
    }
    val temp = target.partFile()
    val startedAtMs = System.currentTimeMillis()
    val voiceTitle = video.downloadVoiceTitle()
    var sessionDownloadedBytes = 0L
    var attempt = 0

    while (true) {
        try {
            check(!isCancelled()) { "Download cancelled" }
            val existingBytes = temp.length().coerceAtLeast(0L)
            val requestBuilder = Request.Builder()
                .url(stream.url)
                .headers(stream.headers.toOkHttpHeaders())
                .header("Accept-Encoding", "identity")
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            downloadClient.newCall(requestBuilder.build()).execute().use { response ->
                if (existingBytes > 0L && response.code == 416) {
                    temp.moveCompleteTo(target)
                    return target
                }
                if (!response.isSuccessful) {
                    throw IOException("Download HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Empty download body")
                val canAppend = existingBytes > 0L && response.code == 206
                if (existingBytes > 0L && !canAppend) {
                    temp.delete()
                }
                val startingBytes = if (canAppend) existingBytes else 0L
                val totalBytes = response.header("Content-Range")?.parseContentRangeTotal()
                    ?: body.contentLength()
                        .takeIf { it > 0L }
                        ?.let { length -> if (canAppend) startingBytes + length else length }
                    ?: -1L
                FileOutputStream(temp, canAppend).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = startingBytes
                        while (true) {
                            check(!isCancelled()) { "Download cancelled" }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bandwidthLimiter.throttle(read.toLong())
                            output.write(buffer, 0, read)
                            readTotal += read
                            sessionDownloadedBytes += read.toLong()
                            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                            val speed = (sessionDownloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
                            val fraction = if (totalBytes > 0L) {
                                (readTotal.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            onProgress(
                                DownloadProgressInfo(
                                    fraction = fraction,
                                    downloadedBytes = readTotal,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speed,
                                    qualityTitle = qualityTitle,
                                    voiceTitle = voiceTitle,
                                ),
                            )
                        }
                    }
                }
                if (totalBytes > 0L && temp.length().coerceAtLeast(0L) < totalBytes) {
                    throw IOException("Download incomplete")
                }
            }
            temp.moveCompleteTo(target)
            break
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
                if (deletePartialOnCancel()) temp.delete()
                throw throwable
            }
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
    onProgress(
        DownloadProgressInfo(
            fraction = 1f,
            downloadedBytes = target.length().coerceAtLeast(0L),
            totalBytes = target.length().coerceAtLeast(0L),
            bytesPerSecond = 0L,
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        ),
    )
    return target
}
