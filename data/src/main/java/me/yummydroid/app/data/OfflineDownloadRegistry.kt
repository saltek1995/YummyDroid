package me.yummydroid.app.data

import java.io.File
import kotlinx.serialization.Serializable

private const val STALE_PARTIAL_DOWNLOAD_MS = 6L * 60L * 60L * 1000L

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
    val status: String = OFFLINE_DOWNLOAD_STATUS_COMPLETED,
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

internal class OfflineDownloadRegistry(private val rootDir: File) {
    fun completedFilesBySlot(animeId: Long): Map<String, List<OfflineVideoFile>> {
        return completedRecords(animeId)
            .groupBy { it.slotKey }
            .mapValues { (_, records) -> records.map { it.toOfflineFile() } }
    }

    fun upsert(video: VideoVariant, offlineFile: OfflineVideoFile) {
        val index = readIndex(video.animeId)
        val record = offlineFile.toDownloadRecord(video)
        val retained = index.records.filterNot { existing ->
            existing.playbackUrl == record.playbackUrl ||
                (
                    existing.slotKey == record.slotKey &&
                        existing.qualityHeight == record.qualityHeight &&
                        existing.player.equals(record.player, ignoreCase = true)
                )
        }
        writeIndex(
            video.animeId,
            index.copy(records = (retained + record).distinctBy { it.playbackUrl }),
        )
    }

    fun remove(animeId: Long, videoId: Long, playbackUrl: String?) {
        val index = readIndex(animeId)
        if (index.records.isEmpty()) return
        val removed = mutableListOf<OfflineDownloadRecord>()
        val retained = index.records.filter { record ->
            val matches = playbackUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { record.playbackUrl == it }
                ?: (record.videoId == videoId)
            if (matches) removed += record
            !matches
        }
        removed.forEach { it.playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage() }
        writeIndex(animeId, index.copy(records = retained))
    }

    private fun completedRecords(animeId: Long): List<OfflineDownloadRecord> {
        val index = readIndex(animeId)
        var changed = false
        val records = index.records.mapNotNull { record ->
            record.validated().also { validated ->
                if (validated != record) changed = true
            }
        }.distinctBy { it.playbackUrl }
        if (changed || records.size != index.records.size) {
            writeIndex(animeId, index.copy(records = records))
        }
        cleanupFiles(animeId, records)
        return records
    }

    private fun OfflineDownloadRecord.validated(): OfflineDownloadRecord? {
        val file = playbackUrl.toOfflineLocalFile()
        if (status != OFFLINE_DOWNLOAD_STATUS_COMPLETED || file?.isCompletedOfflineDownloadFile() != true) {
            file?.deleteOfflineDownloadPackage()
            return null
        }
        val actualBytes = file.downloadPackageSizeBytes()
        return if (bytes == actualBytes) this else copy(bytes = actualBytes)
    }

    private fun OfflineVideoFile.toDownloadRecord(video: VideoVariant): OfflineDownloadRecord {
        val safeBytes = playbackUrl.toOfflineLocalFile()?.downloadPackageSizeBytes()
            ?: bytes.coerceAtLeast(0L)
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
            createdAtMs = createdAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
    }

    private fun readIndex(animeId: Long): OfflineAnimeDownloadIndex {
        return indexFile(animeId).readJsonOrNull<OfflineAnimeDownloadIndex>()
            ?: OfflineAnimeDownloadIndex()
    }

    private fun writeIndex(animeId: Long, index: OfflineAnimeDownloadIndex) {
        val file = indexFile(animeId)
        file.parentFile?.mkdirs()
        file.writeJson(index)
    }

    private fun indexFile(animeId: Long): File {
        return File(File(rootDir, animeId.toString()), OFFLINE_ANIME_DOWNLOAD_INDEX_FILE_NAME)
    }

    private fun cleanupFiles(animeId: Long, records: List<OfflineDownloadRecord>) {
        val animeDir = File(rootDir, animeId.toString())
        if (!animeDir.exists()) return
        val keepPaths = records.mapNotNullTo(mutableSetOf()) { record ->
            record.playbackUrl.toOfflineLocalFile()?.absolutePath
        }
        val now = System.currentTimeMillis()
        animeDir.walkBottomUp().forEach { file ->
            cleanupFile(file, animeDir, keepPaths, now)
        }
    }

    private fun cleanupFile(file: File, animeDir: File, keepPaths: Set<String>, nowMs: Long) {
        when {
            file == animeDir -> Unit
            file.isDirectory -> if (file.listFiles().isNullOrEmpty()) file.delete()
            file.name == OFFLINE_ANIME_DOWNLOAD_INDEX_FILE_NAME -> Unit
            file.absolutePath in keepPaths -> Unit
            file.isFreshPartialDownload(nowMs) -> Unit
            else -> file.deleteOfflineDownloadPackage()
        }
    }

    private fun File.isFreshPartialDownload(nowMs: Long): Boolean {
        val isPartial = extension.equals("part", ignoreCase = true) ||
            extension.equals("state", ignoreCase = true)
        return isPartial && nowMs - lastModified().coerceAtLeast(0L) < STALE_PARTIAL_DOWNLOAD_MS
    }
}
