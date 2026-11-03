package me.yummydroid.app.ui

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Prepares paused, matches the display, reveals a ready frame, then restores playback intent. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DisplayModePlayer(
    private val localPlayer: Player,
    private val scope: CoroutineScope,
    private val matchingEnabled: () -> Boolean,
    private val matchDisplay: suspend (Player) -> Unit,
) : ForwardingSimpleBasePlayer(localPlayer) {
    var preparingDisplay by mutableStateOf(false)
        private set
    var presentationToken by mutableStateOf<Long?>(null)
        private set
    private var holdingPlayback = false
    private var videoOutput: Any? = null
    private var surfaceHolder: SurfaceHolder? = null
    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = holdForNewSurface()
        override fun surfaceDestroyed(holder: SurfaceHolder) = holdForNewSurface()
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    }
    private var requestedPlayWhenReady = false
    private var heldFirstFrame = false
    private var hasVideoFrame = false
    private var handoff: Job? = null
    private var generation = 0L
    private val firstFrameListener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            hasVideoFrame = true
            if (holdingPlayback) heldFirstFrame = true
        }
    }

    init { localPlayer.addListener(firstFrameListener) }

    override fun getState(): State {
        val state = super.getState()
        if (!holdingPlayback) {
            if (!revealFirstFrame) return state
            revealFirstFrame = false
            return state.buildUpon().setNewlyRenderedFirstFrame(true).build()
        }
        // Batched onEvents may still contain an old surface's frame event after its destruction.
        val renderedFirstFrame = presentationToken != null &&
            (revealFirstFrame || (state.newlyRenderedFirstFrame && hasVideoFrame))
        if (presentationToken != null) revealFirstFrame = false
        return state.buildUpon()
            .setPlayWhenReady(requestedPlayWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (state.playbackState == STATE_READY) STATE_BUFFERING else state.playbackState)
            .setNewlyRenderedFirstFrame(renderedFirstFrame)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!holdingPlayback) return super.handleSetPlayWhenReady(playWhenReady)
        requestedPlayWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val intent = if (holdingPlayback) requestedPlayWhenReady else localPlayer.playWhenReady
        cancelHandoff()
        hasVideoFrame = false
        holdPlayback(intent)
        return super.handleSetMediaItems(items, startIndex, startPositionMs)
    }

    override fun handlePrepare(): ListenableFuture<*> {
        val result = super.handlePrepare()
        if (holdingPlayback) startHandoff()
        return result
    }

    fun updateDisplayMatching() {
        // Finish an in-flight switch even if matching was disabled. Reset the mode on screen exit,
        // rather than requesting another physical switch after playback has already resumed.
        if (!holdingPlayback && matchingEnabled() && localPlayer.playbackState != STATE_IDLE) {
            holdPlayback()
            invalidateState()
            startHandoff()
        }
    }

    private fun holdPlayback(intent: Boolean = localPlayer.playWhenReady) {
        requestedPlayWhenReady = intent
        heldFirstFrame = false
        revealFirstFrame = false
        preparingDisplay = true
        holdingPlayback = true
        presentationToken = null
        localPlayer.pause()
    }

    private fun startHandoff() {
        handoff?.cancel()
        val expectedGeneration = ++generation
        handoff = scope.launch {
            if (matchingEnabled()) matchDisplay(localPlayer)
            localPlayer.awaitNativePlaybackReadyOrTerminal()
            awaitFirstVideoFrame()
            coroutineContext.ensureActive()
            if (expectedGeneration == generation) {
                if (localPlayer.playbackState == STATE_READY && localPlayer.playerError == null) {
                    // Show the paused frame first. The host acknowledges a draw without its loading cover.
                    preparingDisplay = false
                    revealFirstFrame = heldFirstFrame
                    presentationToken = expectedGeneration
                    invalidateState()
                } else {
                    finishHandoff()
                }
            }
        }
    }

    private suspend fun awaitFirstVideoFrame() = suspendCancellableCoroutine { continuation ->
        lateinit var listener: Player.Listener
        fun check() {
            val terminal = localPlayer.playbackState == STATE_IDLE || localPlayer.playbackState == STATE_ENDED ||
                localPlayer.playerError != null
            val videoSelected = localPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            if (terminal || (localPlayer.playbackState == STATE_READY && (!videoSelected || hasVideoFrame))) {
                localPlayer.removeListener(listener)
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = check()
        }
        localPlayer.addListener(listener)
        continuation.removePlayerListenerOnCancellation(localPlayer, listener)
        check()
    }

    fun acknowledgeVideoPresentation(token: Long) {
        if (!holdingPlayback || presentationToken != token || generation != token) return
        if (localPlayer.playbackState == STATE_READY && localPlayer.playerError == null) finishHandoff()
        else {
            preparingDisplay = true
            presentationToken = null
            startHandoff()
            invalidateState()
        }
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        if (this.videoOutput !== videoOutput) {
            surfaceHolder?.removeCallback(surfaceCallback)
            this.videoOutput = videoOutput
            surfaceHolder = when (videoOutput) {
                is SurfaceView -> videoOutput.holder
                is SurfaceHolder -> videoOutput
                else -> null
            }
            surfaceHolder?.addCallback(surfaceCallback)
            holdForNewSurface()
        }
        return super.handleSetVideoOutput(videoOutput)
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        if (videoOutput == null || this.videoOutput === videoOutput) {
            surfaceHolder?.removeCallback(surfaceCallback)
            surfaceHolder = null
            this.videoOutput = null
            holdForNewSurface()
        }
        return super.handleClearVideoOutput(videoOutput)
    }

    private fun holdForNewSurface() {
        hasVideoFrame = false
        heldFirstFrame = false
        if (localPlayer.playbackState == STATE_IDLE || localPlayer.playbackState == STATE_ENDED ||
            localPlayer.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }) return
        // A mode switch can recreate the SurfaceView. Keep that same sequential preparation running;
        // cancelling it here would skip its display confirmation/settling on the next attempt.
        if (holdingPlayback && presentationToken == null && handoff?.isActive == true) return
        val intent = if (holdingPlayback) requestedPlayWhenReady else localPlayer.playWhenReady
        handoff?.cancel()
        holdPlayback(intent)
        invalidateState()
        startHandoff()
    }

    private fun finishHandoff() {
        handoff?.cancel()
        handoff = null
        val firstFrame = heldFirstFrame && presentationToken == null
        preparingDisplay = false
        holdingPlayback = false
        presentationToken = null
        heldFirstFrame = false
        // Restoring intent in IDLE/error does not prepare media, and recovery must not inherit our pause.
        localPlayer.playWhenReady = requestedPlayWhenReady
        // The prepared frame may have arrived while the display was still switching.
        revealFirstFrame = firstFrame
        invalidateState()
    }

    private var revealFirstFrame = false

    private fun cancelHandoff() {
        generation++
        handoff?.cancel()
        handoff = null
        preparingDisplay = false
        holdingPlayback = false
        presentationToken = null
        heldFirstFrame = false
        revealFirstFrame = false
    }

    override fun handleStop(): ListenableFuture<*> {
        hasVideoFrame = false
        cancelHandoff()
        return super.handleStop()
    }

    override fun handleRelease(): ListenableFuture<*> {
        surfaceHolder?.removeCallback(surfaceCallback)
        localPlayer.removeListener(firstFrameListener)
        cancelHandoff()
        return super.handleRelease()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal suspend fun Player.awaitVideoDisplayInfo(): VideoDisplayInfo? {
    // A reused ExoPlayer can still expose the previous episode's format while buffering.
    awaitNativePlaybackReadyOrTerminal()
    if (playbackState != Player.STATE_READY || playerError != null) return null
    return withTimeoutOrNull(1_000L) {
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: Player.Listener
            fun check() {
                val terminal = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED || playerError != null
                val info = currentVideoDisplayInfo()?.takeIf {
                    playbackState == Player.STATE_READY && !terminal && it.hasValidDimensions() &&
                        it.frameRate.isFinite() && it.frameRate > 0f
                }
                if (info != null || terminal) {
                    removeListener(listener)
                    if (continuation.isActive) continuation.resume(info)
                }
            }
            listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = check()
            }
            addListener(listener)
            continuation.removePlayerListenerOnCancellation(this@awaitVideoDisplayInfo, listener)
            check()
        }
    }
}

/** Timeout cancellation may run on DefaultExecutor, outside the player's application looper. */
internal fun CancellableContinuation<*>.removePlayerListenerOnCancellation(player: Player, listener: Player.Listener) {
    // Capture the looper while registering on the player thread, before cancellation can run.
    val handler = Handler(player.applicationLooper)
    invokeOnCancellation {
        if (Looper.myLooper() == handler.looper) player.removeListener(listener)
        else handler.post { player.removeListener(listener) }
    }
}

internal suspend fun Activity.prepareVideoDisplayMode(player: Player) {
    val video = player.awaitVideoDisplayInfo() ?: return
    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay
    val previousModeId = display.mode.modeId
    val targetModeId = applyVideoDisplayMode(enabled = true, video = video) ?: return
    if (previousModeId == targetModeId) return
    val manager = getSystemService(DisplayManager::class.java) ?: return
    val switched = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: DisplayManager.DisplayListener
            fun check() {
                if (display.mode.modeId == targetModeId && continuation.isActive) {
                    manager.unregisterDisplayListener(listener)
                    continuation.resume(Unit)
                }
            }
            listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) { if (displayId == display.displayId) check() }
            }
            manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            continuation.invokeOnCancellation { manager.unregisterDisplayListener(listener) }
            check()
        }
    }
    if (switched == null) {
        // Cancel an unfulfilled request so it cannot interrupt playback later.
        window.attributes = window.attributes.apply { preferredDisplayModeId = display.mode.modeId }
    } else {
        // Allow the SurfaceView to settle after the display mode notification.
        delay(300L)
    }
}
