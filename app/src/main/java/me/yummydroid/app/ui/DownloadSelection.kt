package me.yummydroid.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.animeIdForOpen
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND
import me.yummydroid.app.data.MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.downloadVoiceOptions
import me.yummydroid.app.data.downloadedQualityEpisodeCount
import me.yummydroid.app.data.downloadedVoiceEpisodeCount
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.formatNotificationTimestamp
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

// DownloadSelectionDialog
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
        mutableStateOf(voiceOptions.initialDownloadVoiceKey(selectedVideo))
    }
    var selectedQuality by remember(selected) { mutableStateOf(selected) }
    var showQualityStep by remember { mutableStateOf(false) }
    val selectedVoice = voiceOptions.firstOrNull { it.groupKey == selectedVoiceKey } ?: voiceOptions.first()
    var qualityOptions by remember(selectedVoiceKey, videos, allEpisodes) {
        mutableStateOf<List<PreferredQuality>?>(null)
    }
    var qualityError by remember(selectedVoiceKey, videos, allEpisodes) { mutableStateOf<String?>(null) }
    DownloadQualityResolutionEffect(
        enabled = showQualityStep,
        selectedVoiceKey = selectedVoiceKey,
        selectedVoice = selectedVoice,
        videos = videos,
        allEpisodes = allEpisodes,
        preferredQuality = selected,
        qualityCheckFailedText = uiText(UiStringKey.QualityCheckFailed),
        onResolveQualities = onResolveQualities,
    ) { result ->
        qualityOptions = result?.options
        qualityError = result?.error
        result?.selectedQuality?.let { selectedQuality = it }
    }

    DownloadSelectionDialogShell(
        presentation = DownloadSelectionPresentation(
            title = title,
            showQualityStep = showQualityStep,
            voiceOptions = voiceOptions,
            videos = videos,
            selectedVoiceKey = selectedVoiceKey,
            selectedVoice = selectedVoice,
            qualityOptions = qualityOptions,
            qualityError = qualityError,
            selectedQuality = selectedQuality,
            confirmText = confirmText,
        ),
        callbacks = DownloadSelectionCallbacks(
            onVoiceSelected = { selectedVoiceKey = it },
            onQualitySelected = { selectedQuality = it },
            onBack = { showQualityStep = false },
            onConfirm = { onConfirm(selectedVoice, selectedQuality) },
            onDismiss = onDismiss,
            onNext = { showQualityStep = true },
        ),
    )
}

private data class DownloadSelectionPresentation(
    val title: String,
    val showQualityStep: Boolean,
    val voiceOptions: List<VideoVariant>,
    val videos: List<VideoVariant>,
    val selectedVoiceKey: String,
    val selectedVoice: VideoVariant,
    val qualityOptions: List<PreferredQuality>?,
    val qualityError: String?,
    val selectedQuality: PreferredQuality,
    val confirmText: String,
)

private data class DownloadSelectionCallbacks(
    val onVoiceSelected: (String) -> Unit,
    val onQualitySelected: (PreferredQuality) -> Unit,
    val onBack: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val onNext: () -> Unit,
)

@Composable
private fun DownloadSelectionDialogShell(
    presentation: DownloadSelectionPresentation,
    callbacks: DownloadSelectionCallbacks,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = callbacks.onDismiss,
        title = {
            Text(if (presentation.showQualityStep) uiText(UiStringKey.Quality) else presentation.title)
        },
        text = {
            if (presentation.showQualityStep) {
                DownloadQualityChoices(
                    selectedVoice = presentation.selectedVoice,
                    videos = presentation.videos,
                    options = presentation.qualityOptions,
                    error = presentation.qualityError,
                    selected = presentation.selectedQuality,
                    onSelected = callbacks.onQualitySelected,
                )
            } else {
                DownloadVoiceChoices(
                    options = presentation.voiceOptions,
                    videos = presentation.videos,
                    selectedVoiceKey = presentation.selectedVoiceKey,
                    onSelected = callbacks.onVoiceSelected,
                )
            }
        },
        confirmButton = {
            DownloadSelectionActions(
                showQualityStep = presentation.showQualityStep,
                confirmText = presentation.confirmText,
                canConfirm = presentation.qualityOptions.orEmpty().isNotEmpty(),
                onBack = callbacks.onBack,
                onConfirm = callbacks.onConfirm,
                onDismiss = callbacks.onDismiss,
                onNext = callbacks.onNext,
            )
        },
    )
}

private fun List<VideoVariant>.initialDownloadVoiceKey(selectedVideo: VideoVariant?): String {
    return selectedVideo?.groupKey?.takeIf { groupKey -> any { it.groupKey == groupKey } }
        ?: selectedVideo?.matchingVoiceKey?.let { voiceKey ->
            firstOrNull { it.matchingVoiceKey == voiceKey }?.groupKey
        }
        ?: first().groupKey
}

private data class DownloadQualityResolution(
    val options: List<PreferredQuality>,
    val selectedQuality: PreferredQuality? = null,
    val error: String? = null,
)

@Composable
private fun DownloadQualityResolutionEffect(
    enabled: Boolean,
    selectedVoiceKey: String,
    selectedVoice: VideoVariant,
    videos: List<VideoVariant>,
    allEpisodes: Boolean,
    preferredQuality: PreferredQuality,
    qualityCheckFailedText: String,
    onResolveQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onResult: (DownloadQualityResolution?) -> Unit,
) {
    LaunchedEffect(enabled, selectedVoiceKey, videos, allEpisodes) {
        if (!enabled) return@LaunchedEffect
        onResult(null)
        runCatching { onResolveQualities(selectedVoice, videos, allEpisodes) }
            .onSuccess { options ->
                onResult(
                    DownloadQualityResolution(
                        options = options,
                        selectedQuality = options.firstOrNull { it == preferredQuality }
                            ?: options.firstOrNull()
                            ?: PreferredQuality.Auto,
                    ),
                )
            }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                onResult(
                    DownloadQualityResolution(
                        options = emptyList(),
                        error = throwable.message?.takeIf { it.isNotBlank() } ?: qualityCheckFailedText,
                    ),
                )
            }
    }
}

@Composable
private fun DownloadVoiceChoices(
    options: List<VideoVariant>,
    videos: List<VideoVariant>,
    selectedVoiceKey: String,
    onSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item("voice-hint") {
            Text(
                text = uiText(UiStringKey.ChooseVoice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(options, key = { "voice:${it.groupKey}" }) { option ->
            DialogRadioRow(
                title = option.matchingVoiceTitle,
                subtitle = option.downloadVoiceSubtitle(videos),
                downloadedCount = option.downloadedVoiceEpisodeCount(videos),
                selected = option.groupKey == selectedVoiceKey,
                onClick = { onSelected(option.groupKey) },
            )
        }
    }
}

@Composable
private fun DownloadQualityChoices(
    selectedVoice: VideoVariant,
    videos: List<VideoVariant>,
    options: List<PreferredQuality>?,
    error: String?,
    selected: PreferredQuality,
    onSelected: (PreferredQuality) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
            options == null -> item("quality-loading") {
                DownloadQualityLoadingRow()
            }
            options.isEmpty() -> item("quality-empty") {
                InlineErrorMessage(
                    message = error ?: uiText(UiStringKey.NoQualitiesAreAvailableForTheSelectedVoice),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            else -> items(options, key = { "quality:${it.name}" }) { option ->
                DialogRadioRow(
                    title = option.localizedTitle(),
                    downloadedCount = selectedVoice.downloadedQualityEpisodeCount(videos, option),
                    selected = option == selected,
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun DownloadQualityLoadingRow() {
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

@Composable
private fun DownloadSelectionActions(
    showQualityStep: Boolean,
    confirmText: String,
    canConfirm: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
) {
    DialogActionRow {
        if (showQualityStep) {
            DialogActionButton(
                text = uiText(UiStringKey.Back),
                onClick = onBack,
            )
            DialogActionButton(
                text = confirmText,
                primary = true,
                enabled = canConfirm,
                onClick = onConfirm,
            )
        } else {
            DialogActionButton(
                text = uiText(UiStringKey.Cancel),
                onClick = onDismiss,
            )
            DialogActionButton(
                text = uiText(UiStringKey.Next),
                primary = true,
                onClick = onNext,
            )
        }
    }
}

// EpisodeDeleteDialogContent
@Composable
internal fun EpisodeDeleteDialog(
    video: VideoVariant,
    downloadedVariants: List<VideoVariant>,
    onDelete: (List<OfflineDeleteTarget>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (downloadedVariants.isEmpty()) return
    val voiceGroups = downloadedVariants
        .groupBy { it.matchingVoiceKey }
        .values
        .map { variants -> variants.sortedForPlayer() }
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text("${uiText(UiStringKey.Delete)} ${video.localizedEpisodeTitle()}") },
        text = {
            EpisodeDeleteChoices(downloadedVariants, voiceGroups, onDelete)
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    onClick = onDismiss,
                )
            }
        },
    )
}

@Composable
private fun EpisodeDeleteChoices(
    downloadedVariants: List<VideoVariant>,
    voiceGroups: List<List<VideoVariant>>,
    onDelete: (List<OfflineDeleteTarget>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SelectableFilterRow(
                title = uiText(UiStringKey.AllDownloadedVariants),
                selected = false,
                onClick = { onDelete(downloadedVariants.offlineDeleteTargets()) },
            )
        }
        items(voiceGroups, key = { variants -> "delete-offline:${variants.first().matchingVoiceKey}" }) { variants ->
            EpisodeDeleteVoiceGroup(variants, onDelete)
        }
    }
}

@Composable
private fun EpisodeDeleteVoiceGroup(
    variants: List<VideoVariant>,
    onDelete: (List<OfflineDeleteTarget>) -> Unit,
) {
    val files = variants.offlineDeleteFiles()
    val fileRows = files
        .groupBy { it.displayKey() }
        .values
        .map { group -> group.sortedBy { it.file.playbackUrl } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SelectableFilterRow(
            title = files.offlineVoiceGroupInfo(),
            selected = false,
            onClick = { onDelete(variants.offlineDeleteTargets()) },
        )
        if (fileRows.size > 1) {
            fileRows.forEach { row ->
                SelectableFilterRow(
                    title = "  ${row.offlineFileRowInfo()}",
                    selected = false,
                    onClick = { onDelete(row.map { it.target }) },
                )
            }
        }
    }
}

@Composable
private fun List<OfflineDeleteFile>.offlineVoiceGroupInfo(): String {
    val voiceTitle = firstOrNull()?.displayVoiceTitle().orEmpty().ifBlank { uiText(UiStringKey.Voice) }
    val qualities = map { it.file.qualityDisplayTitle() }.distinct().joinToString(", ")
    val bytes = sumOf { it.file.bytes.coerceAtLeast(0L) }
    return listOf(
        voiceTitle,
        qualities.ifBlank { null },
        bytes.takeIf { it > 0L }?.let { localizedByteSize(it) },
    ).filterNot { it.isNullOrBlank() }.joinToString(" \u2022 ")
}

@Composable
private fun List<OfflineDeleteFile>.offlineFileRowInfo(): String {
    val rowBytes = sumOf { it.file.bytes.coerceAtLeast(0L) }
    val voiceTitle = first().displayVoiceTitle().ifBlank { uiText(UiStringKey.Voice) }
    return first().displayTitle(
        voiceTitle = voiceTitle,
        totalBytesLabel = rowBytes.takeIf { it > 0L }?.let { localizedByteSize(it) },
    )
}

// OfflineDeleteSelection
internal data class OfflineDeleteTarget(
    val animeId: Long,
    val videoId: Long,
    val playbackUrl: String?,
)

internal data class OfflineDeleteFile(
    val variant: VideoVariant,
    val file: OfflineVideoFile,
) {
    val target: OfflineDeleteTarget
        get() = OfflineDeleteTarget(variant.animeId, variant.id, file.playbackUrl)
}

internal fun List<VideoVariant>.offlineDeleteFiles(): List<OfflineDeleteFile> {
    return flatMap { variant ->
        variant.offlineFiles
            .filter { it.playbackUrl.isNotBlank() }
            .distinctBy { it.playbackUrl }
            .map { OfflineDeleteFile(variant, it) }
    }
        .distinctBy { it.file.playbackUrl }
        .sortedWith(
            compareBy<OfflineDeleteFile> { it.displayVoiceTitle().lowercase(Locale.ROOT) }
                .thenByDescending { it.file.qualityHeight() }
                .thenBy { it.file.bytes },
        )
}

internal fun List<VideoVariant>.offlineDeleteTargets(): List<OfflineDeleteTarget> {
    val fileTargets = offlineDeleteFiles().map { it.target }
    if (fileTargets.isNotEmpty()) return fileTargets.distinctBy { it.playbackUrl }
    return filter { it.isOfflineAvailable }
        .map { OfflineDeleteTarget(it.animeId, it.id, null) }
        .distinctBy { Triple(it.animeId, it.videoId, it.playbackUrl) }
}

internal fun OfflineDeleteFile.displayVoiceTitle(): String {
    return file.voiceTitle
        .ifBlank { file.voiceTitleFromDownloadPath() }
        .ifBlank { variant.matchingVoiceTitle }
        .ifBlank { file.player.cleanVideoSourceLabel() }
        .ifBlank { variant.player.cleanVideoSourceLabel() }
        .orEmpty()
}

internal fun OfflineDeleteFile.displayKey(): String {
    return cacheRowKey()
}

internal fun OfflineDeleteFile.cacheRowKey(): String {
    return listOf(
        variant.offlineEpisodeIdentity(),
        displayVoiceTitle().lowercase(Locale.ROOT),
        file.qualityDisplayTitle().lowercase(Locale.ROOT),
    ).joinToString("|")
}

internal fun OfflineDeleteFile.displayTitle(
    voiceTitle: String = displayVoiceTitle(),
    totalBytesLabel: String? = null,
): String {
    return listOf(
        voiceTitle,
        file.qualityDisplayTitle(),
        totalBytesLabel,
    ).filterNot { it.isNullOrBlank() }.joinToString(" \u2022 ")
}

internal fun VideoVariant.offlineEpisodeIdentity(): String {
    return episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: id.toString()
}

internal fun VideoVariant.offlineEpisodeSortKey(): Double {
    return offlineEpisodeIdentity().toDoubleOrNull() ?: index.takeIf { it > 0 }?.toDouble() ?: Double.MAX_VALUE
}

internal fun OfflineVideoFile.voiceTitleFromDownloadPath(): String {
    val path = playbackUrl.toUri().path.orEmpty()
    val parts = path.split('/').filter { it.isNotBlank() }
    val rootIndex = parts.indexOfLast { it.equals("YummyDroid", ignoreCase = true) }
    val voicePart = parts.getOrNull(rootIndex + 2).orEmpty()
    return Uri.decode(voicePart)
        .replace('_', ' ')
        .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
        .orEmpty()
}

// OfflineDownloadsDialogs
@Composable
internal fun LoadState<List<OfflineAnimeEntry>>.offlineSummary(): String {
    return when (this) {
        LoadState.Loading -> uiText(UiStringKey.Loading)
        is LoadState.Error -> uiText(UiStringKey.Error)
        is LoadState.Ready -> {
            val videos = data.sumOf { it.downloadedVideos.size }
            val bytes = data.sumOf { it.totalBytes }
            if (videos == 0) uiText(UiStringKey.Empty) else "$videos ${localizedEpisodesWord(videos)} \u2022 ${localizedByteSize(bytes)}"
        }
    }
}

@Composable
internal fun OfflineDownloadsDialog(
    entriesState: LoadState<List<OfflineAnimeEntry>>,
    onDeleteVideo: (Long, Long, String?) -> Unit,
    onDeleteAnime: (Long) -> Unit,
    onRetry: () -> Unit,
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
                if (entriesState is LoadState.Error) {
                    DialogActionButton(
                        text = uiText(UiStringKey.Retry),
                        primary = true,
                        onClick = onRetry,
                    )
                }
                DialogActionButton(
                    text = uiText(UiStringKey.Close),
                    primary = entriesState !is LoadState.Error,
                    onClick = onDismiss,
                )
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
                        text = "$episodeCount ${localizedEpisodesWord(episodeCount)} \u2022 ${localizedByteSize(totalBytes)}",
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
                                .joinToString(" \u2022 "),
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
                        ).filter { it.isNotBlank() }.joinToString(" \u2022 "),
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
