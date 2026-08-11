package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadVoiceEpisodeCount
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.formatDuration
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

// EpisodeCard
@Composable
internal fun EpisodeCard(
    video: VideoVariant,
    episodeViews: Long,
    modifier: Modifier = Modifier,
    watchProgress: PlaybackProgress? = null,
    downloadedVariants: List<VideoVariant> = if (video.isOfflineAvailable) listOf(video) else emptyList(),
    onClick: () -> Unit,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val contentAlpha = if (enabled) 1f else 0.46f
    val progressFraction = watchProgress?.watchProgressFraction() ?: 0f
    val shape = YummyRadii.smallShape
    val cardHeight = if (compact) EpisodeCardCompactHeight else EpisodeCardDefaultHeight
    Surface(
        shape = shape,
        color = yummyActionSurfaceColor(enabled = enabled),
        contentColor = yummyActionContentColor(enabled = enabled),
        border = yummyActionBorder(enabled = enabled),
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeight)
            .dpadClickable(shape, enabled = enabled, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            EpisodeProgressBar(progressFraction)
            EpisodeCardContent(
                video = video,
                episodeViews = episodeViews,
                isDownloaded = downloadedVariants.isNotEmpty(),
                contentAlpha = contentAlpha,
            )
        }
    }
}

@Composable
private fun BoxScope.EpisodeProgressBar(progressFraction: Float) {
    if (progressFraction <= 0f) return
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(progressFraction)
            .height(4.dp)
            .background(YummyColors.watched.copy(alpha = 0.88f), YummyRadii.smallShape),
    )
}

@Composable
private fun EpisodeCardContent(
    video: VideoVariant,
    episodeViews: Long,
    isDownloaded: Boolean,
    contentAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .graphicsLayer { alpha = contentAlpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EpisodePlayIcon()
        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
            verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
            ) {
                Text(
                    text = video.localizedEpisodeTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isDownloaded) {
                    EpisodeOfflineBadge()
                }
            }
            Text(
                text = listOfNotNull(
                    formatDuration(video.durationSeconds),
                    localizedViews(episodeViews),
                ).joinToString(" \u2022 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpisodePlayIcon() {
    Surface(
        shape = YummyRadii.pillShape,
        color = YummyColors.focus,
        contentColor = YummyColors.onFocus,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier
                .padding(5.dp)
                .size(14.dp),
        )
    }
}

@Composable
private fun EpisodeOfflineBadge() {
    Surface(
        color = YummyColors.offline,
        contentColor = Color.Black,
        shape = YummyRadii.pillShape,
    ) {
        Text(
            text = "OFF",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// EpisodePresentation
@Composable
internal fun List<VideoVariant>.downloadedEpisodeSummary(): String? {
    val allEpisodes = distinctBy { it.matchingEpisodeKey }
    val downloaded = filter { it.isOfflineAvailable }
        .distinctBy { it.matchingEpisodeKey }
        .sortedWith(
            compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
                .thenBy { it.index },
        )
    if (downloaded.isEmpty()) return null

    return if (allEpisodes.isNotEmpty() && downloaded.size >= allEpisodes.size) {
        "${uiText(UiStringKey.Downloaded)} ${downloaded.size} " +
            "${uiText(UiStringKey.Of)} ${allEpisodes.size}"
    } else {
        val labels = downloaded.joinToString(", ") { it.shortEpisodeNumberLabel() }
        "${uiText(UiStringKey.Downloaded)}: $labels"
    }
}

@Composable
internal fun AnimeDetails.effectiveEpisodeSummary(): String {
    return when {
        episodeAired > 0 && episodeCount > 0 ->
            "${uiText(UiStringKey.Released)} $episodeAired " +
                "${uiText(UiStringKey.Of)} $episodeCount"
        episodeAired > 0 -> "${uiText(UiStringKey.Released)} $episodeAired"
        episodeCount > 0 -> "$episodeCount ${localizedEpisodesWord(episodeCount)}"
        episodeSummary.isNotBlank() -> episodeSummary
        else -> ""
    }
}

internal fun VideoVariant.shortEpisodeLabel(episodeWord: String): String {
    return episode.takeIf { it.isNotBlank() }
        ?.let { "$episodeWord $it" }
        ?: episodeTitle.lowercase(Locale.ROOT)
}

internal fun VideoVariant.shortEpisodeNumberLabel(): String {
    return episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: id.toString()
}

internal fun VideoVariant.localizedEpisodeTitle(episodeWord: String, fallback: String): String {
    return episode.takeIf { it.isNotBlank() }?.let { "$episodeWord $it" } ?: fallback
}

@Composable
internal fun VideoVariant.localizedEpisodeTitle(): String {
    return localizedEpisodeTitle(
        episodeWord = uiText(UiStringKey.Episode),
        fallback = uiText(UiStringKey.Episode4da919),
    )
}

@Composable
internal fun VideoVariant.downloadVoiceSubtitle(videos: List<VideoVariant>): String {
    val count = downloadVoiceEpisodeCount(videos)
    return "$count ${localizedEpisodesWord(count)}"
}
