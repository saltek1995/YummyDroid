package me.yummydroid.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class SourceQualityCacheEntry(
    val animeId: Long,
    val videoId: Long,
    val player: String,
    val dubbing: String,
    val episode: String,
    val urlFingerprint: String,
    val qualities: List<SourceQuality>,
    val maxVideoHeight: Int? = null,
    val updatedAtMs: Long,
)

class SourceQualityCacheStorage(context: Context) {
    private val cacheFile = File(context.filesDir, CACHE_FILE_NAME)

    @Synchronized
    fun applyTo(videos: List<VideoVariant>): List<VideoVariant> {
        val cache = readCache()
        if (cache.isEmpty()) return videos
        val now = System.currentTimeMillis()
        return videos.map { video ->
            val entry = cache[video.id]
                ?.takeIf { it.isFreshFor(video, now) }
                ?: return@map video
            video.copy(sourceQualities = entry.qualities.normalizedSourceQualities())
        }
    }

    @Synchronized
    fun save(video: VideoVariant, stream: ResolvedVideoStream) {
        val qualities = stream.availableQualities
            .ifEmpty { stream.maxVideoHeight?.let { listOf(SourceQuality(height = it)) }.orEmpty() }
            .normalizedSourceQualities()
        if (qualities.isEmpty()) return

        val cache = readCache().toMutableMap()
        cache[video.id] = SourceQualityCacheEntry(
            animeId = video.animeId,
            videoId = video.id,
            player = video.player,
            dubbing = video.dubbing,
            episode = video.episode,
            urlFingerprint = video.url.sourceCacheFingerprint(),
            qualities = qualities,
            maxVideoHeight = stream.maxVideoHeight ?: qualities.mapNotNull { it.height }.maxOrNull(),
            updatedAtMs = System.currentTimeMillis(),
        )
        writeCache(cache)
    }

    @Synchronized
    fun remove(video: VideoVariant) {
        val cache = readCache()
        if (video.id !in cache) return
        writeCache(cache - video.id)
    }

    private fun SourceQualityCacheEntry.isFreshFor(video: VideoVariant, now: Long): Boolean {
        return animeId == video.animeId &&
            videoId == video.id &&
            urlFingerprint == video.url.sourceCacheFingerprint() &&
            now - updatedAtMs <= CACHE_TTL_MS &&
            qualities.isNotEmpty()
    }

    private fun readCache(): Map<Long, SourceQualityCacheEntry> {
        if (!cacheFile.exists()) return emptyMap()
        return runCatching {
            AppJson.decodeFromString<Map<Long, SourceQualityCacheEntry>>(cacheFile.readText())
        }.getOrDefault(emptyMap())
    }

    private fun writeCache(cache: Map<Long, SourceQualityCacheEntry>) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(AppJson.encodeToString(cache))
    }

    private companion object {
        const val CACHE_FILE_NAME = "source_quality_cache.json"
        const val CACHE_TTL_MS = 14L * 24L * 60L * 60L * 1000L
    }
}

internal fun List<SourceQuality>.normalizedSourceQualities(): List<SourceQuality> {
    return asSequence()
        .mapNotNull { quality ->
            val height = quality.height?.takeIf { it in 100..4320 }
            if (height == null && quality.bitrate <= 0) null else quality.copy(height = height)
        }
        .distinctBy { "${it.height}:${it.bitrate}" }
        .sortedWith(compareByDescending<SourceQuality> { it.height ?: 0 }.thenByDescending { it.bitrate })
        .toList()
}

private fun String.sourceCacheFingerprint(): String {
    return trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase()
}
