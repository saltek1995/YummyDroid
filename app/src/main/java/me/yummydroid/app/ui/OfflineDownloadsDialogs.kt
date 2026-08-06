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
internal fun LoadState<List<OfflineAnimeEntry>>.offlineSummary(): String {
    return when (this) {
        LoadState.Loading -> uiText(UiStringKey.Loading)
        is LoadState.Error -> uiText(UiStringKey.Error)
        is LoadState.Ready -> {
            val videos = data.sumOf { it.downloadedVideos.size }
            val bytes = data.sumOf { it.totalBytes }
            if (videos == 0) uiText(UiStringKey.Empty) else "$videos ${localizedEpisodesWord(videos)} вЂў ${localizedByteSize(bytes)}"
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
                        text = "$episodeCount ${localizedEpisodesWord(episodeCount)} вЂў ${localizedByteSize(totalBytes)}",
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
                                .joinToString(" вЂў "),
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
                        ).filter { it.isNotBlank() }.joinToString(" вЂў "),
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
