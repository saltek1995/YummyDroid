package me.yummydroid.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity

// MainActivityPictureInPictureController
internal class MainActivityPictureInPictureController(
    private val activity: ComponentActivity,
    private val isPlayerRoute: () -> Boolean,
) {
    private val playbackStateListener: (Boolean) -> Unit = { updateParams() }

    fun start() {
        PlayerPipController.addPlaybackStateListener(playbackStateListener)
    }

    fun stop() {
        PlayerPipController.removePlaybackStateListener(playbackStateListener)
    }

    fun isSupported(): Boolean {
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun shouldEnterOnUserLeaveHint(sdkInt: Int): Boolean {
        return MainActivityPipPolicy.shouldEnterOnUserLeaveHint(
            sdkInt = sdkInt,
            isPlayerRoute = isPlayerRoute(),
            hasPlayer = PlayerPipController.hasPlayer,
        )
    }

    fun enter(showMessage: Boolean = true) {
        if (!isSupported()) {
            showMessageIfRequested(showMessage, R.string.pip_not_supported_device)
            return
        }
        if (
            !MainActivityPipPolicy.canEnter(
                isPlayerRoute = isPlayerRoute(),
                isInPictureInPictureMode = activity.isInPictureInPictureMode,
                hasPlayer = PlayerPipController.hasPlayer,
            )
        ) {
            return
        }

        runCatching {
            PlayerPipController.setPictureInPictureMode(true)
            activity.enterPictureInPictureMode(buildParams())
        }.onSuccess { entered ->
            if (!entered) {
                PlayerPipController.setPictureInPictureMode(false)
                showMessageIfRequested(showMessage, R.string.pip_rejected)
            }
        }.onFailure { throwable ->
            PlayerPipController.setPictureInPictureMode(false)
            AppLog.w("YummyDroidPiP", "Failed to enter picture-in-picture", throwable)
            showMessageIfRequested(showMessage, R.string.pip_open_failed)
        }
    }

    fun updateParams() {
        if (!isSupported()) return
        runCatching {
            activity.setPictureInPictureParams(buildParams())
        }.onFailure { throwable ->
            AppLog.w("YummyDroidPiP", "Failed to update picture-in-picture params", throwable)
        }
    }

    private fun buildParams(): PictureInPictureParams {
        val paramsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(buildActions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            paramsBuilder.setAutoEnterEnabled(
                MainActivityPipPolicy.shouldAutoEnter(
                    isPlayerRoute = isPlayerRoute(),
                    hasPlayer = PlayerPipController.hasPlayer,
                ),
            )
        }
        val sourceRectHint = Rect()
        if (activity.window.decorView.getGlobalVisibleRect(sourceRectHint) && !sourceRectHint.isEmpty) {
            paramsBuilder.setSourceRectHint(sourceRectHint)
        }
        return paramsBuilder.build()
    }

    private fun buildActions(): List<RemoteAction> {
        return buildList {
            if (PlayerPipController.canPlayPreviousEpisode) {
                add(
                    buildAction(
                        action = PipActionReceiver.ACTION_PREVIOUS_EPISODE,
                        iconRes = R.drawable.ic_pip_previous,
                        label = activity.getString(R.string.player_previous),
                        requestCode = PIP_PREVIOUS_REQUEST_CODE,
                    ),
                )
            }
            add(buildPlayPauseAction())
            if (PlayerPipController.canPlayNextEpisode) {
                add(
                    buildAction(
                        action = PipActionReceiver.ACTION_NEXT_EPISODE,
                        iconRes = R.drawable.ic_pip_next,
                        label = activity.getString(R.string.player_next),
                        requestCode = PIP_NEXT_REQUEST_CODE,
                    ),
                )
            }
        }
    }

    private fun buildPlayPauseAction(): RemoteAction {
        val isPlaying = PlayerPipController.isPlaying
        val iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
        val label = activity.getString(if (isPlaying) R.string.pip_pause else R.string.pip_play)
        return buildAction(
            action = PipActionReceiver.ACTION_TOGGLE_PLAY_PAUSE,
            iconRes = iconRes,
            label = label,
            requestCode = PIP_PLAY_PAUSE_REQUEST_CODE,
        )
    }

    private fun buildAction(
        action: String,
        iconRes: Int,
        label: String,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(activity, PipActionReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(
            Icon.createWithResource(activity, iconRes),
            label,
            label,
            pendingIntent,
        )
    }

    private fun showMessageIfRequested(showMessage: Boolean, messageRes: Int) {
        if (showMessage) {
            Toast.makeText(activity, activity.getString(messageRes), Toast.LENGTH_SHORT).show()
        }
    }
}

internal object MainActivityPipPolicy {
    fun canEnter(
        isPlayerRoute: Boolean,
        isInPictureInPictureMode: Boolean,
        hasPlayer: Boolean,
    ): Boolean {
        return isPlayerRoute && !isInPictureInPictureMode && hasPlayer
    }

    fun shouldAutoEnter(isPlayerRoute: Boolean, hasPlayer: Boolean): Boolean {
        return isPlayerRoute && hasPlayer
    }

    fun shouldEnterOnUserLeaveHint(
        sdkInt: Int,
        isPlayerRoute: Boolean,
        hasPlayer: Boolean,
    ): Boolean {
        return sdkInt < Build.VERSION_CODES.S && shouldAutoEnter(isPlayerRoute, hasPlayer)
    }
}

private const val PIP_PLAY_PAUSE_REQUEST_CODE = 1001
private const val PIP_PREVIOUS_REQUEST_CODE = 1002
private const val PIP_NEXT_REQUEST_CODE = 1003

// PipActionReceiver
class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> PlayerPipController.togglePlayPause()
            ACTION_PREVIOUS_EPISODE -> PlayerPipController.playPreviousEpisode()
            ACTION_NEXT_EPISODE -> PlayerPipController.playNextEpisode()
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "me.yummydroid.app.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PREVIOUS_EPISODE = "me.yummydroid.app.action.PREVIOUS_EPISODE"
        const val ACTION_NEXT_EPISODE = "me.yummydroid.app.action.NEXT_EPISODE"
    }
}

// PlayerPipController
interface PipPlayerHandle {
    val isPlaying: Boolean
    val canPlayPreviousEpisode: Boolean
        get() = false
    val canPlayNextEpisode: Boolean
        get() = false
    fun play()
    fun pause()
    fun playPreviousEpisode() = Unit
    fun playNextEpisode() = Unit
    fun setPictureInPictureMode(enabled: Boolean) = Unit
    fun hideAppControls() = Unit
}

object PlayerPipController {
    private var playerHandle: PipPlayerHandle? = null
    private var inPictureInPicture = false
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    val isPlaying: Boolean
        get() = playerHandle?.isPlaying == true

    val canPlayPreviousEpisode: Boolean
        get() = playerHandle?.canPlayPreviousEpisode == true

    val canPlayNextEpisode: Boolean
        get() = playerHandle?.canPlayNextEpisode == true

    val hasPlayer: Boolean
        get() = playerHandle != null

    fun registerPlayer(handle: PipPlayerHandle) {
        playerHandle = handle
        handle.setPictureInPictureMode(inPictureInPicture)
        notifyPlayingChanged()
    }

    fun unregisterPlayer(handle: PipPlayerHandle) {
        if (playerHandle === handle) {
            playerHandle = null
            notifyPlayingChanged()
        }
    }

    fun addPlaybackStateListener(listener: (Boolean) -> Unit) {
        listeners += listener
        listener(isPlaying)
    }

    fun removePlaybackStateListener(listener: (Boolean) -> Unit) {
        listeners -= listener
    }

    fun togglePlayPause() {
        val handle = playerHandle ?: return
        if (inPictureInPicture) {
            handle.hideAppControls()
        }
        if (handle.isPlaying) {
            handle.pause()
        } else {
            handle.play()
        }
        if (inPictureInPicture) {
            handle.hideAppControls()
        }
        notifyPlayingChanged()
    }

    fun playPreviousEpisode() {
        val handle = playerHandle ?: return
        if (!handle.canPlayPreviousEpisode) return
        if (inPictureInPicture) {
            handle.hideAppControls()
        }
        handle.playPreviousEpisode()
        notifyPlayingChanged()
    }

    fun playNextEpisode() {
        val handle = playerHandle ?: return
        if (!handle.canPlayNextEpisode) return
        if (inPictureInPicture) {
            handle.hideAppControls()
        }
        handle.playNextEpisode()
        notifyPlayingChanged()
    }

    fun setPictureInPictureMode(enabled: Boolean) {
        inPictureInPicture = enabled
        playerHandle?.setPictureInPictureMode(enabled)
    }

    fun hideAppControls() {
        playerHandle?.hideAppControls()
    }

    fun notifyPlayingChanged() {
        val playing = isPlaying
        listeners.forEach { listener -> listener(playing) }
    }
}
