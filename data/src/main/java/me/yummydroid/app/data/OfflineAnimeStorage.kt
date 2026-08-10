package me.yummydroid.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

class OfflineAnimeStorage(context: Context) {
    private val rootDir = resolveOfflineContentRoot(context.applicationContext).apply { mkdirs() }
    private val indexFile = File(rootDir, OFFLINE_ANIME_INDEX_FILE_NAME)
    private val downloadRegistry = OfflineDownloadRegistry(rootDir)

    @Synchronized
    fun readAll(): List<OfflineAnimeEntry> {
        return readIndex().values
            .map(::restoreExistingDownloads)
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
            ?.let(::restoreExistingDownloads)
            ?.takeIf { it.downloadedVideos.isNotEmpty() }
    }

    @Synchronized
    fun saveAnime(details: AnimeDetails, videos: List<VideoVariant>) {
        val filesBySlot = downloadRegistry.completedFilesBySlot(details.id)
        val mergedVideos = videos.map { video ->
            video.withMergedOfflineFiles(
                files = filesBySlot[video.downloadRecordSlotKey()].orEmpty(),
                previewFallback = video.previewUrl,
            )
        }
        val entry = OfflineAnimeEntry(
            anime = details.toAnimeSummary(),
            details = details,
            videos = mergedVideos.distinctBy { it.id },
            updatedAtMs = System.currentTimeMillis(),
        )
        writeIndex(readIndex() + (details.id to entry))
    }

    @Synchronized
    fun markVideoDownloaded(
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        file: File,
        mimeType: String?,
    ) {
        if (!file.isCompletedOfflineDownloadFile()) {
            throw IOException("Episode file was not fully downloaded")
        }
        val finalFile = file.withDetectedQualityName()
        val offlineFile = OfflineVideoFile(
            playbackUrl = Uri.fromFile(finalFile).toString(),
            mimeType = mimeType ?: finalFile.name.offlineMimeType(),
            bytes = finalFile.downloadPackageSizeBytes(),
            qualityTitle = finalFile.downloadQualityTitle(),
            voiceTitle = video.downloadVoiceTitleForStorage(),
            player = video.player,
            createdAtMs = System.currentTimeMillis(),
        )
        downloadRegistry.upsert(video, offlineFile)

        val storedVideo = readIndex()[details.id]?.videos?.firstOrNull { it.id == video.id }
        val existingVideo = videos.firstOrNull { it.id == video.id }
            ?.mergeStoredPlayback(storedVideo)
            ?: storedVideo
            ?: video
        val localVideo = existingVideo.withDownloadedFile(video, offlineFile)
        saveAnime(details, videos.map { if (it.id == video.id) localVideo else it })
    }

    fun targetFile(
        video: VideoVariant,
        extension: String = "mp4",
        qualityTitle: String = "auto",
    ): File {
        return video.offlineTargetFile(rootDir, extension, qualityTitle)
    }

    @Synchronized
    fun deleteVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        downloadRegistry.remove(animeId, videoId, playbackUrl)
        val index = readIndex().toMutableMap()
        val entry = index[animeId]
            ?.let(::restoreExistingDownloads)
            ?: return
        val updatedVideos = entry.videos.map { video ->
            when {
                playbackUrl != null && video.offlineFiles.any { it.playbackUrl == playbackUrl } -> {
                    video.deleteOfflineFile(playbackUrl)
                }
                video.id == videoId -> video.deleteOfflineFile(playbackUrl)
                else -> video
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
        index.remove(animeId)?.downloadedVariants.orEmpty().forEach { video ->
            video.offlineFiles.forEach { it.playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage() }
            video.localPlaybackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
        }
        File(rootDir, animeId.toString()).deleteRecursively()
        writeIndex(index)
    }

    @Synchronized
    fun clearOfflineCache() {
        rootDir.clearOfflineContent(OFFLINE_ANIME_INDEX_FILE_NAME)
        writeIndex(emptyMap())
    }

    private fun restoreExistingDownloads(entry: OfflineAnimeEntry): OfflineAnimeEntry {
        return entry.withExistingOfflineFiles(downloadRegistry.completedFilesBySlot(entry.anime.id))
    }

    private fun readIndex(): Map<Long, OfflineAnimeEntry> {
        return indexFile.readJsonOrNull<Map<Long, OfflineAnimeEntry>>().orEmpty()
    }

    private fun writeIndex(index: Map<Long, OfflineAnimeEntry>) {
        indexFile.writeJson(index)
    }

    companion object {
        fun contentRoot(context: Context): File {
            return resolveOfflineContentRoot(context.applicationContext)
        }

        fun contentPayloadSizeBytes(context: Context): Long {
            return contentRoot(context).offlinePayloadSizeBytes(OFFLINE_ANIME_INDEX_FILE_NAME)
        }
    }
}
