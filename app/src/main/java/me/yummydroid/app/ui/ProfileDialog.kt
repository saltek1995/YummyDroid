package me.yummydroid.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState

internal enum class ProfileChildDialog {
    Subscriptions,
    Notifications,
}

internal fun profileChildDialogForBack(
    subscriptionsOpen: Boolean,
    notificationsOpen: Boolean,
): ProfileChildDialog? = when {
    subscriptionsOpen -> ProfileChildDialog.Subscriptions
    notificationsOpen -> ProfileChildDialog.Notifications
    else -> null
}

@Composable
internal fun ProfileDialog(
    auth: AuthUiState,
    siteBaseUrl: String,
    subscriptionsState: LoadState<List<VideoSubscription>>,
    notificationsState: LoadState<List<SiteNotification>>,
    onOpenLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
    onRefreshProfileNotifications: () -> Unit,
    onMarkProfileNotificationRead: (SiteNotification) -> Unit,
    onMarkAllProfileNotificationsRead: () -> Unit,
    onDeleteProfileNotification: (SiteNotification) -> Unit,
    openNotificationsRequest: Long = 0L,
    onOpenNotificationsRequestConsumed: () -> Unit = {},
    onLogout: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = auth.profile
    val context = LocalContext.current
    val openSiteError = uiText(UiStringKey.CouldNotOpenTheSite)
    var subscriptionsDialogOpen by remember { mutableStateOf(false) }
    var notificationsDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(openNotificationsRequest, profile?.id) {
        if (openNotificationsRequest > 0L && profile != null) {
            onRefreshProfileNotifications()
            notificationsDialogOpen = true
            onOpenNotificationsRequestConsumed()
        }
    }
    ProfileModalInputEffect(
        subscriptionsOpen = subscriptionsDialogOpen,
        notificationsOpen = notificationsDialogOpen,
        onCloseSubscriptions = { subscriptionsDialogOpen = false },
        onCloseNotifications = { notificationsDialogOpen = false },
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
    )

    ProfileRootDialog(
        auth = auth,
        onOpenLogin = onOpenLogin,
        onOpenLibrary = onOpenLibrary,
        onOpenSubscriptions = {
            onRefreshVideoSubscriptions()
            subscriptionsDialogOpen = true
        },
        onOpenNotifications = {
            onRefreshProfileNotifications()
            notificationsDialogOpen = true
        },
        onOpenSite = {
            profile?.let { currentProfile ->
                openExternalUrl(
                    context = context,
                    url = currentProfile.siteProfileUrl(siteBaseUrl),
                    errorMessage = openSiteError,
                )
            }
        },
        onLogout = onLogout,
        onDismiss = onDismiss,
    )
    ProfileChildDialogs(
        profileAvailable = profile != null,
        subscriptionsOpen = subscriptionsDialogOpen,
        notificationsOpen = notificationsDialogOpen,
        subscriptionsState = subscriptionsState,
        notificationsState = notificationsState,
        context = context,
        openSiteError = openSiteError,
        onOpenAnime = onOpenAnime,
        onUnsubscribe = onUnsubscribeVideoSubscription,
        onMarkNotificationRead = onMarkProfileNotificationRead,
        onMarkAllNotificationsRead = onMarkAllProfileNotificationsRead,
        onDeleteNotification = onDeleteProfileNotification,
        onRefreshNotifications = onRefreshProfileNotifications,
        onCloseSubscriptions = { subscriptionsDialogOpen = false },
        onCloseNotifications = { notificationsDialogOpen = false },
        onDismissProfile = onDismiss,
    )
}

@Composable
private fun ProfileModalInputEffect(
    subscriptionsOpen: Boolean,
    notificationsOpen: Boolean,
    onCloseSubscriptions: () -> Unit,
    onCloseNotifications: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val inputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when (profileChildDialogForBack(subscriptionsOpen, notificationsOpen)) {
                ProfileChildDialog.Subscriptions -> {
                    onCloseSubscriptions()
                    true
                }
                ProfileChildDialog.Notifications -> {
                    onCloseNotifications()
                    true
                }
                null -> false
            }
        }
    }
    DisposableEffect(subscriptionsOpen, notificationsOpen, onRegisterModalInputActionHandler) {
        if (subscriptionsOpen || notificationsOpen) {
            onRegisterModalInputActionHandler { action -> inputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
}

@Composable
private fun ProfileRootDialog(
    auth: AuthUiState,
    onOpenLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSite: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = auth.profile
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) uiText(UiStringKey.Account) else uiText(UiStringKey.ProfileEb0b9b)) },
        text = { ProfileDialogContent(profile = profile, authError = auth.error) },
        confirmButton = {
            ProfileDialogButtons(
                profile = profile,
                onOpenLogin = onOpenLogin,
                onOpenLibrary = onOpenLibrary,
                onOpenSubscriptions = onOpenSubscriptions,
                onOpenNotifications = onOpenNotifications,
                onOpenSite = onOpenSite,
                onLogout = onLogout,
                onDismiss = onDismiss,
            )
        },
    )
}
