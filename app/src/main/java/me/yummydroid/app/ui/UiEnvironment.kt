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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer

// CatalogDisplaySizing
internal fun PosterCardSize.resolveCatalogColumns(screenWidthDp: Int): Int {
    return CatalogWidthTier.forScreenWidth(screenWidthDp).columns(this)
}

private enum class CatalogWidthTier(
    private val compactColumns: Int,
    private val standardColumns: Int,
    private val largeColumns: Int,
) {
    Phone(3, 2, 1),
    Narrow(4, 2, 1),
    Medium(5, 3, 2),
    Wide(6, 4, 2),
    ExtraWide(7, 5, 3),
    ;

    fun columns(cardSize: PosterCardSize): Int = when (cardSize) {
        PosterCardSize.Compact -> compactColumns
        PosterCardSize.Standard -> standardColumns
        PosterCardSize.Large -> largeColumns
    }

    companion object {
        fun forScreenWidth(screenWidthDp: Int): CatalogWidthTier = when {
            screenWidthDp >= 1200 -> ExtraWide
            screenWidthDp >= 900 -> Wide
            screenWidthDp >= 600 -> Medium
            screenWidthDp >= 430 -> Narrow
            else -> Phone
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

internal const val YUMMY_FADE_IN_MS = 260
internal const val YUMMY_FADE_OUT_MS = 220

@Composable
internal fun Modifier.yummyAppearMotion(
    visible: Boolean = true,
    scaleFrom: Float = 0.99f,
): Modifier {
    val progress = remember { Animatable(0f) }
    var layerActive by remember { mutableStateOf(true) }
    LaunchedEffect(visible) {
        layerActive = true
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) YUMMY_FADE_IN_MS else YUMMY_FADE_OUT_MS,
                easing = FastOutSlowInEasing,
            ),
        )
        if (visible) {
            layerActive = false
        }
    }
    if (visible && !layerActive && progress.value >= 0.999f) {
        return this
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value
        val scale = scaleFrom + ((1f - scaleFrom) * value)
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun Modifier.yummyDialogMotion(): Modifier = yummyAppearMotion(scaleFrom = 0.975f)
