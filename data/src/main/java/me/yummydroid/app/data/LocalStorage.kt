package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.Serializable

// AnimeContentCacheKey
private const val AnimeContentCacheSchemaVersion = "poster-original-v2"
private val AnimeContentCacheHexChars = "0123456789abcdef".toCharArray()

internal fun animeContentCacheName(vararg parts: Any?): String {
    val versionedParts = listOf<Any?>(AnimeContentCacheSchemaVersion) + parts.toList()
    val raw = versionedParts.joinToString(separator = "\u001f") { it?.toString().orEmpty() }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
    return digest.toAnimeContentCacheHexString()
}

internal fun Long?.animeContentCacheUserPart(): String {
    return this?.takeIf { it > 0L }?.let { "user:$it" } ?: "anonymous"
}

private fun ByteArray.toAnimeContentCacheHexString(): String {
    val chars = CharArray(size * 2)
    forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        chars[index * 2] = AnimeContentCacheHexChars[value ushr 4]
        chars[index * 2 + 1] = AnimeContentCacheHexChars[value and 0x0F]
    }
    return String(chars)
}

// AnimeRatingStateStorage
class AnimeRatingStateStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(userId: Long): Map<Long, Int> {
        return prefs.getJsonOrNull<StoredAnimeRatings>(key(userId))
            ?.items
            ?.mapNotNull { item ->
                val animeId = item.animeId.takeIf { it > 0L } ?: return@mapNotNull null
                val rating = item.rating.takeIf { it in 1..10 } ?: return@mapNotNull null
                animeId to rating
            }
            ?.toMap()
            .orEmpty()
    }

    fun save(userId: Long, ratingsByAnime: Map<Long, Int?>) {
        val items = ratingsByAnime
            .mapNotNull { (animeId, rating) ->
                if (animeId <= 0L) return@mapNotNull null
                val normalizedRating = rating?.takeIf { it in 1..10 } ?: return@mapNotNull null
                StoredAnimeRating(animeId = animeId, rating = normalizedRating)
            }
            .sortedBy { it.animeId }
        prefs.putJson(key(userId), StoredAnimeRatings(items))
    }

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"

    private companion object {
        const val PREFS_NAME = "yummydroid_anime_rating_state"
        const val KEY_PREFIX = "ratings_"
    }
}

@Serializable
private data class StoredAnimeRatings(
    val items: List<StoredAnimeRating> = emptyList(),
)

@Serializable
private data class StoredAnimeRating(
    val animeId: Long,
    val rating: Int,
)

// AuthStorage
class AuthStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readToken(): String? {
        return prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun readProfile(): UserProfile? {
        val id = prefs.getLong(KEY_PROFILE_ID, 0L).takeIf { it > 0L } ?: return null
        val nickname = prefs.getString(KEY_PROFILE_NICKNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val roles = prefs.getString(KEY_PROFILE_ROLES, "").orEmpty()
            .split(ROLES_SEPARATOR)
            .filter { it.isNotBlank() }

        return UserProfile(
            id = id,
            nickname = nickname,
            avatarUrl = prefs.getString(KEY_PROFILE_AVATAR, "").orEmpty(),
            about = prefs.getString(KEY_PROFILE_ABOUT, "").orEmpty(),
            banned = prefs.getBoolean(KEY_PROFILE_BANNED, false),
            roles = roles,
            unreadNotifications = prefs.getInt(KEY_PROFILE_NOTIFICATIONS, 0),
            unreadMessages = prefs.getInt(KEY_PROFILE_MESSAGES, 0),
        )
    }

    fun saveToken(token: String) {
        prefs.edit {
            putString(KEY_TOKEN, token)
        }
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit {
            putLong(KEY_PROFILE_ID, profile.id)
            putString(KEY_PROFILE_NICKNAME, profile.nickname)
            putString(KEY_PROFILE_AVATAR, profile.avatarUrl)
            putString(KEY_PROFILE_ABOUT, profile.about)
            putBoolean(KEY_PROFILE_BANNED, profile.banned)
            putString(KEY_PROFILE_ROLES, profile.roles.joinToString(ROLES_SEPARATOR))
            putInt(KEY_PROFILE_NOTIFICATIONS, profile.unreadNotifications)
            putInt(KEY_PROFILE_MESSAGES, profile.unreadMessages)
        }
    }

    fun clear() {
        prefs.edit {
            clear()
        }
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_auth"
        const val KEY_TOKEN = "access_token"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_PROFILE_NICKNAME = "profile_nickname"
        const val KEY_PROFILE_AVATAR = "profile_avatar"
        const val KEY_PROFILE_ABOUT = "profile_about"
        const val KEY_PROFILE_BANNED = "profile_banned"
        const val KEY_PROFILE_ROLES = "profile_roles"
        const val KEY_PROFILE_NOTIFICATIONS = "profile_notifications"
        const val KEY_PROFILE_MESSAGES = "profile_messages"
        const val ROLES_SEPARATOR = "|"
    }
}

// FileAnimeContentCacheStorage
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
        name = animeContentCacheName(
            "featured",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            filters.encodeAppJson(),
            offset,
            limit,
        ),
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
            name = animeContentCacheName(
                "featured",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                filters.encodeAppJson(),
                offset,
                limit,
            ),
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
        name = animeContentCacheName(
            "search",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            query.normalizedSearchQuery(),
            filters.encodeAppJson(),
            offset,
            limit,
        ),
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
            name = animeContentCacheName(
                "search",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                query.normalizedSearchQuery(),
                filters.encodeAppJson(),
                offset,
                limit,
            ),
            value = animes,
        )
    }

    fun readFilterCatalog(language: ContentLanguage): FilterCatalog? = readFresh(
        name = animeContentCacheName("filter_catalog", language.apiCode),
        ttlMs = FILTER_CATALOG_CACHE_TTL_MS,
    )

    fun saveFilterCatalog(language: ContentLanguage, catalog: FilterCatalog) {
        write(name = animeContentCacheName("filter_catalog", language.apiCode), value = catalog)
    }

    fun readAnimeWithVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
    ): CachedAnimeWithVideos? = readFresh(
        name = animeContentCacheName(
            "anime_with_videos",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            animeId,
        ),
        ttlMs = DETAILS_CACHE_TTL_MS,
    )

    fun saveAnimeWithVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        value: CachedAnimeWithVideos,
    ) {
        write(
            name = animeContentCacheName(
                "anime_with_videos",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                animeId,
            ),
            value = value,
        )
    }

    fun readVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
    ): List<VideoVariant>? = readFresh(
        name = animeContentCacheName(
            "videos",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            animeId,
        ),
        ttlMs = DETAILS_CACHE_TTL_MS,
    )

    fun saveVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        videos: List<VideoVariant>,
    ) {
        write(
            name = animeContentCacheName(
                "videos",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                animeId,
            ),
            value = videos,
        )
    }

    fun readSchedule(language: ContentLanguage): List<ScheduleAnime>? = readFresh(
        name = animeContentCacheName("schedule", language.apiCode),
        ttlMs = SCHEDULE_CACHE_TTL_MS,
    )

    fun saveSchedule(language: ContentLanguage, schedule: List<ScheduleAnime>) {
        write(name = animeContentCacheName("schedule", language.apiCode), value = schedule)
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

    private companion object {
        const val CACHE_DIR_NAME = "anime_text_cache"
        const val BROWSE_CACHE_TTL_MS = 20L * 60L * 1000L
        const val SCHEDULE_CACHE_TTL_MS = 15L * 60L * 1000L
        const val DETAILS_CACHE_TTL_MS = 30L * 60L * 1000L
        const val FILTER_CATALOG_CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        const val MEMORY_CACHE_MAX_ENTRIES = 96
        const val MEMORY_CACHE_RETAINED_ENTRIES = 72
    }
}

// HistoryAnimeCacheStorage
class HistoryAnimeCacheStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(animeId: Long): Anime? {
        if (animeId <= 0L) return null
        return prefs.getJsonOrNull<Anime>(animeId.key)
    }

    fun readMany(animeIds: Collection<Long>): Map<Long, Anime> {
        return animeIds
            .asSequence()
            .distinct()
            .mapNotNull { animeId -> read(animeId)?.let { animeId to it } }
            .toMap()
    }

    fun save(anime: Anime) {
        if (anime.id <= 0L) return
        prefs.putJson(anime.id.key, anime)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private val Long.key: String
        get() = "anime_$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_history_anime_cache"
    }
}

// PlaybackProgress
@Serializable
data class PlaybackProgress(
    val animeId: Long,
    val videoId: Long,
    val animeTitle: String = "",
    val posterUrl: String = "",
    val groupKey: String,
    val episode: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

// PlaybackProgressIdentity
fun List<PlaybackProgress>.distinctLatestByEpisode(): List<PlaybackProgress> {
    return groupBy { it.progressSyncKey() }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedWith(compareBy<PlaybackProgress> { it.episode.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.videoId })
}

fun PlaybackProgress.sameProgressEpisodeAs(other: PlaybackProgress): Boolean {
    return animeId == other.animeId && progressSyncKey() == other.progressSyncKey()
}

fun PlaybackProgress.progressSyncKey(): String {
    val episodeKey = episode.trim()
    if (episodeKey.isNotBlank()) {
        val voiceKey = groupKey.substringAfter('|', groupKey).normalizedVoiceKey()
        return if (voiceKey.isNotBlank()) {
            "anime:$animeId:episode:$episodeKey:voice:$voiceKey"
        } else {
            "anime:$animeId:episode:$episodeKey"
        }
    }
    return when {
        groupKey.isNotBlank() -> "anime:$animeId:group:$groupKey"
        videoId > 0L -> "anime:$animeId:video:$videoId"
        else -> "anime:$animeId"
    }
}

// SharedPreferencesPlaybackProgressStorage
class PlaybackProgressStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun read(animeId: Long): PlaybackProgress? {
        return readAnimeHistory(animeId).maxByOrNull { it.updatedAtMs }
    }

    @Synchronized
    fun readAll(): List<PlaybackProgress> {
        return prefs.all.keys
            .filter { it.startsWith(HISTORY_KEY_PREFIX) }
            .flatMap { key -> prefs.getJsonOrNull<List<PlaybackProgress>>(key).orEmpty() }
            .filter { it.animeId > 0L && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    @Synchronized
    fun readAnimeHistory(animeId: Long): List<PlaybackProgress> {
        return prefs.getJsonOrNull<List<PlaybackProgress>>(animeId.historyKey).orEmpty()
            .filter { it.animeId == animeId && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    @Synchronized
    fun save(progress: PlaybackProgress) {
        val normalized = progress.normalized()
        val history = (readAnimeHistory(progress.animeId) + normalized).distinctLatestByEpisode()
        prefs.putJson(progress.animeId.historyKey, history)
    }

    @Synchronized
    fun saveIfNewer(progress: PlaybackProgress): PlaybackProgress {
        val normalized = progress.normalized()
        val current = readAnimeHistory(progress.animeId)
            .firstOrNull { it.sameProgressEpisodeAs(normalized) }
        val selected = if (progress.updatedAtMs > (current?.updatedAtMs ?: Long.MIN_VALUE)) {
            normalized
        } else {
            current
        }
        selected?.let(::save)
        return selected ?: normalized
    }

    @Synchronized
    fun clearAnime(animeId: Long) {
        prefs.edit {
            remove(animeId.historyKey)
        }
    }

    @Synchronized
    fun clear() {
        prefs.edit {
            clear()
        }
    }

    private fun PlaybackProgress.normalized(): PlaybackProgress {
        return copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    private val Long.historyKey: String
        get() = "$HISTORY_KEY_PREFIX$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_playback_progress"
        const val HISTORY_KEY_PREFIX = "anime_history_"
    }
}

// SourceQualityCacheStorage
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

    @Synchronized
    fun clear() {
        loadedCache = mutableMapOf()
        cacheFile.delete()
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

fun List<SourceQuality>.normalizedSourceQualities(): List<SourceQuality> {
    return asSequence()
        .mapNotNull { quality ->
            val height = quality.height.validVideoQualityHeight()
            if (height == null && quality.bitrate <= 0) null else quality.copy(height = height, bitrate = 0)
        }
        .distinctBy { it.height }
        .sortedByDescending { it.height ?: 0 }
        .toList()
}

fun List<SourceQuality>.bestSourceQualityPerHeight(): List<SourceQuality> {
    return normalizedSourceQualities()
        .filter { (it.height ?: 0) > 0 }
        .distinctBy { it.height }
        .sortedByDescending { it.height ?: 0 }
}

private fun String.sourceCacheFingerprint(): String {
    return trim()
        .substringBefore('#')
        .substringBefore('?')
        .lowercase()
}
