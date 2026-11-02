package me.yummydroid.app.ui

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** Keeps user intent separate from the temporary pause required for a display handoff. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DisplayModePlayer(
    private val localPlayer: Player,
    private val scope: CoroutineScope,
    private val matchingEnabled: () -> Boolean,
    private val matchDisplay: suspend (Player) -> Unit,
) : ForwardingSimpleBasePlayer(localPlayer) {
    var preparingDisplay by mutableStateOf(false)
        private set
    private var requestedPlayWhenReady = false
    private var heldFirstFrame = false
    private var handoff: Job? = null
    private var generation = 0L

    override fun getState(): State {
        val state = super.getState()
        if (!preparingDisplay) {
            if (!revealFirstFrame) return state
            revealFirstFrame = false
            return state.buildUpon().setNewlyRenderedFirstFrame(true).build()
        }
        heldFirstFrame = heldFirstFrame || state.newlyRenderedFirstFrame
        return state.buildUpon()
            .setPlayWhenReady(requestedPlayWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (state.playbackState == STATE_READY) STATE_BUFFERING else state.playbackState)
            .setNewlyRenderedFirstFrame(false)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (!preparingDisplay) return super.handleSetPlayWhenReady(playWhenReady)
        requestedPlayWhenReady = playWhenReady
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val intent = if (preparingDisplay) requestedPlayWhenReady else localPlayer.playWhenReady
        cancelHandoff()
        if (matchingEnabled()) holdPlayback(intent)
        val result = super.handleSetMediaItems(items, startIndex, startPositionMs)
        if (!preparingDisplay) localPlayer.playWhenReady = intent
        return result
    }

    override fun handlePrepare(): ListenableFuture<*> {
        val result = super.handlePrepare()
        if (preparingDisplay) startHandoff()
        return result
    }

    fun updateDisplayMatching() {
        // Finish an in-flight switch even if matching was disabled. Reset the mode on screen exit,
        // rather than requesting another physical switch after playback has already resumed.
        if (!preparingDisplay && matchingEnabled() && localPlayer.playbackState != STATE_IDLE) {
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
        localPlayer.pause()
    }

    private fun startHandoff() {
        handoff?.cancel()
        val expectedGeneration = ++generation
        handoff = scope.launch {
            matchDisplay(localPlayer)
            coroutineContext.ensureActive()
            if (expectedGeneration == generation) finishHandoff()
        }
    }

    private fun finishHandoff() {
        handoff?.cancel()
        handoff = null
        val firstFrame = heldFirstFrame
        preparingDisplay = false
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
        heldFirstFrame = false
        revealFirstFrame = false
    }

    override fun handleStop(): ListenableFuture<*> {
        cancelHandoff()
        return super.handleStop()
    }

    override fun handleRelease(): ListenableFuture<*> {
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
            continuation.invokeOnCancellation { removeListener(listener) }
            check()
        }
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
