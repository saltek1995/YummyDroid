package me.yummydroid.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultCastOptionsProvider
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions

@OptIn(UnstableApi::class)
class YummyCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val configuredReceiverId = BuildConfig.CAST_RECEIVER_APP_ID.trim()
        val notificationOptions = NotificationOptions.Builder()
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_SKIP_NEXT,
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_STOP_CASTING,
                ),
                intArrayOf(1, 2),
            )
            .setTargetActivityClassName(YummyCastExpandedControlsActivity::class.java.name)
            .build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(YummyCastExpandedControlsActivity::class.java.name)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(
                configuredReceiverId.ifBlank {
                    DefaultCastOptionsProvider.APP_ID_DEFAULT_RECEIVER_WITH_DRM
                },
            )
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setRemoteToLocalEnabled(true)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
