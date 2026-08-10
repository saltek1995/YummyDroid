package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.theme.YummySpacing

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
