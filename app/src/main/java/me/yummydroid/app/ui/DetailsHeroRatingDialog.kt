package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.yummydroid.app.InputAction
import me.yummydroid.app.data.AnimeRatingSummary

@Composable
internal fun DetailsHeroRatingDialogInputEffect(
    open: Boolean,
    onDismiss: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val ratingDialogInputHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && open) {
            onDismiss()
            true
        } else {
            false
        }
    }
    DisposableEffect(open, onRegisterModalInputActionHandler) {
        if (open) {
            onRegisterModalInputActionHandler { action -> ratingDialogInputHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
}

@Composable
internal fun DetailsHeroRatingDialog(
    open: Boolean,
    detailsId: Long,
    ratingSummary: AnimeRatingSummary?,
    onDismiss: () -> Unit,
    onSelected: (Int?) -> Unit,
) {
    if (!open || ratingSummary == null) return
    val inputModeManager = LocalInputModeManager.current
    val dialogFocusGridState = rememberVisualFocusGridState(
        size = 10,
        key = detailsId to ratingSummary.userRating,
    )
    LaunchedEffect(dialogFocusGridState, inputModeManager.inputMode) {
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        withFrameNanos { }
        val focusIndex = ((ratingSummary.userRating ?: 1).coerceIn(1, 10) - 1)
        dialogFocusGridState.requester(focusIndex)?.requestFocusSafely()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .yummyDialogMotion(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Text(
                    text = uiText(UiStringKey.RateAnime),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                RatingScale(
                    selected = ratingSummary.userRating,
                    onSelected = onSelected,
                    focusGridState = dialogFocusGridState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
