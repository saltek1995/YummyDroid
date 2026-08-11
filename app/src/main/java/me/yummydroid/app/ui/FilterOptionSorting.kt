package me.yummydroid.app.ui

import java.text.Collator
import java.util.Locale
import me.yummydroid.app.data.FilterOption

internal fun List<FilterOption>.sortedByTitle(
    locale: Locale = Locale.getDefault(),
): List<FilterOption> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return sortedWith { first, second ->
        val titleComparison = collator.compare(first.title, second.title)
        if (titleComparison != 0) titleComparison else first.value.compareTo(second.value)
    }
}
