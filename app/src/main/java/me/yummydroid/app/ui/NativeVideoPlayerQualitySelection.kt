package me.yummydroid.app.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey

internal data class NativePlayerQualitySelection(
    val options: List<QualityOption>,
    val selectedKey: String?,
    val streamSelectedKey: String?,
    val onSelectedKeyChanged: (String?) -> Unit,
)

@Composable
internal fun rememberNativePlayerQualitySelection(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    playerControlTexts: PlayerControlTexts,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    offlineMode: Boolean,
    tracks: Tracks,
): NativePlayerQualitySelection {
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty()
            .ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
        sourceVideos.sourceQualityOptionsFor(currentVideo)
    }
    val streamQualityOptions = remember(stream.availableQualities) {
        stream.availableQualities.sourceQualityOptions()
    }
    val localQualityOptions = remember(
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
        currentVideo.localPlaybackUrl,
        currentVideo.localFiles,
    ) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(
        onlineQualityOptions,
        sourceQualityOptions,
        streamQualityOptions,
        localQualityOptions,
        offlineMode,
    ) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions + sourceQualityOptions + streamQualityOptions,
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
            downloadedLabel = playerControlTexts.downloaded,
        )
    }
    val streamSelectedQualityKey = remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        stream.selectedVideoHeight
            ?.takeIf { it > 0 }
            ?.let { "height:$it" }
    }
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        mutableStateOf(
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = currentVideo.selectedLocalQualityKey(stream.url),
                streamSelectedQualityKey = streamSelectedQualityKey,
                qualityOptions = qualityOptions,
                playbackPreferredQuality = playbackPreferredQuality,
                defaultQuality = defaultQuality,
            ),
        )
    }
    NativePlayerQualitySelectionEffects(
        player = player,
        playerView = playerView,
        streamUrl = stream.url,
        qualityOptions = qualityOptions,
        streamSelectedQualityKey = streamSelectedQualityKey,
        playbackPreferredQuality = playbackPreferredQuality,
        defaultQuality = defaultQuality,
        selectedQualityKey = selectedQualityKey,
        onSelectedQualityKeyChanged = { selectedQualityKey = it },
    )
    return NativePlayerQualitySelection(
        options = qualityOptions,
        selectedKey = selectedQualityKey,
        streamSelectedKey = streamSelectedQualityKey,
        onSelectedKeyChanged = { selectedQualityKey = it },
    )
}

@Composable
private fun NativePlayerQualitySelectionEffects(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: String?,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    val currentSelectedQualityKey = rememberUpdatedState(selectedQualityKey)
    RefreshMissingQualitySelectionEffect(
        qualityOptions,
        streamSelectedQualityKey,
        playbackPreferredQuality,
        defaultQuality,
        currentSelectedQualityKey,
        onSelectedQualityKeyChanged,
    )
    ApplyInitialQualitySelectionEffect(
        player,
        playerView,
        streamUrl,
        qualityOptions,
        streamSelectedQualityKey,
        playbackPreferredQuality,
        defaultQuality,
        currentSelectedQualityKey,
        onSelectedQualityKeyChanged,
    )
    ApplyPreferredTrackQualityEffect(
        player,
        streamUrl,
        qualityOptions,
        playbackPreferredQuality,
        defaultQuality,
    )
}

@Composable
private fun RefreshMissingQualitySelectionEffect(
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: State<String?>,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    LaunchedEffect(qualityOptions) {
        val currentKey = selectedQualityKey.value
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                ?: defaultQuality
            onSelectedQualityKeyChanged(
                streamSelectedQualityKey
                    ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                    ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity(),
            )
        }
    }
}

@Composable
private fun ApplyInitialQualitySelectionEffect(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: State<String?>,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    LaunchedEffect(
        qualityOptions,
        playbackPreferredQuality,
        defaultQuality,
        streamUrl,
        streamSelectedQualityKey,
    ) {
        val currentKey = selectedQualityKey.value
        if (currentKey != null && qualityOptions.any { it.matchesSelectedQualityKey(currentKey) }) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && currentKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            onSelectedQualityKeyChanged(preferredKey)
            playerView()?.setSelectedQualityTag(preferredKey)
        }
    }
}

@Composable
private fun ApplyPreferredTrackQualityEffect(
    player: ExoPlayer,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
) {
    LaunchedEffect(player, qualityOptions, playbackPreferredQuality, defaultQuality, streamUrl) {
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }
}

private fun PlayerView.setSelectedQualityTag(key: String) {
    findViewById<View>(R.id.yummy_player_quality)
        ?.setTag(R.id.yummy_player_quality, key)
}
