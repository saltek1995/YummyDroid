package me.yummydroid.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.Serializable

@Serializable
data class CachedAnimeWithVideos(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
)

class AnimeContentCacheStorage(context: Context) {
    private val rootDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val clearLock = ReentrantReadWriteLock()
    private val fileLocks = ConcurrentHashMap<String, Any>()
    private val memoryCache = ConcurrentHashMap<String, MemoryCacheEntry>()

    fun readFeatured(
        language: ContentLanguage,
        userId: Long?,
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
    ): List<Anime>? = readFresh(
        name = cacheName("featured", language.apiCode, userId.cachePart(), filters.encodeAppJson(), offset, limit),
        ttlMs = BROWSE_CACHE_TTL_MS,
    )

    fun saveFeatured(
        language: ContentLanguage,
        userId: Long?,
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
        animes: List<Anime>,
    ) {
        write(
            name = cacheName("featured", language.apiCode, userId.cachePart(), filters.encodeAppJson(), offset, limit),
            value = animes,
        )
    }

    fun readSearch(
        language: ContentLanguage,
        userId: Long?,
        query: String,
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
    ): List<Anime>? = readFresh(
        name = cacheName("search", language.apiCode, userId.cachePart(), query.normalizedSearchQuery(), filters.encodeAppJson(), offset, limit),
        ttlMs = BROWSE_CACHE_TTL_MS,
    )

    fun saveSearch(
        language: ContentLanguage,
        userId: Long?,
        query: String,
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
        animes: List<Anime>,
    ) {
        write(
            name = cacheName("search", language.apiCode, userId.cachePart(), query.normalizedSearchQuery(), filters.encodeAppJson(), offset, limit),
            value = animes,
        )
    }

    fun readFilterCatalog(language: ContentLanguage): FilterCatalog? = readFresh(
        name = cacheName("filter_catalog", language.apiCode),
        ttlMs = FILTER_CATALOG_CACHE_TTL_MS,
    )

    fun saveFilterCatalog(language: ContentLanguage, catalog: FilterCatalog) {
        write(name = cacheName("filter_catalog", language.apiCode), value = catalog)
    }

    fun readAnimeWithVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
    ): CachedAnimeWithVideos? = readFresh(
        name = cacheName("anime_with_videos", language.apiCode, userId.cachePart(), animeId),
        ttlMs = DETAILS_CACHE_TTL_MS,
    )

    fun saveAnimeWithVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        value: CachedAnimeWithVideos,
    ) {
        write(name = cacheName("anime_with_videos", language.apiCode, userId.cachePart(), animeId), value = value)
    }

    fun readVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
    ): List<VideoVariant>? = readFresh(
        name = cacheName("videos", language.apiCode, userId.cachePart(), animeId),
        ttlMs = DETAILS_CACHE_TTL_MS,
    )

    fun saveVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        videos: List<VideoVariant>,
    ) {
        write(name = cacheName("videos", language.apiCode, userId.cachePart(), animeId), value = videos)
    }

    fun readSchedule(language: ContentLanguage): List<ScheduleAnime>? = readFresh(
        name = cacheName("schedule", language.apiCode),
        ttlMs = SCHEDULE_CACHE_TTL_MS,
    )

    fun saveSchedule(language: ContentLanguage, schedule: List<ScheduleAnime>) {
        write(name = cacheName("schedule", language.apiCode), value = schedule)
    }

    fun clear() {
        clearLock.write {
            rootDir.deleteRecursively()
            fileLocks.clear()
            memoryCache.clear()
        }
    }

    private inline fun <reified T> readFresh(name: String, ttlMs: Long): T? {
        val now = System.currentTimeMillis()
        memoryCache[name]?.freshValue<T>(now, ttlMs)?.let { return it }

        return withCacheFileLock(name) {
            val lockedNow = System.currentTimeMillis()
            memoryCache[name]?.freshValue<T>(lockedNow, ttlMs)?.let { cached ->
                return@withCacheFileLock cached
            }
            val file = cacheFile(name)
            val envelope = file.readJsonOrNull<CacheEnvelope<T>>() ?: run {
                memoryCache.remove(name)
                return@withCacheFileLock null
            }
            if (lockedNow - envelope.savedAtMs > ttlMs) {
                file.delete()
                memoryCache.remove(name)
                null
            } else {
                putMemoryCacheEntry(name, envelope.savedAtMs, envelope.value)
                envelope.value
            }
        }
    }

    private inline fun <reified T> write(name: String, value: T) {
        withCacheFileLock(name) {
            val savedAtMs = System.currentTimeMillis()
            cacheFile(name).writeJson(CacheEnvelope(savedAtMs = savedAtMs, value = value))
            putMemoryCacheEntry(name, savedAtMs, value)
        }
    }

    private fun putMemoryCacheEntry(name: String, savedAtMs: Long, value: Any?) {
        memoryCache[name] = MemoryCacheEntry(savedAtMs = savedAtMs, value = value ?: return)
        trimMemoryCacheIfNeeded()
    }

    private fun trimMemoryCacheIfNeeded() {
        if (memoryCache.size <= MEMORY_CACHE_MAX_ENTRIES) return
        val removeCount = memoryCache.size - MEMORY_CACHE_RETAINED_ENTRIES
        memoryCache.entries
            .sortedBy { entry -> entry.value.savedAtMs }
            .take(removeCount.coerceAtLeast(0))
            .forEach { entry -> memoryCache.remove(entry.key, entry.value) }
    }

    private inline fun <T> withCacheFileLock(name: String, block: () -> T): T {
        return clearLock.read {
            synchronized(fileLocks.getOrPut(name) { Any() }) {
                block()
            }
        }
    }

    private fun cacheFile(name: String): File = File(rootDir, "$name.json")

    private fun cacheName(vararg parts: Any?): String {
        val versionedParts = listOf<Any?>(CACHE_SCHEMA_VERSION) + parts.toList()
        val raw = versionedParts
            .joinToString(separator = "\u001f") { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.toHexString()
    }

    private fun Long?.cachePart(): String = this?.takeIf { it > 0L }?.let { "user:$it" } ?: "anonymous"

    @Serializable
    private data class CacheEnvelope<T>(
        val savedAtMs: Long,
        val value: T,
    )

    private data class MemoryCacheEntry(
        val savedAtMs: Long,
        val value: Any,
    )

    private inline fun <reified T> MemoryCacheEntry.freshValue(
        nowMs: Long,
        ttlMs: Long,
    ): T? {
        if (nowMs - savedAtMs > ttlMs) return null
        @Suppress("UNCHECKED_CAST")
        return value as? T
    }

    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = HEX_CHARS[value ushr 4]
            chars[index * 2 + 1] = HEX_CHARS[value and 0x0F]
        }
        return String(chars)
    }

    private companion object {
        const val CACHE_DIR_NAME = "anime_text_cache"
        const val CACHE_SCHEMA_VERSION = "poster-original-v2"
        const val BROWSE_CACHE_TTL_MS = 20L * 60L * 1000L
        const val SCHEDULE_CACHE_TTL_MS = 15L * 60L * 1000L
        const val DETAILS_CACHE_TTL_MS = 30L * 60L * 1000L
        const val FILTER_CATALOG_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        const val MEMORY_CACHE_MAX_ENTRIES = 96
        const val MEMORY_CACHE_RETAINED_ENTRIES = 72
        val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
