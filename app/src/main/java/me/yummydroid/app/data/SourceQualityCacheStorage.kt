package me.yummydroid.app.data

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable

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
    private var loadedCache: MutableMap<Long, SourceQualityCacheEntry>? = null

    @Synchronized
    fun applyTo(videos: List<VideoVariant>): List<VideoVariant> {
        val cache = cache()
        if (cache.isEmpty()) return videos
        val now = System.currentTimeMillis()
        return videos.map { video ->
            if (video.id <= 0L) return@map video
            val entry = cache[video.id]
                ?.takeIf { it.isFreshFor(video, now) }
                ?: return@map video
            video.copy(sourceQualities = entry.qualities.normalizedSourceQualities())
        }
    }

    @Synchronized
    fun save(video: VideoVariant, stream: ResolvedVideoStream) {
        if (video.id <= 0L) return
        val qualities = stream.availableQualities
            .ifEmpty { stream.maxVideoHeight?.let { listOf(SourceQuality(height = it)) }.orEmpty() }
            .normalizedSourceQualities()
        if (qualities.isEmpty()) return

        val cache = cache()
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
        val cache = cache()
        if (video.id !in cache) return
        cache.remove(video.id)
        writeCache(cache)
    }

    private fun SourceQualityCacheEntry.isFreshFor(video: VideoVariant, now: Long): Boolean {
        return animeId == video.animeId &&
            videoId == video.id &&
            urlFingerprint == video.url.sourceCacheFingerprint() &&
            now - updatedAtMs <= CACHE_TTL_MS &&
            qualities.isNotEmpty()
    }

    private fun readCache(): Map<Long, SourceQualityCacheEntry> {
        return cacheFile.readJsonOrNull<Map<Long, SourceQualityCacheEntry>>().orEmpty()
    }

    private fun cache(): MutableMap<Long, SourceQualityCacheEntry> {
        val cached = loadedCache
        if (cached != null) return cached
        return readCache().toMutableMap().also { loadedCache = it }
    }

    private fun writeCache(cache: Map<Long, SourceQualityCacheEntry>) {
        cacheFile.writeJson(cache)
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

internal fun List<SourceQuality>.bestSourceQualityPerHeight(): List<SourceQuality> {
    return normalizedSourceQualities()
        .filter { (it.height ?: 0) > 0 }
        .groupBy { it.height }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.bitrate } }
        .sortedWith(compareByDescending<SourceQuality> { it.height ?: 0 }.thenByDescending { it.bitrate })
}

private fun String.sourceCacheFingerprint(): String {
    return trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase()
}
