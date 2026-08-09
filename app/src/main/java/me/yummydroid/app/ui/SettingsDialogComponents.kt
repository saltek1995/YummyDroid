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
