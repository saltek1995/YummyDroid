package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.withVoiceSubscriptionState

internal data class StagedVideoSubscriptionRemoval(
    val target: SubscriptionUnsubscribeTarget,
    internal val previousHints: List<VideoSubscriptionHint>,
)

internal class VideoSubscriptionCoordinator(
    private val readHints: (Long) -> List<VideoSubscriptionHint>,
    private val saveHints: (Long, List<VideoSubscriptionHint>) -> Unit,
    private val fetchSubscriptions: suspend () -> List<VideoSubscription>,
    private val fetchVideos: suspend (Long) -> List<VideoVariant>,
    private val fetchAnime: suspend (Long) -> AnimeDetails,
    private val subscribeVideo: suspend (Long) -> Boolean,
    private val unsubscribeVideo: suspend (Long) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var subscriptionHints: List<VideoSubscriptionHint> = emptyList()

    suspend fun restoreHints(userId: Long?) {
        subscriptionHints = emptyList()
        val validUserId = userId?.takeIf { it > 0L } ?: return
        subscriptionHints = withContext(ioDispatcher) { readHints(validUserId) }
    }

    fun clearHints() {
        subscriptionHints = emptyList()
    }

    fun canonicalizeForVideos(
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ): List<VideoSubscription> {
        return canonicalizeVideoSubscriptionsForVideos(
            subscriptions = subscriptions,
            videos = videos,
            hints = subscriptionHints,
            title = title,
            posterUrl = posterUrl,
        )
    }

    suspend fun synchronize(userId: Long?): List<VideoSubscription> {
        return unsubscribeCompletedAnimeSubscriptions(
            subscriptions = loadResolvedSubscriptions(),
            userId = userId,
        )
    }

    suspend fun loadTargets(
        animeId: Long,
        voiceKey: String,
        fallbackVideos: List<VideoVariant>,
    ): List<VideoVariant> {
        val loadedVideos = fallbackVideos
            .takeIf { videos -> videos.any { it.animeId == animeId && it.matchingVoiceKey == voiceKey } }
            ?: fetchVideos(animeId)
        return loadedVideos
            .filter { it.animeId == animeId && it.matchingVoiceKey == voiceKey && it.id > 0L }
            .distinctBy { it.matchingSourceKey }
    }

    suspend fun setVoiceSubscription(
        videos: List<VideoVariant>,
        subscribed: Boolean,
        title: String,
        posterUrl: String,
        userId: Long?,
    ): List<VideoSubscription> {
        val firstVideo = videos.firstOrNull()
            ?: throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
        val voiceKey = firstVideo.matchingVoiceKey
        val previousHints = subscriptionHints
        return try {
            applySubscriptionStateToVideos(videos, subscribed)
            if (subscribed) {
                rememberHints(videos, title, posterUrl)
            } else {
                forgetHints(firstVideo.animeId, voiceKey)
            }
            persistHints(userId)
            loadResolvedSubscriptions().withVoiceSubscriptionState(
                animeId = firstVideo.animeId,
                voiceKey = voiceKey,
                videos = videos,
                subscribed = subscribed,
                title = title,
                posterUrl = posterUrl,
            )
        } catch (throwable: Throwable) {
            restoreHintsAfterFailure(previousHints, userId)
            throw throwable
        }
    }

    fun stageRemoval(target: SubscriptionUnsubscribeTarget): StagedVideoSubscriptionRemoval {
        val staged = StagedVideoSubscriptionRemoval(
            target = target,
            previousHints = subscriptionHints,
        )
        if (target.voiceKey.isNotBlank()) {
            forgetHints(target.animeId, target.voiceKey)
        }
        return staged
    }

    suspend fun removeSubscription(
        staged: StagedVideoSubscriptionRemoval,
        fallbackVideos: List<VideoVariant>,
        userId: Long?,
    ): List<VideoSubscription> {
        return try {
            persistHints(userId)
            val target = staged.target.withResolvedVideoIds(
                videos = if (staged.target.requiresVideoLookup) {
                    fallbackVideos
                        .takeIf { videos -> videos.any { it.animeId == staged.target.animeId } }
                        ?: fetchVideos(staged.target.animeId)
                } else {
                    emptyList()
                },
            )
            if (target.videoIds.isEmpty()) {
                throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
            }
            applySubscriptionStateToVideoIds(target.videoIds, subscribed = false)
            loadResolvedSubscriptions().withoutUnsubscribeTarget(target)
        } catch (throwable: Throwable) {
            restoreHintsAfterFailure(staged.previousHints, userId)
            throw throwable
        }
    }

    internal fun hintSnapshot(): List<VideoSubscriptionHint> = subscriptionHints.toList()

    suspend fun loadResolvedSubscriptions(): List<VideoSubscription> {
        return resolveVoices(fetchSubscriptions())
    }

    private suspend fun resolveVoices(
        subscriptions: List<VideoSubscription>,
    ): List<VideoSubscription> {
        if (subscriptions.isEmpty()) return subscriptions
        val videoCache = mutableMapOf<Long, List<VideoVariant>>()
        return subscriptions.flatMap { subscription ->
            resolveSubscriptionVoices(subscription, videoCache)
        }.distinctBy { subscription ->
            listOf(
                subscription.animeId,
                subscription.matchingVoiceKey,
                subscription.videoId,
                subscription.playerId,
                subscription.matchingPlayerKey,
            ).joinToString("|")
        }
    }

    private suspend fun resolveSubscriptionVoices(
        subscription: VideoSubscription,
        videoCache: MutableMap<Long, List<VideoVariant>>,
    ): List<VideoSubscription> {
        if (subscription.animeId <= 0L) return listOf(subscription)
        val videos = videoCache[subscription.animeId] ?: providerResult {
            fetchVideos(subscription.animeId)
        }.getOrDefault(emptyList()).also { loaded ->
            videoCache[subscription.animeId] = loaded
        }
        val subscribedVideos = videos.filter(VideoVariant::subscribed)
        if (subscribedVideos.isNotEmpty()) {
            return subscribedVideos.map(subscription::withResolvedVoice)
        }
        videos.firstOrNull { it.id == subscription.videoId }
            ?.let { return listOf(subscription.withResolvedVoice(it)) }

        val hintedSubscriptions = subscription.resolveVoiceHints(subscriptionHints)
            .map(subscription::withResolvedHint)
        if (hintedSubscriptions.isNotEmpty()) return hintedSubscriptions

        return listOfNotNull(subscription.resolveSinglePlayerVoice(videos)?.let(subscription::withResolvedVoice))
            .ifEmpty { listOf(subscription) }
    }

    private suspend fun unsubscribeCompletedAnimeSubscriptions(
        subscriptions: List<VideoSubscription>,
        userId: Long?,
    ): List<VideoSubscription> {
        if (subscriptions.isEmpty()) return subscriptions
        val subscriptionsByAnime = subscriptions
            .filter { it.animeId > 0L }
            .groupBy(VideoSubscription::animeId)
        if (subscriptionsByAnime.isEmpty()) return subscriptions

        val removedAnimeIds = mutableSetOf<Long>()
        subscriptionsByAnime.forEach { (animeId, animeSubscriptions) ->
            val details = providerResult { fetchAnime(animeId) }.getOrNull() ?: return@forEach
            if (!details.isFullyReleased()) return@forEach
            if (unsubscribeCompletedAnimeSubscriptionGroup(animeId, animeSubscriptions)) {
                removedAnimeIds += animeId
            }
        }
        if (removedAnimeIds.isEmpty()) return subscriptions

        val previousHints = subscriptionHints
        subscriptionHints = subscriptionHints.filterNot { it.animeId in removedAnimeIds }
        try {
            persistHints(userId)
        } catch (throwable: Throwable) {
            subscriptionHints = previousHints
            throw throwable
        }
        return subscriptions.filterNot { it.animeId in removedAnimeIds }
    }

    private suspend fun unsubscribeCompletedAnimeSubscriptionGroup(
        animeId: Long,
        subscriptions: List<VideoSubscription>,
    ): Boolean {
        val directVideoIds = subscriptions
            .mapNotNull { it.videoId.takeIf { videoId -> videoId > 0L } }
            .distinct()
        if (unsubscribeByVideoIds(directVideoIds)) return true

        val targetVoiceKeys = subscriptions
            .map(VideoSubscription::matchingVoiceKey)
            .filter(String::isNotBlank)
            .toSet()
        if (targetVoiceKeys.isEmpty()) return false

        val targetVideoIds = providerResult { fetchVideos(animeId) }
            .getOrDefault(emptyList())
            .filter { video -> video.matchingVoiceKey in targetVoiceKeys }
            .distinctBy { it.matchingSourceKey }
            .map(VideoVariant::id)
            .filter { it > 0L }
        return unsubscribeByVideoIds(targetVideoIds)
    }

    private suspend fun unsubscribeByVideoIds(videoIds: List<Long>): Boolean {
        if (videoIds.isEmpty()) return false
        return videoIds.map { videoId ->
            providerResult { unsubscribeVideo(videoId) }.getOrDefault(false)
        }.any { it }
    }

    private suspend fun applySubscriptionStateToVideos(
        videos: List<VideoVariant>,
        subscribed: Boolean,
    ) {
        applySubscriptionStateToVideoIds(
            videoIds = videos.map(VideoVariant::id).filter { it > 0L }.distinct(),
            subscribed = subscribed,
        )
    }

    private suspend fun applySubscriptionStateToVideoIds(
        videoIds: List<Long>,
        subscribed: Boolean,
    ) {
        if (videoIds.isEmpty()) throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
        val operationResults = videoIds.map { videoId ->
            providerResult {
                if (subscribed) {
                    subscribeVideo(videoId)
                } else {
                    unsubscribeVideo(videoId)
                    true
                }
            }
        }
        if (operationResults.none { it.getOrDefault(false) }) {
            throw operationResults.firstNotNullOfOrNull(Result<Boolean>::exceptionOrNull)
                ?: IllegalStateException(
                    if (subscribed) SUBSCRIPTION_ENABLE_FAILED_KEY else SUBSCRIPTION_DISABLE_FAILED_KEY,
                )
        }
    }

    private fun rememberHints(
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) {
        val hints = videos.mapNotNull { video ->
            val voiceKey = video.matchingVoiceKey.takeIf(String::isNotBlank) ?: return@mapNotNull null
            VideoSubscriptionHint(
                animeId = video.animeId,
                playerId = video.playerId,
                playerKey = video.matchingPlayerKey,
                voiceKey = voiceKey,
                voiceTitle = video.matchingDubbingTitle.ifBlank { video.matchingVoiceTitle },
                title = title,
                posterUrl = posterUrl,
            )
        }
        if (hints.isEmpty()) return
        subscriptionHints = subscriptionHints.filterNot { existing ->
            hints.any { hint -> existing.matchesIdentityOf(hint) }
        } + hints
    }

    private fun forgetHints(animeId: Long, voiceKey: String) {
        val normalizedVoiceKey = voiceKey.takeIf(String::isNotBlank) ?: return
        subscriptionHints = subscriptionHints.filterNot { hint ->
            hint.animeId == animeId && hint.voiceKey == normalizedVoiceKey
        }
    }

    private suspend fun persistHints(userId: Long?) {
        val validUserId = userId?.takeIf { it > 0L } ?: return
        val snapshot = subscriptionHints
        withContext(ioDispatcher) { saveHints(validUserId, snapshot) }
    }

    private suspend fun restoreHintsAfterFailure(
        previousHints: List<VideoSubscriptionHint>,
        userId: Long?,
    ) {
        subscriptionHints = previousHints
        withContext(NonCancellable) {
            runCatching { persistHints(userId) }
        }
    }

    private suspend fun <T> providerResult(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(throwable)
        }
    }
}

private fun VideoSubscriptionHint.matchesIdentityOf(other: VideoSubscriptionHint): Boolean {
    return animeId == other.animeId &&
        voiceKey == other.voiceKey &&
        (
            (other.playerId > 0L && playerId == other.playerId) ||
                (other.playerKey.isNotBlank() && playerKey == other.playerKey)
            )
}
