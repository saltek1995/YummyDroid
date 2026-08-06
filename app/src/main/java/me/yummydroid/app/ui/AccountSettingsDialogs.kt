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
    val appContentCacheSizeText = remember(appContentCacheSizeBytes) {
        formatCacheSize(appContentCacheSizeBytes)
    }
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
                        value = uiText(UiStringKey.CacheSize, appContentCacheSizeText),
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
