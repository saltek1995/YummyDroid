package me.yummydroid.app.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class CachedAnimeWithVideos(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
)

class AnimeContentCacheStorage(context: Context) {
    private val rootDir = File(context.cacheDir, CACHE_DIR_NAME)
    private val lock = Any()

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
        synchronized(lock) {
            rootDir.deleteRecursively()
        }
    }

    private inline fun <reified T> readFresh(name: String, ttlMs: Long): T? {
        return synchronized(lock) {
            val envelope = cacheFile(name).readJsonOrNull<CacheEnvelope<T>>() ?: return@synchronized null
            val now = System.currentTimeMillis()
            if (now - envelope.savedAtMs > ttlMs) {
                cacheFile(name).delete()
                null
            } else {
                envelope.value
            }
        }
    }

    private inline fun <reified T> write(name: String, value: T) {
        synchronized(lock) {
            cacheFile(name).writeJson(CacheEnvelope(savedAtMs = System.currentTimeMillis(), value = value))
        }
    }

    private fun cacheFile(name: String): File = File(rootDir, "$name.json")

    private fun cacheName(vararg parts: Any?): String {
        val raw = parts.joinToString(separator = "\u001f") { it?.toString().orEmpty() }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun Long?.cachePart(): String = this?.takeIf { it > 0L }?.let { "user:$it" } ?: "anonymous"

    @Serializable
    private data class CacheEnvelope<T>(
        val savedAtMs: Long,
        val value: T,
    )

    private companion object {
        const val CACHE_DIR_NAME = "anime_text_cache"
        const val BROWSE_CACHE_TTL_MS = 20L * 60L * 1000L
        const val SCHEDULE_CACHE_TTL_MS = 15L * 60L * 1000L
        const val DETAILS_CACHE_TTL_MS = 30L * 60L * 1000L
        const val FILTER_CATALOG_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
