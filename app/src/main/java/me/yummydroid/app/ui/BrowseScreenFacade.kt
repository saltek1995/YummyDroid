package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters

@Composable
internal fun BrowseScreen(
    state: YummyDroidUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    activeFocusRequestNonce: Long,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit = {},
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    loginDialogOpen: Boolean = false,
    profileDialogOpen: Boolean = false,
    settingsDialogOpen: Boolean = false,
    active: Boolean = true,
    onOpenAnime: (Long) -> Unit,
) {
    BrowseScreenRuntime(
        state = state,
        config = BrowseScreenRuntimeConfig(
            browseCoordinator = browseCoordinator,
            activeFocusRequestNonce = activeFocusRequestNonce,
            onRegisterHomeBackToTopHandler = onRegisterHomeBackToTopHandler,
            onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            onRegisterDpadFocusRecoveryHandler = onRegisterDpadFocusRecoveryHandler,
            loginDialogOpen = loginDialogOpen,
            profileDialogOpen = profileDialogOpen,
            settingsDialogOpen = settingsDialogOpen,
            active = active,
        ),
        actions = BrowseScreenRuntimeActions(
            onQueryChange = onQueryChange,
            onSearchSubmitted = onSearchSubmitted,
            onSearchHistorySelected = onSearchHistorySelected,
            onRefresh = onRefresh,
            onLoadMoreAnime = onLoadMoreAnime,
            onBrowseSectionChange = onBrowseSectionChange,
            onFiltersChange = onFiltersChange,
            onResetFilters = onResetFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onClearDownloadHistory = onClearDownloadHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onOpenAnime = onOpenAnime,
        ),
    )
}
