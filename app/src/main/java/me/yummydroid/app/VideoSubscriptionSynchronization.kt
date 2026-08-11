package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class VideoSubscriptionSynchronization(
    private val scope: CoroutineScope,
    private val store: VideoSubscriptionStateStore,
) {
    private var job: Job? = null

    fun clear() {
        job?.cancel()
        job = null
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
        val launched = scope.launch { synchronize(profileId) }
        job = launched
        launched.invokeOnCompletion {
            if (job == launched) job = null
        }
    }

    private suspend fun synchronize(profileId: Long) {
        try {
            val resolved = store.subscriptions.synchronize(profileId)
            if (store.isActiveProfile(profileId)) store.publish(resolved)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            if (!store.isActiveProfile(profileId)) return
            if (!store.requestRetry(throwable) { synchronize() }) {
                store.update {
                    it.copy(globalSubscriptions = LoadState.Error(throwable.userMessage()))
                }
            }
        }
    }
}
