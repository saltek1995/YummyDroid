package me.yummydroid.app

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.isUnauthorizedApiError

// AuthStateRuntime
internal class AuthStateRuntime(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: YummyAnimeRepository,
    private val authOperations: LatestStateOperationCoordinator,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val playbackHistoryOperations: LatestStateOperationCoordinator,
    private val animeRatingCoordinator: AnimeRatingCoordinator,
    private val animeRatingStateRuntime: AnimeRatingStateRuntime,
    private val videoSubscriptionStateCoordinator: VideoSubscriptionStateCoordinator,
    private val animeMarkCoordinator: AnimeMarkCoordinator,
    private val playbackHistoryStateRuntime: PlaybackHistoryStateRuntime,
    private val profileNotificationStateRuntime: ProfileNotificationStateRuntime,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val detailsLoadOperations: LatestStateOperationCoordinator,
    private val detailsExtrasOperations: LatestStateOperationCoordinator,
    private val commentsOperations: LatestStateOperationCoordinator,
    private val commentMutations: SerialStateOperationCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val saveBrowseFilters: (BrowseFilters) -> AppSettings,
    private val clearDetailsRouteCache: () -> Unit,
    private val loadAnimeExtras: (Long) -> Unit,
    private val syncPlaybackHistoryFromSite: (
        mergeLocalHistory: Boolean,
        mergeCandidates: List<PlaybackProgress>?,
        allowLocalHistoryMergePrompt: Boolean,
    ) -> Unit,
    private val enterLoginAndPasswordMessage: () -> String,
) {
    private var pendingCaptchaAction: (suspend () -> Unit)? = null

    fun submitCaptchaResponse(captchaResponse: String) {
        val action = pendingCaptchaAction ?: return
        if (captchaResponse.isBlank()) return
        pendingCaptchaAction = null
        repository.submitCaptchaResponse(captchaResponse)
        scope.launch { action() }
    }

    fun cancelCaptchaChallenge(error: String?) {
        pendingCaptchaAction = null
        updateState {
            it.copy(
                auth = it.auth.copy(
                    loading = false,
                    error = error?.takeIf { message -> message.isNotBlank() },
                ),
            )
        }
    }

    fun requestCaptchaRetry(throwable: Throwable, action: suspend () -> Unit): Boolean {
        if (throwable !is CaptchaRequiredException) return false
        pendingCaptchaAction = action
        updateState {
            it.copy(
                auth = it.auth.copy(
                    loading = false,
                    error = throwable.userMessage(),
                    captchaRequestNonce = it.auth.captchaRequestNonce + 1,
                ),
            )
        }
        return true
    }

    fun login(login: String, password: String, captchaResponse: String? = null) {
        if (login.isBlank() || password.isBlank()) {
            updateState { it.copy(auth = it.auth.copy(error = enterLoginAndPasswordMessage())) }
            return
        }

        val normalizedLogin = login.trim()
        updateState { it.copy(auth = it.auth.copy(loading = true, error = null)) }
        authOperations.launchLatest(scope) { lease ->
            runCatching { repository.login(normalizedLogin, password, captchaResponse) }
                .onSuccess { profile ->
                    if (!lease.isCurrent) return@onSuccess
                    updateState {
                        it.copy(
                            auth = AuthUiState(profile = profile),
                            localWatchHistoryMergePrompt = null,
                        )
                    }
                    animeRatingCoordinator.restore(profile.id)
                    syncPlaybackHistoryFromSite(false, null, true)
                    videoSubscriptionStateCoordinator.synchronize()
                    (currentState().route as? AppRoute.Details)?.let { route ->
                        animeMarkCoordinator.load(route.animeId)
                        loadAnimeExtras(route.animeId)
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    if (!requestCaptchaRetry(throwable) { login(normalizedLogin, password) }) {
                        updateState {
                            it.copy(auth = AuthUiState(error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun logout() {
        pendingCaptchaAction = null
        videoSubscriptionStateCoordinator.cancelPendingOperations()
        authOperations.launchLatest(scope) {
            withContext(Dispatchers.IO) { repository.logout() }
            videoSubscriptionStateCoordinator.clear()
        }
        animeMarkCoordinator.clear()
        playbackProgressOperations.cancelAll()
        playbackHistoryOperations.cancel()
        animeRatingCoordinator.clear()
        animeRatingStateRuntime.cancel()
        clearDetailsRouteCache()
        profileNotificationStateRuntime.cancel()
        detailsLoadOperations.cancel()
        detailsExtrasOperations.cancel()
        commentsOperations.cancel()
        commentMutations.cancel()
        playbackHistoryStateRuntime.clearProfileState()
        SubscriptionNotificationScheduler.cancel(application)
        val filters = currentState().filters.copy(userMarks = emptySet())
        val updatedSettings = saveBrowseFilters(filters)
        updateState {
            it.copy(
                auth = AuthUiState(),
                animeMark = LoadState.Ready(null),
                globalSubscriptions = LoadState.Ready(emptyList()),
                profileNotifications = LoadState.Ready(emptyList()),
                localWatchHistoryMergePrompt = null,
                playbackHistoryLoading = false,
                filters = filters,
                settings = updatedSettings,
            )
        }
        browseContentCoordinator.reload()
    }

    fun authenticatedDetailsAnimeIdOrNull(): Long? {
        val animeId = (currentState().route as? AppRoute.Details)?.animeId ?: return null
        if (currentState().auth.profile == null) {
            updateState { it.copy(auth = it.auth.copy(error = AUTH_REQUIRED_ERROR_KEY)) }
            return null
        }
        return animeId
    }

    fun restoreProfile() {
        updateState { it.copy(auth = it.auth.copy(loading = true)) }
        authOperations.launchLatest(scope) { lease ->
            val cachedProfile = withContext(Dispatchers.IO) { repository.cachedProfile() }
            if (!lease.isCurrent) return@launchLatest
            updateState { it.copy(auth = AuthUiState(profile = cachedProfile, loading = true)) }
            if (cachedProfile != null) {
                syncPlaybackHistoryFromSite(false, null, false)
            }
            runCatching { repository.restoreProfile() }
                .onSuccess { profile ->
                    if (!lease.isCurrent) return@onSuccess
                    val activeProfile = profile
                    updateState {
                        it.copy(
                            auth = AuthUiState(profile = activeProfile),
                            localWatchHistoryMergePrompt = null,
                            playbackHistoryLoading = if (activeProfile == null) false else it.playbackHistoryLoading,
                        )
                    }
                    animeRatingCoordinator.restore(activeProfile?.id)
                    if (activeProfile != null) {
                        if (cachedProfile?.id != activeProfile.id || !playbackHistoryOperations.isActive) {
                            syncPlaybackHistoryFromSite(false, null, false)
                        }
                        videoSubscriptionStateCoordinator.synchronize()
                    } else {
                        playbackHistoryStateRuntime.clearProfileState()
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    if (throwable.isUnauthorizedApiError()) {
                        withContext(Dispatchers.IO) { repository.logout() }
                        animeRatingCoordinator.clear()
                        clearDetailsRouteCache()
                        videoSubscriptionStateCoordinator.clear()
                        playbackHistoryStateRuntime.clearProfileState()
                        updateState {
                            it.copy(
                                auth = AuthUiState(),
                                localWatchHistoryMergePrompt = null,
                                playbackHistoryLoading = false,
                            )
                        }
                    } else {
                        updateState {
                            it.copy(auth = AuthUiState(profile = cachedProfile, error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun isActiveProfile(profileId: Long): Boolean {
        val current = currentState()
        return !current.forcedOfflineMode && current.auth.profile?.id == profileId
    }
}
