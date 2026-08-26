package me.yummydroid.app.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.abs
import kotlin.math.roundToInt
import me.yummydroid.app.R
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import okhttp3.OkHttpClient

// PlayerVideoZoom
@OptIn(UnstableApi::class)
internal fun PlayerView.installVideoZoomGestures(token: String) {
    val state = videoZoomGestureState(token)
    if (isVideoZoomGestureHandlerInstalled(token)) {
        post { applyVideoZoom(state) }
        return
    }
    val scaleDetector = createVideoScaleDetector(state)
    val gestureHandler = { event: MotionEvent -> handleVideoGesture(event, state, scaleDetector) }
    val touchListener = videoGestureTouchListener(state, gestureHandler)
    setTag(R.id.yummy_video_zoom_handler_token_tag, token)
    (this as? YummyPlayerView)?.videoGestureHandler = gestureHandler

    if (this is YummyPlayerView) {
        post { applyVideoZoom(state) }
        return
    }

    installVideoGestureTargets(touchListener)
    post {
        installVideoGestureTargets(touchListener)
        applyVideoZoom(state)
    }
    postDelayed({ installVideoGestureTargets(touchListener) }, 250L)
    postDelayed({ installVideoGestureTargets(touchListener) }, 1_000L)
}

private fun PlayerView.isVideoZoomGestureHandlerInstalled(token: String): Boolean {
    if (tagValue<String>(R.id.yummy_video_zoom_handler_token_tag) != token) return false
    return this !is YummyPlayerView || videoGestureHandler != null
}

private fun PlayerView.videoZoomGestureState(token: String): VideoZoomGestureState {
    val currentToken = tagValue<String>(R.id.yummy_video_zoom_token_tag)
    val currentState = tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)
    if (currentToken == token && currentState != null) return currentState
    resetVideoZoom()
    clearTagValue(R.id.yummy_video_zoom_handler_token_tag)
    return VideoZoomGestureState().also { state ->
        setTag(R.id.yummy_video_zoom_token_tag, token)
        setTag(R.id.yummy_video_zoom_state_tag, state)
    }
}

private fun PlayerView.createVideoScaleDetector(state: VideoZoomGestureState): ScaleGestureDetector {
    return ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                updateVideoScale(state, detector)
                applyVideoZoom(state)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (state.scale > 1.01f) return
                state.resetVideoTransform()
                applyVideoZoom(state)
            }
        },
    )
}

private fun PlayerView.updateVideoScale(
    state: VideoZoomGestureState,
    detector: ScaleGestureDetector,
) {
    val previousScale = state.scale
    state.scale = (state.scale * detector.scaleFactor).coerceIn(1f, 4f)
    if (state.scale <= 1.01f) {
        state.resetVideoTransform()
        return
    }
    if (previousScale <= 0f) return
    val scaleRatio = state.scale / previousScale
    state.offsetX = (state.offsetX * scaleRatio) + ((detector.focusX - width / 2f) * (1f - scaleRatio))
    state.offsetY = (state.offsetY * scaleRatio) + ((detector.focusY - height / 2f) * (1f - scaleRatio))
}

private fun VideoZoomGestureState.resetVideoTransform() {
    scale = 1f
    offsetX = 0f
    offsetY = 0f
}

private fun PlayerView.handleVideoGesture(
    event: MotionEvent,
    state: VideoZoomGestureState,
    scaleDetector: ScaleGestureDetector,
): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN && isInsideInteractivePlayerControl(event)) {
        state.handlingTouch = false
        return false
    }
    if (event.actionMasked != MotionEvent.ACTION_DOWN && !state.handlingTouch) return false
    scaleDetector.onTouchEvent(event)
    return when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> startVideoGesture(event, state)
        MotionEvent.ACTION_POINTER_DOWN -> hidePlayerControls().let { true }
        MotionEvent.ACTION_MOVE -> moveVideoGesture(event, state, scaleDetector)
        MotionEvent.ACTION_UP -> finishVideoGesture(state)
        MotionEvent.ACTION_CANCEL -> cancelVideoGesture(state)
        else -> event.pointerCount > 1 || state.scale > 1f
    }
}

private fun PlayerView.startVideoGesture(event: MotionEvent, state: VideoZoomGestureState): Boolean {
    state.handlingTouch = true
    setTag(R.id.yummy_player_last_touch_down_at, SystemClock.uptimeMillis())
    clearPlayerControlFocusAfterTouch()
    state.lastX = event.x
    state.lastY = event.y
    state.moved = false
    return true
}

private fun PlayerView.moveVideoGesture(
    event: MotionEvent,
    state: VideoZoomGestureState,
    scaleDetector: ScaleGestureDetector,
): Boolean {
    if (event.isScalingVideo(scaleDetector)) return true
    val dx = event.x - state.lastX
    val dy = event.y - state.lastY
    if (state.scale <= 1f) {
        state.markVideoGestureMoved(dx, dy)
        return true
    }
    state.lastX = event.x
    state.lastY = event.y
    if (hasVideoGestureMovement(dx, dy, 0.5f)) {
        state.offsetX += dx
        state.offsetY += dy
        state.markVideoGestureMoved(dx, dy)
        applyVideoZoom(state)
    }
    return true
}

private fun MotionEvent.isScalingVideo(scaleDetector: ScaleGestureDetector): Boolean =
    pointerCount > 1 || scaleDetector.isInProgress

private fun VideoZoomGestureState.markVideoGestureMoved(dx: Float, dy: Float) {
    if (!moved && hasVideoGestureMovement(dx, dy, 6f)) moved = true
}

private fun hasVideoGestureMovement(dx: Float, dy: Float, threshold: Float): Boolean =
    abs(dx) > threshold || abs(dy) > threshold

private fun PlayerView.finishVideoGesture(state: VideoZoomGestureState): Boolean {
    state.handlingTouch = false
    if (state.moved) return state.scale > 1f
    if (state.scale > 1f) {
        showPlayerControls()
        clearPlayerControlFocusAfterTouch()
        return true
    }
    if (hasVisiblePlayerControls()) {
        hidePlayerControls()
    } else {
        showPlayerControls()
        clearPlayerControlFocusAfterTouch()
    }
    return true
}

private fun cancelVideoGesture(state: VideoZoomGestureState): Boolean {
    state.handlingTouch = false
    return true
}

private fun PlayerView.videoGestureTouchListener(
    state: VideoZoomGestureState,
    gestureHandler: (MotionEvent) -> Boolean,
): View.OnTouchListener = View.OnTouchListener { view, event ->
    val clickDetected = event.actionMasked == MotionEvent.ACTION_UP && !state.moved
    val handled = gestureHandler(event)
    if (handled && clickDetected) view.performClick()
    handled
}

@OptIn(UnstableApi::class)
private fun PlayerView.installVideoGestureTargets(touchListener: View.OnTouchListener) {
    setOnTouchListener(touchListener)
    findViewById<View>(Media3R.id.exo_content_frame)?.setTouchListenerRecursively(touchListener)
    findViewById<View>(Media3R.id.exo_overlay)?.setTouchListenerRecursively(touchListener)
    findViewById<View>(Media3R.id.exo_subtitles)?.setTouchListenerRecursively(touchListener)
    videoSurfaceView?.setOnTouchListener(touchListener)
}

private fun PlayerView.isInsideInteractivePlayerControl(event: MotionEvent): Boolean {
    if (!hasVisiblePlayerControls()) return false
    return playerControlIds.any { id ->
        findViewById<View>(id)?.let { control ->
            control.isVisible && control.isEnabled && control.containsRawPoint(event.rawX, event.rawY)
        } == true
    }
}

private fun View.containsRawPoint(rawX: Float, rawY: Float): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return rawX >= location[0] &&
        rawX <= location[0] + width &&
        rawY >= location[1] &&
        rawY <= location[1] + height
}

private fun View.setTouchListenerRecursively(listener: View.OnTouchListener) {
    setOnTouchListener(listener)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).setTouchListenerRecursively(listener)
        }
    }
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
        state.handlingTouch = false
    }
    videoSurfaceView?.apply {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }
}

// VideoPlayerFactory
private const val DEFAULT_INITIAL_VIDEO_BITRATE = 12_000_000L
private const val MAX_INITIAL_VIDEO_BITRATE = 50_000_000L
private const val INITIAL_VIDEO_BITRATE_HEADROOM = 2L

@OptIn(UnstableApi::class)
internal class ReusableVideoPlayer internal constructor(
    val player: ExoPlayer,
    private val httpDataSourceFactory: StreamHttpDataSourceFactory,
) {
    fun load(
        targetPlayer: Player,
        stream: ResolvedVideoStream,
        mediaMetadata: MediaMetadata,
        mediaId: String,
        startPositionMs: Long,
        playWhenReady: Boolean,
    ) {
        httpDataSourceFactory.update(stream)
        targetPlayer.prepareMediaItemForPlayback(
            stream.toMediaItem(mediaMetadata, mediaId),
            startPositionMs.coerceAtLeast(0L),
            playWhenReady,
        )
    }
}

internal fun Player.prepareMediaItemForPlayback(
    mediaItem: MediaItem,
    startPositionMs: Long,
    playWhenReady: Boolean,
) {
    this.playWhenReady = playWhenReady
    setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
    prepare()
}

private data class StreamRequestProperties(
    val userAgent: String,
    val headers: Map<String, String>,
)

@OptIn(UnstableApi::class)
internal class StreamHttpDataSourceFactory(
    private val httpClient: OkHttpClient,
    stream: ResolvedVideoStream,
) : DataSource.Factory {
    @Volatile
    private var requestProperties = stream.requestProperties()

    fun update(stream: ResolvedVideoStream) {
        requestProperties = stream.requestProperties()
    }

    override fun createDataSource(): DataSource {
        val properties = requestProperties
        return OkHttpDataSource.Factory(httpClient)
            .setUserAgent(properties.userAgent)
            .setDefaultRequestProperties(properties.headers)
            .createDataSource()
    }
}

@OptIn(UnstableApi::class)
internal fun createVideoPlayer(
    context: Context,
    stream: ResolvedVideoStream,
    httpClient: OkHttpClient,
    renderersFactory: DefaultRenderersFactory,
    loadControl: DefaultLoadControl,
): ReusableVideoPlayer {
    val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(initialVideoBitrateEstimate(stream.availableQualities))
        .build()
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
    }
    val httpDataSourceFactory = StreamHttpDataSourceFactory(httpClient, stream)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        .setTransferListener(bandwidthMeter)

    val player = ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setBandwidthMeter(bandwidthMeter)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            setForegroundMode(true)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            playWhenReady = false
        }
    return ReusableVideoPlayer(player, httpDataSourceFactory)
}

private fun ResolvedVideoStream.requestProperties(): StreamRequestProperties {
    val userAgent = headers.entries
        .firstOrNull { (name, _) -> name.equals("User-Agent", ignoreCase = true) }
        ?.value
        ?.takeIf(String::isNotBlank)
        ?: APP_USER_AGENT
    val requestHeaders = headers.filterKeys { name -> !name.isMedia3ManagedRequestHeader() }
    return StreamRequestProperties(userAgent, requestHeaders)
}

private fun String.isMedia3ManagedRequestHeader(): Boolean {
    return equals("User-Agent", ignoreCase = true) || equals("Accept-Encoding", ignoreCase = true)
}

internal fun initialVideoBitrateEstimate(qualities: List<SourceQuality>): Long {
    val highestDeclaredBitrate = qualities.maxOfOrNull { it.bitrate.coerceAtLeast(0).toLong() } ?: 0L
    return (highestDeclaredBitrate * INITIAL_VIDEO_BITRATE_HEADROOM)
        .coerceIn(DEFAULT_INITIAL_VIDEO_BITRATE, MAX_INITIAL_VIDEO_BITRATE)
}

internal fun ResolvedVideoStream.toMediaItem(
    mediaMetadata: MediaMetadata = MediaMetadata.EMPTY,
    mediaId: String = "",
): MediaItem {
    val mediaItemBuilder = MediaItem.Builder().setUri(url)
        .setMediaMetadata(mediaMetadata)
    if (mediaId.isNotBlank()) mediaItemBuilder.setMediaId(mediaId)
    mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return mediaItemBuilder.build()
}

// YummyPlayerView
@OptIn(UnstableApi::class)
internal class YummyPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PlayerView(context, attrs, defStyleAttr) {
    var videoGestureHandler: ((MotionEvent) -> Boolean)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (videoGestureHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateControllerViewport(width, height)
    }

    fun updateControllerViewport() {
        updateControllerViewport(width, height)
    }

    private fun updateControllerViewport(width: Int, height: Int) {
        val viewport = findViewById<FrameLayout>(R.id.yummy_player_controls_viewport) ?: return
        val layoutParams = viewport.layoutParams as? FrameLayout.LayoutParams ?: return
        val targetHeight = if (width > 0 && height > width) {
            val videoSize = player?.videoSize
            val videoWidth = videoSize?.let { it.width * it.pixelWidthHeightRatio } ?: 0f
            val videoHeight = videoSize?.height ?: 0
            val videoAspectRatio = if (videoWidth > 0f && videoHeight > 0) {
                videoWidth / videoHeight
            } else {
                DEFAULT_LANDSCAPE_VIDEO_ASPECT_RATIO
            }
            val fittedVideoHeight = (width / videoAspectRatio).roundToInt()
            val minimumHeight = resources.getDimensionPixelSize(
                R.dimen.yummy_player_compact_viewport_min_height,
            )
            maxOf(fittedVideoHeight, minimumHeight).coerceAtMost(height)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (layoutParams.height == targetHeight && layoutParams.gravity == Gravity.CENTER) return
        layoutParams.height = targetHeight
        layoutParams.gravity = Gravity.CENTER
        viewport.layoutParams = layoutParams
    }

    private companion object {
        const val DEFAULT_LANDSCAPE_VIDEO_ASPECT_RATIO = 16f / 9f
    }
}

// YummyRenderersFactory
@OptIn(UnstableApi::class)
internal class YummyRenderersFactory(
    context: Context,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
            .build()
    }
}

// PlayerShellControllerBinding
internal inline fun <reified T> View.tagValue(tagId: Int): T? {
    return getTag(tagId) as? T
}

internal fun View.clearTagValue(tagId: Int) {
    setTag(tagId, null)
}

internal fun View.removeTaggedRunnable(tagId: Int) {
    tagValue<Runnable>(tagId)?.let(::removeCallbacks)
    clearTagValue(tagId)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyShellController(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    showCenterControls: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
) {
    applyPlayerControlIconColors()
    bindShellHeader(
        animeTitle = animeTitle,
        currentVideo = currentVideo,
        videos = groups.values.flatten(),
        texts = texts,
    )
    bindShellTransport(showCenterControls, previousVideo, nextVideo, onPlayVideo, onBack)
    bindVoiceSelector(
        currentVideo = currentVideo,
        groups = groups,
        selectedKey = selectedKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectGroup = onSelectGroup,
    )
    bindSourceSelector(
        sourceOptions = sourceOptions,
        selectedSourceKey = selectedSourceKey,
        texts = texts,
        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
        onSelectSource = onSelectSource,
    )
    bindStaticShellControls(
        settings = settings,
        allowSubscription = allowSubscription,
        subscriptionActive = subscriptionActive,
        canUsePictureInPicture = canUsePictureInPicture,
        texts = texts,
        onToggleSubscription = onToggleSubscription,
    )
    configurePlayerFocusNavigation()
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellHeader(
    animeTitle: String,
    currentVideo: VideoVariant,
    videos: List<VideoVariant>,
    texts: PlayerControlTexts,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle(texts, videos)
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = context.getString(R.string.player_zero_time)
    findViewById<TextView>(Media3R.id.exo_duration)?.text = context.getString(R.string.player_zero_time)
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindShellTransport(
    showCenterControls: Boolean,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    setSkipControlsActive(false)
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (showCenterControls) {
        View.VISIBLE
    } else {
        View.GONE
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (showCenterControls && previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (showCenterControls && nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindVoiceSelector(
    currentVideo: VideoVariant,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
) {
    bindPopupSelector(
        controlId = R.id.yummy_player_voice,
        iconResId = R.drawable.ic_player_voice,
        label = texts.voice,
        optionCount = groups.size,
    ) { anchor ->
        showVoicePopup(
            anchor = anchor,
            groups = groups,
            selectedKey = selectedKey,
            preferredGroupKey = currentVideo.groupKey,
            currentVideo = currentVideo,
            texts = texts,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onSelectGroup = onSelectGroup,
        )
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindSourceSelector(
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    texts: PlayerControlTexts,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
) {
    bindPopupSelector(
        controlId = R.id.yummy_player_source,
        iconResId = R.drawable.ic_player_source,
        label = texts.source,
        optionCount = sourceOptions.size,
    ) { anchor ->
        showSourcePopup(
            anchor = anchor,
            options = sourceOptions,
            selectedSourceKey = selectedSourceKey,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onSelectSource = onSelectSource,
        )
    }
}

internal fun playerSelectorEnabled(optionCount: Int): Boolean = optionCount > 1

private fun PlayerView.bindPopupSelector(
    controlId: Int,
    iconResId: Int,
    label: String,
    optionCount: Int,
    openPopup: (ImageButton) -> Unit,
) {
    val enabled = playerSelectorEnabled(optionCount)
    findViewById<ImageButton>(controlId)?.apply {
        applyPlayerIconControl(iconResId, label)
        visibility = View.VISIBLE
        setPlayerControlEnabled(enabled)
        setOnClickListener {
            if (!enabled) return@setOnClickListener
            this@bindPopupSelector.showPlayerControls()
            openPopup(this)
        }
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindStaticShellControls(
    settings: AppSettings,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        applyPlayerQualityControl(PLAYER_AUTO_QUALITY_LABEL, texts.quality)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_subtitles, texts.subtitles)
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_subscription)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subscription,
            label = if (subscriptionActive) texts.subscribed else texts.subscription,
            active = subscriptionActive,
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(allowSubscription)
        setOnClickListener {
            if (!allowSubscription) return@setOnClickListener
            showPlayerControls()
            onToggleSubscription()
        }
    }
    findViewById<ImageButton>(R.id.yummy_player_speed)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_speed,
            label = "${context.getString(R.string.player_speed)}: ${settings.playerSpeed.title}",
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setPlayerControlEnabled(false)
    }

    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isEnabled = false
        isFocusable = false
    }
}
