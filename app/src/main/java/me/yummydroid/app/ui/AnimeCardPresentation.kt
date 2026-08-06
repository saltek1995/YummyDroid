package me.yummydroid.app.ui

import android.content.res.Configuration
import me.yummydroid.app.data.Anime

internal const val AnimeCardPosterAspectRatio = 2f / 3f
internal const val AnimeCardTouchScale = 1.035f
internal const val AnimeCardScaleDurationMillis = 90
internal const val AnimeCardCollapsedTitleLines = 2
internal const val AnimeCardExpandedTitleLines = 8

internal fun animeCardTouchScaleEnabled(uiMode: Int): Boolean {
    return (uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun animeCardExpanded(dpadFocused: Boolean, touchHeld: Boolean): Boolean {
    return dpadFocused || touchHeld
}

internal fun animeCardScaled(touchScaleEnabled: Boolean, touchHeld: Boolean): Boolean {
    return touchScaleEnabled && touchHeld
}

internal fun animeCardMetaText(anime: Anime, overrideText: String?): String {
    return overrideText ?: anime.meta
}
