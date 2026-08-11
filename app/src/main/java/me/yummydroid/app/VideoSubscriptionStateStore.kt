package me.yummydroid.app

import me.yummydroid.app.data.VideoSubscription

internal class VideoSubscriptionStateStore(
    val subscriptions: VideoSubscriptionCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val cacheCurrentDetailsRouteState: () -> Unit,
    private val showToggleNotice: (subscribed: Boolean) -> Unit,
) {
    fun current(): YummyDroidUiState = currentState()

    fun update(transform: (YummyDroidUiState) -> YummyDroidUiState) {
        updateState(transform)
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

    fun updateDetailsSubscriptions(
        resolved: List<VideoSubscription>,
        fallback: AnimeDetailsExtras = AnimeDetailsExtras(),
    ) {
        updateState { state ->
            val extras = state.detailsExtras.readyDataOrNull() ?: fallback
            state.copy(detailsExtras = LoadState.Ready(extras.copy(subscriptions = resolved)))
        }
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
}
