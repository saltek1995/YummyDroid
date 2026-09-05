package me.yummydroid.app.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

val VideoVariant.downloadSourceKey: String
    get() = player.cleanVideoSourceLabel().trim().lowercase(Locale.ROOT)
        .ifBlank { url.toHttpUrlOrNull()?.host ?: "unknown" }

class DownloadSourceCoolingDown(val retryAtMs: Long) : IOException("Download source is temporarily restricted")

class DownloadSourceCooldowns internal constructor(
    private val prefs: SharedPreferences,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("download_source_cooldowns", Context.MODE_PRIVATE))

    fun isAvailable(video: VideoVariant): Boolean = waitingFor(listOf(video)) == null

    fun waitingFor(videos: List<VideoVariant>): DownloadSourceCoolingDown? = synchronized(lock) {
        val now = nowMs()
        videos.map { prefs.getLong(it.cooldownKey(), 0L) }.filter { it > now }.minOrNull()
            ?.let(::DownloadSourceCoolingDown)
    }

    internal fun restrict(video: VideoVariant): DownloadSourceCoolingDown = synchronized(lock) {
        val key = video.cooldownKey()
        val now = nowMs()
        val until = prefs.getLong(key, 0L).takeIf { it > now } ?: (now + COOLDOWN_MS).also {
            prefs.edit().putLong(key, it).apply()
        }
        DownloadSourceCoolingDown(until)
    }

    private fun VideoVariant.cooldownKey(): String = "source:" + downloadSourceKey

    private companion object {
        val lock = Any()
        const val COOLDOWN_MS = 5 * 60 * 1000L
    }
}

internal class DownloadSourceRequestPolicy(
    private val cooldowns: DownloadSourceCooldowns,
    private val video: VideoVariant,
) : HttpRequestPolicy() {
    override fun beforeRequest() {
        cooldowns.waitingFor(listOf(video))?.let { throw it }
    }

    override fun onResponse(statusCode: Int) {
        if (statusCode == 403) throw cooldowns.restrict(video)
    }
}

internal suspend fun <T> YummyAnimeRepository.withDownloadSource(video: VideoVariant, action: suspend () -> T): T {
    val cooldowns = downloadSourceCooldowns ?: return action()
    val policy = DownloadSourceRequestPolicy(cooldowns, video)
    policy.beforeRequest()
    return withContext(policy) { action() }
}

// OfflineAnimeEntry
internal const val MIN_COMPLETED_VIDEO_BYTES = 256L * 1024L

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
            .uniquePlayableOfflineFiles()
            .sumOf { it.bytes.coerceAtLeast(0L) }
}

internal fun VideoVariant.storageEpisodeKey(): String {
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

// OfflineAnimeFiles
internal fun OfflineAnimeEntry.withExistingOfflineFiles(
    filesBySlot: Map<String, List<OfflineVideoFile>>,
): OfflineAnimeEntry {
    val updatedVideos = videos.map { video ->
        video.withExistingOfflineFiles(filesBySlot[video.downloadRecordSlotKey()].orEmpty())
    }
    return copy(videos = updatedVideos)
}

internal fun VideoVariant.withMergedOfflineFiles(
    files: List<OfflineVideoFile>,
    previewFallback: String,
): VideoVariant {
    val mergedFiles = files.validOfflineFiles()
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
        withoutLocalPlayback()
    }
}

internal fun VideoVariant.deleteOfflineFile(playbackUrl: String?): VideoVariant {
    if (playbackUrl.isNullOrBlank()) {
        offlineFiles.forEach { it.playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage() }
        localPlaybackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
        return withoutLocalPlayback()
    }

    val remainingFiles = offlineFiles
        .filterNot { it.playbackUrl == playbackUrl }
        .uniquePlayableOfflineFiles()
    playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
    return withPrimaryOfflineFile(remainingFiles)
}

internal fun VideoVariant.mergeStoredPlayback(storedVideo: VideoVariant?): VideoVariant {
    if (storedVideo == null) return this
    return copy(
        localPlaybackUrl = storedVideo.localPlaybackUrl,
        localMimeType = storedVideo.localMimeType,
        localBytes = storedVideo.localBytes,
        localFiles = storedVideo.offlineFiles,
    )
}

internal fun VideoVariant.withDownloadedFile(
    sourceVideo: VideoVariant,
    offlineFile: OfflineVideoFile,
): VideoVariant {
    val videoWithPlaybackMetadata = copy(skipSegments = skipSegments.ifEmpty { sourceVideo.skipSegments })
    val mergedFiles = (offlineFiles + offlineFile)
        .uniquePlayableOfflineFilesByQuality()
    val primaryFile = mergedFiles.firstOrNull() ?: offlineFile
    return videoWithPlaybackMetadata.copy(
        localPlaybackUrl = primaryFile.playbackUrl,
        localMimeType = primaryFile.mimeType,
        localBytes = primaryFile.bytes,
        localFiles = mergedFiles,
        previewUrl = videoWithPlaybackMetadata.previewUrl.ifBlank { sourceVideo.previewUrl },
    )
}

private fun VideoVariant.withExistingOfflineFiles(files: List<OfflineVideoFile>): VideoVariant {
    if (files.isNotEmpty()) return withPrimaryOfflineFile(files)
    return if (isOfflineAvailable) withoutLocalPlayback() else this
}

private fun VideoVariant.withPrimaryOfflineFile(files: List<OfflineVideoFile>): VideoVariant {
    if (files.isEmpty()) return withoutLocalPlayback()
    val sortedFiles = files.sortedOfflineFiles()
    val primaryFile = files.maxWith(
        compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes },
    )
    return copy(
        localPlaybackUrl = primaryFile.playbackUrl,
        localMimeType = primaryFile.mimeType,
        localBytes = primaryFile.bytes,
        localFiles = sortedFiles,
    )
}



private fun List<OfflineVideoFile>.validOfflineFiles(): List<OfflineVideoFile> {
    return mapNotNull { offlineFile ->
        val file = offlineFile.playbackUrl.toOfflineLocalFile()
        if (file?.isCompletedOfflineDownloadFile() == true) {
            offlineFile.copy(bytes = file.downloadPackageSizeBytes())
        } else {
            null
        }
    }
        .uniquePlayableOfflineFilesByQuality()
}

// OfflineAnimeStorageRuntime
class OfflineAnimeStorage(context: Context) {
    private val rootDir = resolveOfflineContentRoot(context.applicationContext).apply { mkdirs() }
    private val indexFile = File(rootDir, OFFLINE_ANIME_INDEX_FILE_NAME)
    private val downloadRegistry = OfflineDownloadRegistry(rootDir)

    fun readAll(): List<OfflineAnimeEntry> = synchronized(OfflineStorageAccess) {
        return readIndex().values
            .map(::restoreExistingDownloads)
            .filter { it.downloadedVideos.isNotEmpty() }
            .sortedBy { it.anime.title.lowercase() }
    }

    fun readAnimeIds(): Set<Long> = synchronized(OfflineStorageAccess) {
        return readAll().mapTo(mutableSetOf()) { it.anime.id }
    }

    fun searchOffline(query: String, offset: Int, limit: Int): List<Anime> = synchronized(OfflineStorageAccess) {
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

    fun read(animeId: Long): OfflineAnimeEntry? = synchronized(OfflineStorageAccess) {
        return readIndex()[animeId]
            ?.let(::restoreExistingDownloads)
            ?.takeIf { it.downloadedVideos.isNotEmpty() }
    }

    fun saveAnime(details: AnimeDetails, videos: List<VideoVariant>) = synchronized(OfflineStorageAccess) {
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

    fun markVideoDownloaded(
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        file: File,
        mimeType: String?,
    ) = synchronized(OfflineStorageAccess) {
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

    internal suspend fun <T> withDownload(animeId: Long, action: suspend () -> T): T {
        return OfflineStorageAccess.withDownload(File(rootDir, animeId.toString()), action)
    }

    fun targetFile(
        video: VideoVariant,
        extension: String = "mp4",
        qualityTitle: String = "auto",
    ): File {
        return video.offlineTargetFile(rootDir, extension, qualityTitle)
    }

    fun deleteVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) = synchronized(OfflineStorageAccess) {
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
            val directory = File(rootDir, animeId.toString())
            if (!OfflineStorageAccess.isDownloading(directory)) directory.deleteRecursively()
        } else {
            index[animeId] = entry.copy(videos = updatedVideos, updatedAtMs = System.currentTimeMillis())
        }
        writeIndex(index)
    }

    fun deleteAnime(animeId: Long) = synchronized(OfflineStorageAccess) {
        val index = readIndex().toMutableMap()
        index.remove(animeId)?.downloadedVariants.orEmpty().forEach { video ->
            video.offlineFiles.forEach { it.playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage() }
            video.localPlaybackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
        }
        File(rootDir, animeId.toString()).deleteRecursively()
        writeIndex(index)
    }

    fun clearOfflineCache() = synchronized(OfflineStorageAccess) {
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

// OfflineDownloadFiles
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
        .replace(StorageVoiceIdentitySeparatorPattern, "")
        .trim()
}

private val StorageVoiceIdentitySeparatorPattern = Regex("""[\s./|\u2022:_-]+""")

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
        .replace(OfflinePathForbiddenCharsPattern, "_")
        .replace(OfflinePathWhitespacePattern, " ")
        .trim('.', ' ')
        .take(maxLength)
}

private val OfflinePathForbiddenCharsPattern = Regex("""[\\/:*?"<>|]+""")
private val OfflinePathWhitespacePattern = Regex("""\s+""")

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

// OfflineDownloadRegistry
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

// One process-wide owner for offline indexes and in-flight artifact registration.
internal object OfflineStorageAccess {
    private val writers = mutableMapOf<File, Int>()

    suspend fun <T> withDownload(directory: File, action: suspend () -> T): T {
        val key = directory.canonicalFile
        synchronized(this) { writers[key] = writers.getOrDefault(key, 0) + 1 }
        try {
            return action()
        } finally {
            synchronized(this) {
                val remaining = writers.getValue(key) - 1
                if (remaining == 0) writers.remove(key) else writers[key] = remaining
            }
        }
    }

    @Synchronized
    fun isDownloading(directory: File): Boolean = directory.canonicalFile in writers
}

internal class OfflineDownloadRegistry(private val rootDir: File) {
    fun completedFilesBySlot(animeId: Long): Map<String, List<OfflineVideoFile>> = synchronized(OfflineStorageAccess) {
        return completedRecords(animeId)
            .groupBy { it.slotKey }
            .mapValues { (_, records) -> records.map { it.toOfflineFile() } }
    }

    fun upsert(video: VideoVariant, offlineFile: OfflineVideoFile) = synchronized(OfflineStorageAccess) {
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

    fun remove(animeId: Long, videoId: Long, playbackUrl: String?) = synchronized(OfflineStorageAccess) {
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
        if (!animeDir.exists() || OfflineStorageAccess.isDownloading(animeDir)) return
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

// RepositoryOfflineData
internal suspend fun YummyAnimeRepository.repositoryOfflineAnime(): List<OfflineAnimeEntry> =
    withContext(Dispatchers.IO) {
        offlineStorage?.readAll().orEmpty()
    }

internal suspend fun YummyAnimeRepository.repositoryDeleteOfflineVideo(
    animeId: Long,
    videoId: Long,
    playbackUrl: String?,
) = withContext(Dispatchers.IO) {
    offlineStorage?.deleteVideo(animeId, videoId, playbackUrl)
}

internal suspend fun YummyAnimeRepository.repositoryDeleteOfflineAnime(
    animeId: Long,
) = withContext(Dispatchers.IO) {
    offlineStorage?.deleteAnime(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryClearAppContentCache(
    playbackProgressStorage: PlaybackProgressStorage,
) = withContext(Dispatchers.IO) {
    offlineStorage?.clearOfflineCache()
    playbackProgressStorage.clear()
    contentCache?.clear()
    sourceQualityCache?.clear()
}

internal suspend fun YummyAnimeRepository.repositoryDownloadVideo(
    details: AnimeDetails,
    videos: List<VideoVariant>,
    video: VideoVariant,
    preferredQuality: PreferredQuality,
    onProgress: (VideoVariant, DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
): VideoVariant = withContext(Dispatchers.IO) {
    val storage = offlineStorage ?: error("Offline storage is unavailable")
    check(!isCancelled()) { "Download cancelled" }
    val request = OfflineDownloadRequest(
        details = details,
        videos = videos,
        preferredQuality = preferredQuality,
        onProgress = onProgress,
        isCancelled = isCancelled,
        deletePartialOnCancel = deletePartialOnCancel,
    )
    val playbacks = repositoryResolveDownloadPlaybacks(
        requested = video,
        videos = videos,
        preferredQuality = preferredQuality,
    )
    val failures = mutableListOf<String>()

    for (playback in playbacks) {
        val downloaded = storage.withDownload(details.id) {
            tryDownloadOfflinePlayback(storage, playback, request, failures)?.let { target ->
                storage.registerDownloadedPlayback(playback, target, request)
            }
        }
        if (downloaded != null) return@withContext downloaded
    }

    downloadSourceCooldowns?.waitingFor(videos.downloadCandidatesFor(video))?.let { throw it }
    throw IOException(downloadFailureMessage(failures))
}

private data class OfflineDownloadRequest(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val preferredQuality: PreferredQuality,
    val onProgress: (VideoVariant, DownloadProgressInfo) -> Unit,
    val isCancelled: () -> Boolean,
    val deletePartialOnCancel: () -> Boolean,
)

private suspend fun YummyAnimeRepository.tryDownloadOfflinePlayback(
    storage: OfflineAnimeStorage,
    playback: ResolvedPlayback,
    request: OfflineDownloadRequest,
    failures: MutableList<String>,
): File? {
    return runCatching {
        withDownloadSource(playback.video) { downloadOfflinePlaybackFile(storage, playback, request) }
    }.getOrElse { throwable ->
        throwable.rethrowIfDownloadCancelled(request.isCancelled)
        failures += downloadFailureDescription(playback.video, throwable)
        null
    }
}

private suspend fun YummyAnimeRepository.downloadOfflinePlaybackFile(
    storage: OfflineAnimeStorage,
    playback: ResolvedPlayback,
    request: OfflineDownloadRequest,
): File {
    val reportProgress: (DownloadProgressInfo) -> Unit = { progress ->
        request.onProgress(playback.video, progress)
    }
    val stream = playback.stream
    return when {
        stream.isHlsStream() -> downloadHlsAsSingleVideoFile(
            storage = storage,
            video = playback.video,
            stream = stream,
            preferredQuality = request.preferredQuality,
            onProgress = reportProgress,
            isCancelled = request.isCancelled,
            deletePartialOnCancel = request.deletePartialOnCancel,
            bandwidthLimiter = downloadBandwidthLimiter,
        )
        stream.isDashStream() -> throw IOException(
            "DASH offline downloading is not available for this source yet",
        )
        else -> downloadDirectVideo(
            storage = storage,
            video = playback.video,
            stream = stream,
            preferredQuality = request.preferredQuality,
            onProgress = reportProgress,
            isCancelled = request.isCancelled,
            deletePartialOnCancel = request.deletePartialOnCancel,
            bandwidthLimiter = downloadBandwidthLimiter,
        )
    }
}

private fun OfflineAnimeStorage.registerDownloadedPlayback(
    playback: ResolvedPlayback,
    target: File,
    request: OfflineDownloadRequest,
): VideoVariant {
    if (request.isCancelled()) {
        if (request.deletePartialOnCancel()) target.delete()
        throw IllegalStateException("Download cancelled")
    }
    markVideoDownloaded(
        details = request.details,
        videos = request.videos,
        video = playback.video,
        file = target,
        mimeType = target.name.mimeTypeFromFileName() ?: playback.stream.mimeType,
    )
    val downloaded = read(request.details.id)
        ?.videos
        ?.firstOrNull { stored ->
            stored.matchesDownloadedPlayback(playback.video, request.preferredQuality)
        }
        ?: throw IOException("Downloaded file was not confirmed by the offline index")
    request.reportCompletedDownload(playback.video, target)
    return downloaded
}

private fun OfflineDownloadRequest.reportCompletedDownload(video: VideoVariant, target: File) {
    val downloadedBytes = target.length().coerceAtLeast(0L)
    onProgress(
        video,
        DownloadProgressInfo(
            fraction = 1f,
            downloadedBytes = downloadedBytes,
            totalBytes = downloadedBytes,
            bytesPerSecond = 0L,
            qualityTitle = target.downloadQualityTitle(),
            voiceTitle = video.downloadVoiceTitle(),
        ),
    )
}

private fun Throwable.rethrowIfDownloadCancelled(isCancelled: () -> Boolean) {
    throwIfCancellation()
    if (isCancelled() || message.equals("Download cancelled", ignoreCase = true)) {
        throw IllegalStateException("Download cancelled", this)
    }
}

internal fun downloadFailureMessage(failures: List<String>): String {
    val detailsText = failures.take(3).joinToString("; ").takeIf { it.isNotBlank() }
    return buildString {
        append("Could not download episode")
        if (detailsText != null) append(": ").append(detailsText)
    }
}

private fun downloadFailureDescription(video: VideoVariant, throwable: Throwable): String {
    val sourceTitle = video.groupTitle.ifBlank { video.player }
    return "$sourceTitle: ${throwable.message.orEmpty()}"
}

private fun VideoVariant.matchesDownloadedPlayback(
    playbackVideo: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val matchesVoice = id == playbackVideo.id ||
        downloadVoiceSlotKey == playbackVideo.downloadVoiceSlotKey
    return matchesVoice && offlineFiles.any { file ->
        file.matchesPreferredQuality(preferredQuality) && file.bytes > 0L
    }
}

// RepositoryOfflineFallback
internal fun List<VideoVariant>.withOfflineDownloads(
    offlineVideos: List<VideoVariant>,
    details: AnimeDetails,
): List<VideoVariant> {
    val availableOfflineVideos = offlineVideos.filter { it.isOfflineAvailable }
    val offlineById = availableOfflineVideos.groupBy { it.id }
    val offlineBySlot = availableOfflineVideos.groupBy { it.sourceSlotKey }
    val offlineByVoiceSlot = availableOfflineVideos.groupBy { it.downloadVoiceSlotKey }

    return map { video ->
        val offlineMatches = buildList {
            addAll(offlineById[video.id].orEmpty())
            addAll(offlineBySlot[video.sourceSlotKey].orEmpty())
            addAll(offlineByVoiceSlot[video.downloadVoiceSlotKey].orEmpty())
        }.distinctBy { it.id to it.localPlaybackUrl }

        if (offlineMatches.isNotEmpty()) {
            val offlineFiles = offlineMatches
                .flatMap { it.offlineFiles }
                .uniquePlayableOfflineFilesByQuality()
            val primaryFile = offlineFiles.firstOrNull()
            val fallbackOffline = offlineMatches.first()
            video.copy(
                previewUrl = video.previewUrl.ifBlank { fallbackOffline.previewUrl },
                localPlaybackUrl = primaryFile?.playbackUrl ?: fallbackOffline.localPlaybackUrl,
                localMimeType = primaryFile?.mimeType ?: fallbackOffline.localMimeType,
                localBytes = primaryFile?.bytes ?: fallbackOffline.localBytes,
                localFiles = offlineFiles.ifEmpty { fallbackOffline.offlineFiles },
            )
        } else {
            video
        }
    }
}

internal data class UserMarkFilterIds(
    val includedIds: Set<Long>?,
    val excludedIds: Set<Long>,
)

internal fun VideoVariant.withoutOfflinePlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}

internal fun List<OfflineAnimeEntry>.filteredOfflineAnime(
    query: String = "",
    filters: BrowseFilters,
): List<Anime> {
    val normalizedQuery = query.normalizedFilterToken()
    return asSequence()
        .map(OfflineAnimeEntry::toFilterCandidate)
        .filter { it.matches(normalizedQuery, filters) }
        .map(OfflineAnimeFilterCandidate::anime)
        .toList()
        .sortedOffline(filters.sort)
}

private data class OfflineAnimeFilterCandidate(
    val anime: Anime,
    val details: AnimeDetails,
    val year: Int?,
    val rating: Double?,
    val genres: Set<String>,
    val type: String,
    val status: String,
    val episodeCount: Int,
) {
    fun matches(normalizedQuery: String, filters: BrowseFilters): Boolean {
        if (!matchesQuery(normalizedQuery)) return false
        if (!matchesNumericFilters(filters)) return false
        return matchesCategoryFilters(filters)
    }

    private fun matchesQuery(normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        val haystack = listOf(
            anime.title,
            anime.description,
            details.description,
            details.otherTitles.joinToString(" "),
            details.genreTags.joinToString(" ") { it.title },
            details.genres.joinToString(" "),
        ).joinToString(" ").normalizedFilterToken()
        return haystack.contains(normalizedQuery)
    }

    private fun matchesNumericFilters(filters: BrowseFilters): Boolean {
        return year.matchesInclusiveRange(filters.fromYear, filters.toYear) &&
            rating.matchesInclusiveRange(filters.minRating, filters.maxRating) &&
            episodeCount.matchesInclusiveRange(filters.episodeFrom, filters.episodeTo)
    }

    private fun matchesCategoryFilters(filters: BrowseFilters): Boolean {
        return status.matchesAnyFilterToken(filters.statuses) &&
            type.matchesAnyFilterToken(filters.types) &&
            genres.matchesIncludedGenres(filters.genres) &&
            genres.matchesExcludedGenres(filters.excludedGenres)
    }
}

private fun OfflineAnimeEntry.toFilterCandidate(): OfflineAnimeFilterCandidate {
    val normalizedGenres = (details.genreTags.map { it.title } + details.genres + anime.genres)
        .map { it.normalizedFilterToken() }
        .filterTo(mutableSetOf(), String::isNotBlank)
    return OfflineAnimeFilterCandidate(
        anime = anime,
        details = details,
        year = details.year ?: anime.year,
        rating = details.rating ?: anime.rating,
        genres = normalizedGenres,
        type = details.type.ifBlank { anime.type }.normalizedFilterToken(),
        status = details.status.ifBlank { anime.status }.normalizedFilterToken(),
        episodeCount = downloadedVideos.size,
    )
}

private fun <T : Comparable<T>> T?.matchesInclusiveRange(minimum: T?, maximum: T?): Boolean {
    val meetsMinimum = minimum == null || this != null && this >= minimum
    val meetsMaximum = maximum == null || this != null && this <= maximum
    return meetsMinimum && meetsMaximum
}

private fun String.matchesAnyFilterToken(selected: Set<String>): Boolean =
    selected.isEmpty() || selected.any(::matchesFilterToken)

private fun Set<String>.matchesIncludedGenres(selected: Set<String>): Boolean =
    selected.isEmpty() || any { genre -> selected.any(genre::matchesFilterToken) }

private fun Set<String>.matchesExcludedGenres(excluded: Set<String>): Boolean =
    excluded.isEmpty() || none { genre -> excluded.any(genre::matchesFilterToken) }

private fun List<Anime>.sortedOffline(sort: AnimeSort): List<Anime> {
    return when (sort) {
        AnimeSort.Title -> sortedBy { it.title.lowercase() }
        AnimeSort.Views -> sortedByDescending { it.views }
        AnimeSort.Year -> sortedByDescending { it.year ?: 0 }
        AnimeSort.Top,
        AnimeSort.Rating -> sortedByDescending { it.rating ?: 0.0 }
        AnimeSort.RatingCounters,
        AnimeSort.Id -> sortedByDescending { it.id }
        AnimeSort.Random -> shuffled()
    }
}

private fun String.matchesFilterToken(selected: String): Boolean {
    val value = normalizedFilterToken()
    val token = selected.normalizedFilterToken().substringAfterLast("/")
    return value == token || value.contains(token) || token.contains(value)
}

private fun String.normalizedFilterToken(): String {
    return trim()
        .lowercase()
        .replace('\u0451', '\u0435')
        .replace(FilterTokenSeparatorPattern, " ")
        .trim()
}

private val FilterTokenSeparatorPattern = Regex("[^a-z\\u0430-\\u044f0-9]+")
