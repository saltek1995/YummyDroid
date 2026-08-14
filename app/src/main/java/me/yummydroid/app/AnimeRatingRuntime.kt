package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.data.CaptchaRequiredException

internal class AnimeRatingStateRuntime(
    private val scope: CoroutineScope,
    private val coordinator: AnimeRatingCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val authenticatedDetailsAnimeId: () -> Long?,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val showErrorNotice: (String) -> Unit,
) {
    private val mutations = SerialStateOperationCoordinator()

    fun cancel() {
        mutations.cancel()
    }

    fun setRating(rating: Int?) {
        if (currentState().forcedOfflineMode) return
        val animeId = authenticatedDetailsAnimeId() ?: return
        val operationState = currentState()
        val profileId = operationState.auth.profile?.id ?: return
        val previousDetails = operationState.details
        val previousExtras = operationState.detailsExtras
        val stagedRating = coordinator.stage(animeId, rating)
        updateState { state ->
            state.withOptimisticAnimeRating(animeId, stagedRating.optimisticRating)
        }
        cacheDetailsRouteState(animeId)
        mutations.launch(scope) { lease ->
            runCatching { coordinator.submit(stagedRating) }
                .onSuccess { update ->
                    if (!lease.isCurrent || !update.accepted || !acceptsResult(animeId, profileId, stagedRating)) {
                        return@onSuccess
                    }
                    updateState { state -> state.withConfirmedAnimeRating(animeId, update) }
                    cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent || !acceptsResult(animeId, profileId, stagedRating)) {
                        return@onFailure
                    }
                    updateState { state ->
                        state.withRestoredAnimeRating(
                            animeId = animeId,
                            previousDetails = previousDetails,
                            previousExtras = previousExtras,
                        )
                    }
                    cacheDetailsRouteState(animeId)
                    if (throwable is CaptchaRequiredException) {
                        requestCaptchaRetry(throwable) { setRating(rating) }
                    } else {
                        showErrorNotice(throwable.userMessage())
                    }
                }
        }
    }

    private fun acceptsResult(
        animeId: Long,
        profileId: Long,
        stagedRating: StagedAnimeRating,
    ): Boolean {
        val current = currentState()
        return coordinator.isCurrent(stagedRating) &&
            current.auth.profile?.id == profileId &&
            (current.route as? AppRoute.Details)?.animeId == animeId
    }
}
