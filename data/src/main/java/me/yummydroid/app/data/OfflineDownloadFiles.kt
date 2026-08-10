package me.yummydroid.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.net.URI

internal const val OFFLINE_ANIME_INDEX_FILE_NAME = "index.json"
internal const val OFFLINE_ANIME_DOWNLOAD_INDEX_FILE_NAME = "downloads_index.json"
internal const val OFFLINE_DOWNLOAD_STATUS_COMPLETED = "completed"

private const val RU_VOICE_PREFIX_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_LABEL = "\u041f\u043b\u0435\u0435\u0440"

internal fun resolveOfflineContentRoot(context: Context): File {
    val publicRoot = File(Environment.getExternalStorageDirectory(), "YummyDroid")
    val publicAvailable = runCatching {
        publicRoot.mkdirs()
        publicRoot.exists() && publicRoot.canWrite()
    }.getOrDefault(false)
    if (publicAvailable) return publicRoot

    return File(
        context.getExternalFilesDir(null) ?: context.filesDir,
        "YummyDroid",
    )
}

internal fun VideoVariant.offlineTargetFile(
    rootDir: File,
    extension: String,
    qualityTitle: String,
): File {
    val episodeDir = File(
        File(File(rootDir, animeId.toString()), downloadVoiceFolderName()),
        episodeFolderName(),
    )
    episodeDir.mkdirs()
    val safeExtension = extension.trim().trimStart('.').ifBlank { "mp4" }
    val safeQuality = qualityTitle.safeOfflinePathPart(maxLength = 32).ifBlank { "auto" }
    return File(episodeDir, "${id}_${safeQuality}.$safeExtension")
}

internal fun VideoVariant.downloadRecordSlotKey(): String {
    return listOf(storageEpisodeKey(), storageVoiceKey()).joinToString("|")
}

internal fun VideoVariant.downloadVoiceTitleForStorage(): String {
    return dubbing.cleanStorageLabel(RU_VOICE_PREFIX_LABEL)
        .ifBlank { player.cleanStorageLabel(RU_PLAYER_PREFIX_LABEL) }
        .ifBlank { "Voice" }
}

internal fun String.toOfflineLocalFile(): File? {
    return runCatching {
        URI(this)
            .takeIf { it.scheme.equals("file", ignoreCase = true) }
            ?.let(::File)
    }.getOrNull()
}

internal fun File.deleteOfflineDownloadPackage() {
    if (extension.equals("m3u8", ignoreCase = true)) {
        File(parentFile, nameWithoutExtension + "_segments").deleteRecursively()
    }
    delete()
}

internal fun File.isCompletedOfflineDownloadFile(): Boolean {
    return exists() &&
        length() >= MIN_COMPLETED_VIDEO_BYTES &&
        !extension.equals("m3u8", ignoreCase = true)
}

internal fun File.downloadPackageSizeBytes(): Long {
    return length().coerceAtLeast(0L)
}

internal fun File.downloadQualityTitle(): String {
    return nameWithoutExtension
        .substringAfter('_', "")
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "Auto"
}

internal fun File.withDetectedQualityName(): File {
    val detectedQuality = detectVideoQualityTitle() ?: return this
    if (downloadQualityTitle().equals(detectedQuality, ignoreCase = true)) return this
    val prefix = nameWithoutExtension.substringBefore('_', nameWithoutExtension)
    val target = File(parentFile, "${prefix}_${detectedQuality.safeOfflinePathPart(32)}.$extension")
    if (target.absolutePath == absolutePath) return this
    target.delete()
    return if (renameTo(target)) target else this
}

internal fun String.offlineMimeType(): String? {
    val lower = lowercase()
    return when {
        lower.endsWith(".m3u8") -> "application/x-mpegURL"
        lower.endsWith(".mpd") -> "application/dash+xml"
        lower.endsWith(".mp4") -> "video/mp4"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".webm") -> "video/webm"
        lower.endsWith(".ts") -> "video/mp2t"
        else -> null
    }
}

internal fun File.clearOfflineContent(indexFileName: String) {
    listFiles().orEmpty()
        .filterNot { it.name == indexFileName }
        .forEach { it.deleteRecursively() }
}

internal fun File.offlinePayloadSizeBytes(indexFileName: String): Long {
    return listFiles().orEmpty()
        .filterNot { it.name == indexFileName }
        .sumOf { it.totalSizeBytes() }
}

internal fun VideoVariant.storageVoiceKey(): String {
    return dubbing.cleanStorageLabel(RU_VOICE_PREFIX_LABEL)
        .cleanStorageLabel(RU_SUBTITLES_PREFIX_LABEL)
        .cleanStorageLabel(RU_PLAYER_PREFIX_LABEL)
        .ifBlank { player.cleanStorageLabel(RU_PLAYER_PREFIX_LABEL) }
        .normalizedStorageVoiceIdentity()
}

private fun String.normalizedStorageVoiceIdentity(): String {
    return lowercase()
        .replace('\u0451', '\u0435')
        .replace(Regex("""[\s./|\u2022:_-]+"""), "")
        .trim()
}

private fun String.cleanStorageLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

private fun VideoVariant.downloadVoiceFolderName(): String {
    val voice = dubbing.cleanOfflinePathPrefix(RU_VOICE_PREFIX_LABEL)
        .cleanOfflinePathPrefix(RU_SUBTITLES_PREFIX_LABEL)
        .ifBlank { player.cleanOfflinePathPrefix(RU_PLAYER_PREFIX_LABEL) }
    return voice.safeOfflinePathPart(maxLength = 80).ifBlank { "voice" }
}

private fun VideoVariant.episodeFolderName(): String {
    val rawName = episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: "video_$id"
    return rawName.safeOfflinePathPart(maxLength = 40).ifBlank { "episode" }
}

private fun String.cleanOfflinePathPrefix(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

private fun String.safeOfflinePathPart(maxLength: Int): String {
    return trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim('.', ' ')
        .take(maxLength)
}

private fun File.detectVideoQualityTitle(): String? {
    if (!exists() || !isFile) return null
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?.let { "${it}p" }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
