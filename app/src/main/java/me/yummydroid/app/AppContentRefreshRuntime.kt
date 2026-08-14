package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.data.YummyAnimeRepository

internal class AppContentRefreshRuntime(
    private val scope: CoroutineScope,
    private val repository: YummyAnimeRepository,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val reloadCurrentRoute: (AppRoute) -> Unit,
) {
    private val filterCatalogOperations = LatestStateOperationCoordinator()
    private var offlineRecoveryJob: Job? = null

    fun loadFilterCatalog() {
        updateState { it.copy(filterCatalog = LoadState.Loading) }
        filterCatalogOperations.launchLatest(scope) { lease ->
            runCatching { repository.getFilterCatalog() }
                .onSuccess { catalog ->
                    if (!lease.isCurrent) return@onSuccess
                    updateState { it.copy(filterCatalog = LoadState.Ready(catalog)) }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (!lease.isCurrent) return@onFailure
                    updateState { it.copy(filterCatalog = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    fun startOfflineRecoveryMonitor() {
        offlineRecoveryJob?.cancel()
        offlineRecoveryJob = scope.launch {
            while (true) {
                delay(OFFLINE_RECOVERY_CHECK_INTERVAL_MS)
                if (!currentState().forcedOfflineMode) continue

                val reachableBaseUrl = runCatching { repository.checkReachableSiteBaseUrl() }.getOrNull()
                    ?: continue
                updateState {
                    it.copy(
                        forcedOfflineMode = false,
                        siteBaseUrl = reachableBaseUrl,
                    )
                }
                reloadCurrentRoute(currentState().route)
            }
        }
    }

    private companion object {
        const val OFFLINE_RECOVERY_CHECK_INTERVAL_MS = 30_000L
    }
}
