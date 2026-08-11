package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueStep: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    supportingText: String? = null,
    onValueChange: (Int) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val coercedValue = normalizeSliderValue(value, valueRange, valueStep)
    val shape = YummyRadii.smallShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.sm),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valueText(coercedValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = coercedValue.toFloat(),
                onValueChange = { raw ->
                    onValueChange(normalizeSliderValue(raw.roundToInt(), valueRange, valueStep))
                },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                steps = sliderStepCount(valueRange, valueStep),
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.DirectionLeft -> {
                                onValueChange(
                                    normalizeSliderValue(coercedValue - valueStep, valueRange, valueStep),
                                )
                                true
                            }
                            Key.DirectionRight -> {
                                onValueChange(
                                    normalizeSliderValue(coercedValue + valueStep, valueRange, valueStep),
                                )
                                true
                            }
                            Key.DirectionUp -> {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            }
                            Key.DirectionDown -> {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}

internal fun normalizeSliderValue(value: Int, valueRange: IntRange, valueStep: Int): Int {
    require(!valueRange.isEmpty()) { "valueRange must not be empty" }
    require(valueStep > 0) { "valueStep must be positive" }
    require((valueRange.last - valueRange.first) % valueStep == 0) {
        "valueStep must divide valueRange evenly"
    }
    val clamped = value.coerceIn(valueRange.first, valueRange.last)
    val stepOffset = clamped - valueRange.first
    val normalizedStep = (stepOffset + valueStep / 2) / valueStep
    return valueRange.first + normalizedStep * valueStep
}

internal fun sliderStepCount(valueRange: IntRange, valueStep: Int): Int {
    normalizeSliderValue(valueRange.first, valueRange, valueStep)
    return ((valueRange.last - valueRange.first) / valueStep - 1).coerceAtLeast(0)
}
