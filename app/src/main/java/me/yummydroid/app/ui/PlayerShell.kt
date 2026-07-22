package me.yummydroid.app.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import java.util.Locale
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.R
import me.yummydroid.app.ui.components.focusRing

@Composable
@OptIn(UnstableApi::class)
internal fun PlayerShellPane(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val configuration = LocalConfiguration.current
    val playerControlTexts = rememberPlayerControlTexts()
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        key(
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.smallestScreenWidthDp,
        ) {
            AndroidView(
                factory = { viewContext ->
                    val parent = FrameLayout(viewContext)
                    LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
                },
                update = { view ->
                    view.player = null
                    view.useController = true
                    view.controllerAutoShow = true
                    view.setControllerAnimationEnabled(false)
                    view.setControllerShowTimeoutMs(0)
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.keepScreenOn = true
                    view.bindYummyShellController(
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        settings = settings,
                        groups = groups,
                        selectedKey = selectedKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        canUsePictureInPicture = canUsePictureInPicture,
                        showCenterControls = message == null,
                        texts = playerControlTexts,
                        onToggleSubscription = onToggleSubscription,
                        onSelectGroup = onSelectGroup,
                        onPlayVideo = onPlayVideo,
                        onBack = onBack,
                    )
                    view.showController()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (message == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(top = 112.dp, bottom = 176.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                DialogActionButton(
                    text = uiText("Повторить"),
                    primary = true,
                    onClick = onRetry,
                )
            }
        }
    }
}

internal inline fun <reified T> View.tagValue(tagId: Int): T? {
    return getTag(tagId) as? T
}

internal fun View.clearTagValue(tagId: Int) {
    setTag(tagId, null)
}

internal fun View.removeTaggedRunnable(tagId: Int) {
    tagValue<Runnable>(tagId)?.let(::removeCallbacks)
    clearTagValue(tagId)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyShellController(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    showCenterControls: Boolean,
    texts: PlayerControlTexts,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onBack: () -> Unit,
) {
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text = currentVideo.playbackSubtitle(texts)
    findViewById<TextView>(R.id.yummy_player_info)?.text = currentVideo.playbackSourceLabel(false)
    findViewById<TextView>(Media3R.id.exo_position)?.text = context.getString(R.string.player_zero_time)
    findViewById<TextView>(Media3R.id.exo_duration)?.text = context.getString(R.string.player_zero_time)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = View.GONE
    findViewById<View>(Media3R.id.exo_play_pause)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (showCenterControls) {
        View.VISIBLE
    } else {
        View.GONE
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (showCenterControls && previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { previousVideo?.let(onPlayVideo) }
    }
    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (showCenterControls && nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener { nextVideo?.let(onPlayVideo) }
    }

    findViewById<TextView>(R.id.yummy_player_voice)?.apply {
        text = texts.voice
        visibility = if (groups.size > 1) View.VISIBLE else View.GONE
        setPlayerControlEnabled(groups.size > 1)
        setOnClickListener {
            showController()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                texts = texts,
                onSelectGroup = onSelectGroup,
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        text = texts.quality
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_subtitles)?.apply {
        text = texts.subtitles
        visibility = View.GONE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_subscription)?.apply {
        text = if (subscriptionActive) texts.subscribed else texts.subscription
        visibility = if (allowSubscription) View.VISIBLE else View.GONE
        setPlayerControlEnabled(allowSubscription)
        applyPlayerSubscriptionState(subscriptionActive)
        setOnClickListener {
            showController()
            onToggleSubscription()
        }
    }
    findViewById<TextView>(R.id.yummy_player_speed)?.apply {
        text = settings.playerSpeed.title
        visibility = View.VISIBLE
        setPlayerControlEnabled(false)
    }
    findViewById<TextView>(R.id.yummy_player_pip)?.apply {
        text = context.getString(R.string.player_pip)
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setPlayerControlEnabled(false)
    }

    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isEnabled = false
        isFocusable = false
    }
}

internal fun List<VideoVariant>.sortedForPlayer(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
            .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
            .thenBy { if (it.isOfflineAvailable) 0 else 1 }
            .thenBy { it.id },
    )
}

@Composable
internal fun PagingGridFooter(
    paging: PagingUiState,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(paging.isLoadingMore, paging.canLoadMore, paging.error) {
        if (paging.canLoadMore && !paging.isLoadingMore && paging.error == null) {
            onLoadMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            paging.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            paging.error != null -> Button(
                onClick = onLoadMore,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Еще раз"))
            }
        }
    }

}

internal fun List<VideoVariant>.sortedForPlayer(preferredGroupKey: String?): List<VideoVariant> {
    return groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()
}

internal fun List<VideoSubscription>.isVideoVoiceSubscribed(video: VideoVariant): Boolean {
    return isSubscribedTo(video)
}

internal fun VideoVariant.playbackSourceLabel(isLocalPlayback: Boolean = localPlaybackUrl.isNotBlank()): String {
    return if (isLocalPlayback) {
        "Local"
    } else {
        player.cleanVideoSourceLabel().ifBlank { player }.ifBlank { "HLS" }
    }
}

internal fun ResolvedSubtitleTrack.toMedia3SubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    val resolvedMimeType = subtitleMimeTypeForMedia3(cleanUri, mimeType)
        ?.takeIf { it.isSideLoadedSubtitleMimeType() }
        ?: return null
    return MediaItem.SubtitleConfiguration.Builder(cleanUri.toUri()).apply {
        setMimeType(resolvedMimeType)
        language?.takeIf { it.isNotBlank() }?.let(::setLanguage)
        label.takeIf { it.isNotBlank() }?.let(::setLabel)
        setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

internal fun subtitleMimeTypeForMedia3(uri: String, mimeType: String?): String? {
    val source = mimeType?.takeIf { it.isNotBlank() } ?: uri
    val lower = source.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        "mpegurl" in lower || lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        "subrip" in lower || lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        "text/vtt" in lower || lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        "text/x-ssa" in lower || lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        "ttml" in lower || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

internal fun String.isSideLoadedSubtitleMimeType(): Boolean {
    return this == MimeTypes.TEXT_VTT ||
        this == MimeTypes.APPLICATION_SUBRIP ||
        this == MimeTypes.TEXT_SSA ||
        this == MimeTypes.APPLICATION_TTML
}
