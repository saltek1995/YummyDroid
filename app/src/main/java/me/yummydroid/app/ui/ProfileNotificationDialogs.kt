package me.yummydroid.app.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.animeIdForOpen
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND
import me.yummydroid.app.data.downloadVoiceOptions
import me.yummydroid.app.data.downloadedVoiceEpisodeCount
import me.yummydroid.app.data.downloadedQualityEpisodeCount
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatNotificationTimestamp
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.UpdateDownloadService

@Composable
internal fun ProfileNotificationsDialog(
    notificationsState: LoadState<List<SiteNotification>>,
    onOpenNotification: (SiteNotification) -> Unit,
    onMarkRead: (SiteNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onDelete: (SiteNotification) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Notifications)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (notificationsState) {
                    LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingPane(
                            Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                        )
                    }
                    is LoadState.Error -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        InlineErrorMessage(message = notificationsState.message)
                    }
                    is LoadState.Ready -> {
                        val notifications = notificationsState.data
                        if (notifications.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 460.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = uiText(UiStringKey.NoNotifications),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 460.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    notifications,
                                    key = { notification -> "profile-notification:${notification.id}" },
                                ) { notification ->
                                    ProfileNotificationRow(
                                        notification = notification,
                                        onOpen = { onOpenNotification(notification) },
                                        onMarkRead = { onMarkRead(notification) },
                                        onDelete = { onDelete(notification) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Refresh),
                    onClick = onRefresh,
                    compact = true,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.MarkAllRead),
                    onClick = onMarkAllRead,
                    enabled = notificationsState.readyDataOrNull()?.any { !it.viewed } == true,
                    compact = true,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    primary = true,
                    onClick = onDismiss,
                    compact = true,
                )
            }
        },
    )
}

@Composable
private fun ProfileNotificationRow(
    notification: SiteNotification,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val unread = !notification.viewed
    val unreadAccent = MaterialTheme.colorScheme.secondary
    Surface(
        modifier = Modifier
            .dpadClickable(shape, onOpen)
            .then(
                if (unread) {
                    Modifier.border(1.dp, unreadAccent.copy(alpha = 0.28f), shape)
                } else {
                    Modifier
                },
            ),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (unread) {
                            unreadAccent.copy(alpha = 0.85f)
                        } else {
                            Color.Transparent
                        },
                    ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatNotificationTimestamp(notification.dateSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (unread) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = uiText(UiStringKey.New),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = notification.title.ifBlank { uiText(UiStringKey.Notifications) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Black else FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (notification.text.isNotBlank()) {
                    Text(
                        text = notification.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (unread) {
                        ProfileNotificationActionChip(
                            text = uiText(UiStringKey.MarkRead),
                            onClick = onMarkRead,
                        )
                    }
                    ProfileNotificationActionChip(
                        text = uiText(UiStringKey.Delete),
                        onClick = onDelete,
                        destructive = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileNotificationActionChip(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier.dpadClickable(shape, onClick),
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(destructive = destructive),
        border = yummyActionBorder(),
        shape = shape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
