package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND
import me.yummydroid.app.data.INTERFACE_SCALE_STEP_PERCENT
import me.yummydroid.app.data.InterfaceScale
import me.yummydroid.app.data.MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.MAX_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.MIN_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// SettingsDialogComponents
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

// SettingsDialogRows
@Composable
internal fun DialogRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    downloadedCount: Int = 0,
) {
    val shape = YummyRadii.smallShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (selected) {
                    Modifier.background(yummyActionSurfaceColor(selected = true), shape)
                } else {
                    Modifier
                },
            )
            .dpadClickable(shape, onClick)
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
        color = yummyActionSurfaceColor(selected = true),
        contentColor = yummyActionContentColor(selected = true),
        border = yummyActionBorder(selected = true),
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

// SettingsDialogSections
internal enum class SettingsChildDialog {
    ClearCache,
    Update,
    Quality,
    Decoder,
    Buffer,
    CardSize,
    InterfaceScale,
    Language,
    Domains,
    OfflineDownloads,
}

internal fun shouldCloseSettingsChildDialog(
    action: InputAction,
    childDialog: SettingsChildDialog?,
): Boolean = action == InputAction.Back && childDialog != null

@Composable
internal fun SettingsDialogContent(
    settings: AppSettings,
    offlineEntries: LoadState<List<OfflineAnimeEntry>>,
    appContentCacheSizeText: String,
    displayModeMatchingAvailable: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenChildDialog: (SettingsChildDialog) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 500.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InterfaceAndCatalogSettings(
            settings = settings,
            onOpenChildDialog = onOpenChildDialog,
        )
        PlayerSettings(
            settings = settings,
            displayModeMatchingAvailable = displayModeMatchingAvailable,
            onSettingsChange = onSettingsChange,
            onOpenChildDialog = onOpenChildDialog,
        )
        DownloadAndStorageSettings(
            settings = settings,
            offlineEntries = offlineEntries,
            appContentCacheSizeText = appContentCacheSizeText,
            onSettingsChange = onSettingsChange,
            onOpenChildDialog = onOpenChildDialog,
        )
        ViewingStatusSettings(settings, onSettingsChange)
        NotificationSettings(settings, onSettingsChange)
        NetworkAndUpdateSettings(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onOpenChildDialog = onOpenChildDialog,
            onCheckForUpdates = onCheckForUpdates,
        )
    }
}

@Composable
private fun InterfaceAndCatalogSettings(
    settings: AppSettings,
    onOpenChildDialog: (SettingsChildDialog) -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.SettingsInterfaceAndCatalog)) {
        SettingsActionRow(
            title = uiText(UiStringKey.AppAndContentLanguage),
            value = settings.contentLanguage.localizedTitle(),
            onClick = { onOpenChildDialog(SettingsChildDialog.Language) },
            isPicker = true,
        )
        SettingsActionRow(
            title = uiText(UiStringKey.CardSize),
            value = settings.posterCardSize.localizedTitle(),
            onClick = { onOpenChildDialog(SettingsChildDialog.CardSize) },
            isPicker = true,
        )
        SettingsActionRow(
            title = uiText(UiStringKey.InterfaceScale),
            value = settings.interfaceScale.title,
            onClick = { onOpenChildDialog(SettingsChildDialog.InterfaceScale) },
            isPicker = true,
        )
    }
}

@Composable
private fun PlayerSettings(
    settings: AppSettings,
    displayModeMatchingAvailable: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenChildDialog: (SettingsChildDialog) -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.SettingsPlayer)) {
        SettingsActionRow(
            title = uiText(UiStringKey.DefaultQuality),
            value = settings.defaultQuality.localizedTitle(),
            onClick = { onOpenChildDialog(SettingsChildDialog.Quality) },
            isPicker = true,
        )
        SettingsActionRow(
            title = uiText(UiStringKey.Decoder),
            value = settings.decoderMode.localizedTitle(),
            onClick = { onOpenChildDialog(SettingsChildDialog.Decoder) },
            isPicker = true,
        )
        SettingsActionRow(
            title = uiText(UiStringKey.BufferSize),
            value = settings.playerBufferPreset.localizedTitle(),
            onClick = { onOpenChildDialog(SettingsChildDialog.Buffer) },
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
}

@Composable
private fun DownloadAndStorageSettings(
    settings: AppSettings,
    offlineEntries: LoadState<List<OfflineAnimeEntry>>,
    appContentCacheSizeText: String,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenChildDialog: (SettingsChildDialog) -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.SettingsDownloadsAndStorage)) {
        SettingsActionRow(
            title = uiText(UiStringKey.DownloadedEpisodes),
            value = offlineEntries.offlineSummary(),
            onClick = { onOpenChildDialog(SettingsChildDialog.OfflineDownloads) },
        )
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
        SettingsActionRow(
            title = uiText(UiStringKey.ClearCache),
            value = uiText(UiStringKey.CacheSize, appContentCacheSizeText),
            onClick = { onOpenChildDialog(SettingsChildDialog.ClearCache) },
        )
    }
}

@Composable
private fun ViewingStatusSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.SettingsViewingStatuses)) {
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
}

@Composable
private fun NotificationSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.Notifications)) {
        SettingsSwitchRow(
            title = uiText(UiStringKey.AppNotifications),
            checked = settings.notificationsEnabled,
            onCheckedChange = { onSettingsChange(settings.copy(notificationsEnabled = it)) },
        )
    }
}

@Composable
private fun NetworkAndUpdateSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenChildDialog: (SettingsChildDialog) -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    SettingsGroup(title = uiText(UiStringKey.SettingsNetworkAndUpdates)) {
        SettingsActionRow(
            title = uiText(UiStringKey.SiteDomains),
            value = "${settings.siteDomains.size} ${uiText(UiStringKey.Domains)}",
            onClick = { onOpenChildDialog(SettingsChildDialog.Domains) },
        )
        SettingsVersionRow(
            version = "${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
            autoCheckUpdates = settings.autoCheckUpdates,
            onAutoCheckUpdatesChange = { onSettingsChange(settings.copy(autoCheckUpdates = it)) },
            onCheckForUpdates = {
                onOpenChildDialog(SettingsChildDialog.Update)
                onCheckForUpdates()
            },
        )
    }
}

@Composable
internal fun SettingsChildDialogHost(
    childDialog: SettingsChildDialog?,
    settings: AppSettings,
    offlineEntries: LoadState<List<OfflineAnimeEntry>>,
    updateState: LoadState<AppUpdateInfo?>,
    onSettingsChange: (AppSettings) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    when (childDialog) {
        SettingsChildDialog.ClearCache -> ClearCacheDialog(onClearAppContentCache, onDismiss)
        SettingsChildDialog.Update -> UpdateCheckDialog(
            updateState = updateState,
            onInstallUpdate = { info ->
                onDismiss()
                UpdateDownloadService.start(context, info.apkUrl, info.version)
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.Quality -> SettingsPickerDialog(
            title = uiText(UiStringKey.DefaultQuality),
            options = PreferredQuality.entries,
            selected = settings.defaultQuality,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(defaultQuality = it))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.Decoder -> SettingsPickerDialog(
            title = uiText(UiStringKey.Decoder),
            options = PlayerDecoderMode.entries,
            selected = settings.decoderMode,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(decoderMode = it))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.Buffer -> SettingsPickerDialog(
            title = uiText(UiStringKey.BufferSize),
            options = PlayerBufferPreset.entries,
            selected = settings.playerBufferPreset,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(playerBufferPreset = it))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.CardSize -> SettingsPickerDialog(
            title = uiText(UiStringKey.CardSize),
            options = PosterCardSize.entries,
            selected = settings.posterCardSize,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(posterCardSize = it))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.InterfaceScale -> InterfaceScaleDialog(
            scale = settings.interfaceScale,
            onApply = { scale ->
                onSettingsChange(settings.copy(interfaceScale = scale))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.Language -> SettingsPickerDialog(
            title = uiText(UiStringKey.AppAndContentLanguage),
            options = ContentLanguage.entries,
            selected = settings.contentLanguage,
            optionTitle = { it.localizedTitle() },
            onSelected = {
                onSettingsChange(settings.copy(contentLanguage = it))
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        SettingsChildDialog.Domains -> SettingsDomainsDialog(
            settings = settings,
            onSettingsChange = onSettingsChange,
            onDismiss = onDismiss,
        )
        SettingsChildDialog.OfflineDownloads -> OfflineDownloadsDialog(
            entriesState = offlineEntries,
            onDeleteVideo = onDeleteOfflineVideo,
            onDeleteAnime = onDeleteOfflineAnime,
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun ClearCacheDialog(
    onClearAppContentCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ClearCache)) },
        text = {
            Text(uiText(UiStringKey.DownloadedEpisodesCachedAnimeCardsAndLocalPlaybackProgressWillBeDeletedAccountAn))
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = onDismiss)
                DialogActionButton(
                    text = uiText(UiStringKey.Clear),
                    primary = true,
                    onClick = {
                        onDismiss()
                        onClearAppContentCache()
                    },
                )
            }
        },
    )
}

// SettingsPickerDialog
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

// SettingsRootDialog
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
    updateState: LoadState<AppUpdateInfo?>,
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
        String.format(Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}

// SettingsSliderRow
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueStep: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    supportingText: String? = null,
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coercedValue = normalizeSliderValue(value, valueRange, valueStep)
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
                onValueChange = { raw ->
                    onValueChange(normalizeSliderValue(raw.roundToInt(), valueRange, valueStep))
                },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = sliderStepCount(valueRange, valueStep),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onValueChange(
                                    normalizeSliderValue(coercedValue - valueStep, valueRange, valueStep),
                                )
                                true
                            }
                            Key.DirectionRight -> {
                                onValueChange(
                                    normalizeSliderValue(coercedValue + valueStep, valueRange, valueStep),
                                )
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

internal fun normalizeSliderValue(value: Int, valueRange: IntRange, valueStep: Int): Int {
    require(!valueRange.isEmpty()) { "valueRange must not be empty" }
    require(valueStep > 0) { "valueStep must be positive" }
    require((valueRange.last - valueRange.first) % valueStep == 0) {
        "valueStep must divide valueRange evenly"
    }
    val clamped = value.coerceIn(valueRange.first, valueRange.last)
    val stepOffset = clamped - valueRange.first
    val normalizedStep = (stepOffset + valueStep / 2) / valueStep
    return valueRange.first + normalizedStep * valueStep
}

internal fun sliderStepCount(valueRange: IntRange, valueStep: Int): Int {
    normalizeSliderValue(valueRange.first, valueRange, valueStep)
    return ((valueRange.last - valueRange.first) / valueStep - 1).coerceAtLeast(0)
}
