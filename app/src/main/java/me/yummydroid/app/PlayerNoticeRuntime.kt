package me.yummydroid.app

import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.matchingVoiceTitle

internal class PlayerNoticeRuntime(
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val sourceFallbackMessage: (String, String, String) -> String,
    private val voiceFallbackMessage: (String, String, String) -> String,
    private val playbackPlayerErrorMessage: () -> String,
    private val playbackBufferingTimeoutMessage: () -> String,
    private val genericSourceLabel: () -> String,
) {
    private var playerNoticeId = 0L

    fun showTransientNotice(message: String) {
        updateState {
            it.copy(
                playerNotice = PlayerNotice(
                    id = ++playerNoticeId,
                    message = message,
                ),
            )
        }
    }

    fun showPlaybackSourceFallbackNotice(notice: SourceFallbackNotice, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSamePlaybackSourceAs(notice.selectedVideo)) return
        val selectedLabel = notice.selectedVideo.playbackNoticeSourceLabel()
        val fallbackLabel = fallbackVideo.playbackNoticeSourceLabel()
        showTransientNotice(sourceFallbackMessage(selectedLabel, notice.reason, fallbackLabel))
    }

    fun showPlaybackVoiceFallbackNotice(previousVideo: VideoVariant, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSameVoiceAs(previousVideo)) return
        showTransientNotice(
            voiceFallbackMessage(
                previousVideo.matchingVoiceTitle,
                fallbackVideo.episodeTitle,
                fallbackVideo.matchingVoiceTitle,
            ),
        )
    }

    fun playbackFailureReason(failure: PlaybackFailure): String {
        return failure.message
            ?.takeIf { it.isNotBlank() }
            ?: when (failure.kind) {
                PlaybackFailureKind.PlayerError -> playbackPlayerErrorMessage()
                PlaybackFailureKind.BufferingTimeout -> playbackBufferingTimeoutMessage()
                PlaybackFailureKind.SourceUnavailable -> playbackPlayerErrorMessage()
            }
    }

    private fun VideoVariant.playbackNoticeSourceLabel(): String {
        return player.cleanVideoSourceLabel()
            .ifBlank { player }
            .ifBlank { genericSourceLabel() }
    }
}
