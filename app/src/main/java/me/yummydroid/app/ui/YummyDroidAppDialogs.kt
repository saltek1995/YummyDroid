package me.yummydroid.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.AppUpdateInfo

internal enum class AppModalInputOwner {
    ProfileDialog,
    SettingsDialog,
}

internal class YummyDroidAppDialogRuntime(
    val context: Context,
    val actions: YummyDroidAppActions,
    val openProfileNotificationsRequest: Long,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val pendingUpdate: AppUpdateInfo?,
    val onLoginDialogOpenChange: (Boolean) -> Unit,
    val onProfileDialogOpenChange: (Boolean) -> Unit,
    val onSettingsDialogOpenChange: (Boolean) -> Unit,
    val onAutoUpdatePromptDismissed: () -> Unit,
    val onRegisterModalInputActionHandler: (Any, ((InputAction) -> Boolean)?) -> Unit,
)

@Composable
internal fun YummyDroidAppDialogHost(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    val actions = runtime.actions
    if (runtime.loginDialogOpen) {
        LoginDialog(
            auth = state.auth,
            siteBaseUrl = state.siteBaseUrl,
            onLogin = actions.onLogin,
            onDismiss = { runtime.onLoginDialogOpenChange(false) },
        )
    }

    if (runtime.profileDialogOpen) {
        ProfileDialog(
            auth = state.auth,
            siteBaseUrl = state.siteBaseUrl,
            subscriptionsState = state.globalSubscriptions,
            notificationsState = state.profileNotifications,
            onOpenLogin = {
                runtime.onProfileDialogOpenChange(false)
                runtime.onLoginDialogOpenChange(true)
            },
            onOpenLibrary = {
                runtime.onProfileDialogOpenChange(false)
                actions.onOpenLibraryFilter()
            },
            onOpenAnime = { animeId ->
                runtime.onProfileDialogOpenChange(false)
                actions.onOpenAnime(animeId)
            },
            onUnsubscribeVideoSubscription = actions.onUnsubscribeVideoSubscription,
            onRefreshVideoSubscriptions = actions.onRefreshVideoSubscriptions,
            onRefreshProfileNotifications = actions.onRefreshProfileNotifications,
            onMarkProfileNotificationRead = actions.onMarkProfileNotificationRead,
            onMarkAllProfileNotificationsRead = actions.onMarkAllProfileNotificationsRead,
            onDeleteProfileNotification = actions.onDeleteProfileNotification,
            openNotificationsRequest = runtime.openProfileNotificationsRequest,
            onOpenNotificationsRequestConsumed = actions.onProfileNotificationsRequestConsumed,
            onLogout = {
                runtime.onProfileDialogOpenChange(false)
                actions.onLogout()
            },
            onRegisterModalInputActionHandler = { handler ->
                runtime.onRegisterModalInputActionHandler(AppModalInputOwner.ProfileDialog, handler)
            },
            onDismiss = { runtime.onProfileDialogOpenChange(false) },
        )
    }

    if (runtime.settingsDialogOpen) {
        SettingsDialog(
            settings = state.settings,
            offlineEntries = state.offlineEntries,
            appContentCacheSizeBytes = state.appContentCacheSizeBytes,
            updateState = state.updateState,
            onSettingsChange = actions.onSettingsChange,
            onDeleteOfflineVideo = actions.onDeleteOfflineVideo,
            onDeleteOfflineAnime = actions.onDeleteOfflineAnime,
            onClearAppContentCache = actions.onClearAppContentCache,
            onCheckForUpdates = actions.onCheckForUpdates,
            onRegisterModalInputActionHandler = { handler ->
                runtime.onRegisterModalInputActionHandler(AppModalInputOwner.SettingsDialog, handler)
            },
            onDismiss = { runtime.onSettingsDialogOpenChange(false) },
        )
    }

    runtime.pendingUpdate?.let { pendingUpdate ->
        UpdateCheckDialog(
            updateState = LoadState.Ready(pendingUpdate),
            onInstallUpdate = { info ->
                runtime.onAutoUpdatePromptDismissed()
                UpdateDownloadService.start(runtime.context, info.apkUrl, info.version)
            },
            onDismiss = runtime.onAutoUpdatePromptDismissed,
        )
    }
}
