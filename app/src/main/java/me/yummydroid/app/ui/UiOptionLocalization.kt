package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import java.util.Locale
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality

@Composable
internal fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

@Composable
internal fun PreferredQuality.localizedTitle(): String = when (this) {
    PreferredQuality.Auto -> uiText(UiStringKey.Auto)
    else -> title
}

@Composable
internal fun PlayerDecoderMode.localizedTitle(): String = when (this) {
    PlayerDecoderMode.Auto -> uiText(UiStringKey.Auto)
    PlayerDecoderMode.Hardware -> uiText(UiStringKey.Hardware)
    PlayerDecoderMode.Software -> uiText(UiStringKey.Software)
}

@Composable
internal fun PlayerBufferPreset.localizedTitle(): String = when (this) {
    PlayerBufferPreset.Compact -> uiText(UiStringKey.Compact)
    PlayerBufferPreset.Standard -> uiText(UiStringKey.Standard)
    PlayerBufferPreset.Large -> uiText(UiStringKey.Large)
    PlayerBufferPreset.Maximum -> uiText(UiStringKey.Maximum)
}

@Composable
internal fun PosterCardSize.localizedTitle(): String = when (this) {
    PosterCardSize.Compact -> uiText(UiStringKey.Compact)
    PosterCardSize.Standard -> uiText(UiStringKey.Standard)
    PosterCardSize.Large -> uiText(UiStringKey.Large)
}

@Composable
internal fun ContentLanguage.localizedTitle(): String = when (this) {
    ContentLanguage.Russian -> uiText(UiStringKey.LanguageRussian)
    ContentLanguage.English -> uiText(UiStringKey.LanguageEnglish)
    ContentLanguage.Ukrainian -> uiText(UiStringKey.LanguageUkrainian)
}

@Composable
internal fun AnimeSort.localizedTitle(): String = uiText(
    when (this) {
        AnimeSort.Rating -> UiStringKey.Rating5709e2
        AnimeSort.RatingCounters -> UiStringKey.Votes
        AnimeSort.Views -> UiStringKey.Views
        AnimeSort.Year -> UiStringKey.New
        AnimeSort.Top -> UiStringKey.Top
        AnimeSort.Title -> UiStringKey.AZ
        AnimeSort.Id -> UiStringKey.RecentlyAdded
        AnimeSort.Random -> UiStringKey.Random
    },
)

@Composable
internal fun FilterOption.localizedTitle(): String = when (value) {
    "released" -> uiText(UiStringKey.Released)
    "ongoing" -> uiText(UiStringKey.Ongoing)
    "announcement" -> uiText(UiStringKey.Announcements)
    "winter" -> uiText(UiStringKey.Winter)
    "spring" -> uiText(UiStringKey.Spring)
    "summer" -> uiText(UiStringKey.Summer)
    "fall" -> uiText(UiStringKey.Fall)
    "dubbing" -> uiText(UiStringKey.FullDubbing)
    "multivoice" -> uiText(UiStringKey.MultiVoice)
    "duet" -> uiText(UiStringKey.TwoVoice)
    "onevoice" -> uiText(UiStringKey.SingleVoice)
    "subtitles" -> uiText(UiStringKey.Subtitles)
    "0" -> uiText(UiStringKey.Watching)
    "1" -> uiText(UiStringKey.Planned)
    "2" -> uiText(UiStringKey.Watched)
    "3" -> uiText(UiStringKey.Dropped)
    "4" -> uiText(UiStringKey.Favorites)
    "5" -> uiText(UiStringKey.Postponed)
    else -> title
}

internal fun ContentLanguage.voiceRecognizerTag(): String = when (this) {
    ContentLanguage.Russian -> "ru-RU"
    ContentLanguage.English -> "en-US"
    ContentLanguage.Ukrainian -> "uk-UA"
}

internal fun ContentLanguage.uiLocale(): Locale {
    return locale
}
