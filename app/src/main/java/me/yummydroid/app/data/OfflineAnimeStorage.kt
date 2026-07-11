package me.yummydroid.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OfflineAnimeEntry(
    val anime: Anime,
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val updatedAtMs: Long,
) {
    val downloadedVideos: List<VideoVariant>
        get() = videos.filter { it.isOfflineAvailable }

    val totalBytes: Long
        get() = downloadedVideos.sumOf { it.localBytes.coerceAtLeast(0L) }
}

class OfflineAnimeStorage(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, OFFLINE_DIR).apply { mkdirs() }
    private val indexFile = File(rootDir, INDEX_FILE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

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
        val existingVideosById = current?.videos.orEmpty()
            .filter { it.isOfflineAvailable }
            .associateBy { it.id }
        val mergedVideos = videos.map { video ->
            existingVideosById[video.id]?.let { downloaded ->
                video.copy(
                    localPlaybackUrl = downloaded.localPlaybackUrl,
                    localMimeType = downloaded.localMimeType,
                    localBytes = downloaded.localBytes,
                    previewUrl = video.previewUrl.ifBlank { downloaded.previewUrl },
                )
            } ?: video
        }
        val entry = OfflineAnimeEntry(
            anime = details.toAnimeSummary(),
            details = details,
            videos = mergedVideos,
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
        val localUri = Uri.fromFile(file).toString()
        val localVideo = video.copy(
            localPlaybackUrl = localUri,
            localMimeType = mimeType ?: file.name.mimeTypeFromFileName(),
            localBytes = file.length().coerceAtLeast(0L),
        )
        val merged = videos.map { if (it.id == video.id) localVideo else it }
        saveAnime(details, merged)
    }

    fun targetFile(video: VideoVariant, extension: String = "mp4"): File {
        val safeEpisode = video.episode.ifBlank { video.index.toString() }
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
            .ifBlank { "episode" }
        val safeVoice = video.groupKey
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
            .take(80)
            .ifBlank { "voice" }
        val animeDir = File(rootDir, video.animeId.toString()).apply { mkdirs() }
        return File(animeDir, "${safeEpisode}_${safeVoice}_${video.id}.$extension")
    }

    @Synchronized
    fun deleteVideo(animeId: Long, videoId: Long) {
        val index = readIndex().toMutableMap()
        val entry = index[animeId] ?: return
        val updatedVideos = entry.videos.map { video ->
            if (video.id == videoId) {
                video.localPlaybackUrl.toLocalFile()?.delete()
                video.copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L)
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

    @Synchronized
    fun deleteAnime(animeId: Long) {
        val index = readIndex().toMutableMap()
        index.remove(animeId)?.downloadedVideos.orEmpty().forEach { video ->
            video.localPlaybackUrl.toLocalFile()?.delete()
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
            json.decodeFromString<Map<Long, OfflineAnimeEntry>>(indexFile.readText())
        }.getOrDefault(emptyMap())
    }

    private fun writeIndex(index: Map<Long, OfflineAnimeEntry>) {
        rootDir.mkdirs()
        indexFile.writeText(json.encodeToString(index))
    }

    private fun OfflineAnimeEntry.withExistingFilesOnly(): OfflineAnimeEntry {
        val updatedVideos = videos.map { video ->
            val file = video.localPlaybackUrl.toLocalFile()
            if (file != null && file.exists()) {
                video.copy(localBytes = file.length().coerceAtLeast(video.localBytes))
            } else if (video.isOfflineAvailable) {
                video.copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L)
            } else {
                video
            }
        }
        return copy(videos = updatedVideos)
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

    private fun String.mimeTypeFromFileName(): String? {
        val lower = lowercase()
        return when {
            lower.endsWith(".m3u8") -> "application/x-mpegURL"
            lower.endsWith(".mpd") -> "application/dash+xml"
            lower.endsWith(".mp4") -> "video/mp4"
            else -> null
        }
    }

    private companion object {
        const val OFFLINE_DIR = "offline_anime"
        const val INDEX_FILE = "index.json"
    }
}
