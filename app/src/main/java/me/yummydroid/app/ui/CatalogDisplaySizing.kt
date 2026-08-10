package me.yummydroid.app.ui

import me.yummydroid.app.data.PosterCardSize

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
