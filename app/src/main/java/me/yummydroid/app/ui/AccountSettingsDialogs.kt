package me.yummydroid.app.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.Locale
import kotlinx.coroutines.launch
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.animeIdForOpen
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.InterfaceScale
import me.yummydroid.app.data.INTERFACE_SCALE_STEP_PERCENT
import me.yummydroid.app.data.MAX_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.MIN_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.isTelevisionDevice
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummySpacing

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
    appContentCacheSizeBytes: Long,
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
    var childDialog by remember { mutableStateOf<SettingsChildDialog?>(null) }
    val displayModeMatchingAvailable = remember(context) { context.supportsDisplayModeMatching() }
    val televisionDevice = remember(context) { context.isTelevisionDevice() }
    val appContentCacheSizeText = remember(appContentCacheSizeBytes) {
        formatCacheSize(appContentCacheSizeBytes)
    }
    val childDialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (!shouldCloseSettingsChildDialog(action, childDialog)) {
            false
        } else {
            childDialog = null
            true
        }
    }
    DisposableEffect(childDialog, onRegisterModalInputActionHandler) {
        if (childDialog != null) {
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
            SettingsDialogContent(
                settings = settings,
                offlineEntries = offlineEntries,
                appContentCacheSizeText = appContentCacheSizeText,
                televisionDevice = televisionDevice,
                displayModeMatchingAvailable = displayModeMatchingAvailable,
                onSettingsChange = onSettingsChange,
                onOpenChildDialog = { dialog -> childDialog = dialog },
                onCheckForUpdates = onCheckForUpdates,
            )
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

    SettingsChildDialogHost(
        childDialog = childDialog,
        settings = settings,
        offlineEntries = offlineEntries,
        updateState = updateState,
        onSettingsChange = onSettingsChange,
        onDeleteOfflineVideo = onDeleteOfflineVideo,
        onDeleteOfflineAnime = onDeleteOfflineAnime,
        onClearAppContentCache = onClearAppContentCache,
        onDismiss = { childDialog = null },
    )
}

@Composable
internal fun InterfaceScaleDialog(
    scale: InterfaceScale,
    onApply: (InterfaceScale) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPercent by remember(scale.percent) {
        mutableIntStateOf(InterfaceScale.fromPercent(scale.percent).percent)
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.InterfaceScale)) },
        text = {
            SettingsSliderRow(
                title = uiText(UiStringKey.InterfaceScale),
                value = selectedPercent,
                valueRange = MIN_INTERFACE_SCALE_PERCENT..MAX_INTERFACE_SCALE_PERCENT,
                valueStep = INTERFACE_SCALE_STEP_PERCENT,
                valueText = { "$it%" },
                onValueChange = { selectedPercent = it },
            )
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = onDismiss)
                DialogActionButton(
                    text = uiText(UiStringKey.Apply),
                    primary = true,
                    onClick = { onApply(InterfaceScale.fromPercent(selectedPercent)) },
                )
            }
        },
    )
}

internal fun formatCacheSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L).toDouble()
    val units = listOf("B", "KB", "MB", "GB")
    var value = safeBytes
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val formatted = if (unitIndex == 0 || value >= 100.0) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}
