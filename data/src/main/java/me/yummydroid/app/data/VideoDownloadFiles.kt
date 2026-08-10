package me.yummydroid.app.data

import java.io.File
import kotlinx.coroutines.CancellationException

internal fun ResolvedVideoStream.withSourceSubtitleVideo(video: VideoVariant): ResolvedVideoStream {
    val sourceKey = video.matchingSourceKey.takeIf { it.isNotBlank() && hasResolvedSubtitles } ?: return this
    return copy(sourceSubtitleSourceKeys = sourceSubtitleSourceKeys + sourceKey)
}

internal fun Throwable.throwIfCancellation() {
    if (this is CancellationException) throw this
}

internal fun File.partFile(): File {
    return File(parentFile, "$name.part")
}

internal fun File.hlsStateFile(): File {
    return File(parentFile, "$name.state")
}

internal data class HlsResumeState(
    val initWritten: Boolean,
    val nextSegmentIndex: Int,
)

internal fun File.readHlsResumeState(signature: String): HlsResumeState? {
    if (!exists()) return null
    val lines = runCatching { readLines() }.getOrNull() ?: return null
    if (lines.getOrNull(0) != signature) return null
    return HlsResumeState(
        initWritten = lines.getOrNull(1)?.toBooleanStrictOrNull() ?: false,
        nextSegmentIndex = lines.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )
}

internal fun File.writeHlsResumeState(
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

internal fun File.moveCompleteTo(target: File) {
    target.delete()
    if (!renameTo(target)) {
        inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        delete()
    }
}

internal fun String.parseContentRangeTotal(): Long? {
    return substringAfter('/', "")
        .takeIf { it.isNotBlank() && it != "*" }
        ?.toLongOrNull()
}

internal fun ResolvedVideoStream.qualityTitle(): String {
    return selectedVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: maxVideoHeight?.takeIf { it > 0 }?.let { "${it}p" }
        ?: url.detectDownloadQualityHeight()?.let { "${it}p" }
        ?: ""
}

internal fun HlsVariant.qualityTitle(): String {
    return height.validVideoQualityHeight()?.let { "${it}p" }.orEmpty()
}

internal const val DOWNLOAD_RETRY_COUNT = 5
internal const val DOWNLOAD_RETRY_DELAY_MS = 700L
