package me.yummydroid.app

import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

internal class VideoSubscriptionStateCoordinator(
    scope: CoroutineScope,
    private val subscriptions: VideoSubscriptionCoordinator,
    currentState: () -> YummyDroidUiState,
    updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    cacheDetailsRouteState: (Long) -> Unit,
    cacheCurrentDetailsRouteState: () -> Unit,
    showToggleNotice: (subscribed: Boolean) -> Unit,
) {
    private val store = VideoSubscriptionStateStore(
        subscriptions = subscriptions,
        currentState = currentState,
        updateState = updateState,
        requestCaptchaRetry = requestCaptchaRetry,
        cacheDetailsRouteState = cacheDetailsRouteState,
        cacheCurrentDetailsRouteState = cacheCurrentDetailsRouteState,
        showToggleNotice = showToggleNotice,
    )
    private val mutationRunner = VideoSubscriptionMutationRunner(scope)
    private val synchronization = VideoSubscriptionSynchronization(scope, store)
    private val toggle = VideoSubscriptionToggle(store, mutationRunner)
    private val unsubscriber = VideoSubscriptionUnsubscriber(
        store = store,
        mutationRunner = mutationRunner,
        synchronize = synchronization::synchronize,
    )

    suspend fun restoreHints(profileId: Long?) {
        subscriptions.restoreHints(profileId)
    }

    fun clear() {
        synchronization.clear()
        mutationRunner.clear()
        subscriptions.clearHints()
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
