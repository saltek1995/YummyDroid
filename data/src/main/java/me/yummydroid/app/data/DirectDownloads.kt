package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// DirectDownloadBodyPlan
internal data class DirectDownloadBodyPlan(
    val canAppend: Boolean,
    val startingBytes: Long,
    val totalBytes: Long,
)

internal fun validateDirectDownloadRange(code: Int, contentRange: String?, existingBytes: Long): Boolean {
    if (code == 416) {
        val total = contentRange?.takeIf { it.startsWith("bytes */", ignoreCase = true) }?.parseContentRangeTotal()
        if (existingBytes <= 0L || total != existingBytes) throw IOException("Download range no longer matches the partial file")
        return true
    }
    if (code != 206) return false
    val range = DirectDownloadContentRangePattern.matchEntire(contentRange.orEmpty())
        ?: throw IOException("Missing or invalid download Content-Range")
    val start = range.groupValues[1].toLongOrNull()
    val end = range.groupValues[2].toLongOrNull()
    val total = range.groupValues[3].toLongOrNull()
    if (total == null && range.groupValues[3] != "*") throw IOException("Invalid download Content-Range size")
    if (start != existingBytes || end == null || end < existingBytes || (total != null && end >= total)) {
        throw IOException("Download response starts at an unexpected offset")
    }
    return false
}

private val DirectDownloadContentRangePattern = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""", RegexOption.IGNORE_CASE)

internal fun directDownloadBodyPlan(
    existingBytes: Long,
    responseCode: Int,
    contentRangeTotal: Long?,
    contentLength: Long,
): DirectDownloadBodyPlan {
    val canAppend = existingBytes > 0L && responseCode == 206
    val startingBytes = if (canAppend) existingBytes else 0L
    val totalBytes = contentRangeTotal
        ?: contentLength.takeIf { it > 0L }
            ?.let { length -> if (canAppend) startingBytes + length else length }
        ?: -1L
    return DirectDownloadBodyPlan(canAppend, startingBytes, totalBytes)
}

// DirectDownloadSession
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

// DirectVideoDownload
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
    return DirectDownloadQualityHeightPattern.find(substringBefore('?').substringBefore('#'))
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        .validVideoQualityHeight()
}

private val DirectDownloadQualityHeightPattern = Regex("""(?i)(?:^|[^\d])(\d{3,4})p(?:[^\d]|$)""")

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

// DirectVideoDownloadAttempt
internal suspend fun YummyAnimeRepository.downloadDirectVideoAttempt(
    session: DirectDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): Boolean = downloadClient.downloadDirectVideoAttempt(session, stream, onProgress, isCancelled, bandwidthLimiter)

internal suspend fun OkHttpClient.downloadDirectVideoAttempt(
    session: DirectDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
): Boolean {
    check(!isCancelled()) { "Download cancelled" }
    val identity = stream.directDownloadIdentity()
    val saved = session.readDirectDownloadIdentity()?.takeIf { it[0] == identity && session.temp.length() > 0L }
    if (saved == null) session.discardDirectDownloadPartial()
    val existingBytes = session.temp.length().coerceAtLeast(0L)
    val request = stream.directDownloadRequest(existingBytes, saved?.get(1))
    val shouldReportCompletion = withCancellableResponse(request) { response ->
        val alreadyComplete = try {
            if (existingBytes > 0L && (response.code == 206 || response.code == 416)) {
                if (response.header("ETag").strongDirectDownloadEtag() != saved?.get(1) ||
                    response.request.url.toString().directDownloadHash() != saved?.get(2)
                ) throw IOException("Download resource changed while resuming")
            }
            validateDirectDownloadRange(response.code, response.header("Content-Range"), existingBytes)
        } catch (failure: IOException) {
            session.discardDirectDownloadPartial()
            throw failure
        }
        if (alreadyComplete) {
            return@withCancellableResponse false
        }
        if (response.isSuccessful && (existingBytes == 0L || response.code != 206)) {
            // Remove old bytes before storing the new validator, including on a full If-Range response.
            session.discardDirectDownloadPartial()
            response.header("ETag").strongDirectDownloadEtag()?.let { etag ->
                session.directDownloadIdentityFile().writeText(
                    "$identity\n$etag\n${response.request.url.toString().directDownloadHash()}",
                )
            }
        }
        response.writeDirectDownloadBody(
            session = session,
            existingBytes = existingBytes,
            onProgress = onProgress,
            isCancelled = isCancelled,
            bandwidthLimiter = bandwidthLimiter,
        )
        true
    }
    session.temp.moveCompleteTo(session.target)
    session.directDownloadIdentityFile().delete()
    return shouldReportCompletion
}

internal fun ResolvedVideoStream.directDownloadRequest(existingBytes: Long, etag: String? = null): Request {
    val builder = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .header("Accept-Encoding", "identity")
        .removeHeader("Range")
        .removeHeader("If-Range")
    if (existingBytes > 0L) {
        builder.header("Range", "bytes=$existingBytes-")
        etag?.let { builder.header("If-Range", it) }
    }
    return builder.build()
}

// DirectVideoDownloadEntry
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
            attempt = nextDirectDownloadAttempt(
                attempt = attempt,
                throwable = throwable,
                session = session,
                isCancelled = isCancelled,
                deletePartialOnCancel = deletePartialOnCancel,
            )
        }
    }
}

private fun DirectDownloadSession.directDownloadIdentityFile() = File(temp.parentFile, "${temp.name}.identity")

private fun DirectDownloadSession.discardDirectDownloadPartial() {
    // Invalidate first: failed deletion must never leave reusable metadata for old bytes.
    val metadata = directDownloadIdentityFile()
    if (metadata.exists() && !metadata.delete()) throw IOException("Cannot remove download identity")
    if (temp.exists() && !temp.delete()) throw IOException("Cannot remove partial download")
}

private fun DirectDownloadSession.readDirectDownloadIdentity(): List<String>? = try {
    directDownloadIdentityFile().readLines().takeIf {
        it.size == 3 && it[0].matches(Regex("[0-9a-f]{64}")) &&
            it[1].strongDirectDownloadEtag() != null && it[2].matches(Regex("[0-9a-f]{64}"))
    }
} catch (_: IOException) {
    null
}

private fun String?.strongDirectDownloadEtag(): String? = this?.takeIf {
    it.length >= 2 && it.first() == '"' && it.last() == '"' &&
        it.substring(1, it.lastIndex).all { char -> char == '\u0021' || char in '\u0023'..'\u007e' || char in '\u0080'..'\u00ff' }
}

private fun String.directDownloadHash(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun ResolvedVideoStream.directDownloadIdentity(): String {
    val request = directDownloadRequest(0L)
    return buildString {
        append(request.url).append('\n')
        request.headers.names().sortedBy { it.lowercase(java.util.Locale.ROOT) }.forEach { name ->
            append(name.lowercase(java.util.Locale.ROOT)).append(':')
            request.headers.values(name).forEach { append(it.length).append(':').append(it) }
            append('\n')
        }
    }.directDownloadHash()
}

private suspend fun nextDirectDownloadAttempt(
    attempt: Int,
    throwable: Throwable,
    session: DirectDownloadSession,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
): Int {
    throwable.throwIfCancellation()
    if (throwable is DownloadSourceCoolingDown) throw throwable
    if (isDirectDownloadCancelled(throwable, isCancelled)) {
        if (deletePartialOnCancel()) session.discardDirectDownloadPartial()
        throw throwable
    }
    val nextAttempt = attempt + 1
    if (nextAttempt >= DOWNLOAD_RETRY_COUNT) throw throwable
    delay(DOWNLOAD_RETRY_DELAY_MS * nextAttempt)
    return nextAttempt
}

private fun isDirectDownloadCancelled(
    throwable: Throwable,
    isCancelled: () -> Boolean,
): Boolean = isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)

// DirectVideoResponseWriter
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
    if (plan.totalBytes > 0L && session.temp.length().coerceAtLeast(0L) != plan.totalBytes) {
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
