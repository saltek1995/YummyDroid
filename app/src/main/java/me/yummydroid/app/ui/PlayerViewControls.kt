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

@OptIn(UnstableApi::class)
internal fun PlayerView.installVideoZoomGestures(token: String) {
    val currentToken = tagValue<String>(R.id.yummy_video_zoom_token_tag)
    val currentState = tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)
    if (currentToken == token && currentState != null) {
        post { applyVideoZoom(currentState) }
        return
    }

    resetVideoZoom()
    val state = VideoZoomGestureState()
    val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val previousScale = state.scale
                state.scale = (state.scale * detector.scaleFactor).coerceIn(1f, 4f)
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                } else if (previousScale > 0f) {
                    val scaleRatio = state.scale / previousScale
                    state.offsetX = (state.offsetX * scaleRatio) + ((detector.focusX - width / 2f) * (1f - scaleRatio))
                    state.offsetY = (state.offsetY * scaleRatio) + ((detector.focusY - height / 2f) * (1f - scaleRatio))
                }
                applyVideoZoom(state)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                    applyVideoZoom(state)
                }
            }
        },
    )

    setTag(R.id.yummy_video_zoom_token_tag, token)
    setTag(R.id.yummy_video_zoom_state_tag, state)
    setOnTouchListener { view, event ->
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.lastX = event.x
                state.lastY = event.y
                state.moved = false
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                hideController()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                    true
                } else if (state.scale > 1f) {
                    val dx = event.x - state.lastX
                    val dy = event.y - state.lastY
                    state.lastX = event.x
                    state.lastY = event.y
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        state.offsetX += dx
                        state.offsetY += dy
                        state.moved = state.moved || abs(dx) > 6f || abs(dy) > 6f
                        applyVideoZoom(state)
                    }
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (state.scale > 1f && !state.moved) {
                    view.performClick()
                    showController()
                    true
                } else {
                    state.scale > 1f
                }
            }
            MotionEvent.ACTION_CANCEL -> false
            else -> event.pointerCount > 1 || state.scale > 1f
        }
    }
    post { applyVideoZoom(state) }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.applyVideoZoom(state: VideoZoomGestureState) {
    val surface = videoSurfaceView ?: return
    val scale = state.scale.coerceIn(1f, 4f)
    val maxOffsetX = surface.width * (scale - 1f) / 2f
    val maxOffsetY = surface.height * (scale - 1f) / 2f
    state.offsetX = if (maxOffsetX > 0f) state.offsetX.coerceIn(-maxOffsetX, maxOffsetX) else 0f
    state.offsetY = if (maxOffsetY > 0f) state.offsetY.coerceIn(-maxOffsetY, maxOffsetY) else 0f

    surface.pivotX = surface.width / 2f
    surface.pivotY = surface.height / 2f
    surface.scaleX = scale
    surface.scaleY = scale
    surface.translationX = state.offsetX
    surface.translationY = state.offsetY
}

@OptIn(UnstableApi::class)
internal fun PlayerView.resetVideoZoom() {
    tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)?.let { state ->
        state.scale = 1f
        state.offsetX = 0f
        state.offsetY = 0f
        state.moved = false
    }
    videoSurfaceView?.apply {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.applyPictureInPictureControllerMode(enabled: Boolean) {
    useController = !enabled
    controllerAutoShow = !enabled
    if (enabled) {
        hideController()
    }
    requestLayout()
    invalidate()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.restoreControllerAfterPictureInPicture() {
    useController = true
    controllerAutoShow = true
    hideController()
    requestLayout()
    post {
        requestLayout()
        invalidate()
        postDelayed({ showController() }, 220L)
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hasVisiblePlayerControls(): Boolean {
    if (isControllerFullyVisible) return true
    return listOf(
        Media3R.id.exo_controls_background,
        R.id.yummy_player_top_bar,
        R.id.yummy_player_episode_controls,
        R.id.yummy_skip_controls,
        Media3R.id.exo_bottom_bar,
    ).any { id ->
        findViewById<View>(id)?.let { view ->
            view.visibility == View.VISIBLE && view.isShown
        } == true
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hidePlayerControls() {
    cancelSkipAutoCountdown()
    clearActiveSkipPrompt(markDismissed = true)
    hideController()
    clearFocus()
    requestFocus()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hideVisiblePlayerControls(): Boolean {
    if (!hasVisiblePlayerControls()) return false
    hidePlayerControls()
    return true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.handleRemoteInputAction(event: InputActionEvent): Boolean {
    val action = event.action
    if (!useController) return false
    if (isSkipOnlyControllerMode()) {
        val skipButton = findViewById<View>(R.id.yummy_skip_skip)
        val watchButton = findViewById<View>(R.id.yummy_skip_watch)
        val timeBar = findViewById<View>(Media3R.id.exo_progress)
        if (action == InputAction.Confirm && skipButton?.hasFocus() == true) {
            skipButton.performClick()
            return true
        }
        if (action == InputAction.Confirm && watchButton?.hasFocus() == true) {
            watchButton.performClick()
            return true
        }
        if (action == InputAction.Back) {
            hideVisiblePlayerControls()
            return true
        }
        val movedInsideSkipPrompt = when {
            action == InputAction.Right && skipButton?.hasFocus() == true -> watchButton?.requestFocus() == true
            action == InputAction.Left && watchButton?.hasFocus() == true -> skipButton.requestFocus()
            else -> false
        }
        cancelSkipAutoCountdown()
        if (movedInsideSkipPrompt) {
            return true
        }
        setSkipOnlyControllerMode(false)
        showController()
        post {
            val focused = when {
                action == InputAction.Down && (skipButton?.hasFocus() == true || watchButton?.hasFocus() == true) -> timeBar?.requestFocus() == true
                else -> findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
            }
            if (!focused) requestFocus()
        }
        return true
    }
    cancelSkipAutoCountdown()
    return when (action) {
        InputAction.Back -> hideVisiblePlayerControls()
        InputAction.Up,
        InputAction.Down,
        InputAction.Confirm -> {
            if (!hasVisiblePlayerControls()) {
                showController()
                post {
                    val focused = findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
                    if (!focused) requestFocus()
                }
                true
            } else {
                false
            }
        }
        InputAction.Left,
        InputAction.Right -> {
            if (!hasVisiblePlayerControls()) {
                showController()
                post {
                    val focused = findViewById<View>(Media3R.id.exo_play_pause)?.requestFocus() == true
                    if (!focused) requestFocus()
                }
                true
            } else {
                seekTimelineIfFocused(
                    forward = action == InputAction.Right,
                    repeatedInput = event.isRepeated,
                )
            }
        }
        InputAction.Play -> {
            player?.play()
            true
        }
        InputAction.Pause -> {
            player?.pause()
            true
        }
        InputAction.PlayPause -> {
            player?.let { currentPlayer ->
                if (currentPlayer.isPlaying) {
                    currentPlayer.pause()
                } else {
                    currentPlayer.play()
                }
                true
            } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
        }
        InputAction.PreviousEpisode,
        InputAction.NextEpisode -> false
    }
}

internal fun PlayerView.isSkipOnlyControllerMode(): Boolean {
    return tagValue<Boolean>(R.id.yummy_player_skip_only_mode) == true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.setSkipOnlyControllerMode(enabled: Boolean) {
    setTag(R.id.yummy_player_skip_only_mode, enabled)
    setControllerShowTimeoutMs(if (enabled) 0 else PLAYER_CONTROLS_AUTO_HIDE_MS.toInt())
    findViewById<View>(Media3R.id.exo_controls_background)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_top_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(Media3R.id.exo_bottom_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
}

@OptIn(UnstableApi::class)
internal fun PlayerView.seekTimelineIfFocused(
    forward: Boolean,
    repeatedInput: Boolean,
): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false

    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val now = SystemClock.uptimeMillis()
    val direction = if (forward) 1 else -1
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        ?: TimelineScrubState(pendingPositionMs = currentPlayer.currentPosition.coerceIn(0L, duration))
    state.clearRunnable?.let(::removeCallbacks)
    state.clearRunnable = null

    val keepsHoldingSameDirection = repeatedInput && state.lastDirection == direction
    state.repeatedInputCount = if (keepsHoldingSameDirection) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.generation += 1
    state.pendingPositionMs = (state.pendingPositionMs + direction.toLong() * state.stepMs(duration)).coerceIn(0L, duration)
    setTag(R.id.yummy_player_timeline_manual_until, now + PLAYER_TIMELINE_MANUAL_FREEZE_MS)

    state.commitRunnable?.let(::removeCallbacks)
    val commitGeneration = state.generation
    val commitRunnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                ?: return
            if (latestState.generation != commitGeneration) return

            val elapsedSinceInputMs = SystemClock.uptimeMillis() - latestState.lastInputAtMs
            if (elapsedSinceInputMs < PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS) {
                postDelayed(this, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS - elapsedSinceInputMs)
                return
            }

            val targetPositionMs = latestState.pendingPositionMs.coerceIn(0L, duration)
            currentPlayer.seekTo(targetPositionMs)
            latestState.pendingPositionMs = targetPositionMs
            latestState.repeatedInputCount = 0
            latestState.commitRunnable = null
            renderTimelineScrubPosition(latestState)
            val freezeUntil = SystemClock.uptimeMillis() + PLAYER_TIMELINE_MANUAL_FREEZE_MS
            setTag(R.id.yummy_player_timeline_manual_until, freezeUntil)
            val clearRunnable = object : Runnable {
                override fun run() {
                    val currentState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                    if (currentState !== latestState) return
                    if (isTimelineManuallyControlled()) {
                        postDelayed(this, 50L)
                        return
                    }
                    clearTimelineScrubState()
                }
            }
            latestState.clearRunnable = clearRunnable
            postDelayed(clearRunnable, PLAYER_TIMELINE_MANUAL_FREEZE_MS)
        }
    }
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    renderTimelineScrubPosition(state)
    post {
        val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        if (latestState?.generation == commitGeneration) {
            renderTimelineScrubPosition(latestState)
        }
    }
    holdTimelineScrubPosition()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    return true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.renderTimelineScrubPosition(state: TimelineScrubState) {
    (findViewById<View>(Media3R.id.exo_progress) as? TimeBar)?.setPosition(state.pendingPositionMs)
    findViewById<TextView>(Media3R.id.exo_position)?.text = formatPlaybackTime(state.pendingPositionMs)
}

internal fun PlayerView.isTimelineManuallyControlled(): Boolean {
    val until = tagValue<Long>(R.id.yummy_player_timeline_manual_until) ?: return false
    return SystemClock.uptimeMillis() < until
}

@OptIn(UnstableApi::class)
internal fun PlayerView.holdTimelineScrubPosition() {
    if (tagValue<Runnable>(R.id.yummy_player_timeline_hold_runnable) != null) return
    val runnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (latestState == null || !isTimelineManuallyControlled()) {
                clearTagValue(R.id.yummy_player_timeline_hold_runnable)
                return
            }
            renderTimelineScrubPosition(latestState)
            postOnAnimation(this)
        }
    }
    setTag(R.id.yummy_player_timeline_hold_runnable, runnable)
    postOnAnimation(runnable)
}

internal fun PlayerView.clearTimelineScrubState() {
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.commitRunnable?.let(::removeCallbacks)
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.clearRunnable?.let(::removeCallbacks)
    removeTaggedRunnable(R.id.yummy_player_timeline_hold_runnable)
    clearTagValue(R.id.yummy_player_timeline_scrub_state)
    clearTagValue(R.id.yummy_player_timeline_manual_until)
}

internal data class TimelineScrubState(
    var pendingPositionMs: Long,
    var repeatedInputCount: Int = 0,
    var lastDirection: Int = 0,
    var generation: Int = 0,
    var lastInputAtMs: Long = 0L,
    var commitRunnable: Runnable? = null,
    var clearRunnable: Runnable? = null,
) {
    fun stepMs(durationMs: Long): Long {
        val requestedStep = when {
            repeatedInputCount <= 3 -> PLAYER_TIMELINE_BASE_STEP_MS
            repeatedInputCount <= 7 -> 10_000L
            repeatedInputCount <= 13 -> 30_000L
            else -> 60_000L
        }
        val maxStep = (durationMs / PLAYER_TIMELINE_MAX_STEP_DIVISOR).coerceAtLeast(1_000L)
        return requestedStep.coerceAtMost(maxStep)
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyController(
    player: ExoPlayer,
    animeTitle: String,
    currentVideo: VideoVariant,
    isLocalPlayback: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleKey: String,
    onSelectedSubtitleKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    settings: AppSettings,
    texts: PlayerControlTexts,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text =
        currentVideo.playbackSubtitle(texts)
    findViewById<TextView>(R.id.yummy_player_info)?.text =
        currentVideo.playbackSourceLabel(isLocalPlayback)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            previousVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            nextVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                player.pause()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
        text = texts.voice
        visibility = if (groups.size > 1) View.VISIBLE else View.GONE
        setOnClickListener {
            showController()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                texts = texts,
                onSelectGroup = { groupKey, replacement ->
                    player.pause()
                    onSelectGroup(groupKey, replacement, player.currentPosition.coerceAtLeast(0L))
                },
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        text = texts.quality
        visibility = if (qualityOptions.isNotEmpty()) View.VISIBLE else View.GONE
        setOnClickListener {
            showController()
            showQualityPopup(
                anchor = this,
                player = player,
                options = qualityOptions,
                selectedQualityKey = selectedQualityKey,
                onSelectedQualityKeyChange = onSelectedQualityKeyChange,
                onSelectLocalQuality = onSelectLocalQuality,
                onSelectPreferredQuality = onSelectPreferredQuality,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_subtitles)?.apply {
        text = texts.subtitles
        visibility = if (subtitleOptions.isNotEmpty()) View.VISIBLE else View.GONE
        setPlayerControlEnabled(subtitleOptions.isNotEmpty())
        applyPlayerToggleState(selectedSubtitleKey != SUBTITLE_OFF_KEY && subtitleOptions.isNotEmpty())
        setOnClickListener {
            showController()
            showSubtitlePopup(
                anchor = this,
                player = player,
                options = subtitleOptions,
                selectedSubtitleKey = selectedSubtitleKey,
                texts = texts,
                onSelectedSubtitleKeyChange = onSelectedSubtitleKeyChange,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_subscription)?.apply {
        text = if (subscriptionActive) texts.subscribed else texts.subscription
        visibility = if (allowSubscription) View.VISIBLE else View.GONE
        applyPlayerSubscriptionState(subscriptionActive)
        setOnClickListener {
            showController()
            onToggleSubscription()
        }
    }

    findViewById<TextView>(R.id.yummy_player_speed)?.apply {
        text = settings.playerSpeed.title
        visibility = View.VISIBLE
        setOnClickListener {
            showController()
            showSpeedPopup(
                anchor = this,
                selected = settings.playerSpeed,
                onSelected = { onSettingsChange(settings.copy(playerSpeed = it)) },
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_pip)?.apply {
        text = context.getString(R.string.player_pip)
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hideController()
            postDelayed({ onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }

    if (settings.skipOpeningsAndEndings) {
        bindSkipControls(player = player, currentVideo = currentVideo, texts = texts)
    } else {
        unbindSkipControls()
    }
    configurePlayerFocusNavigation(previousVideo != null, nextVideo != null)
}

internal fun PlayerView.configurePlayerFocusNavigation(
    hasPreviousVideo: Boolean,
    hasNextVideo: Boolean,
) {
    val back = findViewById<View>(R.id.yummy_player_back)
    val previous = findViewById<View>(R.id.yummy_episode_previous)
    val playPause = findViewById<View>(Media3R.id.exo_play_pause)
    val next = findViewById<View>(R.id.yummy_episode_next)
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    val bottomControls = listOfNotNull(
        findViewById<View>(R.id.yummy_player_voice)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_quality)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_subtitles)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_subscription)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_speed)?.takeIf { it.isVisible },
        findViewById<View>(R.id.yummy_player_pip)?.takeIf { it.isVisible },
    )
    val firstBottomControl = bottomControls.firstOrNull()
    val timeBarFocusId = timeBar?.id ?: firstBottomControl?.id ?: Media3R.id.exo_play_pause

    playPause?.apply {
        nextFocusLeftId = if (hasPreviousVideo) R.id.yummy_episode_previous else id
        nextFocusRightId = if (hasNextVideo) R.id.yummy_episode_next else id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    previous?.apply {
        nextFocusLeftId = id
        nextFocusRightId = Media3R.id.exo_play_pause
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    next?.apply {
        nextFocusLeftId = Media3R.id.exo_play_pause
        nextFocusRightId = id
        nextFocusUpId = R.id.yummy_player_back
        nextFocusDownId = timeBarFocusId
    }

    back?.nextFocusDownId = Media3R.id.exo_play_pause

    timeBar?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
        nextFocusLeftId = id
        nextFocusRightId = id
        nextFocusUpId = Media3R.id.exo_play_pause
        nextFocusDownId = firstBottomControl?.id ?: Media3R.id.exo_play_pause
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                seekTimelineIfFocused(
                    forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT,
                    repeatedInput = event.repeatCount > 0,
                )
            }
            true
        }
        applyPlayerTimelineFocusColors()
    }

    bottomControls.forEachIndexed { index, view ->
        view.nextFocusUpId = timeBar?.id ?: Media3R.id.exo_play_pause
        view.nextFocusDownId = view.id
        view.nextFocusLeftId = bottomControls.getOrNull(index - 1)?.id ?: view.id
        view.nextFocusRightId = bottomControls.getOrNull(index + 1)?.id ?: view.id
    }

    configureSkipFocusNavigation(findViewById<View>(R.id.yummy_skip_controls)?.isVisible == true)
}

@OptIn(UnstableApi::class)
internal fun View.applyPlayerTimelineFocusColors() {
    val timeBar = this as? DefaultTimeBar ?: return
    timeBar.defaultFocusHighlightEnabled = false
    fun update(focused: Boolean) {
        val accent = if (focused) PLAYER_ACCENT_COLOR else android.graphics.Color.WHITE
        timeBar.setScrubberColor(accent)
        timeBar.setPlayedColor(accent)
    }
    update(hasFocus())
    setOnFocusChangeListener { _, focused -> update(focused) }
}

internal fun PlayerView.configureSkipFocusNavigation(active: Boolean) {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    val skipButton = findViewById<View>(R.id.yummy_skip_skip)
    val watchButton = findViewById<View>(R.id.yummy_skip_watch)
    if (active && skipButton != null && watchButton != null) {
        timeBar?.nextFocusUpId = R.id.yummy_skip_skip
        skipButton.nextFocusLeftId = R.id.yummy_skip_skip
        skipButton.nextFocusRightId = R.id.yummy_skip_watch
        skipButton.nextFocusUpId = Media3R.id.exo_play_pause
        skipButton.nextFocusDownId = timeBar?.id ?: R.id.yummy_skip_skip
        watchButton.nextFocusLeftId = R.id.yummy_skip_skip
        watchButton.nextFocusRightId = R.id.yummy_skip_watch
        watchButton.nextFocusUpId = Media3R.id.exo_play_pause
        watchButton.nextFocusDownId = timeBar?.id ?: R.id.yummy_skip_watch
    } else if (timeBar?.nextFocusUpId == R.id.yummy_skip_skip) {
        timeBar.nextFocusUpId = Media3R.id.exo_play_pause
    }
}

internal fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    applyPlayerToggleState(active)
}

internal fun TextView.applyPlayerToggleState(active: Boolean) {
    backgroundTintList = null
    setBackgroundResource(if (active) R.drawable.player_control_chip_active else R.drawable.player_control_chip)
    setTextColor(if (active) PLAYER_ACCENT_CONTENT_COLOR else PLAYER_CONTROL_CONTENT_COLOR)
}

internal val PLAYER_ACCENT_COLOR: Int = 0xFFFFB454.toInt()
internal val PLAYER_ACCENT_CONTENT_COLOR: Int = 0xFF1B1305.toInt()
internal val PLAYER_CONTROL_COLOR: Int = 0xFF111827.toInt()
internal val PLAYER_CONTROL_CONTENT_COLOR: Int = 0xFFF3F6FA.toInt()

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipControls(
    player: ExoPlayer,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
) {
    if (tagValue<Long>(R.id.yummy_player_skip_video_id) != currentVideo.id) {
        unbindSkipControls()
        setTag(R.id.yummy_player_skip_video_id, currentVideo.id)
        setTag(R.id.yummy_player_skip_dismissed_keys, mutableSetOf<String>())
    }

    val container = findViewById<View>(R.id.yummy_skip_controls) ?: return
    val skipButton = findViewById<TextView>(R.id.yummy_skip_skip) ?: return
    val watchButton = findViewById<TextView>(R.id.yummy_skip_watch) ?: return
    setTag(R.id.yummy_player_skip_text_tag, texts.skip)
    watchButton.text = texts.watch
    if (currentVideo.skipSegments.isEmpty()) {
        container.visibility = View.GONE
        return
    }
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)

    fun dismissActivePrompt() {
        clearActiveSkipPrompt(markDismissed = true)
    }

    fun skipActivePrompt() {
        val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment) ?: return
        val targetEndMs = prompt.targetEndMs
        clearActiveSkipPrompt(markDismissed = true)
        hideController()
        if (player.currentPosition.coerceAtLeast(0L) < targetEndMs) {
            player.seekTo(targetEndMs)
        }
    }

    fun updateSkipButtonText(state: SkipCountdownState, nowMs: Long = SystemClock.elapsedRealtime()) {
        val remainingSeconds = (((state.deadlineMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L)
            .toInt()
            .coerceIn(0, SKIP_PROMPT_COUNTDOWN_SECONDS)
        skipButton.text = if (state.autoSkipEnabled) {
            context.getString(R.string.player_skip_countdown, texts.skip, remainingSeconds)
        } else {
            texts.skip
        }
    }

    fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val state = SkipCountdownState(
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + SKIP_PROMPT_COUNTDOWN_SECONDS * 1_000L,
            autoSkipEnabled = true,
        )
        setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)

        fun tick() {
            val activeKey = tagValue<String>(R.id.yummy_player_active_skip_key)
            if (activeKey != prompt.key || !state.autoSkipEnabled) return
            val playerPositionMs = player.currentPosition.coerceAtLeast(0L)
            if (!prompt.hasUsefulSkipAt(playerPositionMs)) {
                clearActiveSkipPrompt(markDismissed = true)
                return
            }
            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = state.deadlineMs - nowMs
            if (remainingMs <= 0L) {
                updateSkipButtonText(state, state.deadlineMs)
                val finishCountdown = Runnable {
                    val currentKey = tagValue<String>(R.id.yummy_player_active_skip_key)
                    if (currentKey == prompt.key && state.autoSkipEnabled) {
                        skipActivePrompt()
                    }
                }
                setTag(R.id.yummy_player_skip_countdown_runnable, finishCountdown)
                postDelayed(finishCountdown, SKIP_PROMPT_ZERO_DISPLAY_MS)
            } else {
                updateSkipButtonText(state, nowMs)
                val nextTick = Runnable { tick() }
                setTag(R.id.yummy_player_skip_countdown_runnable, nextTick)
                val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0L)
                val nextSecondMs = ((elapsedMs / 1_000L) + 1L) * 1_000L
                val delayMs = (nextSecondMs - elapsedMs).coerceIn(16L, remainingMs)
                postDelayed(nextTick, delayMs)
            }
        }

        val firstTick = Runnable { tick() }
        setTag(R.id.yummy_player_skip_countdown_runnable, firstTick)
        postDelayed(firstTick, 1_000L)
    }

    fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if (tagValue<String>(R.id.yummy_player_active_skip_key) == key) return
        val cluster = currentVideo.skipSegments.skipPromptCluster(segment)
        val prompt = ActiveSkipPrompt(
            key = key,
            segment = segment,
            dismissKeys = cluster.mapTo(mutableSetOf()) { clusterSegment -> clusterSegment.key },
            targetEndMs = cluster.maxOfOrNull { clusterSegment -> clusterSegment.endMs } ?: segment.endMs,
        )
        setTag(R.id.yummy_player_active_skip_key, key)
        setTag(R.id.yummy_player_active_skip_segment, prompt)
        container.visibility = View.VISIBLE
        showController()
        setSkipOnlyControllerMode(true)
        skipButton.setOnClickListener { skipActivePrompt() }
        watchButton.setOnClickListener { dismissActivePrompt() }
        configureSkipFocusNavigation(active = true)
        scheduleCountdown(prompt)
        post { skipButton.requestFocus() }
    }

    val pollRunnable = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            val activePrompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
            val countdownState = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)
            if (
                activePrompt != null &&
                countdownState?.autoSkipEnabled != true &&
                !activePrompt.hasUsefulSkipAt(position)
            ) {
                clearActiveSkipPrompt(markDismissed = true)
            }
            if (container.visibility != View.VISIBLE) {
                val segment = currentVideo.skipSegments.firstOrNull { segment ->
                    segment.key !in dismissedSkipKeys() &&
                        segment.hasUsefulSkipAt(position)
                }
                if (segment != null) {
                    showPrompt(segment)
                }
            }
            postDelayed(this, SKIP_PROMPT_POLL_MS)
        }
    }

    setTag(R.id.yummy_player_skip_poll_runnable, pollRunnable)
    post(pollRunnable)
}

internal fun PlayerView.cancelSkipAutoCountdown() {
    val state = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled) ?: return
    if (!state.autoSkipEnabled) return
    state.autoSkipEnabled = false
    val skipText = tagValue<String>(R.id.yummy_player_skip_text_tag) ?: defaultPlayerControlTexts.skip
    findViewById<TextView>(R.id.yummy_skip_skip)?.text = skipText
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
}

internal fun PlayerView.unbindSkipControls() {
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)
    clearActiveSkipPrompt(markDismissed = false)
}

internal fun Long.normalizedDurationMs(): Long {
    return takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
}

internal fun TextView.setPlayerControlEnabled(enabled: Boolean) {
    isEnabled = enabled
    isFocusable = enabled
    alpha = if (enabled) 1f else 0.45f
}

internal fun showVoicePopup(
    anchor: View,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    preferredGroupKey: String?,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    val entries = groups.entries.sortedBy { it.value.firstOrNull()?.matchingVoiceTitle.orEmpty() }
    val totalEpisodeCount = groups.values
        .flatten()
        .map { it.matchingEpisodeKey }
        .distinct()
        .size
        .coerceAtLeast(1)
    PopupMenu(anchor.context, anchor).apply {
        entries.forEachIndexed { index, entry ->
            val voiceTitle = entry.value.firstOrNull()?.matchingVoiceTitle.orEmpty().ifBlank { "${texts.voice} ${index + 1}" }
            val availableEpisodes = entry.value.map { it.matchingEpisodeKey }.distinct().size
            val downloadedEpisodes = entry.value
                .asSequence()
                .filter { it.isOfflineAvailable }
                .map { it.matchingEpisodeKey }
                .distinct()
                .count()
            val downloadedSuffix = if (downloadedEpisodes > 0) " • ${texts.downloaded}: $downloadedEpisodes" else ""
            val title = "$voiceTitle  $availableEpisodes / $totalEpisodeCount$downloadedSuffix"
            menu.add(VOICE_MENU_GROUP_ID, index, index, title).apply {
                isCheckable = true
                isChecked = entry.key == selectedKey
            }
        }
        menu.setGroupCheckable(VOICE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val entry = entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            val sortedVideos = entry.value.sortedForPlayer(preferredGroupKey)
            val replacement = sortedVideos.firstOrNull { it.isSameEpisodeAs(currentVideo) }
                ?: sortedVideos.firstOrNull()
            val groupKey = replacement?.groupKey ?: entry.value.firstOrNull()?.groupKey ?: entry.key
            anchor.post { onSelectGroup(groupKey, replacement) }
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
internal fun showQualityPopup(
    anchor: View,
    player: ExoPlayer,
    options: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedQualityKey = anchor.tagValue<String>(R.id.yummy_player_quality)
            ?: selectedQualityKey
            ?: player.currentQualityKey()
        options.forEachIndexed { index, option ->
            menu.add(QUALITY_MENU_GROUP_ID, index, index, option.label).apply {
                isCheckable = true
                isChecked = option.matchesSelectedQualityKey(effectiveSelectedQualityKey)
            }
        }
        menu.setGroupCheckable(QUALITY_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val option = options.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            option.localFile?.let { localFile ->
                anchor.post { onSelectLocalQuality(localFile) }
            } ?: option.preferredQuality?.let { preferredQuality ->
                anchor.post { onSelectPreferredQuality(preferredQuality) }
            } ?: player.selectQuality(option)
            val stableKey = option.qualityOptionIdentity()
            anchor.setTag(R.id.yummy_player_quality, stableKey)
            onSelectedQualityKeyChange(stableKey)
            true
        }
        show()
    }
}

@OptIn(UnstableApi::class)
internal fun showSubtitlePopup(
    anchor: View,
    player: ExoPlayer,
    options: List<SubtitleOption>,
    selectedSubtitleKey: String,
    texts: PlayerControlTexts,
    onSelectedSubtitleKeyChange: (String) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        val effectiveSelectedSubtitleKey = anchor.tagValue<String>(R.id.yummy_player_subtitles)
            ?: selectedSubtitleKey
        menu.add(SUBTITLE_MENU_GROUP_ID, 0, 0, texts.subtitlesOff).apply {
            isCheckable = true
            isChecked = effectiveSelectedSubtitleKey == SUBTITLE_OFF_KEY
        }
        options.forEachIndexed { index, option ->
            menu.add(SUBTITLE_MENU_GROUP_ID, index + 1, index + 1, option.label).apply {
                isCheckable = true
                isChecked = option.matchesSelectedSubtitleKey(effectiveSelectedSubtitleKey)
            }
        }
        menu.setGroupCheckable(SUBTITLE_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            if (item.itemId == 0) {
                player.disableSubtitles()
                anchor.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                onSelectedSubtitleKeyChange(SUBTITLE_OFF_KEY)
                return@setOnMenuItemClickListener true
            }
            val option = options.getOrNull(item.itemId - 1) ?: return@setOnMenuItemClickListener false
            player.selectSubtitle(option)
            val stableKey = option.subtitleOptionIdentity()
            anchor.setTag(R.id.yummy_player_subtitles, stableKey)
            onSelectedSubtitleKeyChange(stableKey)
            true
        }
        show()
    }
}

internal fun showSpeedPopup(
    anchor: View,
    selected: PlayerSpeed,
    onSelected: (PlayerSpeed) -> Unit,
) {
    PopupMenu(anchor.context, anchor).apply {
        PlayerSpeed.entries.forEachIndexed { index, speed ->
            menu.add(SPEED_MENU_GROUP_ID, index, index, speed.title).apply {
                isCheckable = true
                isChecked = speed == selected
            }
        }
        menu.setGroupCheckable(SPEED_MENU_GROUP_ID, true, true)
        setOnMenuItemClickListener { item ->
            val speed = PlayerSpeed.entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            onSelected(speed)
            true
        }
        show()
    }
}
