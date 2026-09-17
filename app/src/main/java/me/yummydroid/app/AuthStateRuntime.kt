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
import me.yummydroid.app.data.UserProfile
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
    private val loadAnimeDetails: (Long) -> Unit,
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
                    animeMarkCoordinator.clear()
                    animeRatingStateRuntime.cancel()
                    clearDetailsRouteCache()
                    detailsLoadOperations.cancel()
                    detailsExtrasOperations.cancel()
                    commentsOperations.cancel()
                    commentMutations.cancel()
                    pendingCaptchaAction = null
                    updateState {
                        it.copy(
                            auth = AuthUiState(profile = profile),
                            contentSessionRevision = it.contentSessionRevision + 1L,
                            localWatchHistoryMergePrompt = null,
                        )
                    }
                    animeRatingCoordinator.restore(profile.id)
                    syncPlaybackHistoryFromSite(false, null, true)
                    videoSubscriptionStateCoordinator.synchronize()
                    (currentState().route as? AppRoute.Details)?.let { route ->
                        loadAnimeDetails(route.animeId)
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    if (!requestCaptchaRetry(throwable) { login(normalizedLogin, password) }) {
                        updateState {
                            it.copy(auth = it.auth.copy(loading = false, error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun logout() {
        authOperations.cancel()
        endProfileSession()
        authOperations.launchLatest(scope) { lease ->
            withContext(Dispatchers.IO) { repository.logout() }
            if (lease.isCurrent) reloadGuestContent()
        }
    }

    private fun endProfileSession() {
        pendingCaptchaAction = null
        videoSubscriptionStateCoordinator.cancelPendingOperations()
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
        updateState { it.withEndedProfileSession(updatedSettings) }
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
            val previousContext = currentState().contentContext()
            updateState { it.copy(auth = AuthUiState(profile = cachedProfile, loading = true)) }
            ensureRestoredVisibleContent(previousContext)
            if (cachedProfile != null) {
                videoSubscriptionStateCoordinator.synchronize()
                syncPlaybackHistoryFromSite(false, null, false)
            }
            runCatching { repository.restoreProfile() }
                .onSuccess { profile ->
                    if (!lease.isCurrent) return@onSuccess
                    applyRestoredProfile(profile, cachedProfile)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    if (throwable.isUnauthorizedApiError()) {
                        withContext(Dispatchers.IO) { repository.logout() }
                        if (lease.isCurrent) {
                            endProfileSession()
                            reloadGuestContent()
                        }
                    } else {
                        updateState {
                            it.copy(auth = AuthUiState(profile = cachedProfile, error = throwable.userMessage()))
                        }
                    }
                }
        }
    }

    private suspend fun applyRestoredProfile(profile: UserProfile?, cachedProfile: UserProfile?) {
        if (profile == null && cachedProfile != null) {
            endProfileSession()
            reloadGuestContent()
            return
        }
        val previousContext = currentState().contentContext()
        updateState {
            it.copy(
                auth = AuthUiState(profile = profile),
                localWatchHistoryMergePrompt = null,
                playbackHistoryLoading = profile != null && it.playbackHistoryLoading,
            )
        }
        ensureRestoredVisibleContent(previousContext)
        animeRatingCoordinator.restore(profile?.id)
        if (profile == null) {
            videoSubscriptionStateCoordinator.synchronize()
            playbackHistoryStateRuntime.clearProfileState()
            return
        }
        if (cachedProfile?.id != profile.id || !playbackHistoryOperations.isActive) {
            syncPlaybackHistoryFromSite(false, null, false)
        }
        if (cachedProfile?.id != profile.id || currentState().globalSubscriptions is LoadState.Error) {
            videoSubscriptionStateCoordinator.synchronize()
        }
    }

    private fun ensureRestoredVisibleContent(previousContext: ContentContext) =
        currentState().ensureRestoredVisibleContent(previousContext,
            ensureBrowseLoaded = browseContentCoordinator::ensureLoaded,
            loadAnimeDetails = loadAnimeDetails,
        )

    private fun reloadGuestContent() {
        browseContentCoordinator.reload()
        (currentState().route as? AppRoute.Details)?.let { loadAnimeDetails(it.animeId) }
    }

    fun isActiveProfile(profileId: Long): Boolean {
        val current = currentState()
        return !current.forcedOfflineMode && current.auth.profile?.id == profileId
    }
}

internal fun YummyDroidUiState.ensureRestoredVisibleContent(
    previousContext: ContentContext,
    ensureBrowseLoaded: (BrowseSection) -> Unit,
    loadAnimeDetails: (Long) -> Unit,
) {
    // Restoration races with the visible content request. A changed context invalidates
    // its response; restart Details too, but keep an unchanged account's current request.
    when (val visibleRoute = route) {
        AppRoute.Home -> ensureBrowseLoaded(homeSection)
        is AppRoute.Details -> if (contentContext() != previousContext) loadAnimeDetails(visibleRoute.animeId)
        else -> Unit
    }
}

internal fun YummyDroidUiState.withEndedProfileSession(settings: AppSettings): YummyDroidUiState = copy(
    contentSessionRevision = contentSessionRevision + if (auth.profile != null) 1L else 0L,
    detailsContentContext = null,
    details = LoadState.Loading,
    videos = LoadState.Loading,
    detailsExtras = LoadState.Loading,
    selectedVideoGroup = null,
    auth = AuthUiState(),
    commentSubmission = null,
    animeMark = LoadState.Ready(null),
    globalSubscriptions = LoadState.Ready(emptyList()),
    profileNotifications = LoadState.Ready(emptyList()),
    localWatchHistoryMergePrompt = null,
    playbackHistoryLoading = false,
    filters = filters.copy(userMarks = emptySet()),
    settings = settings,
)
