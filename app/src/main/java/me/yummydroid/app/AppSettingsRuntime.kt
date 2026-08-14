package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.GitHubUpdateChecker
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.normalized

internal class AppSettingsRuntime(
    private val scope: CoroutineScope,
    private val settingsStorage: AppSettingsStorage,
    private val repository: YummyAnimeRepository,
    private val siteDomainResolver: SiteDomainResolver,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val reloadCurrentRoute: (AppRoute) -> Unit,
    private val currentVersionInstalledMessage: () -> String,
) {
    private val updateChecker = GitHubUpdateChecker()
    private val siteBaseUrlOperations = LatestStateOperationCoordinator()
    private val settingsSaveOperations = LatestStateOperationCoordinator()
    private val updateCheckOperations = LatestStateOperationCoordinator()

    fun refreshSiteBaseUrl() {
        updateState { it.copy(siteBaseUrl = repository.cachedSiteBaseUrl()) }
        siteBaseUrlOperations.launchLatest(scope) { lease ->
            runCatching { repository.activeSiteBaseUrl() }
                .onSuccess { baseUrl ->
                    if (lease.isCurrent) {
                        updateState { it.copy(siteBaseUrl = baseUrl) }
                    }
                }
        }
    }

    fun updateSettings(settings: AppSettings) {
        val previousSettings = currentState().settings
        val normalizedSettings = settings.normalized()
        val languageChanged = previousSettings.contentLanguage != normalizedSettings.contentLanguage
        persistSettings(normalizedSettings)
        repository.updateContentLanguage(normalizedSettings.contentLanguage)
        siteDomainResolver.updateCandidates(normalizedSettings.siteDomains)
        updateState {
            it.copy(
                settings = normalizedSettings,
                siteBaseUrl = siteDomainResolver.cachedOrDefaultBaseUrl(),
            )
        }
        refreshSiteBaseUrl()
        if (languageChanged) {
            reloadCurrentRoute(currentState().route)
        }
    }

    fun saveBrowseFilters(filters: BrowseFilters): AppSettings {
        val updatedSettings = currentState().settings.copy(savedBrowseFilters = filters).normalized()
        persistSettings(updatedSettings)
        return updatedSettings
    }

    fun checkForUpdates() {
        updateState { it.copy(updateState = LoadState.Loading) }
        updateCheckOperations.launchLatest(scope) { lease ->
            runCatching { updateChecker.latestRelease() }
                .onSuccess { updateInfo ->
                    if (!lease.isCurrent) return@onSuccess
                    updateState {
                        it.copy(
                            updateState = LoadState.Ready(
                                updateInfo.copy(
                                    title = if (updateInfo.isNewerThanVersion(BuildConfig.VERSION_NAME)) {
                                        updateInfo.title
                                    } else {
                                        currentVersionInstalledMessage()
                                    },
                                ),
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (lease.isCurrent) {
                        updateState { it.copy(updateState = LoadState.Error(throwable.userMessage())) }
                    }
                }
        }
    }

    private fun persistSettings(settings: AppSettings) {
        settingsSaveOperations.launchLatest(scope) {
            withContext(Dispatchers.IO) {
                settingsStorage.save(settings)
            }
        }
    }
}
