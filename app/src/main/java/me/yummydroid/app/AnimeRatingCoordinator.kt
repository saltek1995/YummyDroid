package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AnimeRatingSummary

internal data class StagedAnimeRating(
    val animeId: Long,
    val requestedRating: Int?,
    val optimisticRating: Int?,
    internal val hadPreviousRating: Boolean,
    internal val previousRating: Int?,
)

internal data class AnimeRatingUpdate(
    val summary: AnimeRatingSummary,
    val userRating: Int?,
)

internal class AnimeRatingCoordinator(
    private val readRatings: (Long) -> Map<Long, Int>,
    private val saveRatings: (Long, Map<Long, Int?>) -> Unit,
    private val setRating: suspend (Long, Int) -> AnimeRatingSummary,
    private val deleteRating: suspend (Long) -> AnimeRatingSummary,
    private val fetchUserRating: suspend (Long) -> Int?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val knownRatings = mutableMapOf<Long, Int?>()
    private var activeUserId: Long? = null
    private var accountGeneration = 0L

    suspend fun restore(userId: Long?) {
        val generation = ++accountGeneration
        knownRatings.clear()
        val validUserId = userId?.takeIf { it > 0L }
        activeUserId = validUserId
        if (validUserId == null) return

        val restored = try {
            withContext(ioDispatcher) { readRatings(validUserId) }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyMap()
        }
        if (generation != accountGeneration || activeUserId != validUserId) return
        knownRatings.putAll(restored)
    }

    fun clear() {
        accountGeneration += 1L
        activeUserId = null
        knownRatings.clear()
    }

    suspend fun effectiveRating(
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ): Int? {
        val normalized = remoteRating.normalizedRating()
        if (!trustRemote) return normalized ?: knownRatings[animeId]

        val changed = if (normalized != null) {
            knownRatings.put(animeId, normalized) != normalized
        } else {
            knownRatings.remove(animeId) != null
        }
        if (changed) persistBestEffort()
        return normalized
    }

    fun stage(animeId: Long, rating: Int?): StagedAnimeRating {
        val staged = StagedAnimeRating(
            animeId = animeId,
            requestedRating = rating,
            optimisticRating = rating.normalizedRating(),
            hadPreviousRating = knownRatings.containsKey(animeId),
            previousRating = knownRatings[animeId],
        )
        knownRatings[animeId] = staged.optimisticRating
        return staged
    }

    suspend fun submit(staged: StagedAnimeRating): AnimeRatingUpdate {
        return try {
            val summary = staged.requestedRating?.let { rating ->
                setRating(staged.animeId, rating)
            } ?: deleteRating(staged.animeId)
            val confirmedRating = if (staged.requestedRating == null) {
                null
            } else {
                fetchConfirmedRating(staged.animeId)
            }
            val selectedRating = if (staged.requestedRating == null) {
                null
            } else {
                confirmedRating ?: staged.optimisticRating
            }
            knownRatings[staged.animeId] = selectedRating
            persistBestEffort()
            AnimeRatingUpdate(
                summary = summary.copy(userRating = selectedRating),
                userRating = selectedRating,
            )
        } catch (throwable: Throwable) {
            restoreStagedRating(staged)
            throw throwable
        }
    }

    internal fun snapshot(): Map<Long, Int?> = knownRatings.toMap()

    private suspend fun fetchConfirmedRating(animeId: Long): Int? {
        return try {
            fetchUserRating(animeId).normalizedRating()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            null
        }
    }

    private fun restoreStagedRating(staged: StagedAnimeRating) {
        if (staged.hadPreviousRating) {
            knownRatings[staged.animeId] = staged.previousRating
        } else {
            knownRatings.remove(staged.animeId)
        }
    }

    private suspend fun persistBestEffort() {
        val userId = activeUserId ?: return
        val snapshot = knownRatings.toMap()
        try {
            withContext(ioDispatcher) { saveRatings(userId, snapshot) }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
        }
    }
}

private fun Int?.normalizedRating(): Int? = this?.takeIf { it in 1..10 }
