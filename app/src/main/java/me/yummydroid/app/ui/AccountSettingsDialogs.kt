package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import me.yummydroid.app.AppLog
import me.yummydroid.app.AppRoute
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.LoadState
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatScheduleTimestamp
import me.yummydroid.app.formatViews
import me.yummydroid.app.formatWatchedAtTimestamp
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlaybackRecoveryCandidate
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeGenreFilter
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AnimeStatusFilter
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.downloadedEpisodeCountForVoice
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.selectForPreferredQuality
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle

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
        onDismissRequest = onDismiss,
        title = { Text(uiText("Вход")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text(uiText("Email")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(uiText("Пароль")) },
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
                        Text(uiText("Регистрация"), maxLines = 1, softWrap = false)
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, sitePageUrl(siteBaseUrl, "login/reset-password").toUri()),
                            )
                        },
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Text(uiText("Забыли пароль?"), maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Отмена"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Войти"),
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
    onOpenLogin: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
    onLogout: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onDismiss: () -> Unit,
) {
    val profile = auth.profile
    val context = LocalContext.current
    val openSiteError = uiText("Не удалось открыть сайт")
    var subscriptionsDialogOpen by remember { mutableStateOf(false) }
    val subscriptionsInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && subscriptionsDialogOpen) {
            subscriptionsDialogOpen = false
            true
        } else {
            false
        }
    }
    DisposableEffect(subscriptionsDialogOpen, onRegisterModalInputActionHandler) {
        if (subscriptionsDialogOpen) {
            onRegisterModalInputActionHandler { action -> subscriptionsInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile == null) uiText("Аккаунт") else uiText("Профиль")) },
        text = {
            if (profile == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = uiText("Вы не авторизованы."),
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
                                text = profile.nickname.ifBlank { uiText("Пользователь") },
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
                        InlineErrorMessage(message = uiText("Аккаунт заблокирован на сайте."))
                    }

                    if (profile.about.isNotBlank()) {
                        ProfileProperty(label = uiText("О себе"), value = profile.about)
                    }

                    ProfileProperty(
                        label = uiText("Роли"),
                        value = profile.roles.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: uiText("Нет"),
                    )
                    ProfileProperty(
                        label = uiText("Уведомления"),
                        value = profile.unreadNotifications.toString(),
                    )
                    ProfileProperty(
                        label = uiText("Сообщения"),
                        value = profile.unreadMessages.toString(),
                    )
                }
            }
        },
        confirmButton = {
            if (profile == null) {
                DialogActionRow {
                    DialogActionButton(
                        text = uiText("Закрыть"),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText("Войти"),
                        primary = true,
                        onClick = onOpenLogin,
                    )
                }
            } else {
                ProfileDialogActions(
                    onOpenLibrary = onOpenLibrary,
                    onOpenSubscriptions = {
                        onRefreshVideoSubscriptions()
                        subscriptionsDialogOpen = true
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
}

@Composable
internal fun ProfileDialogActions(
    onOpenLibrary: () -> Unit,
    onOpenSubscriptions: () -> Unit,
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
                text = uiText("Библиотека"),
                onClick = onOpenLibrary,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText("Подписки"),
                onClick = onOpenSubscriptions,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DialogActionButton(
                text = uiText("ЛК"),
                onClick = onOpenSite,
                modifier = Modifier.weight(1f),
            )
            DialogActionButton(
                text = uiText("Выйти"),
                primary = true,
                onClick = onLogout,
                modifier = Modifier.weight(1f),
            )
        }
        DialogActionButton(
            text = uiText("Закрыть"),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
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
        onDismissRequest = onDismiss,
        title = { Text(uiText("Подписки")) },
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
                                    text = uiText("Подписок нет"),
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
                DialogActionButton(text = uiText("Закрыть"), primary = true, onClick = onDismiss)
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
                    text = subscription.title.ifBlank { uiText("Аниме") },
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
                Icon(Icons.Default.Close, contentDescription = uiText("Отключить"))
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
        Icon(Icons.Default.Settings, contentDescription = uiText("Настройки"))
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
            title = uiText("Проверять обновления при запуске"),
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
                    text = uiText("Версия"),
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
                text = uiText("Проверить"),
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
    var downloadsDialogOpen by remember { mutableStateOf(false) }
    var clearCacheDialogOpen by remember { mutableStateOf(false) }
    var updateDialogOpen by remember { mutableStateOf(false) }
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var decoderPickerOpen by remember { mutableStateOf(false) }
    var bufferPickerOpen by remember { mutableStateOf(false) }
    var cardSizePickerOpen by remember { mutableStateOf(false) }
    var languagePickerOpen by remember { mutableStateOf(false) }
    var domainsDialogOpen by remember { mutableStateOf(false) }
    val displayModeMatchingAvailable = remember(context) { context.supportsDisplayModeMatching() }
    val childDialogOpen = downloadsDialogOpen ||
        clearCacheDialogOpen ||
        updateDialogOpen ||
        qualityPickerOpen ||
        decoderPickerOpen ||
        bufferPickerOpen ||
        cardSizePickerOpen ||
        languagePickerOpen ||
        domainsDialogOpen
    val childDialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                domainsDialogOpen -> {
                    domainsDialogOpen = false
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
                downloadsDialogOpen -> {
                    downloadsDialogOpen = false
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
        onDismissRequest = onDismiss,
        title = { Text(uiText("Настройки")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsGroup(title = uiText("Хранилище")) {
                    SettingsActionRow(
                        title = uiText("Скачанные серии"),
                        value = offlineEntries.offlineSummary(),
                        onClick = { downloadsDialogOpen = true },
                    )
                    SettingsActionRow(
                        title = uiText("Очистить кэш"),
                        value = uiText("Видео, карточки и прогресс"),
                        onClick = { clearCacheDialogOpen = true },
                    )
                }

                SettingsGroup(title = uiText("Загрузки")) {
                    SettingsSliderRow(
                        title = uiText("Потоки загрузки"),
                        value = settings.downloadParallelism,
                        valueRange = 1..4,
                        onValueChange = { onSettingsChange(settings.copy(downloadParallelism = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText("Скачивать через мобильный интернет"),
                        checked = settings.allowMeteredDownloads,
                        onCheckedChange = { onSettingsChange(settings.copy(allowMeteredDownloads = it)) },
                    )
                }

                SettingsGroup(title = uiText("Воспроизведение")) {
                    SettingsActionRow(
                        title = uiText("Качество по умолчанию"),
                        value = settings.defaultQuality.localizedTitle(),
                        onClick = { qualityPickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText("Декодер"),
                        value = settings.decoderMode.localizedTitle(),
                        onClick = { decoderPickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText("Объём буфера"),
                        value = settings.playerBufferPreset.localizedTitle(),
                        onClick = { bufferPickerOpen = true },
                        isPicker = true,
                    )
                    if (displayModeMatchingAvailable) {
                        SettingsSwitchRow(
                            title = uiText("Автоподстройка экрана под видео"),
                            checked = settings.matchDisplayModeToVideo,
                            onCheckedChange = { onSettingsChange(settings.copy(matchDisplayModeToVideo = it)) },
                        )
                    }
                    SettingsSwitchRow(
                        title = uiText("Пропускать OP/ED"),
                        checked = settings.skipOpeningsAndEndings,
                        onCheckedChange = { onSettingsChange(settings.copy(skipOpeningsAndEndings = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText("Автовоспроизведение следующей серии"),
                        checked = settings.autoplayNextEpisode,
                        onCheckedChange = { onSettingsChange(settings.copy(autoplayNextEpisode = it)) },
                    )
                }

                SettingsGroup(title = uiText("Каталог и оформление")) {
                    SettingsActionRow(
                        title = uiText("Размер карточек"),
                        value = settings.posterCardSize.localizedTitle(),
                        onClick = { cardSizePickerOpen = true },
                        isPicker = true,
                    )
                    SettingsActionRow(
                        title = uiText("Язык приложения и контента"),
                        value = settings.contentLanguage.localizedTitle(),
                        onClick = { languagePickerOpen = true },
                        isPicker = true,
                    )
                }

                SettingsGroup(title = uiText("Автоматические метки")) {
                    SettingsSwitchRow(
                        title = uiText("Ставить «Смотрю» при воспроизведении"),
                        checked = settings.autoMarkWatchingOnPlayback,
                        onCheckedChange = { onSettingsChange(settings.copy(autoMarkWatchingOnPlayback = it)) },
                    )
                    SettingsSwitchRow(
                        title = uiText("Ставить «Просмотрено» после последней серии"),
                        checked = settings.autoMarkWatchedOnCompletedFinalEpisode,
                        onCheckedChange = {
                            onSettingsChange(settings.copy(autoMarkWatchedOnCompletedFinalEpisode = it))
                        },
                    )
                }

                SettingsGroup(title = uiText("Сеть")) {
                    SettingsSwitchRow(
                        title = uiText("Уведомления приложения"),
                        checked = settings.notificationsEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(notificationsEnabled = it)) },
                    )
                    SettingsActionRow(
                        title = uiText("Домены сайта"),
                        value = "${settings.siteDomains.size} ${uiText("доменов")}",
                        onClick = { domainsDialogOpen = true },
                    )
                }

                SettingsGroup(title = uiText("О программе")) {
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
                    text = uiText("Готово"),
                    primary = true,
                    onClick = onDismiss,
                )
            }
        },
    )

    if (downloadsDialogOpen) {
        OfflineDownloadsDialog(
            entriesState = offlineEntries,
            onDeleteVideo = onDeleteOfflineVideo,
            onDeleteAnime = onDeleteOfflineAnime,
            onDismiss = { downloadsDialogOpen = false },
        )
    }

    if (clearCacheDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearCacheDialogOpen = false },
            title = { Text(uiText("Очистить кэш")) },
            text = {
                Text(uiText("Будут удалены скачанные серии, кэш карточек аниме и локальный прогресс просмотра. Аккаунт и авторизация останутся."))
            },
            confirmButton = {
                DialogActionRow {
                    DialogActionButton(text = uiText("Отмена"), onClick = { clearCacheDialogOpen = false })
                    DialogActionButton(
                        text = uiText("Очистить"),
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
            title = uiText("Качество по умолчанию"),
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
            title = uiText("Декодер"),
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
            title = uiText("Объём буфера"),
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
            title = uiText("Размер карточек"),
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
            title = uiText("Язык приложения и контента"),
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
}

@Composable
internal fun LoadState<List<OfflineAnimeEntry>>.offlineSummary(): String {
    return when (this) {
        LoadState.Loading -> uiText("Загрузка")
        is LoadState.Error -> uiText("Ошибка")
        is LoadState.Ready -> {
            val videos = data.sumOf { it.downloadedVideos.size }
            val bytes = data.sumOf { it.totalBytes }
            if (videos == 0) uiText("Пусто") else "$videos ${localizedEpisodesWord(videos)} • ${formatByteSize(bytes)}"
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
        onDismissRequest = onDismiss,
        title = { Text(uiText("Скачанные серии")) },
        text = {
            when (entriesState) {
                LoadState.Loading -> LoadingPane(Modifier.height(160.dp))
                is LoadState.Error -> InlineErrorMessage(message = entriesState.message)
                is LoadState.Ready -> {
                    if (entriesState.data.isEmpty()) {
                        Text(uiText("Скачанных серий пока нет"))
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
                DialogActionButton(text = uiText("Закрыть"), primary = true, onClick = onDismiss)
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
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fileRows = remember(entry.videos) {
                entry.downloadedVideos
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
                        text = "$episodeCount ${localizedEpisodesWord(episodeCount)} • ${formatByteSize(totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onDeleteAnime(entry.anime.id) },
                    modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = uiText("Удалить аниме"))
                }
            }
            if (fileRows.isEmpty()) {
                entry.downloadedVideos
                    .distinctBy { it.offlineEpisodeIdentity() to it.matchingVoiceKey }
                    .forEach { video ->
                        OfflineDownloadFileRow(
                            title = listOf(video.episodeTitle, video.matchingVoiceTitle)
                                .filter { it.isNotBlank() }
                                .joinToString(" • "),
                            size = video.localBytes.takeIf { it > 0L }?.let(::formatByteSize).orEmpty(),
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
                        size = item.file.bytes.takeIf { it > 0L }?.let(::formatByteSize).orEmpty(),
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (size.isNotBlank()) {
            Text(
                text = size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.Close, contentDescription = uiText("Удалить серию"))
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
        onDismissRequest = onDismiss,
        title = { Text(uiText("Обновления")) },
        text = {
            when (updateState) {
                LoadState.Loading -> LoadingPane(Modifier.height(120.dp))
                is LoadState.Error -> InlineErrorMessage(message = updateState.message)
                is LoadState.Ready -> {
                    val info = updateState.data
                    if (info == null) {
                        Text(uiText("Проверка еще не выполнена."))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val title = if (info.title == "Установлена актуальная версия") {
                                uiText("Установлена актуальная версия")
                            } else {
                                info.title.ifBlank { "YummyDroid ${info.version}" }
                            }
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
                                    text = info.body.ifBlank { uiText("Описание версии пока не добавлено.") },
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
                DialogActionButton(text = uiText("Закрыть"), onClick = onDismiss)
                if (info?.apkUrl?.isNotBlank() == true && info.isNewerThanInstalled()) {
                    DialogActionButton(
                        text = uiText("Обновить"),
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = yummySurfaceColor(YummySurfaceRole.Panel),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
        shape = YummyRadii.smallShape,
    ) {
        Column(
            modifier = Modifier.padding(YummySpacing.md),
            verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            SettingsSectionTitle(title)
            content()
        }
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
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
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
                    text = uiText("Закрыть"),
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
                qualityError = throwable.message?.takeIf { it.isNotBlank() } ?: "Не удалось проверить качества"
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showQualityStep) uiText("Качество") else title) },
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
                            text = uiText("Выбери озвучку"),
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
                                        text = uiText("Поиск вариантов качества"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        qualityOptions.orEmpty().isEmpty() -> {
                            item("quality-empty") {
                                InlineErrorMessage(
                                    message = qualityError ?: uiText("Для выбранной озвучки нет доступных качеств"),
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
                        text = uiText("Назад"),
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
                        text = uiText("Отмена"),
                        onClick = onDismiss,
                    )
                    DialogActionButton(
                        text = uiText("Далее"),
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
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
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
    val invalidDomainText = uiText("Некорректный домен")
    val duplicateDomainText = uiText("Домен уже в списке")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${uiText("Домены сайта")} (${settings.siteDomains.size})") },
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
                                    Icon(Icons.Default.Close, contentDescription = uiText("Удалить домен"))
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
                    label = { Text(uiText("Новый домен")) },
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
                    text = uiText("Сбросить"),
                    onClick = {
                        newDomain = ""
                        domainError = null
                        onSettingsChange(settings.copy(siteDomains = SiteDomainResolver.DEFAULT_SITE_DOMAINS))
                    },
                )
                DialogActionButton(
                    text = uiText("Закрыть"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Добавить"),
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
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
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
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coercedValue = value.coerceIn(valueRange.first, valueRange.last)
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
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
                    text = coercedValue.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
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
