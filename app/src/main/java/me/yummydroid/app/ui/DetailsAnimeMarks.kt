package me.yummydroid.app.ui

import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.yummydroid.app.data.UserAnimeListMark

internal fun UserAnimeListMark.icon() = when (this) {
    UserAnimeListMark.Watching -> Icons.Default.RemoveRedEye
    UserAnimeListMark.Planned -> Icons.Default.Cloud
    UserAnimeListMark.Watched -> Icons.Default.Flag
    UserAnimeListMark.Postponed -> Icons.Default.Schedule
    UserAnimeListMark.Dropped -> Icons.Default.VisibilityOff
}

@Composable
internal fun UserAnimeListMark.localizedTitle(): String = uiText(
    when (this) {
        UserAnimeListMark.Watching -> UiStringKey.Watching
        UserAnimeListMark.Planned -> UiStringKey.Planned
        UserAnimeListMark.Watched -> UiStringKey.Watched
        UserAnimeListMark.Postponed -> UiStringKey.Postponed
        UserAnimeListMark.Dropped -> UiStringKey.Dropped
    },
)

internal fun UserAnimeListMark.siteColor() = when (this) {
    UserAnimeListMark.Watching -> Color(0xFFFF5E66)
    UserAnimeListMark.Planned -> Color(0xFFB66DFF)
    UserAnimeListMark.Watched -> Color(0xFF35D47A)
    UserAnimeListMark.Postponed -> Color(0xFFFFB71B)
    UserAnimeListMark.Dropped -> Color(0xFF9EA3AA)
}

internal val favoriteMarkColor = Color(0xFFC94DDB)
