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
