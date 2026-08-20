package me.yummydroid.app

import android.app.Application
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.tv.CastReceiverContext
import com.google.android.gms.cast.tv.media.MediaLoadCommandCallback
import com.google.android.gms.cast.tv.media.MediaManager
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

internal object YummyCastReceiverRuntime {
    private var initialized = false
    private var activeSessionToken: MediaSessionCompat.Token? = null

    @Synchronized
    fun initialize(application: Application) {
        if (initialized || !application.isTelevisionDevice()) return
        try {
            CastReceiverContext.initInstance(application)
            ProcessLifecycleOwner.get().lifecycle.addObserver(ReceiverLifecycle)
            initialized = true
        } catch (error: RuntimeException) {
            AppLog.w(CAST_RECEIVER_LOG_TAG, "Cast receiver initialization failed", error)
        }
    }

    @Synchronized
    fun mediaManagerOrNull(): MediaManager? {
        if (!initialized) return null
        return runCatching { CastReceiverContext.getInstance().mediaManager }
            .onFailure { error ->
                AppLog.w(CAST_RECEIVER_LOG_TAG, "Cast receiver media manager is unavailable", error)
            }
            .getOrNull()
    }

    @Synchronized
    fun attachSession(token: MediaSessionCompat.Token) {
        val mediaManager = mediaManagerOrNull() ?: return
        activeSessionToken = token
        mediaManager.setSessionCompatToken(token)
    }

    @Synchronized
    fun detachSession(token: MediaSessionCompat.Token) {
        if (activeSessionToken != token) return
        mediaManagerOrNull()?.setSessionCompatToken(null)
        activeSessionToken = null
    }

    private object ReceiverLifecycle : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            runCatching { CastReceiverContext.getInstance().start() }
                .onFailure { error ->
                    AppLog.w(CAST_RECEIVER_LOG_TAG, "Cast receiver start failed", error)
                }
        }

        override fun onStop(owner: LifecycleOwner) {
            runCatching { CastReceiverContext.getInstance().stop() }
                .onFailure { error ->
                    AppLog.w(CAST_RECEIVER_LOG_TAG, "Cast receiver stop failed", error)
                }
        }
    }
}

internal class YummyCastReceiverActivityController private constructor(
    private val mediaManager: MediaManager,
    private val onPlaybackRequest: (YummyCastPlaybackRequest) -> Unit,
) {
    private var handledIntent: Intent? = null
    private val loadCallback = object : MediaLoadCommandCallback() {
        override fun onLoad(
            senderId: String?,
            loadRequestData: MediaLoadRequestData,
        ): Task<MediaLoadRequestData> {
            val payload = loadRequestData.mediaInfo
                ?.customData
                .yummyCastPlaybackPayloadOrNull()
                ?: return Tasks.forException(
                    IllegalArgumentException("Missing YummyDroid Cast playback payload"),
                )
            val startPositionMs = loadRequestData.currentTime
                .takeUnless { it == MediaLoadRequestData.PLAY_POSITION_UNASSIGNED }
                ?.coerceAtLeast(0L)
                ?: 0L
            mediaManager.setDataFromLoad(loadRequestData)
            onPlaybackRequest(
                YummyCastPlaybackRequest(
                    payload = payload,
                    startPositionMs = startPositionMs,
                    autoplay = loadRequestData.autoplay != false,
                ),
            )
            mediaManager.broadcastMediaStatus()
            return Tasks.forResult(loadRequestData)
        }
    }

    init {
        mediaManager.setMediaLoadCommandCallback(loadCallback)
    }

    fun handleIntent(intent: Intent): Boolean {
        if (handledIntent === intent) return true
        return mediaManager.onNewIntent(intent).also { handled ->
            if (handled) handledIntent = intent
        }
    }

    fun release() {
        mediaManager.setMediaLoadCommandCallback(null)
    }

    companion object {
        fun create(
            onPlaybackRequest: (YummyCastPlaybackRequest) -> Unit,
        ): YummyCastReceiverActivityController? {
            val mediaManager = YummyCastReceiverRuntime.mediaManagerOrNull() ?: return null
            return YummyCastReceiverActivityController(mediaManager, onPlaybackRequest)
        }
    }
}

private const val CAST_RECEIVER_LOG_TAG = "YummyDroidCastReceiver"
