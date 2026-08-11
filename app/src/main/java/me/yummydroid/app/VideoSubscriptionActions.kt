package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.withVoiceSubscriptionState

// VideoSubscriptionMutationRunner
internal class VideoSubscriptionMutationRunner(
    private val scope: CoroutineScope,
) {
    private val jobs = mutableSetOf<Job>()

    fun clear() {
        jobs.toList().forEach(Job::cancel)
        jobs.clear()
    }

    fun launch(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }
}

// VideoSubscriptionToggle
internal class VideoSubscriptionToggle(
    private val store: VideoSubscriptionStateStore,
    private val mutationRunner: VideoSubscriptionMutationRunner,
) {
    fun toggle(video: VideoVariant, showNotice: Boolean) {
        val request = createRequest(video, showNotice) ?: return
        mutationRunner.launch { process(request) }
    }

    private fun createRequest(video: VideoVariant, showNotice: Boolean): ToggleRequest? {
        val state = store.current()
        if (state.forcedOfflineMode) return null
        val details = state.details.readyDataOrNull()
        if (details?.isFullyReleased() == true) return null
        val profileId = state.auth.profile?.id
        if (profileId == null) {
            store.update { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return null
        }
        return ToggleRequest(
            video = video,
            showNotice = showNotice,
            profileId = profileId,
            title = details?.title.orEmpty(),
            posterUrl = details?.posterUrl.orEmpty(),
        )
    }

    private suspend fun process(request: ToggleRequest) {
        val current = store.current().detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
        val voiceVideos = loadVoiceVideos(request, current) ?: return
        if (voiceVideos.isEmpty() || !store.isActiveProfile(request.profileId)) return

        val shouldSubscribe = !current.subscriptions.hasSubscriptionForVoice(
            request.video.animeId,
            request.video.matchingVoiceKey,
        )
        val optimistic = current.subscriptions.withVoiceSubscriptionState(
            animeId = request.video.animeId,
            voiceKey = request.video.matchingVoiceKey,
            videos = voiceVideos,
            subscribed = shouldSubscribe,
            title = request.title,
            posterUrl = request.posterUrl,
        )
        store.updateDetailsSubscriptions(optimistic, fallback = current)
        store.cacheDetails(request.video.animeId)
        commit(request, current, voiceVideos, shouldSubscribe)
    }

    private suspend fun loadVoiceVideos(
        request: ToggleRequest,
        current: AnimeDetailsExtras,
    ): List<VideoVariant>? {
        return try {
            store.subscriptions.loadTargets(
                animeId = request.video.animeId,
                voiceKey = request.video.matchingVoiceKey,
                fallbackVideos = store.current().videos.readyListOrEmpty(),
            ).ifEmpty { listOf(request.video).filter { it.id > 0L } }
        } catch (throwable: Throwable) {
            handleFailure(request, current, throwable, rollback = false)
            null
        }
    }

    private suspend fun commit(
        request: ToggleRequest,
        previousExtras: AnimeDetailsExtras,
        voiceVideos: List<VideoVariant>,
        shouldSubscribe: Boolean,
    ) {
        try {
            val resolved = store.subscriptions.setVoiceSubscription(
                videos = voiceVideos,
                subscribed = shouldSubscribe,
                title = request.title,
                posterUrl = request.posterUrl,
                userId = request.profileId,
            )
            if (!store.isActiveProfile(request.profileId)) return
            store.publish(resolved)
            if (request.showNotice) store.showNotice(shouldSubscribe)
            store.cacheDetails(request.video.animeId)
        } catch (throwable: Throwable) {
            handleFailure(request, previousExtras, throwable, rollback = true)
        }
    }

    private fun handleFailure(
        request: ToggleRequest,
        previousExtras: AnimeDetailsExtras,
        throwable: Throwable,
        rollback: Boolean,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!store.isActiveProfile(request.profileId)) return
        if (throwable is CaptchaRequiredException) {
            rollbackIfNeeded(request.video.animeId, previousExtras, rollback)
            store.requestRetry(throwable) { toggle(request.video, request.showNotice) }
            return
        }
        store.update { state ->
            val updated = if (rollback) {
                val extras = state.detailsExtras.readyDataOrNull() ?: previousExtras
                state.copy(
                    detailsExtras = LoadState.Ready(
                        extras.copy(subscriptions = previousExtras.subscriptions),
                    ),
                )
            } else {
                state
            }
            updated.copy(auth = updated.auth.copy(error = throwable.userMessage()))
        }
        if (rollback) store.cacheDetails(request.video.animeId)
    }

    private fun rollbackIfNeeded(
        animeId: Long,
        previousExtras: AnimeDetailsExtras,
        rollback: Boolean,
    ) {
        if (!rollback) return
        store.updateDetailsSubscriptions(previousExtras.subscriptions, fallback = previousExtras)
        store.cacheDetails(animeId)
    }
}

private data class ToggleRequest(
    val video: VideoVariant,
    val showNotice: Boolean,
    val profileId: Long,
    val title: String,
    val posterUrl: String,
)

// VideoSubscriptionUnsubscribe
internal data class SubscriptionUnsubscribeTarget(
    val animeId: Long,
    val voiceKey: String,
    val playerId: Long?,
    val playerKey: String,
    val videoIds: List<Long>,
) {
    val requiresVideoLookup: Boolean
        get() = voiceKey.isNotBlank() || playerId != null || playerKey.isNotBlank()

    fun withResolvedVideoIds(videos: List<VideoVariant>): SubscriptionUnsubscribeTarget =
        copy(videoIds = resolveVideoIds(videos))

    fun resolveVideoIds(videos: List<VideoVariant>): List<Long> {
        val resolvedIds = videos
            .filter { video -> video.animeId == animeId && matchesVideo(video) }
            .distinctBy(VideoVariant::matchingSourceKey)
            .map(VideoVariant::id)
            .filter { id -> id > 0L }
        return (videoIds + resolvedIds).distinct()
    }

    fun matchesSubscription(subscription: VideoSubscription): Boolean {
        if (subscription.videoId in videoIds) return true
        if (voiceKey.isNotBlank()) return subscription.matchesAnimeVoice(animeId, voiceKey)
        return matchesPlayerSubscription(subscription)
    }

    fun hintVideos(videos: List<VideoVariant>): List<VideoVariant> {
        if (voiceKey.isBlank()) return emptyList()
        return videos.filter { video -> video.animeId == animeId && video.matchingVoiceKey == voiceKey }
    }

    private fun matchesPlayerSubscription(subscription: VideoSubscription): Boolean {
        if (subscription.animeId != animeId) return false
        if (playerId != null) return subscription.playerId == playerId
        if (playerKey.isBlank()) return false
        return subscription.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
    }

    private fun matchesVideo(video: VideoVariant): Boolean = when {
        voiceKey.isNotBlank() -> video.matchingVoiceKey == voiceKey
        playerId != null -> video.playerId == playerId
        playerKey.isNotBlank() -> video.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
        else -> false
    }
}

internal fun VideoSubscription.unsubscribeTarget(
    currentSubscriptions: List<VideoSubscription>,
): SubscriptionUnsubscribeTarget? {
    val targetAnimeId = animeId.takeIf { id -> id > 0L } ?: return null
    val currentMatch = currentSubscriptions.firstOrNull { subscription ->
        subscription.animeId == targetAnimeId && subscription.videoId == videoId
    }
    val targetVoiceKey = matchingVoiceKey.ifBlank { currentMatch?.matchingVoiceKey.orEmpty() }
    val targetPlayerId = playerId.takeIf { id -> id > 0L }
        ?: currentMatch?.playerId?.takeIf { id -> id > 0L }
    val targetPlayerKey = player.cleanVideoSourceLabel()
    if (!hasUnsubscribeIdentity(targetVoiceKey, targetPlayerId, targetPlayerKey)) return null

    return SubscriptionUnsubscribeTarget(
        animeId = targetAnimeId,
        voiceKey = targetVoiceKey,
        playerId = targetPlayerId,
        playerKey = targetPlayerKey,
        videoIds = resolveDirectVideoIds(currentSubscriptions, targetAnimeId, targetVoiceKey),
    )
}

private fun VideoSubscription.hasUnsubscribeIdentity(
    voiceKey: String,
    resolvedPlayerId: Long?,
    playerKey: String,
): Boolean = voiceKey.isNotBlank() || videoId > 0L || resolvedPlayerId != null || playerKey.isNotBlank()

private fun VideoSubscription.resolveDirectVideoIds(
    subscriptions: List<VideoSubscription>,
    targetAnimeId: Long,
    voiceKey: String,
): List<Long> {
    val resolvedIds = subscriptions
        .filter { subscription ->
            subscription.videoId > 0L &&
                subscription.animeId == targetAnimeId &&
                (subscription.videoId == videoId || subscription.matchesTargetVoice(targetAnimeId, voiceKey))
        }
        .map(VideoSubscription::videoId)
    return resolvedIds.ifEmpty { listOf(videoId).filter { id -> id > 0L } }.distinct()
}

private fun VideoSubscription.matchesTargetVoice(animeId: Long, voiceKey: String): Boolean =
    voiceKey.isNotBlank() && matchesAnimeVoice(animeId, voiceKey)

internal fun List<VideoSubscription>.withoutUnsubscribeTarget(
    target: SubscriptionUnsubscribeTarget,
): List<VideoSubscription> = filterNot(target::matchesSubscription)

// VideoSubscriptionUnsubscriber
internal class VideoSubscriptionUnsubscriber(
    private val store: VideoSubscriptionStateStore,
    private val mutationRunner: VideoSubscriptionMutationRunner,
    private val synchronize: () -> Unit,
) {
    fun unsubscribe(subscription: VideoSubscription) {
        val state = store.current()
        val profileId = state.auth.profile?.id
        if (state.forcedOfflineMode || profileId == null) return
        val currentSubscriptions = state.globalSubscriptions.readyListOrEmpty()
        val target = subscription.unsubscribeTarget(currentSubscriptions) ?: return
        val stagedRemoval = store.subscriptions.stageRemoval(target)

        store.publish(currentSubscriptions.withoutUnsubscribeTarget(target))
        mutationRunner.launch {
            try {
                val resolved = store.subscriptions.removeSubscription(
                    staged = stagedRemoval,
                    fallbackVideos = store.current().videos.readyListOrEmpty(),
                    userId = profileId,
                )
                if (store.isActiveProfile(profileId)) store.publish(resolved)
            } catch (throwable: Throwable) {
                handleFailure(subscription, profileId, throwable)
            }
        }
    }

    private fun handleFailure(
        subscription: VideoSubscription,
        profileId: Long,
        throwable: Throwable,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!store.isActiveProfile(profileId)) return
        synchronize()
        if (throwable is CaptchaRequiredException) {
            store.requestRetry(throwable) { unsubscribe(subscription) }
        } else {
            store.update { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
        }
    }
}
