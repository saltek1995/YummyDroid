package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.yummydroid.app.R
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.formatCompactCount
import me.yummydroid.app.localizedString

private data class CompactCountSuffixes(
    val thousand: String,
    val million: String,
)

private val CompactCountSuffixCache = mutableMapOf<ContentLanguage, CompactCountSuffixes>()

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
internal fun localizedViews(value: Long): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val suffixes = remember(context, language) {
        compactCountSuffixes(context, language)
    }
    return remember(language, value, suffixes) {
        formatCompactCount(
            value = value,
            thousandSuffix = suffixes.thousand,
            millionSuffix = suffixes.million,
        )
    }
}

private fun compactCountSuffixes(
    context: android.content.Context,
    language: ContentLanguage,
): CompactCountSuffixes {
    synchronized(CompactCountSuffixCache) {
        CompactCountSuffixCache[language]?.let { return it }
    }
    val created = CompactCountSuffixes(
        thousand = context.localizedString(R.string.ui_number_thousand_suffix, language),
        million = context.localizedString(R.string.ui_number_million_suffix, language),
    )
    synchronized(CompactCountSuffixCache) {
        return CompactCountSuffixCache.getOrPut(language) { created }
    }
}

@Composable
internal fun localizedByteSize(bytes: Long): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, bytes) {
        formatByteSize(
            bytes = bytes,
            byteUnit = context.localizedString(R.string.ui_unit_byte, language),
            kilobyteUnit = context.localizedString(R.string.ui_unit_kilobyte, language),
            megabyteUnit = context.localizedString(R.string.ui_unit_megabyte, language),
            gigabyteUnit = context.localizedString(R.string.ui_unit_gigabyte, language),
        )
    }
}
