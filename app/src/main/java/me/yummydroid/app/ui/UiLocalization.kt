package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality

internal val LocalUiLanguage = staticCompositionLocalOf { ContentLanguage.Russian }

@Composable
internal fun localizedPluralWord(
    count: Long,
    one: UiStringKey,
    few: UiStringKey,
    many: UiStringKey,
): String {
    val normalized = kotlin.math.abs(count)
    val mod100 = normalized % 100
    val mod10 = normalized % 10
    val key = when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2L..4L -> few
        else -> many
    }
    return uiText(key)
}

@Composable
internal fun localizedEpisodesWord(count: Int): String {
    return localizedPluralWord(
        count = count.toLong(),
        one = UiStringKey.EpisodeOne,
        few = UiStringKey.EpisodeFew,
        many = UiStringKey.EpisodeMany,
    )
}

@Composable
internal fun localizedVotesWord(count: Long): String {
    return localizedPluralWord(
        count = count,
        one = UiStringKey.VoteOne,
        few = UiStringKey.VoteFew,
        many = UiStringKey.VoteMany,
    )
}

@Composable
internal fun BrowseSection.localizedTitle(): String = uiText(
    when (this) {
        BrowseSection.Catalog -> UiStringKey.BrowseCatalog
        BrowseSection.Schedule -> UiStringKey.BrowseSchedule
        BrowseSection.History -> UiStringKey.BrowseHistory
        BrowseSection.Downloads -> UiStringKey.BrowseDownloads
    },
)

internal fun visibleBrowseSections(isAuthorized: Boolean): List<BrowseSection> {
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule, BrowseSection.History)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

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

internal fun PosterCardSize.resolveCatalogColumns(screenWidthDp: Int): Int {
    return when {
        screenWidthDp >= 1200 -> when (this) {
            PosterCardSize.Compact -> 7
            PosterCardSize.Standard -> 5
            PosterCardSize.Large -> 3
        }
        screenWidthDp >= 900 -> when (this) {
            PosterCardSize.Compact -> 6
            PosterCardSize.Standard -> 4
            PosterCardSize.Large -> 2
        }
        screenWidthDp >= 600 -> when (this) {
            PosterCardSize.Compact -> 5
            PosterCardSize.Standard -> 3
            PosterCardSize.Large -> 2
        }
        screenWidthDp >= 430 -> when (this) {
            PosterCardSize.Compact -> 4
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
        else -> when (this) {
            PosterCardSize.Compact -> 3
            PosterCardSize.Standard -> 2
            PosterCardSize.Large -> 1
        }
    }
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
