package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import me.yummydroid.app.data.VideoVariant

internal class DetailsHeroActionFocus(
    private val primaryRequester: FocusRequester,
    private val gridState: VisualFocusGridState?,
) {
    fun primaryModifier(): Modifier {
        return if (gridState == null) {
            Modifier.focusRequester(primaryRequester)
        } else {
            actionModifier(DetailsHeroFocusIndex.PrimaryAction)
        }
    }

    fun actionModifier(index: Int): Modifier {
        val state = gridState ?: return Modifier
        return Modifier.visualFocusGridItem(
            state = state,
            index = index,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroActions,
            blockEntryIndex = index,
        )
    }

    fun resetModifier(watchVideo: VideoVariant?): Modifier = when {
        gridState != null -> actionModifier(DetailsHeroFocusIndex.ResetAction)
        watchVideo == null -> Modifier.focusRequester(primaryRequester)
        else -> Modifier
    }
}

@Composable
internal fun rememberDetailsHeroActionFocus(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    hasWatchProgress: Boolean,
    externalPrimaryFocusRequester: FocusRequester?,
    focusRequestNonce: Long,
    heroFocusGridState: VisualFocusGridState?,
): DetailsHeroActionFocus {
    val primaryVideoId = watchVideo?.id ?: -1L
    val resumeVideoId = resumeTarget?.video?.id ?: -1L
    val internalRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryIndex = detailsHeroPrimaryActionFocusIndex(watchVideo)
    val primaryRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(primaryIndex)
        ?: internalRequester
    val inputModeManager = LocalInputModeManager.current

    LaunchedEffect(focusRequestNonce, primaryVideoId, resumeVideoId, hasWatchProgress) {
        if (focusRequestNonce <= 0L || inputModeManager.inputMode == InputMode.Touch) {
            return@LaunchedEffect
        }
        repeat(4) {
            withFrameNanos { }
            if (primaryRequester.requestFocusSafely()) return@LaunchedEffect
        }
    }
    return DetailsHeroActionFocus(primaryRequester, heroFocusGridState)
}
