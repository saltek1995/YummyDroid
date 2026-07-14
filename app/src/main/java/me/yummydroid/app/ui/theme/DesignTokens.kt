package me.yummydroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object YummyAlpha {
    const val subtleSurface = 0.18f
    const val rowSurface = 0.42f
    const val secondaryButton = 0.88f
    const val disabledSurface = 0.54f
    const val badgeSurface = 0.82f
}

internal object YummyColors {
    val focus = Color(0xFF9BE7FF)
    val offline = Color(0xFF48D882)
}

internal object YummyRadii {
    val small = 8.dp
    val medium = 12.dp
    val pill = 50.dp

    val smallShape
        get() = RoundedCornerShape(small)

    val mediumShape
        get() = RoundedCornerShape(medium)

    val pillShape
        get() = RoundedCornerShape(pill)
}

internal object YummySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

internal object YummySizes {
    val tabHeight = 48.dp
    val dialogButtonHeight = 40.dp
    val dialogButtonMinWidth = 84.dp
    val primaryDialogButtonMinWidth = 104.dp
    val compactIconButton = 36.dp
    val actionIcon = 20.dp
    val episodePlayIcon = 22.dp
    val animeCardInfoHeight = 92.dp
    val animeTitleHeight = 42.dp
    val animeMetaHeight = 18.dp
    val episodeHeight = 86.dp
    val episodeWatchedHeight = 100.dp
    val badgeIcon = 15.dp
}
