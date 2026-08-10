package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.Request

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

internal fun YummyAnimeRepository.downloadText(url: String, headers: Map<String, String>): String {
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

internal fun String.fileExtensionForDownload(): String {
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
