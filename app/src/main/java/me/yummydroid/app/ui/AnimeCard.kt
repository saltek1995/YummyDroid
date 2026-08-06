package me.yummydroid.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.zIndex
import me.yummydroid.app.data.Anime

@Composable
internal fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    topEndContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var touchHeld by remember { mutableStateOf(false) }
    var localFocused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val configuration = LocalConfiguration.current
    val touchScaleEnabled = remember(configuration.uiMode) {
        animeCardTouchScaleEnabled(configuration.uiMode)
    }
    val dpadFocused = localFocused && inputModeManager.inputMode != InputMode.Touch
    val expanded = animeCardExpanded(dpadFocused = dpadFocused, touchHeld = touchHeld)
    val scaled = animeCardScaled(touchScaleEnabled = touchScaleEnabled, touchHeld = touchHeld)
    val focusScale = remember(touchScaleEnabled) {
        if (touchScaleEnabled) Animatable(1f) else null
    }
    val resolvedMetaText = remember(metaText, anime.year, anime.type, anime.status) {
        animeCardMetaText(anime, metaText)
    }

    if (focusScale != null) {
        LaunchedEffect(scaled) {
            focusScale.animateTo(
                targetValue = if (scaled) AnimeCardTouchScale else 1f,
                animationSpec = tween(
                    durationMillis = AnimeCardScaleDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .then(if (expanded) Modifier.zIndex(8f) else Modifier)
            .fillMaxWidth()
            .onFocusChanged { state ->
                localFocused = state.isFocused || state.hasFocus
            }
            .animeCardTouchHold(
                enabled = touchScaleEnabled,
                onTouchHeldChange = { touchHeld = it },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val scale = focusScale?.value ?: 1f
        val touchScaleModifier = if (scaled || scale != 1f) {
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
        } else {
            Modifier
        }
        AnimeCardSurface(
            anime = anime,
            metaText = resolvedMetaText,
            expanded = expanded,
            topEndContent = topEndContent,
            modifier = Modifier
                .fillMaxWidth()
                .then(touchScaleModifier),
            focusBorderActive = dpadFocused,
        )
    }
}
