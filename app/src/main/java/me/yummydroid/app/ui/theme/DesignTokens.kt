package me.yummydroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object YummyAlpha {
    const val subtleSurface = 0.64f
    const val rowSurface = 0.86f
    const val disabledSurface = 0.58f
    const val badgeSurface = 0.82f
    const val chromeSurface = 0.92f
}

internal object YummyColors {
    val focus = Color(0xFF00E5FF)
    val focusOverlay = Color(0xFF00E5FF)
    val offline = Color(0xFFB8FF2D)
    val watched = Color(0xFF3DFF9D)
    val neonPink = Color(0xFFFF40D6)
    val neonLime = Color(0xFFB8FF2D)
    val deepPanel = Color(0xFF0D1628)
    val liftedPanel = Color(0xFF17243A)
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
    val animeCardInfoHeight = 92.dp
    val animeTitleHeight = 42.dp
    val animeMetaHeight = 18.dp
    val episodeHeight = 86.dp
    val badgeIcon = 15.dp
}
