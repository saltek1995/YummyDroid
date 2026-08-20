package me.yummydroid.app.ui

import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import me.yummydroid.app.AppLog
import me.yummydroid.app.YummyCastReceiverRuntime

@OptIn(UnstableApi::class)
@Composable
internal fun BindYummyCastReceiverMediaSession(player: Player) {
    val context = LocalContext.current
    DisposableEffect(context, player) {
        if (YummyCastReceiverRuntime.mediaManagerOrNull() == null) {
            onDispose { }
        } else {
            val mediaSession = MediaSession.Builder(context, player).build()
            val compatToken = MediaSessionCompat.Token.fromToken(mediaSession.platformToken)
            YummyCastReceiverRuntime.attachSession(compatToken)
            AppLog.d(CAST_RECEIVER_SESSION_LOG_TAG, "TV player MediaSession attached")
            onDispose {
                YummyCastReceiverRuntime.detachSession(compatToken)
                mediaSession.release()
                AppLog.d(CAST_RECEIVER_SESSION_LOG_TAG, "TV player MediaSession released")
            }
        }
    }
}

private const val CAST_RECEIVER_SESSION_LOG_TAG = "YummyDroidCastReceiver"
