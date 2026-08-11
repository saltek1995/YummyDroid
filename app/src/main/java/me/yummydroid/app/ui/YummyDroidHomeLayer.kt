package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key

@Composable
internal fun HomeLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val actions = runtime.actions
    AppLayerContainer(zIndex = zIndex, visible = visible) {
        key(AppScreenKey.Home) {
            BrowseScreen(
                state = layer.state,
                browseCoordinator = runtime.browseCoordinator,
                activeFocusRequestNonce = activeLayerValue(active, runtime.activeLayerFocusRequestNonce, 0L),
                onRegisterHomeBackToTopHandler = activeLayerValue(
                    active,
                    runtime.onHomeBackToTopHandlerChange,
                    { _, _ -> },
                ),
                onHomeBrowseBackStateChange = activeLayerValue(
                    active,
                    runtime.onHomeBrowseBackStateChange,
                    {},
                ),
                onRegisterModalInputActionHandler = activeLayerValue(
                    active,
                    { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Home, handler) },
                    {},
                ),
                onRegisterDpadFocusRecoveryHandler = activeLayerValue(
                    active,
                    { handler -> runtime.onRegisterDpadFocusRecoveryHandler(AppScreenKey.Home, handler) },
                    {},
                ),
                onQueryChange = activeLayerValue(active, actions.onQueryChange, { _ -> }),
                onSearchSubmitted = activeLayerValue(active, actions.onSearchSubmitted, { _ -> }),
                onSearchHistorySelected = activeLayerValue(active, actions.onSearchHistorySelected, { _ -> }),
                onRefresh = activeLayerValue(active, actions.onRefresh, {}),
                onLoadMoreAnime = activeLayerValue(active, actions.onLoadMoreAnime, {}),
                onBrowseSectionChange = activeLayerValue(active, actions.onBrowseSectionChange, { _ -> }),
                onFiltersChange = activeLayerValue(active, actions.onFiltersChange, { _ -> }),
                onResetFilters = activeLayerValue(active, actions.onResetFilters, {}),
                onOpenSettings = activeLayerValue(active, runtime.onOpenSettings, {}),
                onOpenDownloads = activeLayerValue(active, runtime.onOpenDownloads, {}),
                onClearDownloadHistory = activeLayerValue(active, actions.onClearDownloadHistory, {}),
                onCancelDownload = activeLayerValue(active, actions.onCancelDownload, { _ -> }),
                onPauseDownload = activeLayerValue(active, actions.onPauseDownload, { _ -> }),
                onResumeDownload = activeLayerValue(active, actions.onResumeDownload, { _ -> }),
                onOpenLogin = activeLayerValue(active, runtime.onOpenLogin, {}),
                onOpenProfile = activeLayerValue(active, runtime.onOpenProfile, {}),
                loginDialogOpen = runtime.loginDialogOpen,
                profileDialogOpen = runtime.profileDialogOpen,
                settingsDialogOpen = runtime.settingsDialogOpen,
                active = active,
                onOpenAnime = activeLayerValue(active, runtime.onOpenAnimeFromCatalog, { _ -> }),
            )
        }
    }
}
