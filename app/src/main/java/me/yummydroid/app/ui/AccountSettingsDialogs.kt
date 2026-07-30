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
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.UpdateDownloadService

@Composable
internal fun CaptchaChallengeEffect(
    requestNonce: Long,
    onSolved: (String) -> Unit,
    onCanceled: (String?) -> Unit,
) {
    val context = LocalContext.current
    var handledNonce by remember { mutableLongStateOf(0L) }
    val captchaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val token = result.data
            ?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_TOKEN)
            .orEmpty()
        if (result.resultCode == Activity.RESULT_OK && token.isNotBlank()) {
            onSolved(token)
        } else {
            onCanceled(result.data?.getStringExtra(HCaptchaActivity.EXTRA_CAPTCHA_ERROR))
        }
    }

    LaunchedEffect(requestNonce) {
        if (requestNonce > 0L && requestNonce != handledNonce) {
            handledNonce = requestNonce
            captchaLauncher.launch(Intent(context, HCaptchaActivity::class.java))
        }
    }
}

@Composable
internal fun LoginDialog(
    auth: AuthUiState,
    siteBaseUrl: String,
    onLogin: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(auth.profile) {
        if (auth.profile != null) {
            onDismiss()
        }
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.SignIn07205a)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(uiText(UiStringKey.Email)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(uiText(UiStringKey.Password)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                auth.error?.let { message ->
                    InlineErrorMessage(message = message)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "register").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText(UiStringKey.SignUp), maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "login/reset-password").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText(UiStringKey.ForgotPassword), maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Cancel),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.SignIn),
                    primary = true,
                    enabled = !auth.loading,
                    loading = auth.loading,
                    onClick = { onLogin(login, password, null) },
                )
            }
        },
    )
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
    val subscriptionsInputActionHandler by rememberUpdatedState { action: InputAction ->
        when {
            action == InputAction.Back && subscriptionsDialogOpen -> {
                subscriptionsDialogOpen = false
                true
            }
            action == InputAction.Back && notificationsDialogOpen -> {
                notificationsDialogOpen = false
                true
            }
            else -> false
        }
    }
    DisposableEffect(subscriptionsDialogOpen, notificationsDialogOpen, onRegisterModalInputActionHandler) {
        if (subscriptionsDialogOpen || notificationsDialogOpen) {
            onRegisterModalInputActionHandler { action -> subscriptionsInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) uiText(UiStringKey.Account) else uiText(UiStringKey.ProfileEb0b9b)) },
        text = {
            if (profile == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = uiText(UiStringKey.YouAreNotSignedIn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    auth.error?.let { message ->
                        InlineErrorMessage(message = message)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
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

                    if (profile.banned) {
                        InlineErrorMessage(message = uiText(UiStringKey.TheAccountIsBlockedOnTheSite))
                    }

                    if (profile.about.isNotBlank()) {
                        ProfileProperty(label = uiText(UiStringKey.About312416), value = profile.about)
                    }

                }
            }
        },
        confirmButton = {
            if (profile == null) {
                DialogActionRow {
                    DialogActionButton(
                        text = uiText(UiStringKey.Close),
                        onClick = onDismiss,
                    )
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
                    onOpenSubscriptions = {
                        onRefreshVideoSubscriptions()
                        subscriptionsDialogOpen = true
                    },
                    onOpenNotifications = {
                        onRefreshProfileNotifications()
                        notificationsDialogOpen = true
                    },
                    onOpenSite = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    profile.siteProfileUrl(siteBaseUrl).toUri(),
                                ),
                            )
                        }.onFailure {
                            Toast.makeText(context, openSiteError, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLogout = onLogout,
                    onDismiss = onDismiss,
                )
            }
        },
    )

    if (subscriptionsDialogOpen && profile != null) {
        ProfileSubscriptionsDialog(
            subscriptionsState = subscriptionsState,
            onOpenAnime = { animeId ->
                subscriptionsDialogOpen = false
                onDismiss()
                onOpenAnime(animeId)
            },
            onUnsubscribe = onUnsubscribeVideoSubscription,
            onDismiss = { subscriptionsDialogOpen = false },
        )
    }
    if (notificationsDialogOpen && profile != null) {
        ProfileNotificationsDialog(
            notificationsState = notificationsState,
            onOpenNotification = { notification ->
                onMarkProfileNotificationRead(notification)
                val animeId = notification.animeIdForOpen()
                if (animeId != null) {
                    notificationsDialogOpen = false
                    onDismiss()
                    onOpenAnime(animeId)
                } else if (notification.clickUrl.isNotBlank()) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, notification.clickUrl.toUri()))
                    }.onFailure {
                        Toast.makeText(context, openSiteError, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMarkRead = onMarkProfileNotificationRead,
            onMarkAllRead = onMarkAllProfileNotificationsRead,
            onDelete = onDeleteProfileNotification,
            onRefresh = onRefreshProfileNotifications,
            onDismiss = { notificationsDialogOpen = false },
        )
    }
}

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText(UiStringKey.Library),
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText(UiStringKey.Subscriptions),
                onClick = onOpenSubscriptions,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText(UiStringKey.Notifications),
                onClick = onOpenNotifications,
                badgeText = unreadNotifications.notificationBadgeText(),
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText(UiStringKey.Profile),
                onClick = onOpenSite,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText(UiStringKey.SignOut),
                primary = true,
                onClick = onLogout,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText(UiStringKey.Close),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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
        color = Color.Transparent,
        contentColor = if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
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

@Composable
internal fun ProfileSubscriptionsDialog(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Subscriptions)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                when (subscriptionsState) {
                    LoadState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
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
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        InlineErrorMessage(message = subscriptionsState.message)
                    }
                    is LoadState.Ready -> {
                        val subscriptions = subscriptionsState.data
                            .groupBy { it.profileDisplayKey }
                            .values
                            .map { group -> group.preferredProfileSubscription() }
                            .filter { it.profileVoiceTitle.isNotBlank() }
                            .sortedWith(
                                compareBy<VideoSubscription> { it.title.lowercase(Locale.ROOT) }
                                    .thenBy { it.profileVoiceTitle.lowercase(Locale.ROOT) },
                            )
                        if (subscriptions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 420.dp)
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = uiText(UiStringKey.NoSubscriptions),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 420.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                lazyItemsIndexed(
                                    subscriptions,
                                    key = { index, subscription ->
                                        "profile-subscription:${subscription.profileDisplayKey}:$index"
                                    },
                                ) { _, subscription ->
                                    SubscriptionManagementRow(
                                        subscription = subscription,
                                        onOpenAnime = { onOpenAnime(subscription.animeId) },
                                        onUnsubscribe = { onUnsubscribe(subscription) },
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
                DialogActionButton(text = uiText(UiStringKey.Close), primary = true, onClick = onDismiss)
            }
        },
    )
}

@Composable
internal fun SubscriptionManagementRow(
    subscription: VideoSubscription,
    onOpenAnime: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PosterImage(
                url = subscription.posterUrl,
                contentDescription = subscription.title,
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = subscription.title.ifBlank { uiText(UiStringKey.Anime) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subscription.profileVoiceTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onUnsubscribe,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.Disable))
            }
        }
    }
}

@Composable
internal fun SettingsActionButton(onOpenSettings: () -> Unit) {
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Settings, contentDescription = uiText(UiStringKey.Settings))
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

@Composable
internal fun SettingsVersionRow(
    version: String,
    autoCheckUpdates: Boolean,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        SettingsSwitchRow(
            title = uiText(UiStringKey.CheckUpdatesOnStartup),
            checked = autoCheckUpdates,
            onCheckedChange = onAutoCheckUpdatesChange,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = uiText(UiStringKey.Version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DialogActionButton(
                text = uiText(UiStringKey.Check),
                onClick = onCheckForUpdates,
            )
        }
    }
}

@Composable
internal fun SettingsDialog(
    settings: AppSettings,
    offlineEntries: LoadState<List<OfflineAnimeEntry>>,
    updateState: LoadState<me.yummydroid.app.data.AppUpdateInfo?>,
    onSettingsChange: (AppSettings) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var clearCacheDialogOpen by remember { mutableStateOf(false) }
    var updateDialogOpen by remember { mutableStateOf(false) }
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var decoderPickerOpen by remember { mutableStateOf(false) }
    var bufferPickerOpen by remember { mutableStateOf(false) }
    var cardSizePickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var domainsDialogOpen by remember { mutableStateOf(false) }
    var offlineDownloadsDialogOpen by remember { mutableStateOf(false) }
    val displayModeMatchingAvailable = remember(context) { context.supportsDisplayModeMatching() }
    val childDialogOpen = clearCacheDialogOpen ||
        updateDialogOpen ||
        qualityPickerOpen ||
        decoderPickerOpen ||
        bufferPickerOpen ||
        cardSizePickerOpen ||
        languagePickerOpen ||
        domainsDialogOpen ||
        offlineDownloadsDialogOpen
    val childDialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                domainsDialogOpen -> {
                    domainsDialogOpen = false
                    true
                }
                offlineDownloadsDialogOpen -> {
                    offlineDownloadsDialogOpen = false
                    true
                }
                languagePickerOpen -> {
                    languagePickerOpen = false
                    true
                }
                cardSizePickerOpen -> {
                    cardSizePickerOpen = false
                    true
                }
                bufferPickerOpen -> {
                    bufferPickerOpen = false
                    true
                }
                decoderPickerOpen -> {
                    decoderPickerOpen = false
                    true
                }
                qualityPickerOpen -> {
                    qualityPickerOpen = false
                    true
                }
                updateDialogOpen -> {
                    updateDialogOpen = false
                    true
                }
                clearCacheDialogOpen -> {
                    clearCacheDialogOpen = false
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(childDialogOpen, onRegisterModalInputActionHandler) {
        if (childDialogOpen) {
            onRegisterModalInputActionHandler { action -> childDialogInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsGroup(title = uiText(UiStringKey.Storage)) {
                    SettingsActionRow(
                        title = uiText(UiStringKey.DownloadedEpisodes),
                        value = offlineEntries.offlineSummary(),
                        onClick = { offlineDownloadsDialogOpen = true },
                    )
                    SettingsActionRow(
                        title = uiText(UiStringKey.ClearCache),
                        value = uiText(UiStringKey.VideosCardsAndProgress),
                        onClick = { clearCacheDialogOpen = true },
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.Downloads)) {
                    SettingsSliderRow(
                        title = uiText(UiStringKey.DownloadThreads),
                        value = settings.downloadParallelism,
                        valueRange = 1..4,
                        onValueChange = { onSettingsChange(settings.copy(downloadParallelism = it)) },
                    )
                    val speedUnit = uiText(UiStringKey.DownloadSpeedMegabytesPerSecond)
                    SettingsSliderRow(
                        title = uiText(UiStringKey.DownloadSpeedLimit),
                        value = settings.downloadSpeedLimitMegabytesPerSecond,
                        valueRange = MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND..MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
                        valueText = { "$it $speedUnit" },
                        supportingText = if (
                            settings.downloadSpeedLimitMegabytesPerSecond >=
                            DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND
                        ) {
                            uiText(UiStringKey.DownloadSpeedLimitWarning)
                        } else {
                            null
                        },
                        onValueChange = {
                            onSettingsChange(settings.copy(downloadSpeedLimitMegabytesPerSecond = it))
                        },
                    )
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.DownloadOverMobileData),
                        checked = settings.allowMeteredDownloads,
                        onCheckedChange = { onSettingsChange(settings.copy(allowMeteredDownloads = it)) },
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.Playback)) {
                    SettingsActionRow(
                        title = uiText(UiStringKey.DefaultQuality),
                        value = settings.defaultQuality.localizedTitle(),
                        onClick = { qualityPickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText(UiStringKey.Decoder),
                        value = settings.decoderMode.localizedTitle(),
                        onClick = { decoderPickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText(UiStringKey.BufferSize),
                        value = settings.playerBufferPreset.localizedTitle(),
                        onClick = { bufferPickerOpen = true },
                        isPicker = true,
                    )
                    if (displayModeMatchingAvailable) {
                        SettingsSwitchRow(
                            title = uiText(UiStringKey.MatchDisplayToVideo),
                            checked = settings.matchDisplayModeToVideo,
                            onCheckedChange = { onSettingsChange(settings.copy(matchDisplayModeToVideo = it)) },
                        )
                    }
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.SkipOPED),
                        checked = settings.skipOpeningsAndEndings,
                        onCheckedChange = { onSettingsChange(settings.copy(skipOpeningsAndEndings = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.AutoplayNextEpisode),
                        checked = settings.autoplayNextEpisode,
                        onCheckedChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.CatalogAndAppearance)) {
                    SettingsActionRow(
                        title = uiText(UiStringKey.CardSize),
                        value = settings.posterCardSize.localizedTitle(),
                        onClick = { cardSizePickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText(UiStringKey.AppAndContentLanguage),
                        value = settings.contentLanguage.localizedTitle(),
                        onClick = { languagePickerOpen = true },
                        isPicker = true,
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.AutomaticMarks)) {
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.MarkAsWatchingOnPlayback),
                        checked = settings.autoMarkWatchingOnPlayback,
                        onCheckedChange = { onSettingsChange(settings.copy(autoMarkWatchingOnPlayback = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.MarkAsWatchedAfterFinalEpisode),
                        checked = settings.autoMarkWatchedOnCompletedFinalEpisode,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(autoMarkWatchedOnCompletedFinalEpisode = it))
                        },
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.Network)) {
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.AppNotifications),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(notificationsEnabled = it)) },
                    )
                    SettingsActionRow(
                        title = uiText(UiStringKey.SiteDomains),
                        value = "${settings.siteDomains.size} ${uiText(UiStringKey.Domains)}",
                        onClick = { domainsDialogOpen = true },
                    )
                }

                SettingsGroup(title = uiText(UiStringKey.About)) {
                    SettingsVersionRow(
                        version = "${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
                        autoCheckUpdates = settings.autoCheckUpdates,
                        onAutoCheckUpdatesChange = { onSettingsChange(settings.copy(autoCheckUpdates = it)) },
                        onCheckForUpdates = {
                            updateDialogOpen = true
                            onCheckForUpdates()
                        },
                    )
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Done),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )

    if (clearCacheDialogOpen) {
        AlertDialog(
            modifier = Modifier.yummyDialogMotion(),
            onDismissRequest = { clearCacheDialogOpen = false },
            title = { Text(uiText(UiStringKey.ClearCache)) },
            text = {
                Text(uiText(UiStringKey.DownloadedEpisodesCachedAnimeCardsAndLocalPlaybackProgressWillBeDeletedAccountAn))
            },
            confirmButton = {
                DialogActionRow {
                    DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = { clearCacheDialogOpen = false })
                    DialogActionButton(
                        text = uiText(UiStringKey.Clear),
                        primary = true,
                        onClick = {
                            clearCacheDialogOpen = false
                            onClearAppContentCache()
                        },
                    )
                }
            },
        )
    }

    if (updateDialogOpen) {
        UpdateCheckDialog(
            updateState = updateState,
            onInstallUpdate = { info ->
                updateDialogOpen = false
                UpdateDownloadService.start(context, info.apkUrl, info.version)
            },
            onDismiss = { updateDialogOpen = false },
        )
    }

    if (qualityPickerOpen) {
        SettingsPickerDialog(
            title = uiText(UiStringKey.DefaultQuality),
            options = PreferredQuality.entries,
            selected = settings.defaultQuality,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(defaultQuality = it))
                qualityPickerOpen = false
            },
            onDismiss = { qualityPickerOpen = false },
        )
    }

    if (decoderPickerOpen) {
        SettingsPickerDialog(
            title = uiText(UiStringKey.Decoder),
            options = PlayerDecoderMode.entries,
            selected = settings.decoderMode,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(decoderMode = it))
                decoderPickerOpen = false
            },
            onDismiss = { decoderPickerOpen = false },
        )
    }

    if (bufferPickerOpen) {
        SettingsPickerDialog(
            title = uiText(UiStringKey.BufferSize),
            options = PlayerBufferPreset.entries,
            selected = settings.playerBufferPreset,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(playerBufferPreset = it))
                bufferPickerOpen = false
            },
            onDismiss = { bufferPickerOpen = false },
        )
    }

    if (cardSizePickerOpen) {
        SettingsPickerDialog(
            title = uiText(UiStringKey.CardSize),
            options = PosterCardSize.entries,
            selected = settings.posterCardSize,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(posterCardSize = it))
                cardSizePickerOpen = false
            },
            onDismiss = { cardSizePickerOpen = false },
        )
    }

    if (languagePickerOpen) {
        SettingsPickerDialog(
            title = uiText(UiStringKey.AppAndContentLanguage),
            options = ContentLanguage.entries,
            selected = settings.contentLanguage,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(contentLanguage = it))
                languagePickerOpen = false
            },
            onDismiss = { languagePickerOpen = false },
        )
    }

    if (domainsDialogOpen) {
        SettingsDomainsDialog(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = { domainsDialogOpen = false },
        )
    }

    if (offlineDownloadsDialogOpen) {
        OfflineDownloadsDialog(
            entriesState = offlineEntries,
            onDeleteVideo = onDeleteOfflineVideo,
            onDeleteAnime = onDeleteOfflineAnime,
            onDismiss = { offlineDownloadsDialogOpen = false },
        )
    }
}

@Composable
internal fun LoadState<List<OfflineAnimeEntry>>.offlineSummary(): String {
    return when (this) {
        LoadState.Loading -> uiText(UiStringKey.Loading)
        is LoadState.Error -> uiText(UiStringKey.Error)
        is LoadState.Ready -> {
            val videos = data.sumOf { it.downloadedVideos.size }
            val bytes = data.sumOf { it.totalBytes }
            if (videos == 0) uiText(UiStringKey.Empty) else "$videos ${localizedEpisodesWord(videos)} • ${localizedByteSize(bytes)}"
        }
    }
}

@Composable
internal fun OfflineDownloadsDialog(
    entriesState: LoadState<List<OfflineAnimeEntry>>,
    onDeleteVideo: (Long, Long, String?) -> Unit,
    onDeleteAnime: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 920.dp)
            .yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.DownloadedEpisodes)) },
        text = {
            when (entriesState) {
                LoadState.Loading -> LoadingPane(Modifier.height(160.dp))
                is LoadState.Error -> InlineErrorMessage(message = entriesState.message)
                is LoadState.Ready -> {
                    if (entriesState.data.isEmpty()) {
                        Text(uiText(UiStringKey.NoDownloadedEpisodesYet))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 460.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            lazyItemsIndexed(
                                entriesState.data,
                                key = { index, entry -> "offline-cache:$index:${entry.anime.id}:${entry.anime.title}" },
                            ) { _, entry ->
                                OfflineAnimeCacheCard(
                                    entry = entry,
                                    onDeleteVideo = onDeleteVideo,
                                    onDeleteAnime = onDeleteAnime,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Close), primary = true, onClick = onDismiss)
            }
        },
    )
}

@Composable
internal fun OfflineAnimeCacheCard(
    entry: OfflineAnimeEntry,
    onDeleteVideo: (Long, Long, String?) -> Unit,
    onDeleteAnime: (Long) -> Unit,
) {
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fileRows = remember(entry.videos) {
                entry.downloadedVariants
                    .offlineDeleteFiles()
                    .groupBy { it.cacheRowKey() }
                    .values
                    .map { group -> group.maxBy { it.file.bytes.coerceAtLeast(0L) } }
                    .sortedWith(
                        compareBy<OfflineDeleteFile> { it.variant.offlineEpisodeSortKey() }
                            .thenBy { it.displayVoiceTitle().lowercase(Locale.ROOT) }
                            .thenByDescending { it.file.qualityHeight() },
                    )
            }
            val episodeCount = remember(fileRows, entry.downloadedVideos) {
                fileRows
                    .map { it.variant.offlineEpisodeIdentity() }
                    .distinct()
                    .size
                    .takeIf { it > 0 }
                    ?: entry.downloadedVideos.map { it.offlineEpisodeIdentity() }.distinct().size
            }
            val totalBytes = remember(fileRows, entry.totalBytes) {
                fileRows.sumOf { it.file.bytes.coerceAtLeast(0L) }.takeIf { it > 0L } ?: entry.totalBytes
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$episodeCount ${localizedEpisodesWord(episodeCount)} • ${localizedByteSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onDeleteAnime(entry.anime.id) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = uiText(UiStringKey.DeleteAnime))
                }
            }
            if (fileRows.isEmpty()) {
                entry.downloadedVariants
                    .distinctBy { it.offlineEpisodeIdentity() to it.matchingVoiceKey }
                    .forEach { video ->
                        OfflineDownloadFileRow(
                            title = listOf(video.episodeTitle, video.matchingVoiceTitle)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            size = video.localBytes.takeIf { it > 0L }?.let { localizedByteSize(it) }.orEmpty(),
                            onDelete = { onDeleteVideo(entry.anime.id, video.id, null) },
                        )
                    }
            } else {
                fileRows.forEach { item ->
                    OfflineDownloadFileRow(
                        title = listOf(
                            item.variant.episodeTitle,
                            item.displayVoiceTitle(),
                            item.file.qualityDisplayTitle(),
                        ).filter { it.isNotBlank() }.joinToString(" • "),
                        size = item.file.bytes.takeIf { it > 0L }?.let { localizedByteSize(it) }.orEmpty(),
                        onDelete = { onDeleteVideo(entry.anime.id, item.variant.id, item.file.playbackUrl) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun OfflineDownloadFileRow(
    title: String,
    size: String,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (size.isNotBlank()) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.DeleteEpisode))
        }
    }
}

@Composable
internal fun UpdateCheckDialog(
    updateState: LoadState<me.yummydroid.app.data.AppUpdateInfo?>,
    onInstallUpdate: (me.yummydroid.app.data.AppUpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Updates)) },
        text = {
            when (updateState) {
                LoadState.Loading -> LoadingPane(Modifier.height(120.dp))
                is LoadState.Error -> InlineErrorMessage(message = updateState.message)
                is LoadState.Ready -> {
                    val info = updateState.data
                    if (info == null) {
                        Text(uiText(UiStringKey.TheUpdateCheckHasNotBeenRunYet))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val title = info.title.ifBlank { "YummyDroid ${info.version}" }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = info.body.ifBlank { uiText(UiStringKey.NoReleaseNotesYet) },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val info = updateState.readyDataOrNull()
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Close), onClick = onDismiss)
                if (info?.apkUrl?.isNotBlank() == true && info.isNewerThanInstalled()) {
                    DialogActionButton(
                        text = uiText(UiStringKey.Refresh),
                        primary = true,
                        onClick = { onInstallUpdate(info) },
                    )
                }
            }
        },
    )
}

internal fun me.yummydroid.app.data.AppUpdateInfo.isNewerThanInstalled(): Boolean {
    return isNewerThanVersion(BuildConfig.VERSION_NAME)
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        SettingsSectionTitle(title)
        content()
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    isPicker: Boolean = false,
) {
    val shape = YummyRadii.smallShape
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (isPicker) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun <T> SettingsPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionTitle: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
            ) {
                items(options, key = { it.toString() }) { option ->
                    val shape = YummyRadii.smallShape
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = YummySizes.tabHeight)
                            .dpadClickable(shape) { onSelected(option) }
                            .padding(horizontal = YummySpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelected(option) },
                        )
                        Text(
                            text = optionTitle(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )
}

@Composable
internal fun DownloadSelectionDialog(
    title: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    allEpisodes: Boolean,
    onResolveQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    confirmText: String,
    onConfirm: (VideoVariant, PreferredQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val voiceOptions = remember(videos, selectedVideo) {
        videos.downloadVoiceOptions(selectedVideo)
    }
    if (voiceOptions.isEmpty()) {
        onDismiss()
        return
    }

    var selectedVoiceKey by remember(voiceOptions, selectedVideo) {
        mutableStateOf(
            selectedVideo?.groupKey?.takeIf { groupKey -> voiceOptions.any { it.groupKey == groupKey } }
                ?: selectedVideo?.matchingVoiceKey?.let { voiceKey ->
                    voiceOptions.firstOrNull { it.matchingVoiceKey == voiceKey }?.groupKey
                }
                ?: voiceOptions.first().groupKey,
        )
    }
    var selectedQuality by remember(selected) { mutableStateOf(selected) }
    var showQualityStep by remember { mutableStateOf(false) }
    val selectedVoice = voiceOptions.firstOrNull { it.groupKey == selectedVoiceKey } ?: voiceOptions.first()
    var qualityOptions by remember(selectedVoiceKey, videos, allEpisodes) { mutableStateOf<List<PreferredQuality>?>(null) }
    var qualityError by remember(selectedVoiceKey, videos, allEpisodes) { mutableStateOf<String?>(null) }
    val qualityCheckFailedText = uiText(UiStringKey.QualityCheckFailed)

    LaunchedEffect(showQualityStep, selectedVoiceKey, videos, allEpisodes) {
        if (!showQualityStep) return@LaunchedEffect
        qualityOptions = null
        qualityError = null
        runCatching { onResolveQualities(selectedVoice, videos, allEpisodes) }
            .onSuccess { options ->
                qualityOptions = options
                selectedQuality = options.firstOrNull { it == selected }
                    ?: options.firstOrNull()
                    ?: PreferredQuality.Auto
            }
            .onFailure { throwable ->
                qualityOptions = emptyList()
                qualityError = throwable.message?.takeIf { it.isNotBlank() } ?: qualityCheckFailedText
            }
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(if (showQualityStep) uiText(UiStringKey.Quality) else title) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!showQualityStep) {
                    item("voice-hint") {
                        Text(
                            text = uiText(UiStringKey.ChooseVoice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(voiceOptions, key = { "voice:${it.groupKey}" }) { option ->
                        DialogRadioRow(
                            title = option.matchingVoiceTitle,
                            subtitle = option.downloadVoiceSubtitle(videos),
                            downloadedCount = option.downloadedVoiceEpisodeCount(videos),
                            selected = option.groupKey == selectedVoiceKey,
                            onClick = { selectedVoiceKey = option.groupKey },
                        )
                    }
                } else {
                    item("quality-hint") {
                        Text(
                            text = selectedVoice.matchingVoiceTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    when {
                        qualityOptions == null -> {
                            item("quality-loading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = uiText(UiStringKey.SearchingQualityOptions),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        qualityOptions.orEmpty().isEmpty() -> {
                            item("quality-empty") {
                                InlineErrorMessage(
                                    message = qualityError ?: uiText(UiStringKey.NoQualitiesAreAvailableForTheSelectedVoice),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
                        else -> {
                            items(qualityOptions.orEmpty(), key = { "quality:${it.name}" }) { option ->
                                DialogRadioRow(
                                    title = option.localizedTitle(),
                                    downloadedCount = selectedVoice.downloadedQualityEpisodeCount(videos, option),
                                    selected = option == selectedQuality,
                                    onClick = { selectedQuality = option },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                if (showQualityStep) {
                    DialogActionButton(
                        text = uiText(UiStringKey.Back),
                        onClick = { showQualityStep = false },
                    )
                    DialogActionButton(
                        text = confirmText,
                        primary = true,
                        enabled = qualityOptions.orEmpty().isNotEmpty(),
                        onClick = { onConfirm(selectedVoice, selectedQuality) },
                    )
                } else {
                    DialogActionButton(
                        text = uiText(UiStringKey.Cancel),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText(UiStringKey.Next),
                        primary = true,
                        onClick = { showQualityStep = true },
                    )
                }
            }
        },
    )
}

@Composable
internal fun DialogRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    downloadedCount: Int = 0,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (downloadedCount > 0) {
            DownloadedVoiceBadge(downloadedCount)
        }
    }
}

@Composable
internal fun DownloadedVoiceBadge(count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SettingsDomainsDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var newDomain by remember(settings.siteDomains) { mutableStateOf("") }
    var domainError by remember(settings.siteDomains) { mutableStateOf<String?>(null) }
    val invalidDomainText = uiText(UiStringKey.InvalidDomain)
    val duplicateDomainText = uiText(UiStringKey.DomainIsAlreadyInTheList)

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text("${uiText(UiStringKey.SiteDomains)} (${settings.siteDomains.size})") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                    .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(settings.siteDomains, key = { it }) { domain ->
                        Surface(
                            color = yummySurfaceColor(YummySurfaceRole.Row),
                            contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
                            shape = YummyRadii.smallShape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = domain.domainDisplayTitle(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    enabled = settings.siteDomains.size > 1,
                                    onClick = {
                                        onSettingsChange(settings.copy(siteDomains = settings.siteDomains - domain))
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .focusRing(RoundedCornerShape(8.dp)),
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.RemoveDomain))
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = newDomain,
                    onValueChange = {
                        newDomain = it
                        domainError = null
                    },
                    singleLine = true,
                    label = { Text(uiText(UiStringKey.NewDomain)) },
                    isError = domainError != null,
                    supportingText = domainError?.let { message -> { Text(message) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Reset),
                    onClick = {
                        newDomain = ""
                        domainError = null
                        onSettingsChange(settings.copy(siteDomains = SiteDomainResolver.DEFAULT_SITE_DOMAINS))
                    },
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Add),
                    primary = true,
                    onClick = {
                        val normalized = normalizeSiteBaseUrl(newDomain)
                        when {
                            normalized == null -> domainError = invalidDomainText
                            settings.siteDomains.any { it.trimEnd('/').equals(normalized.trimEnd('/'), ignoreCase = true) } ->
                                domainError = duplicateDomainText
                            else -> {
                                onSettingsChange(
                                    settings.copy(
                                        siteDomains = (settings.siteDomains + normalized).normalizedSiteBaseUrls(),
                                    ),
                                )
                                newDomain = ""
                                domainError = null
                            }
                        }
                    },
                )
            }
        }
    )
}

internal fun String.domainDisplayTitle(): String {
    return removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = YummyRadii.smallShape
    Surface(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueText: (Int) -> String = { it.toString() },
    supportingText: String? = null,
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valueText(coercedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = coercedValue.toFloat(),
                onValueChange = { raw -> onValueChange(raw.roundToInt().coerceIn(valueRange.first, valueRange.last)) },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = (valueRange.count() - 2).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onValueChange((coercedValue - 1).coerceIn(valueRange.first, valueRange.last))
                                true
                            }
                            Key.DirectionRight -> {
                                onValueChange((coercedValue + 1).coerceIn(valueRange.first, valueRange.last))
                                true
                            }
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}

private fun Int.notificationBadgeText(): String? {
    return takeIf { it > 0 }?.let { count ->
        if (count > 99) "99+" else count.toString()
    }
}
