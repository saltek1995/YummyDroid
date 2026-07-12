package me.yummydroid.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val MIN_COMPLETED_VIDEO_BYTES = 256L * 1024L

@Serializable
data class OfflineAnimeEntry(
    val anime: Anime,
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val updatedAtMs: Long,
) {
    val downloadedVideos: List<VideoVariant>
        get() = videos.filter { video ->
            video.offlineFiles.any { it.bytes >= MIN_COMPLETED_VIDEO_BYTES }
        }

    val totalBytes: Long
        get() = downloadedVideos.sumOf { video ->
            video.offlineFiles.sumOf { it.bytes.coerceAtLeast(0L) }
        }
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
        val current = existing[details.id]
        val existingOfflineVideos = current?.videos.orEmpty()
            .filter { it.isOfflineAvailable }
        val mergedVideos = videos.map { video ->
            val matchedFiles = (video.offlineFiles + video.discoveredOfflineFiles()) + existingOfflineVideos
                .filter { downloaded ->
                    downloaded.id == video.id ||
                        downloaded.storageSlotKey() == video.storageSlotKey() ||
                        downloaded.storageVoiceSlotKey() == video.storageVoiceSlotKey()
                }
                .flatMap { it.offlineFiles }
            video.withMergedOfflineFiles(matchedFiles, previewFallback = video.previewUrl)
        }
        val representedLocalUrls = mergedVideos
            .flatMap { it.offlineFiles }
            .mapTo(mutableSetOf()) { it.playbackUrl }
        val orphanedOfflineVideos = existingOfflineVideos.mapNotNull { downloaded ->
            val remainingFiles = downloaded.offlineFiles.filterNot { it.playbackUrl in representedLocalUrls }
            downloaded.withMergedOfflineFiles(remainingFiles, previewFallback = downloaded.previewUrl)
                .takeIf { it.isOfflineAvailable }
        }
        val entry = OfflineAnimeEntry(
            anime = details.toAnimeSummary(),
            details = details,
            videos = (mergedVideos + orphanedOfflineVideos).distinctBy { it.id },
            updatedAtMs = System.currentTimeMillis(),
        )
        writeIndex(existing + (details.id to entry))
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
            throw IOException("Файл серии не был скачан полностью")
        }
        val localUri = Uri.fromFile(file).toString()
        val offlineFile = OfflineVideoFile(
            playbackUrl = localUri,
            mimeType = mimeType ?: file.name.mimeTypeFromFileName(),
            bytes = file.downloadPackageSizeBytes(),
            qualityTitle = file.qualityTitleFromDownloadName(),
            voiceTitle = video.downloadVoiceTitleForStorage(),
            player = video.player,
            createdAtMs = System.currentTimeMillis(),
        )
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
        val index = readIndex().toMutableMap()
        val entry = index[animeId] ?: return
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
        index.remove(animeId)?.downloadedVideos.orEmpty().forEach { video ->
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

    private fun readIndex(): Map<Long, OfflineAnimeEntry> {
        if (!indexFile.exists()) return emptyMap()
        return runCatching {
            AppJson.decodeFromString<Map<Long, OfflineAnimeEntry>>(indexFile.readText())
        }.getOrDefault(emptyMap())
    }

    private fun writeIndex(index: Map<Long, OfflineAnimeEntry>) {
        rootDir.mkdirs()
        indexFile.writeText(AppJson.encodeToString(index))
    }

    private fun OfflineAnimeEntry.withExistingFilesOnly(): OfflineAnimeEntry {
        val updatedVideos = videos.map { video ->
            val existingFiles = (video.offlineFiles + video.discoveredOfflineFiles()).mapNotNull { offlineFile ->
                val file = offlineFile.playbackUrl.toLocalFile()
                if (file != null && file.isCompletedDownloadFile()) {
                    offlineFile.copy(bytes = file.downloadPackageSizeBytes())
                } else {
                    file?.deleteDownloadPackage()
                    null
                }
            }
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

    private fun VideoVariant.discoveredOfflineFiles(): List<OfflineVideoFile> {
        val episodeDir = File(
            File(File(rootDir, animeId.toString()), downloadVoiceFolderName()),
            episodeFolderName(),
        )
        if (!episodeDir.exists()) return emptyList()

        val episodeFiles = episodeDir.listFiles().orEmpty()
        episodeFiles
            .filter { file ->
                file.isFile &&
                    file.nameWithoutExtension.startsWith("${id}_") &&
                    !file.isCompletedDownloadFile()
            }
            .forEach { file -> file.deleteDownloadPackage() }

        return episodeFiles
            .asSequence()
            .filter { file ->
                file.isFile &&
                    file.nameWithoutExtension.startsWith("${id}_") &&
                    file.isCompletedDownloadFile()
            }
            .map { file ->
                OfflineVideoFile(
                    playbackUrl = Uri.fromFile(file).toString(),
                    mimeType = file.name.mimeTypeFromFileName(),
                    bytes = file.downloadPackageSizeBytes(),
                    qualityTitle = file.qualityTitleFromDownloadName(),
                    voiceTitle = downloadVoiceTitleForStorage(),
                    player = player,
                    createdAtMs = file.lastModified().coerceAtLeast(0L),
                )
            }
            .toList()
    }

    private fun VideoVariant.storageSlotKey(): String {
        return listOf(animeId.toString(), storageEpisodeKey(), player, dubbing)
            .joinToString("|") { it.trim().lowercase() }
    }

    private fun VideoVariant.storageVoiceSlotKey(): String {
        return listOf(animeId.toString(), storageEpisodeKey(), storageVoiceKey())
            .joinToString("|") { it.trim().lowercase() }
    }

    private fun VideoVariant.storageEpisodeKey(): String {
        return episode.trim().takeIf { it.isNotBlank() }
            ?: index.takeIf { it > 0 }?.toString()
            ?: "video:$id"
    }

    private fun VideoVariant.storageVoiceKey(): String {
        return dubbing.cleanStorageLabel("Озвучка")
            .cleanStorageLabel("Субтитры")
            .cleanStorageLabel("Плеер")
            .ifBlank { player.cleanStorageLabel("Плеер") }
            .normalizedStorageVoiceIdentity()
    }

    private fun String.normalizedStorageVoiceIdentity(): String {
        return lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[\s./|•:_-]+"""), "")
            .trim()
    }

    private fun AnimeDetails.toAnimeSummary(): Anime {
        return Anime(
            id = id,
            title = title,
            description = description,
            posterUrl = posterUrl,
            animeUrl = "",
            year = year,
            rating = rating,
            userRating = userRating,
            views = views,
            status = status,
            type = type,
            genres = genres,
            blockedIn = blockedIn,
        )
    }

    private fun String.toLocalFile(): File? {
        return runCatching {
            Uri.parse(this)
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
            ?: "Авто"
    }

    private fun VideoVariant.downloadVoiceTitleForStorage(): String {
        return dubbing.cleanStorageLabel("Озвучка")
            .ifBlank { player.cleanStorageLabel("Плеер") }
            .ifBlank { "Озвучка" }
    }

    private fun String.cleanStorageLabel(prefix: String): String {
        return trim().removePrefix(prefix).trim()
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

    private companion object {
        const val OFFLINE_DIR = "YummyDroid"
        const val INDEX_FILE = "index.json"
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
    val voice = dubbing.cleanOfflinePathPrefix("Озвучка")
        .cleanOfflinePathPrefix("Субтитры")
        .ifBlank {
            player.cleanOfflinePathPrefix("Плеер")
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

private fun String.safePathPart(maxLength: Int): String {
    return trim()
        .replace(Regex("""[\\/:*?"<>|]+"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim('.', ' ')
        .take(maxLength)
}
