package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.UserProfile

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
