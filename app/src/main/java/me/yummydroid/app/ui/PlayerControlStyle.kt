package me.yummydroid.app.ui

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.widget.TextViewCompat
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R

internal fun PlayerView.applyPlayerControlIconColors() {
    playerPrimaryIconIds.forEach { id ->
        findViewById<ImageButton>(id)?.imageTintList = playerControlContentColors(active = false)
    }
}

internal fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    applyPlayerToggleState(active)
}

internal fun ImageButton.applyPlayerIconControl(
    @DrawableRes iconResId: Int,
    label: CharSequence,
    active: Boolean = false,
) {
    contentDescription = label
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    setImageResource(iconResId)
    imageTintList = playerControlContentColors(active)
}

internal fun TextView.applyPlayerToggleState(active: Boolean) {
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    val colors = playerControlContentColors(active)
    setTextColor(colors)
    TextViewCompat.setCompoundDrawableTintList(this, colors)
}

internal fun TextView.applyPlayerQualityControl(
    title: String,
    label: CharSequence,
) {
    text = title
    contentDescription = label
    applyPlayerToggleState(active = false)
}

internal fun playerControlContentColors(active: Boolean): ColorStateList {
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            0x66F3F6FA,
            if (active) PLAYER_ACCENT_COLOR else PLAYER_CONTROL_CONTENT_COLOR,
        ),
    )
}

internal fun List<QualityOption>.selectedQualityControlText(selectedQualityKey: String?): String {
    val selected = firstOrNull { it.matchesSelectedQualityKey(selectedQualityKey) }
    val height = selected?.height?.takeIf { it > 0 }
    if (height != null) return "${height}p"
    return selected?.label?.compactQualityControlText()
        ?: selectedQualityKey?.compactQualityControlText()
        ?: PLAYER_AUTO_QUALITY_LABEL
}

private fun String.compactQualityControlText(): String? {
    val explicitHeight = compactQualityHeightPattern
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
    if (explicitHeight != null) return "${explicitHeight}p"
    if (contains("auto", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    if (contains("adaptive", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    return null
}

private val compactQualityHeightPattern =
    Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)\s*p""")

internal val PLAYER_ACCENT_COLOR: Int = 0xFFFFB454.toInt()
internal val PLAYER_ACCENT_CONTENT_COLOR: Int = 0xFF1B1305.toInt()
internal val PLAYER_CONTROL_CONTENT_COLOR: Int = 0xFFF3F6FA.toInt()
internal const val PLAYER_AUTO_QUALITY_LABEL = "AUTO"

internal fun View.setPlayerControlEnabled(enabled: Boolean) {
    isEnabled = enabled
    isFocusable = enabled
    alpha = if (enabled) 1f else 0.45f
}
