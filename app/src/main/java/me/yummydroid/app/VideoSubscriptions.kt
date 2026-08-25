package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.withServerVideoSubscriptions

// VideoSubscriptionCoordinator
internal class VideoSubscriptionCoordinator(
    private val fetchSubscriptions: suspend (Long) -> List<VideoSubscription>,
    private val fetchVideos: suspend (Long) -> List<VideoVariant>,
    private val subscribeVideo: suspend (Long) -> Boolean,
    private val unsubscribeVideo: suspend (Long) -> Boolean,
) {
    private val operationMutex = Mutex()

    suspend fun setSubscription(
        profileId: Long,
        videoId: Long,
        subscribed: Boolean,
    ): List<VideoSubscription> = operationMutex.withLock {
        applySubscriptionState(videoId, subscribed)
        loadSubscriptionsUnlocked(profileId)
    }

    suspend fun loadSubscriptions(profileId: Long): List<VideoSubscription> = operationMutex.withLock {
        loadSubscriptionsUnlocked(profileId)
    }

    suspend fun removeSubscription(
        profileId: Long,
        subscription: VideoSubscription,
        fallbackVideos: List<VideoVariant>,
    ): List<VideoSubscription> = operationMutex.withLock {
        val videoId = resolveRemovalVideoId(subscription, fallbackVideos)
        applySubscriptionState(videoId, subscribed = false)
        loadSubscriptionsUnlocked(profileId)
    }

    private suspend fun loadSubscriptionsUnlocked(profileId: Long): List<VideoSubscription> {
        return fetchSubscriptions(profileId)
    }

    private suspend fun resolveRemovalVideoId(
        subscription: VideoSubscription,
        fallbackVideos: List<VideoVariant>,
    ): Long {
        subscription.videoId.takeIf { it > 0L }?.let { return it }
        subscription.matchingVideoId(fallbackVideos)?.let { return it }
        return subscription.matchingVideoId(fetchVideos(subscription.animeId))
            ?: throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
    }

    private suspend fun applySubscriptionState(videoId: Long, subscribed: Boolean) {
        if (videoId <= 0L) throw IllegalStateException(SUBSCRIPTION_TARGET_NOT_FOUND_KEY)
        val updated = if (subscribed) {
            subscribeVideo(videoId)
        } else {
            unsubscribeVideo(videoId)
        }
        if (!updated) {
            throw IllegalStateException(
                if (subscribed) SUBSCRIPTION_ENABLE_FAILED_KEY else SUBSCRIPTION_DISABLE_FAILED_KEY,
            )
        }
    }
}

private fun VideoSubscription.matchingVideoId(videos: List<VideoVariant>): Long? {
    if (animeId <= 0L) return null
    val voiceKey = matchingVoiceKey
    return videos
        .filter { video -> video.animeId == animeId && video.id > 0L }
        .filter(::matchesVideoPlayer)
        .filter { video -> voiceKey.isBlank() || video.matchingVoiceKey == voiceKey }
        .sortedWith(compareBy<VideoVariant> { it.index }.thenBy(VideoVariant::id))
        .firstOrNull()
        ?.id
}

// VideoSubscriptionPublishedState
internal fun YummyDroidUiState.withPublishedVideoSubscriptions(
    subscriptions: List<VideoSubscription>,
): YummyDroidUiState {
    val detailsAnimeId = (route as? AppRoute.Details)?.animeId
        ?: details.readyDataOrNull()?.id
    val currentExtras = detailsExtras.readyDataOrNull()
    val currentDetails = details.readyDataOrNull()
    val detailsSubscriptions = if (currentDetails != null) {
        subscriptions.withServerVideoSubscriptions(currentDetails, videos.readyListOrEmpty())
    } else {
        subscriptions
    }
    return copy(
        globalSubscriptions = LoadState.Ready(subscriptions),
        detailsExtras = if (detailsAnimeId != null && currentExtras != null) {
            LoadState.Ready(currentExtras.copy(subscriptions = detailsSubscriptions))
        } else {
            detailsExtras
        },
    )
}

// VideoSubscriptionStateCoordinator
internal class VideoSubscriptionStateCoordinator(
    scope: CoroutineScope,
    private val subscriptions: VideoSubscriptionCoordinator,
    currentState: () -> YummyDroidUiState,
    updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    cacheDetailsRouteState: (Long) -> Unit,
    cacheCurrentDetailsRouteState: () -> Unit,
    showToggleNotice: (subscribed: Boolean) -> Unit,
    showErrorNotice: (String) -> Unit,
) {
    private val store = VideoSubscriptionStateStore(
        subscriptions = subscriptions,
        currentState = currentState,
        updateState = updateState,
        requestCaptchaRetry = requestCaptchaRetry,
        cacheDetailsRouteState = cacheDetailsRouteState,
        cacheCurrentDetailsRouteState = cacheCurrentDetailsRouteState,
        showToggleNotice = showToggleNotice,
        showErrorNotice = showErrorNotice,
    )
    private val mutationRunner = VideoSubscriptionMutationRunner(scope)
    private val synchronization = VideoSubscriptionSynchronization(scope, store)
    private val toggle = VideoSubscriptionToggle(store, mutationRunner)
    private val unsubscriber = VideoSubscriptionUnsubscriber(
        store = store,
        mutationRunner = mutationRunner,
        synchronize = synchronization::synchronize,
    )

    fun cancelPendingOperations() {
        synchronization.clear()
        mutationRunner.clear()
    }

    suspend fun clear() {
        cancelPendingOperations()
    }

    fun synchronize() {
        synchronization.synchronize()
    }

    fun publish(resolved: List<VideoSubscription>) {
        store.publish(resolved)
    }

    fun toggle(video: VideoVariant, showNotice: Boolean) {
        toggle.toggle(video, showNotice)
    }

    fun unsubscribe(subscription: VideoSubscription) {
        unsubscriber.unsubscribe(subscription)
    }
}

// VideoSubscriptionStateStore
internal class VideoSubscriptionStateStore(
    val subscriptions: VideoSubscriptionCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val cacheCurrentDetailsRouteState: () -> Unit,
    private val showToggleNotice: (subscribed: Boolean) -> Unit,
    private val showErrorNotice: (String) -> Unit,
) {
    fun current(): YummyDroidUiState = currentState()

    fun update(transform: (YummyDroidUiState) -> YummyDroidUiState) {
        updateState(transform)
    }

    fun publish(resolved: List<VideoSubscription>) {
        updateState { state ->
            state.withPublishedVideoSubscriptions(resolved)
        }
        cacheCurrentDetailsRouteState()
    }

    fun isActiveProfile(profileId: Long): Boolean {
        val state = currentState()
        return !state.forcedOfflineMode && state.auth.profile?.id == profileId
    }

    fun requestRetry(throwable: Throwable, retry: suspend () -> Unit): Boolean {
        return requestCaptchaRetry(throwable, retry)
    }

    fun cacheDetails(animeId: Long) {
        cacheDetailsRouteState(animeId)
    }

    fun showNotice(subscribed: Boolean) {
        showToggleNotice(subscribed)
    }

    fun showError(message: String) {
        showErrorNotice(message)
    }
}

// VideoSubscriptionSynchronization
internal class VideoSubscriptionSynchronization(
    private val scope: CoroutineScope,
    private val store: VideoSubscriptionStateStore,
) {
    private val operations = LatestStateOperationCoordinator()

    fun clear() {
        operations.cancel()
    }

    fun synchronize() {
        clear()
        val state = store.current()
        val profileId = state.auth.profile?.id
        if (state.forcedOfflineMode || profileId == null) {
            store.update { it.copy(globalSubscriptions = LoadState.Ready(emptyList())) }
            return
        }

        store.update { it.copy(globalSubscriptions = LoadState.Loading) }
        operations.launchLatest(scope) { lease ->
            synchronize(profileId, lease)
        }
    }

    private suspend fun synchronize(profileId: Long, lease: StateOperationLease) {
        try {
            val resolved = store.subscriptions.loadSubscriptions(profileId)
            if (lease.isCurrent && store.isActiveProfile(profileId)) store.publish(resolved)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            if (!lease.isCurrent || !store.isActiveProfile(profileId)) return
            if (!store.requestRetry(throwable) { synchronize() }) {
                store.update {
                    it.copy(globalSubscriptions = LoadState.Error(throwable.userMessage()))
                }
            }
        }
    }
}
