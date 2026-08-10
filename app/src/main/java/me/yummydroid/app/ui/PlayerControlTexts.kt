package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import java.util.Locale

internal data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val source: String,
    val quality: String,
    val subtitles: String,
    val subtitlesOff: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
    val episode: String,
    val episodeFallback: String,
    val of: String,
    val downloaded: String,
)

internal val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Watch",
    watch = "Watch",
    voice = "Voice",
    source = "Source",
    quality = "Quality",
    subtitles = "Subtitles",
    subtitlesOff = "Off",
    subscription = "Subscription",
    subscribed = "Subscribed",
    skip = "Skip",
    episode = "Episode",
    episodeFallback = "Episode",
    of = "of",
    downloaded = "downloaded",
)

@Composable
internal fun rememberPlayerControlTexts(): PlayerControlTexts {
    return PlayerControlTexts(
        title = uiText(UiStringKey.Watch),
        watch = uiText(UiStringKey.Watch5af041),
        voice = uiText(UiStringKey.Voice),
        source = uiText(UiStringKey.Source),
        quality = uiText(UiStringKey.Quality),
        subtitles = uiText(UiStringKey.Subtitles),
        subtitlesOff = uiText(UiStringKey.Off),
        subscription = uiText(UiStringKey.Subscription),
        subscribed = uiText(UiStringKey.Subscribed),
        skip = uiText(UiStringKey.Skip),
        episode = uiText(UiStringKey.Episode),
        episodeFallback = uiText(UiStringKey.Episode4da919),
        of = uiText(UiStringKey.Of),
        downloaded = uiText(UiStringKey.DownloadedBc4f6a).lowercase(Locale.ROOT),
    )
}

