package me.yummydroid.app.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.abs
import me.yummydroid.app.R
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SourceQuality
import okhttp3.OkHttpClient

// PlayerVideoZoom
@OptIn(UnstableApi::class)
internal fun PlayerView.installVideoZoomGestures(token: String) {
    val state = videoZoomGestureState(token)
    val scaleDetector = createVideoScaleDetector(state)
    val gestureHandler = { event: MotionEvent -> handleVideoGesture(event, state, scaleDetector) }
    val touchListener = videoGestureTouchListener(state, gestureHandler)
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

private fun PlayerView.videoZoomGestureState(token: String): VideoZoomGestureState {
    val currentToken = tagValue<String>(R.id.yummy_video_zoom_token_tag)
    val currentState = tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)
    if (currentToken == token && currentState != null) return currentState
    resetVideoZoom()
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
internal fun createVideoPlayer(
    context: Context,
    stream: ResolvedVideoStream,
    startPositionMs: Long,
    httpClient: OkHttpClient,
    renderersFactory: DefaultRenderersFactory,
    loadControl: DefaultLoadControl,
): ExoPlayer {
    val userAgent = stream.headers.entries
        .firstOrNull { (name, _) -> name.equals("User-Agent", ignoreCase = true) }
        ?.value
        ?.takeIf(String::isNotBlank)
        ?: APP_USER_AGENT
    val defaultRequestHeaders = stream.headers.filterKeys { name ->
        !name.isMedia3ManagedRequestHeader()
    }
    val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(initialVideoBitrateEstimate(stream.availableQualities))
        .build()
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
    }
    val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(defaultRequestHeaders)
    val dataSourceFactory = if (stream.url.startsWith("file:", ignoreCase = true)) {
        DefaultDataSource.Factory(context)
    } else {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }.setTransferListener(bandwidthMeter)

    return ExoPlayer.Builder(context, renderersFactory)
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
            setMediaItem(stream.toMediaItem(), startPositionMs.coerceAtLeast(0L))
            playWhenReady = false
            prepare()
        }
}

private fun String.isMedia3ManagedRequestHeader(): Boolean {
    return equals("User-Agent", ignoreCase = true) || equals("Accept-Encoding", ignoreCase = true)
}

internal fun initialVideoBitrateEstimate(qualities: List<SourceQuality>): Long {
    val highestDeclaredBitrate = qualities.maxOfOrNull { it.bitrate.coerceAtLeast(0).toLong() } ?: 0L
    return (highestDeclaredBitrate * INITIAL_VIDEO_BITRATE_HEADROOM)
        .coerceIn(DEFAULT_INITIAL_VIDEO_BITRATE, MAX_INITIAL_VIDEO_BITRATE)
}

internal fun ResolvedVideoStream.toMediaItem(): MediaItem {
    val mediaItemBuilder = MediaItem.Builder().setUri(url)
    mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return mediaItemBuilder.build()
}
