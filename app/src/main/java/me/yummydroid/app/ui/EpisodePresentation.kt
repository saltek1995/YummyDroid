package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import java.util.Locale
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadVoiceEpisodeCount
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.matchingEpisodeKey

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
