package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.withVoiceSubscriptionState

internal class VideoSubscriptionStateCoordinator(
    private val scope: CoroutineScope,
    private val subscriptions: VideoSubscriptionCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val cacheCurrentDetailsRouteState: () -> Unit,
    private val showToggleNotice: (subscribed: Boolean) -> Unit,
) {
    private var synchronizationJob: Job? = null
    private val mutationJobs = mutableSetOf<Job>()

    suspend fun restoreHints(profileId: Long?) {
        subscriptions.restoreHints(profileId)
    }

    fun clear() {
        synchronizationJob?.cancel()
        synchronizationJob = null
        mutationJobs.toList().forEach(Job::cancel)
        mutationJobs.clear()
        subscriptions.clearHints()
    }

    fun synchronize() {
        synchronizationJob?.cancel()
        synchronizationJob = null

        val state = currentState()
        val profileId = state.auth.profile?.id
        if (state.forcedOfflineMode || profileId == null) {
            updateState { it.copy(globalSubscriptions = LoadState.Ready(emptyList())) }
            return
        }

        updateState { it.copy(globalSubscriptions = LoadState.Loading) }
        val job = scope.launch {
            try {
                val resolved = subscriptions.synchronize(profileId)
                if (isActiveProfile(profileId)) publish(resolved)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!isActiveProfile(profileId)) return@launch
                if (!requestCaptchaRetry(throwable) { synchronize() }) {
                    updateState { it.copy(globalSubscriptions = LoadState.Error(throwable.userMessage())) }
                }
            }
        }
        synchronizationJob = job
        job.invokeOnCompletion {
            if (synchronizationJob == job) synchronizationJob = null
        }
    }

    fun publish(resolved: List<VideoSubscription>) {
        updateState { state ->
            state.withPublishedVideoSubscriptions(
                subscriptions = resolved,
                canonicalize = subscriptions::canonicalizeForVideos,
            )
        }
        cacheCurrentDetailsRouteState()
    }

    fun toggle(video: VideoVariant, showNotice: Boolean) {
        val state = currentState()
        if (state.forcedOfflineMode) return
        val details = state.details.readyDataOrNull()
        if (details?.isFullyReleased() == true) return
        val profileId = state.auth.profile?.id
        if (profileId == null) {
            updateState { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return
        }

        launchMutation {
            val current = currentState().detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()
            val allVideos = currentState().videos.readyListOrEmpty()
            val voiceKey = video.matchingVoiceKey
            val voiceVideos = try {
                subscriptions.loadTargets(
                    animeId = video.animeId,
                    voiceKey = voiceKey,
                    fallbackVideos = allVideos,
                ).ifEmpty { listOf(video).filter { it.id > 0L } }
            } catch (throwable: Throwable) {
                handleToggleFailure(
                    video = video,
                    previousExtras = current,
                    throwable = throwable,
                    showNotice = showNotice,
                    rollback = false,
                    profileId = profileId,
                )
                return@launchMutation
            }
            if (voiceVideos.isEmpty() || !isActiveProfile(profileId)) return@launchMutation

            val shouldSubscribe = !current.subscriptions.hasSubscriptionForVoice(video.animeId, voiceKey)
            val title = details?.title.orEmpty()
            val posterUrl = details?.posterUrl.orEmpty()
            val optimisticSubscriptions = current.subscriptions.withVoiceSubscriptionState(
                animeId = video.animeId,
                voiceKey = voiceKey,
                videos = voiceVideos,
                subscribed = shouldSubscribe,
                title = title,
                posterUrl = posterUrl,
            )
            updateDetailsSubscriptions(optimisticSubscriptions, fallback = current)
            cacheDetailsRouteState(video.animeId)

            try {
                val resolved = subscriptions.setVoiceSubscription(
                    videos = voiceVideos,
                    subscribed = shouldSubscribe,
                    title = title,
                    posterUrl = posterUrl,
                    userId = profileId,
                )
                if (!isActiveProfile(profileId)) return@launchMutation
                publish(resolved)
                if (showNotice) showToggleNotice(shouldSubscribe)
                cacheDetailsRouteState(video.animeId)
            } catch (throwable: Throwable) {
                handleToggleFailure(
                    video = video,
                    previousExtras = current,
                    throwable = throwable,
                    showNotice = showNotice,
                    rollback = true,
                    profileId = profileId,
                )
            }
        }
    }

    fun unsubscribe(subscription: VideoSubscription) {
        val state = currentState()
        val profileId = state.auth.profile?.id
        if (state.forcedOfflineMode || profileId == null) return
        val currentSubscriptions = state.globalSubscriptions.readyListOrEmpty()
        val target = subscription.unsubscribeTarget(currentSubscriptions) ?: return
        val stagedRemoval = subscriptions.stageRemoval(target)

        publish(currentSubscriptions.withoutUnsubscribeTarget(target))
        launchMutation {
            try {
                val resolved = subscriptions.removeSubscription(
                    staged = stagedRemoval,
                    fallbackVideos = currentState().videos.readyListOrEmpty(),
                    userId = profileId,
                )
                if (isActiveProfile(profileId)) publish(resolved)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!isActiveProfile(profileId)) return@launchMutation
                synchronize()
                if (throwable is CaptchaRequiredException) {
                    requestCaptchaRetry(throwable) { unsubscribe(subscription) }
                } else {
                    updateState { it.copy(auth = it.auth.copy(error = throwable.userMessage())) }
                }
            }
        }
    }

    private fun handleToggleFailure(
        video: VideoVariant,
        previousExtras: AnimeDetailsExtras,
        throwable: Throwable,
        showNotice: Boolean,
        rollback: Boolean,
        profileId: Long,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!isActiveProfile(profileId)) return
        if (throwable is CaptchaRequiredException) {
            if (rollback) {
                updateDetailsSubscriptions(previousExtras.subscriptions, fallback = previousExtras)
                cacheDetailsRouteState(video.animeId)
            }
            requestCaptchaRetry(throwable) { toggle(video, showNotice) }
        } else {
            updateState { state ->
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
            if (rollback) cacheDetailsRouteState(video.animeId)
        }
    }

    private fun updateDetailsSubscriptions(
        resolved: List<VideoSubscription>,
        fallback: AnimeDetailsExtras = AnimeDetailsExtras(),
    ) {
        updateState { state ->
            val extras = state.detailsExtras.readyDataOrNull() ?: fallback
            state.copy(detailsExtras = LoadState.Ready(extras.copy(subscriptions = resolved)))
        }
    }

    private fun isActiveProfile(profileId: Long): Boolean {
        val state = currentState()
        return !state.forcedOfflineMode && state.auth.profile?.id == profileId
    }

    private fun launchMutation(block: suspend () -> Unit) {
        val job = scope.launch { block() }
        mutationJobs += job
        job.invokeOnCompletion { mutationJobs -= job }
    }
}

internal fun YummyDroidUiState.withPublishedVideoSubscriptions(
    subscriptions: List<VideoSubscription>,
    canonicalize: (
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) -> List<VideoSubscription>,
): YummyDroidUiState {
    val detailsAnimeId = (route as? AppRoute.Details)?.animeId
        ?: details.readyDataOrNull()?.id
    val currentExtras = detailsExtras.readyDataOrNull()
    val currentDetails = details.readyDataOrNull()
    val detailsVideos = videos.readyDataOrNull()
        .orEmpty()
        .filter { it.animeId == detailsAnimeId }
    val detailsSubscriptions = canonicalize(
        subscriptions,
        detailsVideos,
        currentDetails?.title.orEmpty(),
        currentDetails?.posterUrl.orEmpty(),
    )
    return copy(
        globalSubscriptions = LoadState.Ready(subscriptions),
        detailsExtras = if (detailsAnimeId != null && currentExtras != null) {
            LoadState.Ready(currentExtras.copy(subscriptions = detailsSubscriptions))
        } else {
            detailsExtras
        },
    )
}
