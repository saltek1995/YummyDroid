package me.yummydroid.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

// ActionSurfaces
internal fun yummyActionSurfaceColor(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
): Color {
    return when {
        !enabled -> YummyColors.actionSurfaceDisabled
        focused -> YummyColors.focus
        selected -> YummyColors.actionSurfaceSelected
        else -> YummyColors.actionSurface
    }
}

@Composable
internal fun yummyActionContentColor(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
    destructive: Boolean = false,
): Color {
    return when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
        focused -> YummyColors.onFocus
        destructive -> MaterialTheme.colorScheme.error
        selected -> YummyColors.focus
        else -> MaterialTheme.colorScheme.onSurface
    }
}

internal fun yummyActionBorder(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
): BorderStroke {
    val color = when {
        !enabled -> YummyColors.actionBorder.copy(alpha = 0.10f)
        focused -> Color.Transparent
        selected -> YummyColors.focus.copy(alpha = 0.30f)
        else -> YummyColors.actionBorder.copy(alpha = 0.18f)
    }
    return BorderStroke(1.dp, color)
}

// SurfaceRoles
internal enum class YummySurfaceRole {
    Panel,
    Row,
    ActiveRow,
}

@Composable
internal fun yummySurfaceColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.Panel -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.subtleSurface)
        YummySurfaceRole.Row -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.rowSurface)
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    }
}

@Composable
internal fun yummySurfaceContentColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun yummySurfaceBorder(role: YummySurfaceRole): BorderStroke {
    val color = when (role) {
        YummySurfaceRole.ActiveRow -> Color.Transparent
        YummySurfaceRole.Panel -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        YummySurfaceRole.Row -> Color.Transparent
    }
    return BorderStroke(1.dp, color)
}

// YummyAlpha
internal object YummyAlpha {
    const val subtleSurface = 0.42f
    const val rowSurface = 0.92f
    const val disabledSurface = 0.58f
    const val badgeSurface = 0.82f
}

// YummyColors
internal object YummyColors {
    val focus = Color(0xFFFFB454)
    val onFocus = Color(0xFF211200)
    val focusOverlay = Color(0xFFFFB454)
    val rating = Color(0xFFFFB454)
    val offline = Color(0xFFB8FF2D)
    val watched = Color(0xFF3DFF9D)
    val actionSurface = Color(0xFF142238)
    val actionSurfaceSelected = Color(0xFF1A304B)
    val actionSurfaceDisabled = Color(0xFF10192A)
    val actionBorder = Color(0xFF42658A)
}

// YummyColorScheme
internal val YummyDarkColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF211200),
    primaryContainer = Color(0xFF6A4209),
    onPrimaryContainer = Color(0xFFFFE1B1),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF001318),
    secondaryContainer = Color(0xFF063E4A),
    onSecondaryContainer = Color(0xFFC7F7FF),
    tertiary = Color(0xFFFF40D6),
    onTertiary = Color(0xFF26001D),
    tertiaryContainer = Color(0xFF55204B),
    onTertiaryContainer = Color(0xFFFFD6F6),
    background = Color(0xFF121926),
    onBackground = Color(0xFFF3F8FF),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF17243A),
    onSurfaceVariant = Color(0xFFC9D7EA),
    outline = Color(0xFF48617D),
    outlineVariant = Color(0xFF263B55),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2B050B),
    errorContainer = Color(0xFF5E1420),
    onErrorContainer = Color(0xFFFFD7DC),
)

// YummyDroidTheme
@Composable
fun YummyDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = yummyDroidColorScheme(),
        content = content,
    )
}

internal fun yummyDroidColorScheme() = YummyDarkColors

// YummyRadii
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

// YummySizes
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

// YummySpacing
internal object YummySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

// YummyBackground
internal data class YummyBackgroundTextureSpec(
    val size: IntSize,
    val scale: Float,
)

internal fun Modifier.yummyAppBackground(): Modifier = drawWithCache {
    val outputSize = IntSize(
        width = size.width.roundToInt().coerceAtLeast(1),
        height = size.height.roundToInt().coerceAtLeast(1),
    )
    val texture = yummyBackgroundTextureSpec(outputSize.width, outputSize.height)
    val backgroundImage = yummyBackgroundImage(
        widthPx = texture.size.width,
        heightPx = texture.size.height,
        density = density * texture.scale,
    )

    onDrawBehind {
        drawImage(
            image = backgroundImage,
            srcOffset = IntOffset.Zero,
            srcSize = texture.size,
            dstOffset = IntOffset.Zero,
            dstSize = outputSize,
            filterQuality = FilterQuality.Low,
        )
    }
}

internal fun yummyBackgroundTextureSpec(
    widthPx: Int,
    heightPx: Int,
): YummyBackgroundTextureSpec {
    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)
    val scale = minOf(
        1f,
        YummyBackgroundMaxTextureWidthPx / safeWidth,
        YummyBackgroundMaxTextureHeightPx / safeHeight,
    )
    return YummyBackgroundTextureSpec(
        size = IntSize(
            width = (safeWidth * scale).roundToInt().coerceAtLeast(1),
            height = (safeHeight * scale).roundToInt().coerceAtLeast(1),
        ),
        scale = scale,
    )
}

private const val YummyBackgroundMaxTextureWidthPx = 960f
private const val YummyBackgroundMaxTextureHeightPx = 540f

// YummyBackgroundBitmap
private data class YummyBackgroundBitmapKey(
    val widthPx: Int,
    val heightPx: Int,
    val densityKey: Int,
)

private data class YummyBackgroundPattern(
    val density: Float,
    val width: Float,
    val height: Float,
    val lineSpacing: Float = 34f * density,
    val lineShift: Float = 128f * density,
    val lineStroke: Float = density,
    val grainStepX: Float = 58f * density,
    val grainStepY: Float = 46f * density,
    val grainSize: Float = density.coerceAtLeast(1f),
    val accentGrainSize: Float = (1.4f * density).coerceAtLeast(1f),
)

private val bitmapCacheLock = Any()
private val bitmapCache = LruCache<YummyBackgroundBitmapKey, ImageBitmap>(3)

internal fun yummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val key = YummyBackgroundBitmapKey(widthPx, heightPx, (density * 100f).roundToInt())
    synchronized(bitmapCacheLock) {
        bitmapCache.get(key)?.let { return it }
    }
    val created = createYummyBackgroundImage(widthPx, heightPx, density)
    synchronized(bitmapCacheLock) {
        bitmapCache.put(key, created)
    }
    return created
}

private fun createYummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pattern = YummyBackgroundPattern(density, widthPx.toFloat(), heightPx.toFloat())
    canvas.drawBaseGradient(paint, pattern)
    canvas.drawDiagonalLines(paint, pattern)
    canvas.drawGrain(paint, pattern)
    canvas.drawVerticalShade(paint, pattern)
    return bitmap.asImageBitmap()
}

private fun Canvas.drawBaseGradient(paint: Paint, pattern: YummyBackgroundPattern) {
    paint.shader = LinearGradient(
        0f,
        0f,
        pattern.width,
        pattern.height,
        intArrayOf(
            Color(0xFF121926).toArgb(),
            Color(0xFF1F2A3A).toArgb(),
            Color(0xFF133140).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    drawRect(0f, 0f, pattern.width, pattern.height, paint)
}

private fun Canvas.drawDiagonalLines(paint: Paint, pattern: YummyBackgroundPattern) {
    val diagonalLine = Color.White.copy(alpha = 0.035f).toArgb()
    val cyanLine = Color(0xFF00E5FF).copy(alpha = 0.030f).toArgb()
    paint.shader = null
    paint.strokeWidth = pattern.lineStroke
    paint.style = Paint.Style.STROKE
    var lineX = -pattern.height - pattern.lineShift
    var lineIndex = 0
    while (lineX < pattern.width + pattern.lineShift) {
        paint.color = if (lineIndex % 5 == 0) cyanLine else diagonalLine
        drawLine(lineX, pattern.height, lineX + pattern.height + pattern.lineShift, 0f, paint)
        lineX += pattern.lineSpacing
        lineIndex++
    }
}

private fun Canvas.drawGrain(paint: Paint, pattern: YummyBackgroundPattern) {
    val grainColor = Color(0xFFEAF2FF).copy(alpha = 0.10f).toArgb()
    val accentColor = YummyColors.focus.copy(alpha = 0.18f).toArgb()
    paint.style = Paint.Style.FILL
    var row = 0
    var y = 30f * pattern.density
    while (y < pattern.height) {
        drawGrainRow(paint, pattern, row, y, grainColor, accentColor)
        y += pattern.grainStepY
        row++
    }
}

private fun Canvas.drawGrainRow(
    paint: Paint,
    pattern: YummyBackgroundPattern,
    row: Int,
    y: Float,
    grainColor: Int,
    accentColor: Int,
) {
    var column = 0
    var x = 26f * pattern.density + if (row % 2 == 0) 0f else pattern.grainStepX / 2f
    while (x < pattern.width) {
        if ((row + column) % 4 == 0) {
            paint.color = grainColor
            drawRect(x, y, x + pattern.grainSize, y + pattern.grainSize, paint)
        }
        if ((row * 5 + column * 3) % 19 == 0) {
            paint.color = accentColor
            val accentX = x + pattern.grainSize * 3f
            val accentY = y + pattern.grainSize
            drawRect(
                accentX,
                accentY,
                accentX + pattern.accentGrainSize,
                accentY + pattern.accentGrainSize,
                paint,
            )
        }
        x += pattern.grainStepX
        column++
    }
}

private fun Canvas.drawVerticalShade(paint: Paint, pattern: YummyBackgroundPattern) {
    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        pattern.height,
        intArrayOf(
            Color.White.copy(alpha = 0.020f).toArgb(),
            Color.Transparent.toArgb(),
            Color.Black.copy(alpha = 0.12f).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    drawRect(0f, 0f, pattern.width, pattern.height, paint)
}
