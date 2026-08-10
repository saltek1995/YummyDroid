package me.yummydroid.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

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
