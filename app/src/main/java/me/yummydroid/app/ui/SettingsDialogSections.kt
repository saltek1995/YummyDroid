package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND
import me.yummydroid.app.data.MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality

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
