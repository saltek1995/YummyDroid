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
internal fun BrowseTopBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
    showCompactControls: Boolean = true,
) {
    val horizontalPadding = if (isWide) 32.dp else 16.dp
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = !isWide && screenWidthDp < 360

    if (isWide) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppWordmark(
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )

                if (forcedOfflineMode) {
                    OfflineModeChip()
                }

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeDownloadCount = activeDownloadCount,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                )
            }

            BrowseSectionTabs(
                activeSection = activeSection,
                visibleSections = visibleSections,
                onSectionSelected = onSectionSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )

            if (forcedOfflineMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    OfflineModeChip()
                }
            }

            if (showCompactControls) {
                BrowseSectionTabs(
                    activeSection = activeSection,
                    visibleSections = visibleSections,
                    onSectionSelected = onSectionSelected,
                    modifier = Modifier.fillMaxWidth(),
                )

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeDownloadCount = activeDownloadCount,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    spreadActions = !stackActions,
                    stackActions = stackActions,
                )
            }
        }
    }
}

@Composable
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 5.45f),
        )
    }
}

@Composable
internal fun BrowseBottomBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val stackActions = screenWidthDp < 360
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            onSectionSelected = onSectionSelected,
            modifier = Modifier.fillMaxWidth(),
        )
        BrowseTopBarActions(
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            auth = auth,
            activeFilters = activeFilters,
            activeSearch = activeSearch,
            activeDownloadCount = activeDownloadCount,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
            spreadActions = !stackActions,
            stackActions = stackActions,
        )
    }
}

@Composable
internal fun BrowseSectionTabs(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEach { section ->
            val selected = section == activeSection
            val shape = YummyRadii.smallShape
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(YummySizes.tabHeight)
                    .dpadClickable(shape) { onSectionSelected(section) },
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (selected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
                },
                shape = shape,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = YummySpacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = section.localizedTitle(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OfflineModeChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = YummyRadii.pillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
            Text(
                text = uiText("Оффлайн"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun BrowseTopBarActions(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    spreadActions: Boolean = false,
    stackActions: Boolean = false,
) {
    if (stackActions) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SearchActionButton(activeSearch, onOpenSearch)
                FiltersActionButton(activeFilters, onOpenFilters)
                DownloadsActionButton(activeDownloadCount, onOpenDownloads)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SettingsActionButton(onOpenSettings)
                ProfileActionButton(auth, onOpenLogin, onOpenProfile)
            }
        }
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        SearchActionButton(activeSearch, onOpenSearch)
        FiltersActionButton(activeFilters, onOpenFilters)
        DownloadsActionButton(activeDownloadCount, onOpenDownloads)
        SettingsActionButton(onOpenSettings)
        ProfileActionButton(auth, onOpenLogin, onOpenProfile)
    }
}

@Composable
internal fun ProfileActionButton(
    auth: AuthUiState,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    IconButton(
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(
            Icons.Default.AccountCircle,
            contentDescription = if (auth.profile == null) uiText("Войти") else uiText("Профиль"),
        )
    }
}

@Composable
internal fun SearchActionButton(
    activeSearch: Boolean,
    onOpenSearch: () -> Unit,
) {
    IconButton(
        onClick = onOpenSearch,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.Search, contentDescription = uiText("Поиск"))
            if (activeSearch) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .padding(start = 17.dp, top = 1.dp)
                        .size(9.dp),
                ) {}
            }
        }
    }
}
@Composable
internal fun DownloadsActionButton(
    activeDownloadCount: Int,
    onOpenDownloads: () -> Unit,
) {
    IconButton(
        onClick = onOpenDownloads,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.Download, contentDescription = uiText("Загрузки"))
            if (activeDownloadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp)
                        .widthIn(min = 17.dp)
                        .height(17.dp),
                ) {
                    Text(
                        text = if (activeDownloadCount > 9) "9+" else activeDownloadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FiltersActionButton(
    activeFilters: Int,
    onOpenFilters: () -> Unit,
) {
    IconButton(
        onClick = onOpenFilters,
        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(Icons.Default.FilterList, contentDescription = uiText("Фильтры"))
            if (activeFilters > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .size(18.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = activeFilters.coerceAtMost(9).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onExitDown: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiLanguage = LocalUiLanguage.current
    val voicePrompt = uiText("Что найти?")
    val voiceUnavailable = uiText("Голосовой поиск недоступен на этом устройстве")
    val focusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onQueryChange(recognizedText)
            }
        }
    }
    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiLanguage.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
        runCatching {
            keyboardController?.hide()
            voiceSearchLauncher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    voiceUnavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                throw throwable
            }
        }
        Unit
    }

    LaunchedEffect(Unit) {
        delay(120)
        if (isTelevision) {
            micFocusRequester.requestFocus()
        } else {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = if (isTelevision) 40.dp else 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = YummyRadii.mediumShape,
                border = yummySurfaceBorder(YummySurfaceRole.Row),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(YummySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = launchVoiceSearch,
                        modifier = Modifier
                            .size(56.dp)
                            .focusRequester(micFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                    keyboardController?.hide()
                                    onExitDown()
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = uiText("Голосовой поиск"))
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        placeholder = { Text(uiText("Найти аниме")) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        micFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        keyboardController?.hide()
                                        onExitDown()
                                        true
                                    }
                                    else -> false
                                }
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogActionRow(
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        content = content,
    )
}

@Composable
internal fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
) {
    val shape = YummyRadii.smallShape
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.primary
            } else {
                yummySurfaceColor(YummySurfaceRole.Row)
            },
            contentColor = if (primary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.disabledSurface),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = if (primary) {
            null
        } else {
            yummySurfaceBorder(YummySurfaceRole.Row)
        },
        contentPadding = if (compact) {
            PaddingValues(horizontal = 6.dp, vertical = YummySpacing.xs)
        } else {
            PaddingValues(horizontal = YummySpacing.md, vertical = YummySpacing.sm)
        },
        modifier = modifier
            .then(
                if (compact) {
                    Modifier
                } else {
                    Modifier.widthIn(
                        min = if (primary) {
                            YummySizes.primaryDialogButtonMinWidth
                        } else {
                            YummySizes.dialogButtonMinWidth
                        },
                    )
                },
            )
            .defaultMinSize(minWidth = 0.dp, minHeight = YummySizes.dialogButtonHeight)
            .focusRing(shape),
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
            textAlign = if (compact) TextAlign.Center else TextAlign.Unspecified,
        )
    }
}

@Composable
internal fun FiltersDialogAccordion(
    filters: BrowseFilters,
    auth: AuthUiState,
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null && !forcedOfflineMode
    var draft by remember(filters, isAuthorized, forcedOfflineMode) {
        val baseFilters = if (isAuthorized) {
            filters
        } else {
            filters.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
        }
        mutableStateOf(
            if (forcedOfflineMode) {
                baseFilters.copy(offlineOnly = true, userMarks = emptySet(), excludedUserMarks = emptySet())
            } else {
                baseFilters
            },
        )
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            catalogState.readyDataOrNull() ?: FilterCatalog.Empty
        }
    }
    val studioOptions = remember(catalog.studios, draft.studios, draft.studioTitles) {
        mergedFilterOptions(catalog.studios, draft.studios, draft.studioTitles)
    }
    val creatorOptions = remember(catalog.creators, draft.creators, draft.creatorTitles) {
        mergedFilterOptions(catalog.creators, draft.creators, draft.creatorTitles)
    }
    val studioOptionTitles = remember(studioOptions) {
        studioOptions.associate { it.value to it.title }
    }
    val creatorOptionTitles = remember(creatorOptions) {
        creatorOptions.associate { it.value to it.title }
    }
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
    val containerScrollState = rememberScrollState()
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        {
            applyFocusRequester.requestFocus()
            Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Фильтры")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(state = containerScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SortAccordionSection(
                    expanded = expandedSection == "sort",
                    selected = draft.sort,
                    onToggleExpanded = {
                        expandedSection = if (expandedSection == "sort") "" else "sort"
                    },
                    onSelected = { draft = draft.copy(sort = it) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "status",
                    title = uiText("Статус"),
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "genres",
                    title = uiText("Жанры"),
                    options = catalog.genres,
                    selected = draft.genres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                if (!advancedVisible) {
                    AdvancedFiltersButton(
                        activeCount = hiddenActiveCount,
                        onClick = { advancedVisible = true },
                    )
                }

                if (advancedVisible) {
                FilterAccordionSection(
                    id = "excluded_genres",
                    title = uiText("Исключить жанры"),
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "types",
                    title = uiText("Тип"),
                    options = catalog.types,
                    selected = draft.types,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "studios",
                    title = uiText("\u0421\u0442\u0443\u0434\u0438\u044f"),
                    options = studioOptions,
                    selected = draft.studios,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleStudioFilter(value, studioOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "creators",
                    title = uiText("\u0420\u0435\u0436\u0438\u0441\u0441\u0451\u0440"),
                    options = creatorOptions,
                    selected = draft.creators,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleCreatorFilter(value, creatorOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                RangeAccordionSection(
                    id = "years",
                    title = uiText("Год"),
                    summary = rangeSummary(draft.fromYear, draft.toYear),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.fromYear?.toString().orEmpty(),
                    endText = draft.toYear?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(fromYear = value.yearFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(toYear = value.yearFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "seasons",
                    title = uiText("Сезон"),
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "translates",
                    title = uiText("Озвучка"),
                    options = translateFilterOptions,
                    selected = draft.translates,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "age",
                    title = uiText("Возраст"),
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "rating_range",
                    title = uiText("Рейтинг"),
                    summary = rangeSummary(draft.minRating, draft.maxRating),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.minRating.filterText(),
                    endText = draft.maxRating.filterText(),
                    keyboardType = KeyboardType.Decimal,
                    sanitizeInput = ::decimalInput,
                    onStartChange = { value -> draft = draft.copy(minRating = value.ratingFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(maxRating = value.ratingFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "episodes",
                    title = uiText("Серии"),
                    summary = rangeSummary(draft.episodeFrom, draft.episodeTo),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText("От"),
                    endLabel = uiText("До"),
                    startText = draft.episodeFrom?.toString().orEmpty(),
                    endText = draft.episodeTo?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(episodeFrom = value.episodeFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(episodeTo = value.episodeFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                if (isAuthorized) {
                    FilterAccordionSection(
                        id = "user_marks",
                        title = uiText("Метки"),
                        options = userMarkFilterOptions,
                        selected = draft.userMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(userMarks = draft.userMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                    FilterAccordionSection(
                        id = "excluded_user_marks",
                        title = uiText("Исключить метки"),
                        options = userMarkFilterOptions,
                        selected = draft.excludedUserMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(excludedUserMarks = draft.excludedUserMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                }

                if (forcedOfflineMode) {
                    OfflineFilterNotice()
                } else {
                    SettingsSwitchRow(
                        title = uiText("Доступно офлайн"),
                        checked = draft.offlineOnly,
                        onCheckedChange = { checked -> draft = draft.copy(offlineOnly = checked) },
                    )
                }
                }

                if (!forcedOfflineMode && catalogState is LoadState.Error) {
                    InlineErrorMessage(
                        message = catalogState.message,
                        modifier = Modifier.padding(top = YummySpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogActionButton(
                    text = uiText("Сбросить"),
                    modifier = Modifier.weight(1f),
                    compact = true,
                    onClick = {
                        draft = if (forcedOfflineMode) BrowseFilters(offlineOnly = true) else BrowseFilters()
                        onReset()
                        onDismiss()
                    },
                )
                DialogActionButton(
                    text = uiText("Отмена"),
                    modifier = Modifier.weight(1f),
                    compact = true,
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Применить"),
                    primary = true,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(applyFocusRequester),
                    onClick = {
                        onApply(
                            when {
                                forcedOfflineMode -> draft.copy(
                                    offlineOnly = true,
                                    userMarks = emptySet(),
                                    excludedUserMarks = emptySet(),
                                )
                                isAuthorized -> draft
                                else -> draft.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
                            },
                        )
                        onDismiss()
                    },
                )
            }
        },
    )
}

@Composable
internal fun OfflineFilterNotice() {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText("Оффлайн: фильтруются только скачанные аниме"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AdvancedFiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    val title = if (activeCount > 0) {
        "${uiText("Расширенный режим")} • $activeCount"
    } else {
        uiText("Расширенный режим")
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
internal fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
    onSideExit: () -> Unit,
) {
    AccordionHeader(
        title = uiText("Сортировка"),
        summary = selected.localizedTitle(),
        expanded = expanded,
        active = selected != AnimeSort.Rating,
        onClick = onToggleExpanded,
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimeSort.entries.forEach { sort ->
                SelectableFilterRow(
                    title = sort.localizedTitle(),
                    selected = selected == sort,
                    onClick = { onSelected(sort) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun FilterAccordionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSideExit: () -> Unit,
    searchable: Boolean = false,
) {
    if (options.isEmpty()) return

    val sortedOptions = remember(options) { options.sortedByTitle() }
    val expanded = expandedSection == id
    var query by remember(id, expanded) { mutableStateOf("") }
    val visibleOptions = remember(sortedOptions, query, searchable) {
        if (!searchable || query.isBlank()) {
            sortedOptions
        } else {
            sortedOptions.filter { option ->
                option.title.contains(query.trim(), ignoreCase = true) ||
                    option.value.contains(query.trim(), ignoreCase = true)
            }
        }
    }
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (searchable) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(uiText("\u041f\u043e\u0438\u0441\u043a")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.isHorizontalFilterExit()) {
                                onSideExit()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            visibleOptions.forEach { option ->
                SelectableFilterRow(
                    title = option.localizedTitle(),
                    selected = option.value in selected,
                    onClick = { onToggle(option.value) },
                    onSideExit = onSideExit,
                )
            }
        }
    }
}

@Composable
internal fun RangeAccordionSection(
    id: String,
    title: String,
    summary: String,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    startLabel: String,
    endLabel: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    val expanded = expandedSection == id
    var localStart by remember(id, startText) { mutableStateOf(startText) }
    var localEnd by remember(id, endText) { mutableStateOf(endText) }

    AccordionHeader(
        title = title,
        summary = summary,
        expanded = expanded,
        active = startText.isNotBlank() || endText.isNotBlank(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )

    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = localStart,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localStart = sanitized
                    onStartChange(sanitized)
                },
                label = { Text(startLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
            OutlinedTextField(
                value = localEnd,
                onValueChange = { value ->
                    val sanitized = sanitizeInput(value)
                    localEnd = sanitized
                    onEndChange(sanitized)
                },
                label = { Text(endLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier
                    .weight(1f)
                    .padding(2.dp)
                    .defaultMinSize(minWidth = 0.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.isHorizontalFilterExit()) {
                            onSideExit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

@Composable
internal fun AccordionHeader(
    title: String,
    summary: String = "",
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
) {
    val backgroundColor = if (active) {
        yummySurfaceColor(YummySurfaceRole.ActiveRow)
    } else {
        yummySurfaceColor(YummySurfaceRole.Row)
    }
    val contentColor = if (active) {
        yummySurfaceContentColor(YummySurfaceRole.ActiveRow)
    } else {
        yummySurfaceContentColor(YummySurfaceRole.Row)
    }
    val summaryColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(RoundedCornerShape(8.dp), onClick),
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailingText.isNullOrBlank()) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = summaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
    }
}

@Composable
internal fun SelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSideExit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { event ->
                if (event.isHorizontalFilterExit()) {
                    onSideExit?.invoke()
                    onSideExit != null
                } else {
                    false
                }
            }
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onClick() },
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

internal fun androidx.compose.ui.input.key.KeyEvent.isHorizontalFilterExit(): Boolean {
    return type == KeyEventType.KeyDown && (key == Key.DirectionLeft || key == Key.DirectionRight)
}

internal fun Modifier.stopHorizontalFocusEscape(
    index: Int,
    total: Int,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
): Modifier {
    if (total <= 1 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst) left = leftExit ?: FocusRequester.Cancel
        if (isLast) right = rightExit ?: FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown &&
            (
                (event.key == Key.DirectionLeft && isFirst && leftExit == null) ||
                    (event.key == Key.DirectionRight && isLast && rightExit == null)
                )
    }
}

internal fun Modifier.stopGridLineFocusEscape(
    index: Int,
    total: Int,
    columns: Int,
    upTarget: FocusRequester?,
    downTarget: FocusRequester?,
    onMoveToIndex: (Int, Boolean) -> Unit,
): Modifier {
    if (total <= 1 || index < 0 || columns <= 0) return this
    val isFirstInLine = index % columns == 0
    val isLastInLine = index % columns == columns - 1 || index >= total - 1
    val upIndex = index - columns
    val downIndex = index + columns
    val hasUpTarget = upIndex >= 0
    val hasDownTarget = downIndex < total
    return focusProperties {
        left = FocusRequester.Cancel
        right = FocusRequester.Cancel
        if (upTarget != null) up = upTarget
        if (downTarget != null) down = downTarget else down = FocusRequester.Cancel
    }.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            when (event.key) {
                Key.DirectionLeft -> {
                    if (!isFirstInLine) {
                        onMoveToIndex(index - 1, false)
                    }
                    true
                }
                Key.DirectionRight -> {
                    if (!isLastInLine) {
                        onMoveToIndex(index + 1, false)
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (hasUpTarget) {
                        onMoveToIndex(upIndex, true)
                        true
                    } else {
                        false
                    }
                }
                Key.DirectionDown -> {
                    if (hasDownTarget) {
                        onMoveToIndex(downIndex, true)
                    }
                    true
                }
                else -> false
            }
        }
    }
}

@Composable
internal fun rangeSummary(from: Number?, to: Number?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> uiText("Все")
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "${uiText("от")} $start"
        else -> "${uiText("до")} $end"
    }
}

internal fun Number?.filterText(): String {
    return when (this) {
        null -> ""
        is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
        else -> toString()
    }
}

internal fun BrowseFilters.advancedFilterCount(isAuthorized: Boolean): Int {
    return excludedGenres.size +
        seasons.size +
        types.size +
        studios.size +
        creators.size +
        translates.size +
        ageRatings.size +
        listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
        (if (isAuthorized) userMarks.size + excludedUserMarks.size else 0) +
        if (offlineOnly) 1 else 0
}

internal fun integerInput(value: String): String {
    return value.filter { it.isDigit() }.take(5)
}

internal fun decimalInput(value: String): String {
    val normalized = value.replace(',', '.')
    val builder = StringBuilder()
    var dotSeen = false
    normalized.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString().take(4)
}

internal fun String.yearFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 1900..2100 }
}

internal fun String.episodeFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 0..10000 }
}

internal fun String.ratingFilterValue(): Double? {
    return toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
}

internal fun List<FilterOption>.sortedByTitle(): List<FilterOption> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

@Composable
internal fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return uiText("Все")

    val titles = options
        .filter { it.value in selected }
        .map { it.localizedTitle() }

    return when {
        titles.isEmpty() -> "${selected.size} ${uiText("выбрано")}"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}

internal fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
