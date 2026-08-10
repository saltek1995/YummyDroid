package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.data.InterfaceScale
import me.yummydroid.app.data.INTERFACE_SCALE_STEP_PERCENT
import me.yummydroid.app.data.MAX_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.MIN_INTERFACE_SCALE_PERCENT
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.InputAction
import me.yummydroid.app.isTelevisionDevice
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummySpacing

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
        String.format(Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}
