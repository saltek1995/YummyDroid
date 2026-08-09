package me.yummydroid.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun YummyAnimeRepository.repositoryUpdateContentLanguage(language: ContentLanguage) {
    contentLanguage = language
    api.updateContentLanguage(language)
}

internal suspend fun YummyAnimeRepository.repositoryGetFeatured(
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> = withContext(Dispatchers.IO) {
    loadRepositoryAnimePage(
        query = null,
        filters = filters,
        offset = offset,
        limit = limit,
    )
}

internal suspend fun YummyAnimeRepository.repositorySearch(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> = withContext(Dispatchers.IO) {
    loadRepositoryAnimePage(
        query = query,
        filters = filters,
        offset = offset,
        limit = limit,
    )
}

private suspend fun YummyAnimeRepository.loadRepositoryAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> {
    if (filters.offlineOnly) {
        offlineFallbackActive = false
        return offlineAnimePage(query.orEmpty(), filters, offset, limit)
    }

    val token = authStorage?.readToken()
    val userMarkIds = resolveUserMarkAnimeIds(filters, token)
    if (userMarkIds?.includedIds != null && userMarkIds.includedIds.isEmpty()) return emptyList()

    readCachedAnimePage(query, filters, offset, limit)?.let { cached ->
        offlineFallbackActive = false
        return cached
    }

    return try {
        offlineFallbackActive = false
        fetchAnimePage(query, filters, offset, limit, token, userMarkIds)
            .filterNot { it.id in userMarkIds?.excludedIds.orEmpty() }
            .also { animes -> saveCachedAnimePage(query, filters, offset, limit, animes) }
    } catch (throwable: Throwable) {
        throwable.throwIfCancellation()
        val offline = offlineFallbackAnimePage(query.orEmpty(), filters, offset, limit)
        if (offline != null) {
            offlineFallbackActive = true
            offline
        } else {
            throw throwable
        }
    }
}

private fun YummyAnimeRepository.readCachedAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime>? {
    val cache = contentCache ?: return null
    return if (query == null) {
        cache.readFeatured(
            language = contentLanguage,
            userId = cacheUserId(),
            filters = filters,
            offset = offset,
            limit = limit,
        )
    } else {
        cache.readSearch(
            language = contentLanguage,
            userId = cacheUserId(),
            query = query,
            filters = filters,
            offset = offset,
            limit = limit,
        )
    }
}

private suspend fun YummyAnimeRepository.fetchAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
    token: String?,
    userMarkIds: UserMarkFilterIds?,
): List<Anime> {
    return if (query == null) {
        api.featuredAnime(
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userMarkIds?.includedIds.orEmpty(),
        )
    } else {
        api.search(
            query = query,
            limit = limit,
            offset = offset,
            filters = filters,
            authToken = token,
            ids = userMarkIds?.includedIds.orEmpty(),
        )
    }
}

private fun YummyAnimeRepository.saveCachedAnimePage(
    query: String?,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
    animes: List<Anime>,
) {
    val cache = contentCache ?: return
    if (query == null) {
        cache.saveFeatured(
            language = contentLanguage,
            userId = cacheUserId(),
            filters = filters,
            offset = offset,
            limit = limit,
            animes = animes,
        )
    } else {
        cache.saveSearch(
            language = contentLanguage,
            userId = cacheUserId(),
            query = query,
            filters = filters,
            offset = offset,
            limit = limit,
            animes = animes,
        )
    }
}

private fun YummyAnimeRepository.offlineAnimePage(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime> {
    return offlineStorage?.readAll()
        .orEmpty()
        .filteredOfflineAnime(query = query, filters = filters)
        .drop(offset)
        .take(limit)
}

private fun YummyAnimeRepository.offlineFallbackAnimePage(
    query: String,
    filters: BrowseFilters,
    offset: Int,
    limit: Int,
): List<Anime>? = offlineAnimePage(
    query = query,
    filters = filters,
    offset = offset,
    limit = limit,
).takeIf { it.isNotEmpty() }

internal suspend fun YummyAnimeRepository.repositoryGetFilterCatalog(): FilterCatalog =
    withContext(Dispatchers.IO) {
        contentCache?.readFilterCatalog(contentLanguage)?.let { return@withContext it }
        api.getFilterCatalog().also { catalog ->
            contentCache?.saveFilterCatalog(contentLanguage, catalog)
        }
    }

internal suspend fun YummyAnimeRepository.repositoryGetSchedule(): List<ScheduleAnime> =
    withContext(Dispatchers.IO) {
        contentCache?.readSchedule(contentLanguage)?.let { return@withContext it }
        api.getSchedule().also { schedule ->
            contentCache?.saveSchedule(contentLanguage, schedule)
        }
    }

private suspend fun YummyAnimeRepository.resolveUserMarkAnimeIds(
    filters: BrowseFilters,
    token: String?,
): UserMarkFilterIds? {
    if (filters.userMarks.isEmpty() && filters.excludedUserMarks.isEmpty()) return null
    val userId = authStorage?.readProfile()?.id
        ?: return UserMarkFilterIds(emptySet(), emptySet())
    val authToken = token?.takeIf { it.isNotBlank() }
        ?: return UserMarkFilterIds(emptySet(), emptySet())
    val selectedMarkIds = filters.userMarks.mapNotNull { it.toIntOrNull() }.toSet()
    val excludedMarkIds = filters.excludedUserMarks.mapNotNull { it.toIntOrNull() }.toSet()

    suspend fun resolve(markIds: Set<Int>): Set<Long> = buildSet {
        markIds.filterNot { it == FAVORITES_FILTER_ID }
            .forEach { listId -> addAll(api.getUserListAnimeIds(userId, listId, authToken)) }

        if (FAVORITES_FILTER_ID in markIds) {
            addAll(api.getUserFavoriteAnimeIds(userId, authToken))
        }
    }

    val includedIds = if (selectedMarkIds.isNotEmpty()) resolve(selectedMarkIds) else null
    val excludedIds = if (excludedMarkIds.isNotEmpty()) resolve(excludedMarkIds) else emptySet()
    return UserMarkFilterIds(
        includedIds = includedIds,
        excludedIds = excludedIds,
    )
}
