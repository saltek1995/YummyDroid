package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.math.abs
import me.yummydroid.app.InputAction

internal enum class ScreenshotViewerCommand {
    Close,
    Previous,
    Next,
    Ignore,
}

internal fun InputAction.toScreenshotViewerCommand(): ScreenshotViewerCommand {
    return when (this) {
        InputAction.Back,
        InputAction.Up,
        InputAction.Down -> ScreenshotViewerCommand.Close
        InputAction.Left -> ScreenshotViewerCommand.Previous
        InputAction.Right -> ScreenshotViewerCommand.Next
        InputAction.Confirm,
        InputAction.Play,
        InputAction.Pause,
        InputAction.PlayPause,
        InputAction.PreviousEpisode,
        InputAction.NextEpisode -> ScreenshotViewerCommand.Ignore
    }
}

internal fun Key.toScreenshotViewerCommand(): ScreenshotViewerCommand {
    return when (this) {
        Key.DirectionLeft -> ScreenshotViewerCommand.Previous
        Key.DirectionRight -> ScreenshotViewerCommand.Next
        Key.DirectionUp,
        Key.DirectionDown,
        Key.Escape,
        Key.NavigateOut -> ScreenshotViewerCommand.Close
        else -> ScreenshotViewerCommand.Ignore
    }
}

internal fun screenshotViewerTargetPage(
    command: ScreenshotViewerCommand,
    currentPage: Int,
    lastPage: Int,
): Int? {
    return when (command) {
        ScreenshotViewerCommand.Previous -> (currentPage - 1).takeIf { currentPage > 0 }
        ScreenshotViewerCommand.Next -> (currentPage + 1).takeIf { currentPage < lastPage }
        ScreenshotViewerCommand.Close,
        ScreenshotViewerCommand.Ignore -> null
    }
}

internal fun shouldDismissScreenshotViewer(verticalDrag: Float): Boolean {
    return abs(verticalDrag) > SCREENSHOT_DISMISS_DRAG_THRESHOLD
}

private const val SCREENSHOT_DISMISS_DRAG_THRESHOLD = 120f
