package me.yummydroid.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable

private const val MIN_COMPLETED_VIDEO_BYTES = 256L * 1024L
private const val ANIME_DOWNLOAD_INDEX_FILE = "downloads_index.json"
private const val DOWNLOAD_STATUS_COMPLETED = "completed"
private const val STALE_PARTIAL_DOWNLOAD_MS = 6L * 60L * 60L * 1000L

@Serializable
data class OfflineAnimeEntry(
    val anime: Anime,
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val updatedAtMs: Long,
) {
    val downloadedVariants: List<VideoVariant>
        get() = videos.filter { video ->
            video.offlineFiles.any { it.bytes >= MIN_COMPLETED_VIDEO_BYTES }
        }.distinctBy { video ->
            video.offlineFiles
                .map { it.playbackUrl }
                .filter { it.isNotBlank() }
                .sorted()
                .joinToString("|")
                .ifBlank { "${video.animeId}|${video.episode}|${video.dubbing}|${video.player}" }
        }

    val downloadedVideos: List<VideoVariant>
        get() = downloadedVariants
            .sortedWith(compareBy<VideoVariant> { it.storageEpisodeSortKey() }.thenBy { it.index })
            .distinctBy { it.storageEpisodeKey() }

    val totalBytes: Long
        get() = videos
            .flatMap { it.offlineFiles }
            .filter { it.bytes >= MIN_COMPLETED_VIDEO_BYTES }
            .distinctBy { it.playbackUrl }
            .sumOf { it.bytes.coerceAtLeast(0L) }
}

@Serializable
private data class OfflineAnimeDownloadIndex(
    val version: Int = 1,
    val records: List<OfflineDownloadRecord> = emptyList(),
)

@Serializable
private data class OfflineDownloadRecord(
    val videoId: Long,
    val episodeKey: String,
    val voiceKey: String,
    val voiceTitle: String,
    val player: String,
    val playbackUrl: String,
    val mimeType: String? = null,
    val bytes: Long = 0L,
    val qualityTitle: String = "",
    val qualityHeight: Int = 0,
    val status: String = DOWNLOAD_STATUS_COMPLETED,
    val createdAtMs: Long = 0L,
) {
    val slotKey: String
        get() = listOf(episodeKey, voiceKey).joinToString("|")

    fun toOfflineFile(): OfflineVideoFile {
        return OfflineVideoFile(
            playbackUrl = playbackUrl,
            mimeType = mimeType,
            bytes = bytes,
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
            player = player,
            createdAtMs = createdAtMs,
        )
    }
}

private fun VideoVariant.storageEpisodeKey(): String {
    return matchingEpisodeKey.takeIf { it.isNotBlank() }
        ?: episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"
}

private fun VideoVariant.storageEpisodeSortKey(): Double {
    return storageEpisodeKey().toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
        ?: Double.MAX_VALUE
}

private fun VideoVariant.storageVoiceKey(): String {
    return dubbing.cleanStorageLabel(RU_VOICE_PREFIX_LABEL)
        .cleanStorageLabel(RU_SUBTITLES_PREFIX_LABEL)
        .cleanStorageLabel(RU_PLAYER_PREFIX_LABEL)
        .ifBlank { player.cleanStorageLabel(RU_PLAYER_PREFIX_LABEL) }
        .normalizedStorageVoiceIdentity()
}

private fun VideoVariant.downloadRecordSlotKey(): String {
    return listOf(storageEpisodeKey(), storageVoiceKey()).joinToString("|")
}

private fun String.normalizedStorageVoiceIdentity(): String {
    return lowercase()
        .replace('\u0451', '\u0435')
        .replace(Regex("""[\s./|•:_-]+"""), "")
        .trim()
}

private fun String.cleanStorageLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

class OfflineAnimeStorage(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir = resolveRootDir(appContext).apply { mkdirs() }
    private val indexFile = File(rootDir, INDEX_FILE)

    @Synchronized
    fun readAll(): List<OfflineAnimeEntry> {
        return readIndex().values
            .map { it.withExistingFilesOnly() }
            .filter { it.downloadedVideos.isNotEmpty() }
            .sortedBy { it.anime.title.lowercase() }
    }

    @Synchronized
    fun readAnimeIds(): Set<Long> {
        return readAll().mapTo(mutableSetOf()) { it.anime.id }
    }

    @Synchronized
    fun searchOffline(query: String, offset: Int, limit: Int): List<Anime> {
        val normalizedQuery = query.trim().lowercase()
        return readAll()
            .asSequence()
            .map { it.anime }
            .filter { anime ->
                normalizedQuery.isBlank() ||
                    anime.title.lowercase().contains(normalizedQuery) ||
                    anime.description.lowercase().contains(normalizedQuery) ||
                    anime.genres.any { it.lowercase().contains(normalizedQuery) }
            }
            .drop(offset)
            .take(limit)
            .toList()
    }

    @Synchronized
    fun read(animeId: Long): OfflineAnimeEntry? {
        return readIndex()[animeId]
            ?.withExistingFilesOnly()
            ?.takeIf { it.downloadedVideos.isNotEmpty() }
    }

    @Synchronized
    fun saveAnime(details: AnimeDetails, videos: List<VideoVariant>) {
        val existing = readIndex()
        val indexedFilesBySlot = completedDownloadRecords(details.id)
            .groupBy { it.slotKey }
            .mapValues { (_, records) -> records.map { it.toOfflineFile() } }
        val mergedVideos = videos.map { video ->
            video.withMergedOfflineFiles(
                files = indexedFilesBySlot[video.downloadRecordSlotKey()].orEmpty(),
                previewFallback = video.previewUrl,
            )
        }
        val entry = OfflineAnimeEntry(
            anime = details.toAnimeSummary(),
            details = details,
            videos = mergedVideos.distinctBy { it.id },
            updatedAtMs = System.currentTimeMillis(),
        )
        writeIndex(existing + (details.id to entry))
        cleanupAnimeDownloadFiles(details.id, completedDownloadRecords(details.id))
    }

    @Synchronized
    fun markVideoDownloaded(
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        file: File,
        mimeType: String?,
    ) {
        if (!file.isCompletedDownloadFile()) {
            throw IOException("Episode file was not fully downloaded")
        }
        val finalFile = file.withDetectedQualityName()
        val localUri = Uri.fromFile(finalFile).toString()
        val offlineFile = OfflineVideoFile(
            playbackUrl = localUri,
            mimeType = mimeType ?: finalFile.name.mimeTypeFromFileName(),
            bytes = finalFile.downloadPackageSizeBytes(),
            qualityTitle = finalFile.qualityTitleFromDownloadName(),
            voiceTitle = video.downloadVoiceTitleForStorage(),
            player = video.player,
            createdAtMs = System.currentTimeMillis(),
        )
        upsertCompletedDownloadRecord(video, offlineFile)
        val storedVideo = readIndex()[details.id]?.videos?.firstOrNull { it.id == video.id }
        val existingVideo = videos.firstOrNull { it.id == video.id }
            ?.let { fresh ->
                if (storedVideo != null) {
                    fresh.copy(
                        localPlaybackUrl = storedVideo.localPlaybackUrl,
                        localMimeType = storedVideo.localMimeType,
                        localBytes = storedVideo.localBytes,
                        localFiles = storedVideo.offlineFiles,
                    )
                } else {
                    fresh
                }
            }
            ?: storedVideo
            ?: video
        val videoWithPlaybackMetadata = existingVideo.copy(
            skipSegments = existingVideo.skipSegments.ifEmpty { video.skipSegments },
        )
        val mergedFiles = (existingVideo.offlineFiles + offlineFile)
            .filter { it.playbackUrl.isNotBlank() }
            .distinctBy { it.playbackUrl }
            .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
        val primaryFile = mergedFiles.firstOrNull() ?: offlineFile
        val localVideo = videoWithPlaybackMetadata.copy(
            localPlaybackUrl = primaryFile.playbackUrl,
            localMimeType = primaryFile.mimeType,
            localBytes = primaryFile.bytes,
            localFiles = mergedFiles,
            previewUrl = videoWithPlaybackMetadata.previewUrl.ifBlank { video.previewUrl },
        )
        val merged = videos.map { if (it.id == video.id) localVideo else it }
        saveAnime(details, merged)
    }

    fun targetFile(video: VideoVariant, extension: String = "mp4", qualityTitle: String = "auto"): File {
        val animeDir = File(rootDir, video.animeId.toString())
        val voiceDir = File(animeDir, video.downloadVoiceFolderName())
        val episodeDir = File(voiceDir, video.episodeFolderName())
        episodeDir.mkdirs()
        val safeExtension = extension.trim().trimStart('.').ifBlank { "mp4" }
        val safeQuality = qualityTitle.safePathPart(maxLength = 32).ifBlank { "auto" }
        return File(episodeDir, "${video.id}_${safeQuality}.$safeExtension")
    }

    @Synchronized
    fun deleteVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        removeCompletedDownloadRecords(animeId, videoId, playbackUrl)
        val index = readIndex().toMutableMap()
        val entry = index[animeId]?.withExistingFilesOnly() ?: return
        val updatedVideos = entry.videos.map { video ->
            if (playbackUrl != null && video.offlineFiles.any { it.playbackUrl == playbackUrl }) {
                video.deleteOfflineFile(playbackUrl)
            } else if (video.id == videoId) {
                video.deleteOfflineFile(playbackUrl)
            } else {
                video
            }
        }
        if (updatedVideos.none { it.isOfflineAvailable }) {
            index.remove(animeId)
            File(rootDir, animeId.toString()).deleteRecursively()
        } else {
            index[animeId] = entry.copy(videos = updatedVideos, updatedAtMs = System.currentTimeMillis())
        }
        writeIndex(index)
    }

    private fun VideoVariant.deleteOfflineFile(playbackUrl: String?): VideoVariant {
        if (playbackUrl.isNullOrBlank()) {
            offlineFiles.forEach { it.playbackUrl.toLocalFile()?.deleteDownloadPackage() }
            localPlaybackUrl.toLocalFile()?.deleteDownloadPackage()
            return copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L, localFiles = emptyList())
        }

        val remainingFiles = offlineFiles
            .filterNot { it.playbackUrl == playbackUrl }
            .distinctBy { it.playbackUrl }
        playbackUrl.toLocalFile()?.deleteDownloadPackage()
        if (remainingFiles.isEmpty()) {
            return copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L, localFiles = emptyList())
        }
        val primaryFile = remainingFiles
            .maxWith(compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes })
        return copy(
            localPlaybackUrl = primaryFile.playbackUrl,
            localMimeType = primaryFile.mimeType,
            localBytes = primaryFile.bytes,
            localFiles = remainingFiles.sortedWith(
                compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle },
            ),
        )
    }

    @Synchronized
    fun deleteAnime(animeId: Long) {
        val index = readIndex().toMutableMap()
        index.remove(animeId)?.downloadedVariants.orEmpty().forEach { video ->
            video.offlineFiles.forEach { it.playbackUrl.toLocalFile()?.deleteDownloadPackage() }
            video.localPlaybackUrl.toLocalFile()?.deleteDownloadPackage()
        }
        File(rootDir, animeId.toString()).deleteRecursively()
        writeIndex(index)
    }

    @Synchronized
    fun clearOfflineCache() {
        rootDir.listFiles()
            .orEmpty()
            .filterNot { it.name == INDEX_FILE }
            .forEach { it.deleteRecursively() }
        writeIndex(emptyMap())
    }

    private fun upsertCompletedDownloadRecord(video: VideoVariant, offlineFile: OfflineVideoFile) {
        val index = readAnimeDownloadIndex(video.animeId)
        val record = offlineFile.toDownloadRecord(video)
        writeAnimeDownloadIndex(
            video.animeId,
            index.copy(
                records = (index.records
                    .filterNot { existing ->
                        existing.playbackUrl == record.playbackUrl ||
                            (
                                existing.slotKey == record.slotKey &&
                                    existing.qualityHeight == record.qualityHeight &&
                                    existing.player.equals(record.player, ignoreCase = true)
                            )
                    } + record)
                    .distinctBy { it.playbackUrl },
            ),
        )
    }

    private fun removeCompletedDownloadRecords(animeId: Long, videoId: Long, playbackUrl: String?) {
        val index = readAnimeDownloadIndex(animeId)
        if (index.records.isEmpty()) return
        val removed = mutableListOf<OfflineDownloadRecord>()
        val retained = index.records.filter { record ->
            val matches = if (playbackUrl.isNullOrBlank()) {
                record.videoId == videoId
            } else {
                record.playbackUrl == playbackUrl
            }
            if (matches) removed += record
            !matches
        }
        removed.forEach { it.playbackUrl.toLocalFile()?.deleteDownloadPackage() }
        writeAnimeDownloadIndex(animeId, index.copy(records = retained))
    }

    private fun completedDownloadRecords(animeId: Long): List<OfflineDownloadRecord> {
        val index = readAnimeDownloadIndex(animeId)
        if (index.records.isEmpty()) {
            cleanupAnimeDownloadFiles(animeId, emptyList())
            return emptyList()
        }
        var changed = false
        val records = index.records.mapNotNull { record ->
            val file = record.playbackUrl.toLocalFile()
            if (
                record.status == DOWNLOAD_STATUS_COMPLETED &&
                file != null &&
                file.isCompletedDownloadFile()
            ) {
                val actualBytes = file.downloadPackageSizeBytes()
                if (record.bytes != actualBytes) {
                    changed = true
                    record.copy(bytes = actualBytes)
                } else {
                    record
                }
            } else {
                changed = true
                file?.deleteDownloadPackage()
                null
            }
        }
            .distinctBy { it.playbackUrl }
        if (changed || records.size != index.records.size) {
            writeAnimeDownloadIndex(animeId, index.copy(records = records))
        }
        cleanupAnimeDownloadFiles(animeId, records)
        return records
    }

    private fun OfflineVideoFile.toDownloadRecord(video: VideoVariant): OfflineDownloadRecord {
        val safeBytes = playbackUrl.toLocalFile()?.downloadPackageSizeBytes() ?: bytes.coerceAtLeast(0L)
        return OfflineDownloadRecord(
            videoId = video.id,
            episodeKey = video.storageEpisodeKey(),
            voiceKey = video.storageVoiceKey(),
            voiceTitle = voiceTitle.ifBlank { video.downloadVoiceTitleForStorage() },
            player = player.ifBlank { video.player },
            playbackUrl = playbackUrl,
            mimeType = mimeType,
            bytes = safeBytes,
            qualityTitle = qualityTitle,
            qualityHeight = qualityHeight(),
            status = DOWNLOAD_STATUS_COMPLETED,
            createdAtMs = createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun readAnimeDownloadIndex(animeId: Long): OfflineAnimeDownloadIndex {
        return animeDownloadIndexFile(animeId)
            .readJsonOrNull<OfflineAnimeDownloadIndex>()
            ?: OfflineAnimeDownloadIndex()
    }

    private fun writeAnimeDownloadIndex(animeId: Long, index: OfflineAnimeDownloadIndex) {
        val file = animeDownloadIndexFile(animeId)
        file.parentFile?.mkdirs()
        file.writeJson(index)
    }

    private fun animeDownloadIndexFile(animeId: Long): File {
        return File(File(rootDir, animeId.toString()), ANIME_DOWNLOAD_INDEX_FILE)
    }

    private fun cleanupAnimeDownloadFiles(animeId: Long, records: List<OfflineDownloadRecord>) {
        val animeDir = File(rootDir, animeId.toString())
        if (!animeDir.exists()) return
        val keepPaths = records.mapNotNullTo(mutableSetOf()) { record ->
            record.playbackUrl.toLocalFile()?.absolutePath
        }
        val now = System.currentTimeMillis()
        animeDir.walkBottomUp().forEach { file ->
            when {
                file == animeDir -> Unit
                file.isDirectory -> {
                    if (file.listFiles().isNullOrEmpty()) file.delete()
                }
                file.name == ANIME_DOWNLOAD_INDEX_FILE -> Unit
                file.absolutePath in keepPaths -> Unit
                file.isActivePartialDownloadArtifact(now) -> Unit
                file.isPartialDownloadArtifact(now) -> file.deleteDownloadPackage()
                !file.isCompletedDownloadFile() -> file.deleteDownloadPackage()
                else -> file.deleteDownloadPackage()
            }
        }
    }

    private fun File.isActivePartialDownloadArtifact(nowMs: Long): Boolean {
        val fresh = nowMs - lastModified().coerceAtLeast(0L) < STALE_PARTIAL_DOWNLOAD_MS
        return fresh && isPartialDownloadName()
    }

    private fun File.isPartialDownloadArtifact(nowMs: Long): Boolean {
        val stale = nowMs - lastModified().coerceAtLeast(0L) >= STALE_PARTIAL_DOWNLOAD_MS
        return stale && isPartialDownloadName()
    }

    private fun File.isPartialDownloadName(): Boolean {
        return extension.equals("part", ignoreCase = true) ||
            extension.equals("state", ignoreCase = true)
    }

    private fun readIndex(): Map<Long, OfflineAnimeEntry> {
        return indexFile.readJsonOrNull<Map<Long, OfflineAnimeEntry>>().orEmpty()
    }

    private fun writeIndex(index: Map<Long, OfflineAnimeEntry>) {
        indexFile.writeJson(index)
    }

    private fun OfflineAnimeEntry.withExistingFilesOnly(): OfflineAnimeEntry {
        val indexedFilesBySlot = completedDownloadRecords(anime.id)
            .groupBy { it.slotKey }
            .mapValues { (_, records) -> records.map { it.toOfflineFile() } }
        val updatedVideos = videos.map { video ->
            val existingFiles = indexedFilesBySlot[video.downloadRecordSlotKey()].orEmpty()
            if (existingFiles.isNotEmpty()) {
                val primaryFile = existingFiles
                    .maxWith(compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes })
                video.copy(
                    localPlaybackUrl = primaryFile.playbackUrl,
                    localMimeType = primaryFile.mimeType,
                    localBytes = primaryFile.bytes,
                    localFiles = existingFiles.sortedWith(
                        compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle },
                    ),
                )
            } else if (video.isOfflineAvailable) {
                video.copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L, localFiles = emptyList())
            } else {
                video
            }
        }
        return copy(videos = updatedVideos)
    }

    private fun VideoVariant.withMergedOfflineFiles(
        files: List<OfflineVideoFile>,
        previewFallback: String,
    ): VideoVariant {
        val mergedFiles = files
            .mapNotNull { offlineFile ->
                val file = offlineFile.playbackUrl.toLocalFile()
                if (file != null && file.isCompletedDownloadFile()) {
                    offlineFile.copy(bytes = file.downloadPackageSizeBytes())
                } else {
                    null
                }
            }
            .distinctBy { it.playbackUrl }
            .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
        val primaryFile = mergedFiles.firstOrNull()
        return if (primaryFile != null) {
            copy(
                localPlaybackUrl = primaryFile.playbackUrl,
                localMimeType = primaryFile.mimeType,
                localBytes = primaryFile.bytes,
                localFiles = mergedFiles,
                previewUrl = previewUrl.ifBlank { previewFallback },
            )
        } else {
            copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L, localFiles = emptyList())
        }
    }

    private fun String.toLocalFile(): File? {
        return runCatching {
            toUri()
                .takeIf { it.scheme == "file" }
                ?.path
                ?.let(::File)
        }.getOrNull()
    }

    private fun File.deleteDownloadPackage() {
        if (extension.equals("m3u8", ignoreCase = true)) {
            companionSegmentDir().deleteRecursively()
        }
        delete()
    }

    private fun File.isCompletedDownloadFile(): Boolean {
        if (!exists() || length() <= 0L) return false
        if (extension.equals("m3u8", ignoreCase = true)) return false
        return length() >= MIN_COMPLETED_VIDEO_BYTES
    }

    private fun File.downloadPackageSizeBytes(): Long {
        return length().coerceAtLeast(0L)
    }

    private fun File.qualityTitleFromDownloadName(): String {
        return nameWithoutExtension
            .substringAfter('_', "")
            .replace('_', ' ')
            .takeIf { it.isNotBlank() }
            ?: "Auto"
    }

    private fun File.withDetectedQualityName(): File {
        val detectedQuality = detectVideoQualityTitle() ?: return this
        val currentQuality = qualityTitleFromDownloadName()
        if (currentQuality.equals(detectedQuality, ignoreCase = true)) return this
        val prefix = nameWithoutExtension.substringBefore('_', nameWithoutExtension)
        val target = File(parentFile, "${prefix}_${detectedQuality.safePathPart(maxLength = 32)}.$extension")
        if (target.absolutePath == absolutePath) return this
        target.delete()
        return if (renameTo(target)) target else this
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

    private fun VideoVariant.downloadVoiceTitleForStorage(): String {
        return dubbing.cleanStorageLabel(RU_VOICE_PREFIX_LABEL)
            .ifBlank { player.cleanStorageLabel(RU_PLAYER_PREFIX_LABEL) }
            .ifBlank { "Voice" }
    }

    private fun File.companionSegmentDir(): File {
        return File(parentFile, nameWithoutExtension + "_segments")
    }

    private fun String.mimeTypeFromFileName(): String? {
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

    companion object {
        private const val OFFLINE_DIR = "YummyDroid"
        private const val INDEX_FILE = "index.json"

        fun contentRoot(context: Context): File {
            return resolveRootDir(context.applicationContext)
        }

        fun contentPayloadSizeBytes(context: Context): Long {
            return contentRoot(context)
                .listFiles()
                .orEmpty()
                .filterNot { file -> file.name == INDEX_FILE }
                .sumOf { file -> file.totalSizeBytes() }
        }
    }
}

private fun resolveRootDir(context: Context): File {
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

private fun VideoVariant.downloadVoiceFolderName(): String {
    val voice = dubbing.cleanOfflinePathPrefix(RU_VOICE_PREFIX_LABEL)
        .cleanOfflinePathPrefix(RU_SUBTITLES_PREFIX_LABEL)
        .ifBlank {
            player.cleanOfflinePathPrefix(RU_PLAYER_PREFIX_LABEL)
        }
    return voice.safePathPart(maxLength = 80).ifBlank { "voice" }
}

private fun VideoVariant.episodeFolderName(): String {
    val rawName = episode.trim()
        .takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: "video_$id"
    return rawName.safePathPart(maxLength = 40).ifBlank { "episode" }
}

private fun String.cleanOfflinePathPrefix(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

private const val RU_VOICE_PREFIX_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_PREFIX_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_PLAYER_PREFIX_LABEL = "\u041f\u043b\u0435\u0435\u0440"

private fun String.safePathPart(maxLength: Int): String {
    return trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim('.', ' ')
        .take(maxLength)
}
