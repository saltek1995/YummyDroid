package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.abs
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.localizedString

// PlayerDisplayMode
internal data class VideoDisplayInfo(
    val width: Int,
    val height: Int,
    val frameRate: Float,
) {
    fun hasValidDimensions(): Boolean = width > 0 && height > 0
}

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun Context.supportsDisplayModeMatching(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (isTelevision) return true

    val displayManager = getSystemService(DisplayManager::class.java)
    return displayManager
        ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        ?.isNotEmpty() == true
}

internal fun Activity.applyVideoDisplayMode(enabled: Boolean, video: VideoDisplayInfo?) {
    if (!supportsDisplayModeMatching()) return
    if (!enabled) {
        clearPreferredDisplayMode()
        return
    }
    val validVideo = video?.takeIf(VideoDisplayInfo::hasValidDimensions) ?: run {
        clearPreferredDisplayMode()
        return
    }

    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay ?: return
    val targetMode = display.supportedModes
        .filter { mode -> mode.physicalWidth > 0 && mode.physicalHeight > 0 }
        .minByOrNull { mode -> mode.displayModeScore(validVideo) }

    val targetModeId = targetMode?.modeId ?: 0
    if (window.attributes.preferredDisplayModeId == targetModeId) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = targetModeId
    }
}

internal fun Activity.clearPreferredDisplayMode() {
    if (window.attributes.preferredDisplayModeId == 0) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = 0
    }
}

internal fun android.view.Display.Mode.displayModeScore(video: VideoDisplayInfo): Float {
    val modeLongSide = maxOf(physicalWidth, physicalHeight)
    val modeShortSide = minOf(physicalWidth, physicalHeight)
    val videoLongSide = maxOf(video.width, video.height)
    val videoShortSide = minOf(video.width, video.height)
    val resolutionPenalty = when {
        modeLongSide >= videoLongSide && modeShortSide >= videoShortSide ->
            (modeLongSide - videoLongSide) + (modeShortSide - videoShortSide)
        else ->
            100_000 + abs(modeLongSide - videoLongSide) + abs(modeShortSide - videoShortSide)
    }
    return resolutionPenalty + refreshRatePenalty(refreshRate, video.frameRate)
}

internal fun refreshRatePenalty(refreshRate: Float, frameRate: Float): Float {
    if (refreshRate <= 0f || frameRate <= 0f) return 0f
    val candidates = listOf(frameRate, frameRate * 2f, frameRate * 3f, frameRate / 2f)
    return candidates.minOf { abs(refreshRate - it) } * 100f
}

@OptIn(UnstableApi::class)
internal fun Player.currentVideoDisplayInfo(): VideoDisplayInfo? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }

    return currentTracks.groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
        .firstOrNull { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }
}

internal fun VideoSize.toVideoDisplayInfo(): VideoDisplayInfo? {
    if (width <= 0 || height <= 0) return null
    return VideoDisplayInfo(width = width, height = height, frameRate = 0f)
}

// PlayerEpisodeNavigation
internal fun VideoVariant.playbackSubtitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant> = emptyList(),
): String {
    val voice = dubbing.cleanVideoSourceLabel()
    return listOf(voice, localizedPlaybackEpisodeTitle(texts, videos))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" \u2022 ")
}

private fun VideoVariant.localizedPlaybackEpisodeTitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant>,
): String {
    val episodeNumber = episode.trim()
    if (episodeNumber.isBlank()) return texts.episodeFallback
    val episodeCount = playbackEpisodeCount(videos)
    return if (episodeCount > 0) {
        "${texts.episode} $episodeNumber ${texts.of} $episodeCount"
    } else {
        "${texts.episode} $episodeNumber"
    }
}

private fun VideoVariant.playbackEpisodeCount(videos: Collection<VideoVariant>): Int {
    val candidates = videos.ifEmpty { listOf(this) }
    val sameAnime = candidates.filter { it.animeId == animeId }
    val sameVoice = sameAnime.filter { it.matchingVoiceKey == matchingVoiceKey }
    return sameVoice
        .ifEmpty { sameAnime }
        .ifEmpty { candidates.toList() }
        .availableVoiceEpisodeCount()
}

internal fun findAdjacentPlayerVideo(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    forward: Boolean,
): VideoVariant? {
    val videos = allVideos.ifEmpty { listOf(currentVideo) }
    val preferredVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?: currentVideo.matchingVoiceKey
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: currentVideo.groupKey
    val voiceScopedVideos = videos
        .filter { it.matchingVoiceKey == preferredVoiceKey }
        .ifEmpty { videos }

    val episodeVideos = voiceScopedVideos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.matchingVoiceKey == preferredVoiceKey) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()

    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(currentVideo) }
        .takeIf { it >= 0 }
        ?: return null
    val nextIndex = if (forward) currentIndex + 1 else currentIndex - 1
    return episodeVideos.getOrNull(nextIndex)
}

internal fun showVoiceFallbackToast(
    context: Context,
    previousVideo: VideoVariant,
    nextVideo: VideoVariant,
) {
    if (previousVideo.matchingVoiceKey == nextVideo.matchingVoiceKey) return
    val language = AppSettingsStorage(context).read().contentLanguage
    Toast.makeText(
        context,
        context.localizedString(
            R.string.ui_voice_fallback_toast,
            language,
            previousVideo.matchingVoiceTitle,
            nextVideo.episodeTitle,
            nextVideo.matchingVoiceTitle,
        ),
        Toast.LENGTH_LONG,
    ).show()
}

// PlayerFocusNavigation
internal fun View?.playerFocusableTarget(): View? {
    return this?.takeIf { it.isVisible && it.isShown && it.isEnabled && it.isFocusable && it.width > 0 && it.height > 0 }
}

internal fun PlayerView.restorePlayerControlFocus(controlId: Int?): Boolean {
    if (controlId == null || isInTouchMode) return false
    removeTaggedRunnable(R.id.yummy_player_focus_restore_runnable)
    val target = findViewById<View>(controlId)
    if (target?.hasFocus() == true) return true
    showPlayerControls()
    return target
        .playerFocusableTarget()
        ?.requestFocus() == true
}

internal fun PlayerView.restorePlayerControlFocusWhenReady(
    controlId: Int?,
    onRestored: () -> Unit,
) {
    if (controlId == null || isInTouchMode) return
    if (restorePlayerControlFocus(controlId)) {
        onRestored()
        return
    }
    val restoreRunnable = Runnable {
        clearTagValue(R.id.yummy_player_focus_restore_runnable)
        if (restorePlayerControlFocus(controlId)) {
            onRestored()
        }
    }
    setTag(R.id.yummy_player_focus_restore_runnable, restoreRunnable)
    post(restoreRunnable)
}

internal fun PlayerView.configurePlayerFocusNavigation() {
    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
        applyPlayerTimelineFocusColors()
    }
    installDynamicPlayerFocusNavigation()
}

internal enum class PlayerFocusDirection {
    Left,
    Right,
    Up,
    Down,
}

internal data class PlayerFocusBounds(
    val id: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun PlayerView.installDynamicPlayerFocusNavigation() {
    val controls = playerFocusTargets()
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    controls.forEach { control ->
        control.setOnKeyListener { view: View, keyCode: Int, event: KeyEvent ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            if (view.id == Media3R.id.exo_progress) {
                val isHorizontalSeekKey = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                val isConfirmKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER
                if (isHorizontalSeekKey) {
                    seekTimelineIfFocused(
                        forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT,
                        repeatedInput = event.repeatCount > 0,
                    )
                    return@setOnKeyListener true
                }
                if (isConfirmKey) {
                    confirmTimelineScrubOrTogglePlayback()
                    return@setOnKeyListener true
                }
            }
            val direction = keyCode.playerFocusDirection() ?: return@setOnKeyListener false
            requestDynamicPlayerFocus(from = view, direction = direction)
        }
    }
    if (timeBar?.playerFocusableTarget() == null) {
        timeBar?.setOnKeyListener(null)
    }
}

private fun PlayerView.playerFocusTargets(): List<View> {
    return playerControlIds
        .asSequence()
        .mapNotNull { id: Int -> findViewById<View>(id).playerFocusableTarget() }
        .distinctBy { view: View -> view.id }
        .toList()
}

private fun Int.playerFocusDirection(): PlayerFocusDirection? {
    return when (this) {
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerFocusDirection.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerFocusDirection.Right
        KeyEvent.KEYCODE_DPAD_UP -> PlayerFocusDirection.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerFocusDirection.Down
        else -> null
    }
}

private fun PlayerView.requestDynamicPlayerFocus(
    from: View,
    direction: PlayerFocusDirection,
): Boolean {
    val controls = playerFocusTargets()
    val bounds = controls.mapNotNull { view -> view.playerVisibleFocusBounds() }
    val targetId = playerFocusDirectionalTarget(
        bounds = bounds,
        sourceId = from.id,
        direction = direction,
    )
    val target = controls.firstOrNull { view -> view.id == targetId }
    return if (target != null) {
        target.requestFocus()
        true
    } else {
        true
    }
}

internal fun playerFocusDirectionalTarget(
    bounds: Collection<PlayerFocusBounds>,
    sourceId: Int,
    direction: PlayerFocusDirection,
): Int? {
    return visualFocusDirectionalTarget(
        bounds = bounds.map { item ->
            VisualFocusBounds(
                index = item.id,
                left = item.left.toFloat(),
                top = item.top.toFloat(),
                right = item.right.toFloat(),
                bottom = item.bottom.toFloat(),
            )
        },
        sourceIndex = sourceId,
        direction = direction.toVisualGridDirection(),
        allowLoosePerpendicularMatch = true,
    )
}

private fun PlayerFocusDirection.toVisualGridDirection(): VisualGridDirection {
    return when (this) {
        PlayerFocusDirection.Left -> VisualGridDirection.Left
        PlayerFocusDirection.Right -> VisualGridDirection.Right
        PlayerFocusDirection.Up -> VisualGridDirection.Up
        PlayerFocusDirection.Down -> VisualGridDirection.Down
    }
}

private fun View.playerVisibleFocusBounds(): PlayerFocusBounds? {
    val rect = Rect()
    if (!getGlobalVisibleRect(rect)) return null
    if (rect.width() <= 0 || rect.height() <= 0) return null
    return PlayerFocusBounds(
        id = id,
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
    )
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
    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
    }
    setSkipControlsActive(active)
    installDynamicPlayerFocusNavigation()
}

internal fun PlayerView.setSkipControlsActive(active: Boolean) {
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = if (active) View.VISIBLE else View.GONE
    listOf(R.id.yummy_skip_skip, R.id.yummy_skip_watch).forEach { id ->
        findViewById<View>(id)?.apply {
            isEnabled = active
            isFocusable = active
            isClickable = active
            if (!active && hasFocus()) {
                clearFocus()
            }
        }
    }
}

// PlayerInputController
internal class PlayerInputController(
    private val controlsVisible: () -> Boolean,
    private val hideControls: () -> Boolean,
    private val handle: (InputActionEvent) -> Boolean,
) {
    fun hasVisibleControls(): Boolean = controlsVisible()

    fun hideVisibleControls(): Boolean = hideControls()

    fun handleInput(event: InputActionEvent): Boolean = handle(event)
}

internal fun createPlayerInputController(
    playerView: () -> PlayerView?,
    isInPictureInPicture: Boolean = false,
    canInitializeFocus: () -> Boolean = { true },
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): PlayerInputController = PlayerInputController(
    controlsVisible = {
        !isInPictureInPicture && playerView()?.hasVisiblePlayerControls() == true
    },
    hideControls = {
        val view = playerView()
        if (view == null || isInPictureInPicture) {
            false
        } else {
            if (!view.dismissPlayerPopupMenu()) {
                view.hidePlayerControls()
            }
            true
        }
    },
    handle = { event ->
        val view = playerView()
        if (
            view == null ||
            isInPictureInPicture ||
            (!view.hasFocus() && !canInitializeFocus())
        ) {
            false
        } else {
            view.handleRemoteInputAction(
                event = event,
                onRequestPlay = onRequestPlay,
                onPausePlayback = onPausePlayback,
            )
        }
    },
)

// PlayerInputControls
@Suppress("UNCHECKED_CAST")
internal fun PlayerView.requestPlayCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_request_play_callback) as? (() -> Unit)
}

@Suppress("UNCHECKED_CAST")
internal fun PlayerView.pausePlaybackCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_pause_callback) as? (() -> Unit)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.handleRemoteInputAction(
    event: InputActionEvent,
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    if (!useController) return false
    if (isSkipOnlyControllerMode()) {
        return handleSkipOnlyInputAction(event.action)
    }
    return handleStandardPlayerInput(event, requestPlay, pausePlayback)
}

private fun PlayerView.handleSkipOnlyInputAction(action: InputAction): Boolean {
    val skipButton = findViewById<View>(R.id.yummy_skip_skip)
    val watchButton = findViewById<View>(R.id.yummy_skip_watch)
    if (activateFocusedSkipControl(action, skipButton, watchButton)) return true
    if (action == InputAction.Back) {
        hideVisiblePlayerControls()
        return true
    }
    val movedInsideSkipPrompt = moveInsideSkipPrompt(action, skipButton, watchButton)
    cancelSkipAutoCountdown()
    if (movedInsideSkipPrompt) return true

    restoreStandardControlsFromSkipMode(action, skipButton, watchButton)
    return true
}

private fun activateFocusedSkipControl(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
): Boolean {
    if (action != InputAction.Confirm) return false
    val focusedButton = when {
        skipButton?.hasFocus() == true -> skipButton
        watchButton?.hasFocus() == true -> watchButton
        else -> null
    }
    focusedButton ?: return false
    focusedButton.performClick()
    return true
}

private fun moveInsideSkipPrompt(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
): Boolean {
    return when (action) {
        InputAction.Right -> {
            if (skipButton?.hasFocus() == true) watchButton?.requestFocus() == true else false
        }
        InputAction.Left -> {
            if (watchButton?.hasFocus() == true) skipButton?.requestFocus() == true else false
        }
        else -> false
    }
}

private fun PlayerView.restoreStandardControlsFromSkipMode(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
) {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    setSkipOnlyControllerMode(false)
    showPlayerControls()
    post {
        if (requestTimelineFocusFromSkipPrompt(action, skipButton, watchButton, timeBar)) {
            return@post
        }
        requestDefaultPlayerControlFocus()
    }
}

private fun requestTimelineFocusFromSkipPrompt(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
    timeBar: View?,
): Boolean {
    if (action != InputAction.Down) return false
    val skipPromptFocused = skipButton?.hasFocus() == true || watchButton?.hasFocus() == true
    if (!skipPromptFocused) return false
    return timeBar?.requestFocus() == true
}

private fun PlayerView.handleStandardPlayerInput(
    event: InputActionEvent,
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    val action = event.action
    if (action == InputAction.Back && dismissPlayerPopupMenu()) {
        return true
    }
    if (hasPlayerPopupMenu()) {
        return false
    }
    if (action != InputAction.Back) {
        keepVisiblePlayerControlsAwake()
    }
    cancelSkipAutoCountdown()
    if (action == InputAction.Confirm && timelineHasFocus()) {
        return confirmTimelineScrubOrTogglePlayback(
            onRequestPlay = requestPlay,
            onPausePlayback = pausePlayback,
        )
    }
    return handleDirectionalPlayerInput(event)
        ?: handlePlaybackPlayerInput(action, requestPlay, pausePlayback)
}

private fun PlayerView.timelineHasFocus(): Boolean {
    return findViewById<View>(Media3R.id.exo_progress)?.hasFocus() == true
}

private fun PlayerView.handleDirectionalPlayerInput(event: InputActionEvent): Boolean? {
    return when (event.action) {
        InputAction.Back -> hideVisiblePlayerControls()
        InputAction.Up,
        InputAction.Down,
        InputAction.Confirm -> preparePlayerControlsForNavigation()
        InputAction.Left,
        InputAction.Right -> handleHorizontalPlayerInput(event)
        else -> null
    }
}

private fun PlayerView.handleHorizontalPlayerInput(event: InputActionEvent): Boolean {
    if (preparePlayerControlsForNavigation()) return true
    return seekTimelineIfFocused(
        forward = event.action == InputAction.Right,
        repeatedInput = event.isRepeated,
    )
}

private fun PlayerView.handlePlaybackPlayerInput(
    action: InputAction,
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    return when (action) {
        InputAction.Play -> {
            requestPlay?.invoke() ?: player?.play()
            true
        }
        InputAction.Pause -> {
            pausePlayback?.invoke() ?: player?.pause()
            true
        }
        InputAction.PlayPause -> togglePlayerPlayback(requestPlay, pausePlayback)
        else -> false
    }
}

private fun PlayerView.preparePlayerControlsForNavigation(): Boolean {
    if (!hasVisiblePlayerControls()) {
        showPlayerControls()
        post { requestDefaultPlayerControlFocus() }
        return true
    }
    if (!hasFocusedPlayerControl()) {
        requestDefaultPlayerControlFocus()
        return true
    }
    return false
}

internal fun PlayerView.togglePlayerPlayback(
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    return player?.let { currentPlayer ->
        if (currentPlayer.isPlaying) {
            pausePlayback?.invoke() ?: currentPlayer.pause()
        } else {
            requestPlay?.invoke() ?: currentPlayer.play()
        }
        true
    } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
}

// PlayerPlaybackPolicy
internal fun resolvedPlaybackDurationMs(
    playerDurationMs: Long,
    contentDurationMs: Long,
    metadataDurationSeconds: Int?,
): Long? {
    return playerDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: contentDurationMs.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: metadataDurationSeconds
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(1_000L)
}

internal fun isPlaybackEndCloseOrBuffered(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long?,
    switchFallbackThresholdMs: Long,
): Boolean {
    val duration = durationMs?.takeIf { it > 0L } ?: return false
    val safePositionMs = positionMs.coerceAtLeast(0L)
    val safeBufferedPositionMs = bufferedPositionMs.coerceAtLeast(0L)
    val endIgnoreWindowMs = maxOf(PLAYBACK_BUFFER_END_IGNORE_MS, switchFallbackThresholdMs * 2)
    val remainingMs = (duration - safePositionMs).coerceAtLeast(0L)
    return remainingMs <= endIgnoreWindowMs ||
        safeBufferedPositionMs >= duration - PLAYBACK_BUFFER_END_EPSILON_MS
}

internal fun shouldSchedulePlaybackBufferingFallback(
    playbackState: Int,
    fallbackReported: Boolean,
): Boolean {
    return playbackState == Player.STATE_BUFFERING && !fallbackReported
}

internal fun shouldSchedulePlaybackStartupFallback(
    playbackState: Int,
    playbackStartedReported: Boolean,
    fallbackReported: Boolean,
): Boolean {
    if (playbackStartedReported || fallbackReported) return false
    return playbackState != Player.STATE_ENDED
}

internal fun shouldReportPlaybackStartupFallback(
    playbackState: Int,
    playbackStartedReported: Boolean,
    fallbackReported: Boolean,
    playWhenReady: Boolean = true,
): Boolean {
    if (!playWhenReady) return false
    return shouldSchedulePlaybackStartupFallback(
        playbackState = playbackState,
        playbackStartedReported = playbackStartedReported,
        fallbackReported = fallbackReported,
    )
}

internal fun playbackStartupFallbackDelayMs(playerBufferPreset: PlayerBufferPreset): Long {
    return playerBufferPreset.prepareFallbackThresholdMs
}

internal fun playbackBufferingFallbackDelayMs(
    playbackStartedReported: Boolean,
    playerBufferPreset: PlayerBufferPreset,
    fallbackSuppressedUntilMs: Long,
    nowMs: Long,
    playbackType: Int,
): Long {
    val baseDelayMs = when {
        playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE -> playerBufferPreset.prepareFallbackThresholdMs
        playbackStartedReported -> playerBufferPreset.switchFallbackThresholdMs
        else -> playerBufferPreset.prepareFallbackThresholdMs
    }
    return maxOf(baseDelayMs, fallbackSuppressedUntilMs - nowMs).coerceAtLeast(0L)
}
