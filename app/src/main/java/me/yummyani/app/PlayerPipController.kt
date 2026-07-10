package me.yummyani.app

interface PipPlayerHandle {
    val isPlaying: Boolean
    fun play()
    fun pause()
    fun setPictureInPictureMode(enabled: Boolean) = Unit
    fun hideAppControls() = Unit
}

object PlayerPipController {
    private var playerHandle: PipPlayerHandle? = null
    private var inPictureInPicture = false
    private val listeners = mutableSetOf<(Boolean) -> Unit>()

    val isPlaying: Boolean
        get() = playerHandle?.isPlaying == true

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
