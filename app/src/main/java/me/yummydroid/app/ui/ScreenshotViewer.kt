package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import me.yummydroid.app.InputAction

// ScreenshotViewerDialog
@Composable
internal fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    if (screenshots.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size },
    )
    val focusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    fun performCommand(command: ScreenshotViewerCommand): Boolean {
        return when (command) {
            ScreenshotViewerCommand.Close -> {
                if (!isClosing) {
                    isClosing = true
                    onDismiss()
                }
                true
            }
            ScreenshotViewerCommand.Previous,
            ScreenshotViewerCommand.Next -> {
                moveScreenshotPage(command, pagerState, screenshots.lastIndex, scope)
                true
            }
            ScreenshotViewerCommand.Ignore -> false
        }
    }

    val inputActionHandler by rememberUpdatedState { action: InputAction ->
        performCommand(action.toScreenshotViewerCommand())
    }

    DisposableEffect(onRegisterInputActionHandler) {
        onRegisterInputActionHandler { action -> inputActionHandler(action) }
        onDispose { onRegisterInputActionHandler(null) }
    }

    LaunchedEffect(inputModeManager.inputMode) {
        if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        focusRequester.requestFocusSafely()
    }

    ScreenshotViewerSurface(
        screenshots = screenshots,
        pagerState = pagerState,
        focusRequester = focusRequester,
        onCommand = ::performCommand,
    )
}

@Composable
private fun ScreenshotViewerSurface(
    screenshots: List<String>,
    pagerState: PagerState,
    focusRequester: FocusRequester,
    onCommand: (ScreenshotViewerCommand) -> Boolean,
) {
    val currentOnCommand by rememberUpdatedState(onCommand)
    var verticalDrag by remember { mutableFloatStateOf(0f) }
    Dialog(
        onDismissRequest = { currentOnCommand(ScreenshotViewerCommand.Close) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .yummyAppearMotion(scaleFrom = 1f)
                .background(Color.Black)
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (shouldDismissScreenshotViewer(verticalDrag)) {
                                currentOnCommand(ScreenshotViewerCommand.Close)
                            }
                            verticalDrag = 0f
                        },
                        onDragCancel = { verticalDrag = 0f },
                    ) { _, dragAmount ->
                        verticalDrag += dragAmount
                    }
                }
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        currentOnCommand(event.key.toScreenshotViewerCommand())
                },
        ) {
            ScreenshotPager(screenshots, pagerState)
            ScreenshotPageIndicator(
                currentPage = pagerState.currentPage,
                pageCount = screenshots.size,
            )
        }
    }
}

private fun moveScreenshotPage(
    command: ScreenshotViewerCommand,
    pagerState: PagerState,
    lastPage: Int,
    scope: CoroutineScope,
) {
    val target = screenshotViewerTargetPage(command, pagerState.currentPage, lastPage) ?: return
    scope.launch { pagerState.animateScrollToPage(target) }
}

@Composable
private fun ScreenshotPager(
    screenshots: List<String>,
    pagerState: PagerState,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val zoomableState = rememberZoomableState()
        AsyncImage(
            model = screenshots[index],
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomableState),
        )
    }
}

@Composable
private fun BoxScope.ScreenshotPageIndicator(
    currentPage: Int,
    pageCount: Int,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${currentPage + 1} / $pageCount",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

// ScreenshotViewerPolicy
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
