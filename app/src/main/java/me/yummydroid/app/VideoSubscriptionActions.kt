package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.isSameSubscriptionTargetAs

// VideoSubscriptionMutationRunner
internal class VideoSubscriptionMutationRunner(
    private val scope: CoroutineScope,
) {
    private val operations = SerialStateOperationCoordinator()

    fun clear() = operations.cancel()

    fun launch(block: suspend () -> Unit) {
        operations.launch(scope) { block() }
    }
}

// VideoSubscriptionToggle
internal class VideoSubscriptionToggle(
    private val store: VideoSubscriptionStateStore,
    private val mutationRunner: VideoSubscriptionMutationRunner,
    private val synchronize: () -> Unit,
) {
    fun toggle(video: VideoVariant, showNotice: Boolean) {
        val request = createRequest(video, showNotice) ?: return
        mutationRunner.launch { process(request) }
    }

    private fun createRequest(video: VideoVariant, showNotice: Boolean): ToggleRequest? {
        val state = store.current()
        if (state.forcedOfflineMode) return null
        val profileId = state.auth.profile?.id
        if (profileId == null) {
            store.update { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return null
        }
        return ToggleRequest(
            video = video,
            showNotice = showNotice,
            profileId = profileId,
        )
    }

    private suspend fun process(request: ToggleRequest) {
        if (!store.isActiveProfile(request.profileId)) return
        val currentVideos = store.current().videos.readyListOrEmpty()
        val currentTargets = currentVideos.filter { video -> video.isSameSubscriptionTargetAs(request.video) }
        val currentlySubscribed = currentTargets
            .takeIf(List<VideoVariant>::isNotEmpty)
            ?.any(VideoVariant::subscribed)
            ?: request.video.subscribed
        val shouldSubscribe = !currentlySubscribed
        commit(request, request.video.id, shouldSubscribe)
    }

    private suspend fun commit(
        request: ToggleRequest,
        videoId: Long,
        shouldSubscribe: Boolean,
    ) {
        try {
            store.subscriptions.setSubscription(
                videoId = videoId,
                subscribed = shouldSubscribe,
            )
            if (!store.isActiveProfile(request.profileId)) return
            store.confirmVideoSubscription(request.video, shouldSubscribe)
            if (request.showNotice) store.showNotice(shouldSubscribe)
            store.cacheDetails(request.video.animeId)
            synchronize()
        } catch (throwable: Throwable) {
            handleFailure(request, throwable)
        }
    }

    private fun handleFailure(
        request: ToggleRequest,
        throwable: Throwable,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!store.isActiveProfile(request.profileId)) return
        if (throwable is CaptchaRequiredException) {
            store.requestRetry(throwable) { toggle(request.video, request.showNotice) }
            return
        }
        store.showError(throwable.userMessage())
    }
}

private data class ToggleRequest(
    val video: VideoVariant,
    val showNotice: Boolean,
    val profileId: Long,
)

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

        mutationRunner.launch {
            try {
                val resolved = store.subscriptions.removeSubscription(
                    profileId = profileId,
                    subscription = subscription,
                    fallbackVideos = store.current().videos.readyListOrEmpty(),
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
            store.showError(throwable.userMessage())
        }
    }
}
