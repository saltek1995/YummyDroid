package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.hasSubscriptionForVoice
import me.yummydroid.app.data.isFullyReleased
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.withVoiceSubscriptionState

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
