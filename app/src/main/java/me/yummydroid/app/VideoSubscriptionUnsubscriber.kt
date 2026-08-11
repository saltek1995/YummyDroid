package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.VideoSubscription

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
