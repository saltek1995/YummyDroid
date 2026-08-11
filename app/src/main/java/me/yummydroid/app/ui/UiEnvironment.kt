package me.yummydroid.app.ui

import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.R
import me.yummydroid.app.baseUiDensityDpi
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.UserProfile

// CatalogDisplaySizing
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

// ConsumeUnhandledPointerInput
internal fun Modifier.consumeUnhandledPointerInput(key: Any = Unit): Modifier {
    return pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { change ->
                    if (!change.isConsumed) {
                        change.consume()
                    }
                }
            }
        }
    }
}

// HomeBackToTopHandler
internal class HomeBackToTopHandler(
    val section: BrowseSection,
    private val canHandle: () -> Boolean,
    private val handle: (withFocus: Boolean) -> Boolean,
) {
    fun canHandleBackToTop(): Boolean = canHandle()

    fun handleBackToTop(withFocus: Boolean): Boolean = handle(withFocus)
}

// SiteNavigation
internal fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/users/id$id"
}

internal fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/" + path.trim().trimStart('/')
}

internal fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()))
    }.onFailure {
        Toast.makeText(this, getString(R.string.ui_could_not_open_the_site), Toast.LENGTH_SHORT).show()
    }
}

// WindowSize
@Composable
internal fun currentWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        DpSize(containerSize.width.toDp(), containerSize.height.toDp())
    }
}

@Composable
internal fun currentResponsiveWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    return responsiveWindowSizeDp(
        widthPixels = containerSize.width,
        heightPixels = containerSize.height,
        densityDpi = LocalContext.current.baseUiDensityDpi(),
    )
}

internal fun responsiveWindowSizeDp(
    widthPixels: Int,
    heightPixels: Int,
    densityDpi: Int,
): DpSize {
    if (widthPixels <= 0 || heightPixels <= 0 || densityDpi <= 0) return DpSize.Zero
    val dpPerPixel = DisplayMetrics.DENSITY_DEFAULT.toFloat() / densityDpi
    return DpSize(
        width = (widthPixels * dpPerPixel).dp,
        height = (heightPixels * dpPerPixel).dp,
    )
}
