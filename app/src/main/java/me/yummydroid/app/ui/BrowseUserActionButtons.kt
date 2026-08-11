package me.yummydroid.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.yummydroid.app.AuthUiState

@Composable
internal fun BrowseDownloadsActionButton(
    activeDownloadCount: Int,
    activeDownloads: Boolean,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = onOpenDownloads,
        modifier = modifier,
        active = activeDownloadCount > 0 || activeDownloads,
        badgeText = activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseProfileActionButton(
    auth: AuthUiState,
    activeProfile: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val unreadNotifications = auth.profile?.unreadNotifications ?: 0
    BrowseActionIconButton(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (auth.profile == null) uiText(UiStringKey.SignIn) else uiText(UiStringKey.Profile),
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = modifier,
        active = activeProfile,
        badgeText = unreadNotifications.notificationBadgeText(),
        focusLinks = focusLinks,
    )
}
