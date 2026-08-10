package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.dpadClickable

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingScale(
    selected: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leftExitRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
) {
    val shape = RoundedCornerShape(8.dp)
    var focusedRating by remember { mutableStateOf<Int?>(null) }
    val previewRating = focusedRating
    val filledRating = previewRating ?: selected
    val fillAlpha = if (previewRating != null) 0.24f else 0.16f
    val internalFocusGridState = rememberVisualFocusGridState(size = 10)
    val effectiveFocusGridState = focusGridState ?: internalFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        shape = shape,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            (1..10).forEach { value ->
                val active = filledRating != null && value <= filledRating
                val siteScaleColor = ratingScaleColorForValue(value)
                val itemShape = when (value) {
                    1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                    10 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .visualFocusGridItem(
                            state = effectiveFocusGridState,
                            index = effectiveFocusIndexOffset + value - 1,
                            vertical = focusGridState != null,
                            leftExit = leftExitRequester,
                        )
                        .background(
                            color = if (active) siteScaleColor.copy(alpha = fillAlpha) else Color.Transparent,
                            shape = itemShape,
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedRating = value
                            } else if (focusedRating == value) {
                                focusedRating = null
                            }
                        }
                        .dpadClickable(itemShape) { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "${uiText(UiStringKey.Rating)} $value",
                        modifier = Modifier.size(19.dp),
                        tint = if (active) siteScaleColor else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (value < 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                    )
                }
            }
        }
    }
}

internal fun ratingScaleColorForValue(value: Int): Color {
    return ratingColorForSiteScale(value.coerceIn(1, 10).toDouble())
}
