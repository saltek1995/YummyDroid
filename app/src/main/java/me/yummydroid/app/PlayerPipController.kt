package me.yummydroid.app

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
