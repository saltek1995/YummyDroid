package me.yummydroid.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.animeIdForOpen
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.ui.theme.YummySpacing

// ProfileChildDialogs
@Composable
internal fun ProfileChildDialogs(
    profileAvailable: Boolean,
    subscriptionsOpen: Boolean,
    notificationsOpen: Boolean,
    subscriptionsState: LoadState<List<VideoSubscription>>,
    notificationsState: LoadState<List<SiteNotification>>,
    context: Context,
    openSiteError: String,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onMarkNotificationRead: (SiteNotification) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    onDeleteNotification: (SiteNotification) -> Unit,
    onRefreshNotifications: () -> Unit,
    onCloseSubscriptions: () -> Unit,
    onCloseNotifications: () -> Unit,
    onDismissProfile: () -> Unit,
) {
    if (subscriptionsOpen && profileAvailable) {
        ProfileSubscriptionsDialog(
            subscriptionsState = subscriptionsState,
            onOpenAnime = { animeId ->
                onCloseSubscriptions()
                onDismissProfile()
                onOpenAnime(animeId)
            },
            onUnsubscribe = onUnsubscribe,
            onDismiss = onCloseSubscriptions,
        )
    }
    if (notificationsOpen && profileAvailable) {
        ProfileNotificationsDialog(
            notificationsState = notificationsState,
            onOpenNotification = { notification ->
                onMarkNotificationRead(notification)
                val animeId = notification.animeIdForOpen()
                if (animeId != null) {
                    onCloseNotifications()
                    onDismissProfile()
                    onOpenAnime(animeId)
                } else if (notification.clickUrl.isNotBlank()) {
                    openExternalUrl(context, notification.clickUrl, openSiteError)
                }
            },
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = onMarkAllNotificationsRead,
            onDelete = onDeleteNotification,
            onRefresh = onRefreshNotifications,
            onDismiss = onCloseNotifications,
        )
    }
}

internal fun openExternalUrl(
    context: Context,
    url: String,
    errorMessage: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }.onFailure {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}

// ProfileDialog
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

// ProfileDialogActions
@Composable
internal fun ProfileDialogActions(
    unreadNotifications: Int,
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSite: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        ProfileActionRow(
            firstText = uiText(UiStringKey.Library),
            onFirst = onOpenLibrary,
            secondText = uiText(UiStringKey.Subscriptions),
            onSecond = onOpenSubscriptions,
        )
        ProfileActionRow(
            firstText = uiText(UiStringKey.Notifications),
            onFirst = onOpenNotifications,
            firstBadgeText = unreadNotifications.notificationBadgeText(),
            secondText = uiText(UiStringKey.Profile),
            onSecond = onOpenSite,
        )
        ProfileActionRow(
            firstText = uiText(UiStringKey.SignOut),
            onFirst = onLogout,
            firstPrimary = true,
            secondText = uiText(UiStringKey.Close),
            onSecond = onDismiss,
        )
    }
}

@Composable
private fun ProfileActionRow(
    firstText: String,
    onFirst: () -> Unit,
    secondText: String,
    onSecond: () -> Unit,
    firstBadgeText: String? = null,
    firstPrimary: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        DialogActionButton(
            text = firstText,
            primary = firstPrimary,
            onClick = onFirst,
            badgeText = firstBadgeText,
            modifier = Modifier.weight(1f),
        )
        DialogActionButton(
            text = secondText,
            onClick = onSecond,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun ProfileProperty(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ProfileDialogContent
@Composable
internal fun ProfileDialogContent(
    profile: UserProfile?,
    authError: String?,
) {
    if (profile == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = uiText(UiStringKey.YouAreNotSignedIn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            authError?.let { message -> InlineErrorMessage(message = message) }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileSummary(profile)
        if (profile.banned) {
            InlineErrorMessage(message = uiText(UiStringKey.TheAccountIsBlockedOnTheSite))
        }
        if (profile.about.isNotBlank()) {
            ProfileProperty(label = uiText(UiStringKey.About312416), value = profile.about)
        }
    }
}

@Composable
private fun ProfileSummary(profile: UserProfile) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (profile.avatarUrl.isBlank()) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            } else {
                PosterImage(
                    url = profile.avatarUrl,
                    contentDescription = profile.nickname,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = profile.nickname.ifBlank { uiText(UiStringKey.User) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "ID: ${profile.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ProfileDialogButtons(
    profile: UserProfile?,
    onOpenLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSite: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (profile == null) {
        DialogActionRow {
            DialogActionButton(text = uiText(UiStringKey.Close), onClick = onDismiss)
            DialogActionButton(
                text = uiText(UiStringKey.SignIn),
                primary = true,
                onClick = onOpenLogin,
            )
        }
    } else {
        ProfileDialogActions(
            unreadNotifications = profile.unreadNotifications,
            onOpenLibrary = onOpenLibrary,
            onOpenSubscriptions = onOpenSubscriptions,
            onOpenNotifications = onOpenNotifications,
            onOpenSite = onOpenSite,
            onLogout = onLogout,
            onDismiss = onDismiss,
        )
    }
}
