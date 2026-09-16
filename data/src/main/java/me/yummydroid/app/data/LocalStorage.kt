package me.yummydroid.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.serialization.Serializable

class AccountVideoSubscriptionStorage internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences(
        "yummydroid_account_video_subscriptions", Context.MODE_PRIVATE))

    fun rememberVideos(userId: Long, videos: List<VideoVariant>) = update(userId) { state ->
        val records = state.records.toMutableList()
        val pending = state.pending.toMutableMap()
        videos.filter { it.animeId > 0 && it.id > 0 }.groupBy { Triple(it.animeId, it.player, it.dubbing) }.values.forEach { group ->
            val video = group.first()
            val ids = records.filter { it.matches(video) }.flatMap { it.videoIds }.toSet() + group.map { it.id }
            records.removeAll { it.matches(video) }
            records += AccountVideoSubscriptionRecord(video.animeId, video.player, video.playerId, video.dubbing, ids, group.any { it.subscribed })
            ids.forEach(pending::remove)
        }
        AccountVideoSubscriptionSnapshot(records, pending)
    }

    fun rememberSubscriptions(userId: Long, subscriptions: List<VideoSubscription>) = update(userId) { state ->
        val records = state.records.map { record ->
            val matching = subscriptions.filter(record::matches)
            when {
                matching.isNotEmpty() -> record.copy(subscribed = true, videoIds = record.videoIds + matching.map { it.videoId }.filter { it > 0 })
                subscriptions.any { it.matchingVoiceKey.isBlank() && record.samePlayer(it) } -> record
                else -> record.copy(subscribed = false)
            }
        }.toMutableList()
        subscriptions.filter { it.animeId > 0 && it.matchingVoiceKey.isNotBlank() && (it.playerId > 0 || it.player.isNotBlank()) }.forEach { item ->
            if (records.none { it.matches(item) }) records += AccountVideoSubscriptionRecord(
                item.animeId, item.player, item.playerId, item.dubbing, setOf(item.videoId).filterTo(mutableSetOf()) { it > 0 }, true)
        }
        val knownIds = records.flatMap { it.videoIds }.toSet()
        AccountVideoSubscriptionSnapshot(records, if (subscriptions.isEmpty()) emptyMap() else state.pending.filterKeys { it !in knownIds })
    }

    fun setSubscribed(userId: Long, videoId: Long, subscribed: Boolean) {
        if (videoId <= 0) return
        update(userId) { state ->
            val known = state.records.any { videoId in it.videoIds }
            val pending = state.pending.toMutableMap()
            if (known) pending.remove(videoId) else pending[videoId] = subscribed
            AccountVideoSubscriptionSnapshot(state.records.map {
                if (videoId in it.videoIds) it.copy(subscribed = subscribed) else it
            }, pending)
        }
    }

    fun overlay(userId: Long?, videos: List<VideoVariant>): List<VideoVariant> = synchronized(Lock) {
        val state = userId?.takeIf { it > 0 }?.let(::read) ?: AccountVideoSubscriptionSnapshot()
        videos.map { video -> video.copy(subscribed = state.pending[video.id]
            ?: state.records.lastOrNull { it.matches(video) }?.subscribed ?: false) }
    }

    private fun update(userId: Long, transform: (AccountVideoSubscriptionSnapshot) -> AccountVideoSubscriptionSnapshot) {
        if (userId <= 0) return
        synchronized(Lock) { prefs.putJson("user_$userId", transform(read(userId))) }
    }
    private fun read(userId: Long) = prefs.getJsonOrNull<AccountVideoSubscriptionSnapshot>("user_$userId") ?: AccountVideoSubscriptionSnapshot()
    private companion object { val Lock = Any() }
}

@Serializable
private data class AccountVideoSubscriptionSnapshot(
    val records: List<AccountVideoSubscriptionRecord> = emptyList(),
    val pending: Map<Long, Boolean> = emptyMap(),
)

@Serializable
private data class AccountVideoSubscriptionRecord(
    val animeId: Long, val player: String, val playerId: Long = 0, val dubbing: String,
    val videoIds: Set<Long> = emptySet(), val subscribed: Boolean = false,
) {
    private fun asSubscription() = VideoSubscription(animeId, "", "", player, dubbing, playerId)
    fun matches(video: VideoVariant): Boolean {
        if (animeId != video.animeId) return false
        if (player == video.player && dubbing == video.dubbing) return true
        val subscription = asSubscription()
        return subscription.matchingVoiceKey.isNotBlank() && subscription.matchingVoiceKey == video.matchingVoiceKey && subscription.matchesVideoPlayer(video)
    }
    fun samePlayer(subscription: VideoSubscription): Boolean = animeId == subscription.animeId &&
        ((playerId > 0 && playerId == subscription.playerId) || (player.isNotBlank() && player.cleanVideoSourceLabel().equals(subscription.player.cleanVideoSourceLabel(), true)))
    fun matches(subscription: VideoSubscription): Boolean = asSubscription().matchingVoiceKey.let { voice ->
        voice.isNotBlank() && voice == subscription.matchingVoiceKey && samePlayer(subscription)
    }
}

// AnimeContentCacheKey
private const val AnimeContentCacheSchemaVersion = "raw-browse-pages-v3"
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
internal class StoredAuthSession(val token: String, val profile: UserProfile)

class AuthStorage internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun readToken(): String? = synchronized(sessionLock) {
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun readProfile(): UserProfile? = synchronized(sessionLock) {
        val id = prefs.getLong(KEY_PROFILE_ID, 0L).takeIf { it > 0L } ?: return@synchronized null
        val nickname = prefs.getString(KEY_PROFILE_NICKNAME, null)?.takeIf { it.isNotBlank() }
            ?: return@synchronized null
        val roles = prefs.getString(KEY_PROFILE_ROLES, "").orEmpty()
            .split(ROLES_SEPARATOR)
            .filter(String::isNotBlank)
        UserProfile(
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

    fun saveSession(token: String, profile: UserProfile) = synchronized(sessionLock) {
        prefs.edit {
            putString(KEY_TOKEN, token)
            putProfile(profile)
        }
    }

    internal fun readSession(): StoredAuthSession? = synchronized(sessionLock) {
        val token = readToken() ?: return@synchronized null
        val profile = readProfile() ?: return@synchronized null
        StoredAuthSession(token, profile)
    }

    internal fun withSession(session: StoredAuthSession?, action: () -> Unit): Boolean = synchronized(sessionLock) {
        val current = readSession()
        if (current?.token != session?.token || current?.profile?.id != session?.profile?.id) return@synchronized false
        action()
        true
    }

    internal fun refreshToken(expectedToken: String, token: String): Boolean = synchronized(sessionLock) {
        if (readToken() != expectedToken) return@synchronized false
        prefs.edit { putString(KEY_TOKEN, token) }
        true
    }

    internal fun refreshProfile(expectedToken: String, profile: UserProfile): Boolean = synchronized(sessionLock) {
        if (readToken() != expectedToken) return@synchronized false
        prefs.edit { putProfile(profile) }
        true
    }

    fun updateUnreadNotifications(
        profileId: Long,
        count: Int,
        onUpdated: () -> Unit = {},
    ): Boolean = synchronized(sessionLock) {
        if (readToken() == null || prefs.getLong(KEY_PROFILE_ID, 0L) != profileId) return@synchronized false
        prefs.edit { putInt(KEY_PROFILE_NOTIFICATIONS, count.coerceAtLeast(0)) }
        onUpdated()
        true
    }

    internal fun clearIfToken(expectedToken: String?): Boolean = synchronized(sessionLock) {
        if (readToken() != expectedToken) return@synchronized false
        clear()
        true
    }

    fun clear() = synchronized(sessionLock) {
        prefs.edit { clear() }
    }

    private fun SharedPreferences.Editor.putProfile(profile: UserProfile) {
        putLong(KEY_PROFILE_ID, profile.id)
        putString(KEY_PROFILE_NICKNAME, profile.nickname)
        putString(KEY_PROFILE_AVATAR, profile.avatarUrl)
        putString(KEY_PROFILE_ABOUT, profile.about)
        putBoolean(KEY_PROFILE_BANNED, profile.banned)
        putString(KEY_PROFILE_ROLES, profile.roles.joinToString(ROLES_SEPARATOR))
        putInt(KEY_PROFILE_NOTIFICATIONS, profile.unreadNotifications)
        putInt(KEY_PROFILE_MESSAGES, profile.unreadMessages)
    }

    private companion object {
        // Repository and background workers construct separate storage instances.
        val sessionLock = Any()
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
class AnimeContentCacheStorage internal constructor(private val rootDir: File) {
    constructor(context: Context) : this(File(context.cacheDir, CACHE_DIR_NAME))

    private class CacheState {
        val clearLock = ReentrantReadWriteLock()
        val fileLocks = ConcurrentHashMap<String, Any>()
        val memoryCache = ConcurrentHashMap<String, MemoryCacheEntry>()
        var generation = 0L
    }

    private val state = states.getOrPut(rootDir.canonicalFile) { CacheState() }

    internal fun generation(): Long = state.clearLock.read { state.generation }

    internal fun publishIfCurrent(generation: Long, action: () -> Unit) = state.clearLock.read {
        if (state.generation == generation) action()
    }

    fun readFeatured(
        language: ContentLanguage,
        userId: Long?,
        filters: BrowseFilters,
        offset: Int,
        limit: Int,
        includedIds: Set<Long>? = null,
    ): List<Anime>? = readFresh(
        name = animeContentCacheName(
            "featured",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            filters.encodeAppJson(),
            includedIds?.sorted()?.joinToString(","),
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
        includedIds: Set<Long>? = null,
    ) {
        write(
            name = animeContentCacheName(
                "featured",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                filters.encodeAppJson(),
                includedIds?.sorted()?.joinToString(","),
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
        includedIds: Set<Long>? = null,
    ): List<Anime>? = readFresh(
        name = animeContentCacheName(
            "search",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            query.normalizedSearchQuery(),
            filters.encodeAppJson(),
            includedIds?.sorted()?.joinToString(","),
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
        includedIds: Set<Long>? = null,
    ) {
        write(
            name = animeContentCacheName(
                "search",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                query.normalizedSearchQuery(),
                filters.encodeAppJson(),
                includedIds?.sorted()?.joinToString(","),
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
    ): CachedAnimeWithVideos? = withVideoCacheLock(language, userId, animeId) { readFresh(
        name = animeContentCacheName(
            "anime_with_videos",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            animeId,
        ),
        ttlMs = DETAILS_CACHE_TTL_MS,
    ) }

    fun saveAnimeWithVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        value: CachedAnimeWithVideos,
    ) = withVideoCacheLock(language, userId, animeId) {
        write(
            name = animeContentCacheName(
                "anime_with_videos",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                animeId,
            ),
            value = value,
        )
        write(animeContentCacheName("videos", language.apiCode, userId.animeContentCacheUserPart(), animeId), value.videos)
    }

    fun readVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
    ): List<VideoVariant>? = withVideoCacheLock(language, userId, animeId) { readFresh(
        name = animeContentCacheName(
            "videos",
            language.apiCode,
            userId.animeContentCacheUserPart(),
            animeId,
        ),
        ttlMs = DETAILS_CACHE_TTL_MS,
    ) }

    fun saveVideos(
        language: ContentLanguage,
        userId: Long?,
        animeId: Long,
        videos: List<VideoVariant>,
    ) = withVideoCacheLock(language, userId, animeId) {
        write(
            name = animeContentCacheName(
                "videos",
                language.apiCode,
                userId.animeContentCacheUserPart(),
                animeId,
            ),
            value = videos,
        )
        // A videos-only refresh must not leave an older combined snapshot readable.
        val combinedName = animeContentCacheName("anime_with_videos", language.apiCode, userId.animeContentCacheUserPart(), animeId)
        cacheFile(combinedName).delete()
        state.memoryCache.remove(combinedName)
        Unit
    }

    private inline fun <T> withVideoCacheLock(language: ContentLanguage, userId: Long?, animeId: Long, block: () -> T): T =
        withCacheFileLock(animeContentCacheName("video_snapshot", language.apiCode, userId.animeContentCacheUserPart(), animeId), block)

    fun readSchedule(language: ContentLanguage): List<ScheduleAnime>? = readFresh(
        name = animeContentCacheName("schedule", language.apiCode),
        ttlMs = SCHEDULE_CACHE_TTL_MS,
    )

    fun saveSchedule(language: ContentLanguage, schedule: List<ScheduleAnime>) {
        write(name = animeContentCacheName("schedule", language.apiCode), value = schedule)
    }

    fun clear() {
        state.clearLock.write {
            state.generation += 1L
            rootDir.deleteRecursively()
            state.fileLocks.clear()
            state.memoryCache.clear()
        }
    }

    fun invalidateAccountContent() = state.clearLock.write {
        state.generation += 1L
        val publicKeys = ContentLanguage.entries.flatMap { language ->
            listOf(animeContentCacheName("filter_catalog", language.apiCode), animeContentCacheName("schedule", language.apiCode))
        }.toSet()
        rootDir.listFiles().orEmpty().filterNot { it.nameWithoutExtension in publicKeys }.forEach { it.delete() }
        state.memoryCache.keys.removeIf { it !in publicKeys }
        state.fileLocks.keys.removeIf { it !in publicKeys }
    }

    private inline fun <reified T> readFresh(name: String, ttlMs: Long): T? {
        val now = System.currentTimeMillis()
        state.memoryCache[name]?.freshValue<T>(now, ttlMs)?.let { return it }

        return withCacheFileLock(name) {
            val lockedNow = System.currentTimeMillis()
            state.memoryCache[name]?.freshValue<T>(lockedNow, ttlMs)?.let { cached ->
                return@withCacheFileLock cached
            }
            val file = cacheFile(name)
            val envelope = file.readJsonOrNull<CacheEnvelope<T>>() ?: run {
                state.memoryCache.remove(name)
                return@withCacheFileLock null
            }
            if (lockedNow - envelope.savedAtMs > ttlMs) {
                file.delete()
                state.memoryCache.remove(name)
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
        state.memoryCache[name] = MemoryCacheEntry(savedAtMs = savedAtMs, value = value ?: return)
        trimMemoryCacheIfNeeded()
    }

    private fun trimMemoryCacheIfNeeded() {
        if (state.memoryCache.size <= MEMORY_CACHE_MAX_ENTRIES) return
        val removeCount = state.memoryCache.size - MEMORY_CACHE_RETAINED_ENTRIES
        state.memoryCache.entries
            .sortedBy { entry -> entry.value.savedAtMs }
            .take(removeCount.coerceAtLeast(0))
            .forEach { entry -> state.memoryCache.remove(entry.key, entry.value) }
    }

    private inline fun <T> withCacheFileLock(name: String, block: () -> T): T {
        return state.clearLock.read {
            synchronized(state.fileLocks.getOrPut(name) { Any() }) {
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
        val states = ConcurrentHashMap<File, CacheState>()
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
        return prefs.getJsonOrNull<Anime>(animeId.key)?.copy(userRating = null)
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
        prefs.putJson(anime.id.key, anime.copy(userRating = null))
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

@Serializable
data class PlaybackSelection(
    val animeId: Long,
    val groupKey: String,
    val voiceKey: String,
    val sourceKey: String,
    val updatedAtMs: Long,
)

// PlaybackProgressIdentity
fun List<PlaybackProgress>.distinctLatestByEpisode(): List<PlaybackProgress> {
    return groupingBy { it.progressSyncKey() }
        .reduce { _, latest, entry -> if (entry.updatedAtMs > latest.updatedAtMs) entry else latest }
        .values
        .sortedWith(playbackHistoryOrder)
}

private val playbackHistoryOrder =
    compareBy<PlaybackProgress> { it.episode.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.videoId }

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

internal fun PlaybackProgress.shouldReplaceCachedProgress(current: PlaybackProgress?): Boolean {
    if (current == null) return true
    return positionMs > current.positionMs ||
        positionMs == current.positionMs && updatedAtMs > current.updatedAtMs
}

// SharedPreferencesPlaybackProgressStorage
class PlaybackProgressStorage internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    // In-flight remote snapshots must not replace intervening player saves or resets.
    private var historyRevision = 0L

    @Synchronized
    fun readHistoryRevision(): Long = historyRevision

    @Synchronized
    fun replaceAllIfRevision(history: List<PlaybackProgress>, expectedRevision: Long): Long? {
        if (historyRevision != expectedRevision) return null
        replaceAll(history)
        return historyRevision
    }

    @Synchronized
    fun replaceAnimeIfRevision(animeId: Long, history: List<PlaybackProgress>, expectedRevision: Long): Long? {
        if (historyRevision != expectedRevision) return null
        replaceAnime(animeId, history)
        return historyRevision
    }

    @Synchronized
    fun withHistoryRevision(expectedRevision: Long, action: () -> Unit): Boolean {
        if (historyRevision != expectedRevision) return false
        action()
        return true
    }

    @Synchronized
    fun read(animeId: Long): PlaybackProgress? {
        return readAnimeHistory(animeId).maxByOrNull { it.updatedAtMs }
    }

    @Synchronized
    fun readSelection(animeId: Long): PlaybackSelection? {
        if (animeId <= 0L) return null
        return prefs.getJsonOrNull<PlaybackSelection>(animeId.selectionKey)
            ?.normalized()
            ?.takeIf { it.animeId == animeId && it.groupKey.isNotBlank() && it.sourceKey.isNotBlank() }
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
        historyRevision += 1L
        val normalized = progress.normalized()
        val history = (readAnimeHistory(progress.animeId) + normalized).distinctLatestByEpisode()
        prefs.putJson(progress.animeId.historyKey, history)
    }

    @Synchronized
    fun saveSelection(selection: PlaybackSelection) {
        val normalized = selection.normalized()
        if (normalized.animeId <= 0L || normalized.groupKey.isBlank() || normalized.sourceKey.isBlank()) return
        val current = readSelection(normalized.animeId)
        if (current?.sameTargetAs(normalized) == true) return
        prefs.putJson(normalized.animeId.selectionKey, normalized)
    }

    @Synchronized
    fun replaceAll(history: List<PlaybackProgress>) {
        historyRevision += 1L
        val replacements = history.groupBy { it.animeId }
            .mapValues { (_, entries) -> replacementHistory(entries).encodeAppJson() }
        val historyKeys = prefs.all.keys.filter { it.startsWith(HISTORY_KEY_PREFIX) }
        prefs.edit {
            historyKeys.forEach(::remove)
            replacements.forEach { (animeId, json) -> putString(animeId.historyKey, json) }
        }
    }

    @Synchronized
    fun replaceAnime(animeId: Long, history: List<PlaybackProgress>) {
        historyRevision += 1L
        val entries = history.filter { it.animeId == animeId }
        val json = entries.takeIf { it.isNotEmpty() }?.let { replacementHistory(it).encodeAppJson() }
        prefs.edit {
            if (json == null) remove(animeId.historyKey) else putString(animeId.historyKey, json)
        }
    }

    private fun replacementHistory(entries: List<PlaybackProgress>): List<PlaybackProgress> {
        val normalized = entries.map { it.normalized() }
        val result = normalized.distinctLatestByEpisode()
        // Stable ordering of equal episode/video keys can depend on intermediate winners.
        // Preserve that rare legacy case in memory, without repeated preference IO.
        if ((1 until result.size).any { playbackHistoryOrder.compare(result[it - 1], result[it]) == 0 }) {
            return normalized.fold(emptyList()) { accumulated, progress ->
                (accumulated + progress).distinctLatestByEpisode()
            }
        }
        return result
    }

    @Synchronized
    fun saveIfNewer(progress: PlaybackProgress): PlaybackProgress {
        val normalized = progress.normalized()
        val history = readAnimeHistory(progress.animeId)
        val current = history
            .firstOrNull { it.sameProgressEpisodeAs(normalized) }
        val selected = if (normalized.shouldReplaceCachedProgress(current)) {
            normalized
        } else {
            current
        }
        if (selected != null && selected != current) {
            val updated = (history + selected).distinctLatestByEpisode()
            if (updated != history) {
                historyRevision += 1L
                prefs.putJson(progress.animeId.historyKey, updated)
            }
        }
        return selected ?: normalized
    }

    @Synchronized
    fun clearAnime(animeId: Long) {
        historyRevision += 1L
        prefs.edit {
            remove(animeId.historyKey)
        }
    }

    @Synchronized
    fun clear() {
        clearHistory()
    }

    private fun clearHistory() {
        historyRevision += 1L
        val historyKeys = prefs.all.keys.filter { it.startsWith(HISTORY_KEY_PREFIX) }
        prefs.edit {
            historyKeys.forEach(::remove)
        }
    }

    private fun PlaybackProgress.normalized(): PlaybackProgress {
        return copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    private fun PlaybackSelection.normalized(): PlaybackSelection {
        return copy(
            groupKey = groupKey.trim(),
            voiceKey = voiceKey.trim(),
            sourceKey = sourceKey.trim(),
            updatedAtMs = updatedAtMs.coerceAtLeast(0L),
        )
    }

    private fun PlaybackSelection.sameTargetAs(other: PlaybackSelection): Boolean {
        return animeId == other.animeId &&
            groupKey == other.groupKey &&
            voiceKey == other.voiceKey &&
            sourceKey == other.sourceKey
    }

    private val Long.historyKey: String
        get() = "$HISTORY_KEY_PREFIX$this"

    private val Long.selectionKey: String
        get() = "$SELECTION_KEY_PREFIX$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_playback_progress"
        const val HISTORY_KEY_PREFIX = "anime_history_"
        const val SELECTION_KEY_PREFIX = "anime_selection_"
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

class SourceQualityCacheStorage internal constructor(
    private val cacheFile: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(File(context.filesDir, CACHE_FILE_NAME))

    fun applyTo(videos: List<VideoVariant>): List<VideoVariant> = synchronized(lock) {
        val cache = cache()
        if (cache.isEmpty()) return videos
        val now = nowMs()
        return videos.map { video ->
            if (video.id <= 0L) return@map video
            val entry = cache[video.id]
                ?.takeIf { it.isFreshFor(video, now) }
                ?: return@map video
            video.copy(sourceQualities = entry.qualities.normalizedSourceQualities())
        }
    }

    fun save(video: VideoVariant, stream: ResolvedVideoStream) = synchronized(lock) {
        if (video.id <= 0L) return
        val qualities = stream.availableQualities
            .ifEmpty { stream.maxVideoHeight?.let { listOf(SourceQuality(height = it)) }.orEmpty() }
            .normalizedSourceQualities()
        if (qualities.isEmpty()) return

        val cache = cache()
        val now = nowMs()
        cache.entries.removeAll { now - it.value.updatedAtMs > CACHE_TTL_MS }
        cache[video.id] = SourceQualityCacheEntry(
            animeId = video.animeId,
            videoId = video.id,
            player = video.player,
            dubbing = video.dubbing,
            episode = video.episode,
            urlFingerprint = video.url.sourceCacheFingerprint(),
            qualities = qualities,
            maxVideoHeight = stream.maxVideoHeight ?: qualities.mapNotNull { it.height }.maxOrNull(),
            updatedAtMs = now,
        )
        writeCache(cache)
    }

    fun clear() = synchronized(lock) {
        caches.remove(cacheFile.canonicalFile)
        cacheFile.delete()
        Unit
    }

    private fun SourceQualityCacheEntry.isFreshFor(video: VideoVariant, now: Long): Boolean {
        return animeId == video.animeId &&
            videoId == video.id &&
            player == video.player && dubbing == video.dubbing && episode == video.episode &&
            urlFingerprint == video.url.sourceCacheFingerprint() &&
            now - updatedAtMs <= CACHE_TTL_MS &&
            qualities.isNotEmpty()
    }

    private fun readCache(): Map<Long, SourceQualityCacheEntry> {
        return cacheFile.readJsonOrNull<Map<Long, SourceQualityCacheEntry>>().orEmpty()
    }

    private fun cache(): MutableMap<Long, SourceQualityCacheEntry> {
        return caches.getOrPut(cacheFile.canonicalFile) { readCache().toMutableMap() }
    }

    private fun writeCache(cache: Map<Long, SourceQualityCacheEntry>) {
        cacheFile.writeJson(cache)
    }

    private companion object {
        val lock = Any()
        val caches = mutableMapOf<File, MutableMap<Long, SourceQualityCacheEntry>>()
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
}

private fun String.sourceCacheFingerprint(): String {
    return trim()
        .substringBefore('#')
}
