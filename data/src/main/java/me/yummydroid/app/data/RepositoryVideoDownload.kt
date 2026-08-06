package me.yummydroid.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
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

private fun String.detectDownloadQualityHeight(): Int? {
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

internal fun File.downloadQualityTitle(): String {
    return nameWithoutExtension
        .substringAfter('_', "")
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "Auto"
}

private fun File.isCompletedDownloadFile(): Boolean {
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
    val initialPlaylist = downloadText(stream.url, stream.headers)
    val hlsVariants = initialPlaylist.hlsVariants(stream.url)
    val selectedVariant = if (preferredQuality.height != null && hlsVariants.isNotEmpty()) {
        hlsVariants.selectExactQuality(preferredQuality)
            ?: throw IOException("HLS source does not contain ${preferredQuality.title} quality")
    } else {
        hlsVariants.selectForQuality(preferredQuality)
    }
    if (hlsVariants.isEmpty()) {
        stream.requireExactDownloadQuality(preferredQuality)
    }
    val mediaUrl = selectedVariant?.url ?: stream.url
    val mediaPlaylist = if (mediaUrl == stream.url) initialPlaylist else downloadText(mediaUrl, stream.headers)
    val plan = mediaPlaylist.toHlsSingleFilePlan(mediaUrl, selectedVariant?.bandwidth ?: 0)
    if (plan.segments.isEmpty()) {
        throw IOException("HLS playlist does not contain segments to download")
    }

    val keyCache = mutableMapOf<String, ByteArray>()
    val startedAtMs = System.currentTimeMillis()
    val qualityTitle = selectedVariant?.qualityTitle() ?: stream.qualityTitle()
    val target = storage.targetFile(video, plan.outputExtension, qualityTitle.ifBlank { "auto" })
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
    val stateFile = temp.hlsStateFile()
    val signature = plan.signature()
    val resumeState = stateFile.readHlsResumeState(signature)
    if (temp.exists() && temp.length() > 0L && resumeState == null) {
        temp.delete()
        stateFile.delete()
    }
    var downloadedBytes = temp.length().coerceAtLeast(0L)
    var sessionDownloadedBytes = 0L
    val voiceTitle = video.downloadVoiceTitle()

    try {
        FileOutputStream(temp, true).use { output ->
            var initWritten = resumeState?.initWritten ?: false
            var nextSegmentIndex = resumeState?.nextSegmentIndex ?: 0
            if (plan.initUrl != null && !initWritten) {
                val bytes = downloadUrlBytes(plan.initUrl, stream.headers, bandwidthLimiter)
                output.write(bytes)
                output.flush()
                downloadedBytes = temp.length().coerceAtLeast(0L)
                sessionDownloadedBytes += bytes.size.toLong()
                initWritten = true
                stateFile.writeHlsResumeState(signature, initWritten, nextSegmentIndex)
            }
            while (nextSegmentIndex < plan.segments.size) {
                val index = nextSegmentIndex
                val segment = plan.segments[index]
                check(!isCancelled()) { "Download cancelled" }
                val bytes = downloadUrlBytes(segment.url, stream.headers, bandwidthLimiter)
                val payload = segment.encryption?.let { encryption ->
                    decryptHlsSegment(
                        bytes = bytes,
                        encryption = encryption,
                        sequenceNumber = plan.mediaSequence + index,
                        headers = stream.headers,
                        keyCache = keyCache,
                        bandwidthLimiter = bandwidthLimiter,
                    )
                } ?: bytes
                output.write(payload)
                output.flush()
                nextSegmentIndex = index + 1
                downloadedBytes = temp.length().coerceAtLeast(0L)
                sessionDownloadedBytes += payload.size.toLong()
                stateFile.writeHlsResumeState(signature, initWritten = true, nextSegmentIndex = nextSegmentIndex)
                val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)
                val speed = (sessionDownloadedBytes * 1000L / elapsedMs).coerceAtLeast(0L)
                val fraction = (nextSegmentIndex.toFloat() / plan.segments.size.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    DownloadProgressInfo(
                        fraction = fraction,
                        downloadedBytes = downloadedBytes,
                        totalBytes = -1L,
                        bytesPerSecond = speed,
                        qualityTitle = qualityTitle,
                        voiceTitle = voiceTitle,
                    ),
                )
            }
        }
        stateFile.delete()
        temp.moveCompleteTo(target)
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
            if (deletePartialOnCancel()) {
                temp.delete()
                stateFile.delete()
            }
        }
        throw throwable
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

private fun YummyAnimeRepository.downloadText(url: String, headers: Map<String, String>): String {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return downloadClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
        response.body?.string().orEmpty().takeIf { it.isNotBlank() }
            ?: throw IOException("Empty playlist")
    }
}

private suspend fun YummyAnimeRepository.downloadUrlBytes(
    url: String,
    headers: Map<String, String>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    var attempt = 0
    while (true) {
        try {
            val request = Request.Builder()
                .url(url)
                .headers(headers.toOkHttpHeaders())
                .build()
            return downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty HLS resource")
                ByteArrayOutputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            bandwidthLimiter.throttle(read.toLong())
                            output.write(buffer, 0, read)
                        }
                    }
                    output.toByteArray()
                }
            }
        } catch (throwable: Throwable) {
            throwable.throwIfCancellation()
            attempt += 1
            if (attempt >= DOWNLOAD_RETRY_COUNT) throw throwable
            delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
        }
    }
}

private fun String.fileExtensionForDownload(): String {
    val path = substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> "m3u8"
        path.endsWith(".mpd") -> "mpd"
        path.endsWith(".m4s") -> "m4s"
        path.endsWith(".ts") -> "ts"
        path.endsWith(".mp4") -> "mp4"
        path.endsWith(".mkv") -> "mkv"
        path.endsWith(".webm") -> "webm"
        else -> "mp4"
    }
}

internal fun String.mimeTypeFromFileName(): String? {
    val lower = lowercase()
    return when {
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        lower.endsWith(".mpd") -> "application/dash+xml"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".m4s") -> "video/mp4"
        lower.endsWith(".ts") -> "video/mp2t"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".webm") -> "video/webm"
        else -> null
    }
}

private data class HlsSingleFilePlan(
    val mediaSequence: Long,
    val initUrl: String?,
    val outputExtension: String,
    val variantBandwidth: Int,
    val segments: List<HlsMediaSegment>,
) {
    fun signature(): String {
        return buildString {
            append(mediaSequence)
            append('|').append(initUrl.orEmpty())
            append('|').append(outputExtension)
            append('|').append(variantBandwidth)
            segments.forEach { segment ->
                append('|').append(segment.url)
                append('@').append(segment.durationSeconds)
                append('@').append(segment.encryption?.method.orEmpty())
                append('@').append(segment.encryption?.keyUrl.orEmpty())
            }
        }
    }
}

private data class HlsMediaSegment(
    val url: String,
    val encryption: HlsEncryption?,
    val durationSeconds: Double,
)

private data class HlsEncryption(
    val method: String,
    val keyUrl: String?,
    val iv: ByteArray?,
)

private fun String.toHlsSingleFilePlan(baseUrl: String, variantBandwidth: Int): HlsSingleFilePlan {
    val segments = mutableListOf<HlsMediaSegment>()
    var encryption: HlsEncryption? = null
    var initUrl: String? = null
    var mediaSequence = 0L
    var nextSegmentDuration = 0.0

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) -> {
                mediaSequence = line.substringAfter(':', "").trim().toLongOrNull() ?: 0L
            }
            line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                encryption = line.toHlsEncryption(baseUrl)
            }
            line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                initUrl = line.hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
            }
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                nextSegmentDuration = line.substringAfter(':', "")
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull()
                    ?: 0.0
            }
            line.isBlank() || line.startsWith("#") -> Unit
            else -> {
                segments += HlsMediaSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    encryption = encryption,
                    durationSeconds = nextSegmentDuration,
                )
                nextSegmentDuration = 0.0
            }
        }
    }

    val extension = when {
        initUrl != null -> "mp4"
        segments.any { it.url.fileExtensionForDownload() in setOf("m4s", "mp4") } -> "mp4"
        else -> "ts"
    }
    return HlsSingleFilePlan(
        mediaSequence = mediaSequence,
        initUrl = initUrl,
        outputExtension = extension,
        variantBandwidth = variantBandwidth,
        segments = segments,
    )
}

private fun String.toHlsEncryption(baseUrl: String): HlsEncryption? {
    val method = hlsAttribute("METHOD").orEmpty()
    if (method.equals("NONE", ignoreCase = true)) return null
    val keyUrl = hlsAttribute("URI")?.let { it.resolveUrlAgainst(baseUrl) }
    return HlsEncryption(
        method = method,
        keyUrl = keyUrl,
        iv = hlsAttribute("IV")?.hexToBytes(),
    )
}

private suspend fun YummyAnimeRepository.decryptHlsSegment(
    bytes: ByteArray,
    encryption: HlsEncryption,
    sequenceNumber: Long,
    headers: Map<String, String>,
    keyCache: MutableMap<String, ByteArray>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    if (!encryption.method.equals("AES-128", ignoreCase = true)) {
        throw IOException("HLS ${encryption.method} is not supported for offline downloading")
    }
    val keyUrl = encryption.keyUrl ?: throw IOException("HLS encryption key was not found")
    val key = keyCache[keyUrl] ?: downloadUrlBytes(keyUrl, headers, bandwidthLimiter).also { keyCache[keyUrl] = it }
    if (key.size != 16) throw IOException("Invalid HLS encryption key")
    val iv = encryption.iv ?: sequenceNumber.toAesIv()
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
    return cipher.doFinal(bytes)
}

private fun Long.toAesIv(): ByteArray {
    val result = ByteArray(16)
    var value = this
    for (index in 15 downTo 8) {
        result[index] = (value and 0xff).toByte()
        value = value ushr 8
    }
    return result
}

private fun String.hexToBytes(): ByteArray? {
    val clean = removePrefix("0x").removePrefix("0X").trim()
    if (clean.length % 2 != 0) return null
    return runCatching {
        ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }.getOrNull()
}

internal fun ResolvedVideoStream.withSourceSubtitleVideo(video: VideoVariant): ResolvedVideoStream {
    val sourceKey = video.matchingSourceKey.takeIf { it.isNotBlank() && hasResolvedSubtitles } ?: return this
    return copy(sourceSubtitleSourceKeys = sourceSubtitleSourceKeys + sourceKey)
}

internal fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

private fun File.partFile(): File {
    return File(parentFile, "$name.part")
}

private fun File.hlsStateFile(): File {
    return File(parentFile, "$name.state")
}

private data class HlsResumeState(
    val initWritten: Boolean,
    val nextSegmentIndex: Int,
)

private fun File.readHlsResumeState(signature: String): HlsResumeState? {
    if (!exists()) return null
    val lines = runCatching { readLines() }.getOrNull() ?: return null
    if (lines.getOrNull(0) != signature) return null
    return HlsResumeState(
        initWritten = lines.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
        nextSegmentIndex = lines.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

private fun File.writeHlsResumeState(
    signature: String,
    initWritten: Boolean,
    nextSegmentIndex: Int,
) {
    parentFile?.mkdirs()
    writeText(
        listOf(signature, initWritten.toString(), nextSegmentIndex.coerceAtLeast(0).toString())
            .joinToString("\n"),
    )
}

private fun File.moveCompleteTo(target: File) {
    target.delete()
    if (!renameTo(target)) {
        inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        delete()
    }
}

private fun String.parseContentRangeTotal(): Long? {
    return substringAfter('/', "")
        .takeIf { it.isNotBlank() && it != "*" }
        ?.toLongOrNull()
}

private fun ResolvedVideoStream.qualityTitle(): String {
    return selectedVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: maxVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: url.detectDownloadQualityHeight()?.let { "${it}p" }
        ?: ""
}

private fun HlsVariant.qualityTitle(): String {
    return height.validVideoQualityHeight()?.let { "${it}p" }.orEmpty()
}

private const val DOWNLOAD_RETRY_COUNT = 5
private const val DOWNLOAD_RETRY_DELAY_MS = 700L
