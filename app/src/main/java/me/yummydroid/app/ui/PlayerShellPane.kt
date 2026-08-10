package me.yummydroid.app.ui

import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.R

@Composable
@OptIn(UnstableApi::class)
internal fun PlayerShellPane(
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    canUsePictureInPicture: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?) -> Unit,
    onSelectSource: (VideoVariant) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    message: String? = null,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    val playerControlTexts = rememberPlayerControlTexts()
    val retryFocusRequester = remember(message) { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(message, inputModeManager.inputMode) {
        if (message == null || inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (retryFocusRequester.requestFocusSafely()) return@LaunchedEffect
        }
    }
    Box(
        modifier = modifier.background(Color.Black),
    ) {
        key(
            configuration.orientation,
            windowSize.width,
            windowSize.height,
            configuration.smallestScreenWidthDp,
        ) {
            AndroidView(
                factory = { viewContext ->
                    val parent = FrameLayout(viewContext)
                    LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
                },
                update = { view ->
                    view.player = null
                    view.useController = true
                    view.controllerAutoShow = false
                    view.setControllerAnimationEnabled(false)
                    view.installPlayerControlsVisibilitySync()
                    view.setControllerShowTimeoutMs(0)
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.keepScreenOn = true
                    view.bindYummyShellController(
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        settings = settings,
                        groups = groups,
                        selectedKey = selectedKey,
                        sourceOptions = sourceOptions,
                        selectedSourceKey = selectedSourceKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        canUsePictureInPicture = canUsePictureInPicture,
                        showCenterControls = message == null,
                        texts = playerControlTexts,
                        onToggleSubscription = onToggleSubscription,
                        onSelectGroup = onSelectGroup,
                        onSelectSource = onSelectSource,
                        onPlayVideo = onPlayVideo,
                        onBack = onBack,
                        onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                    )
                    view.showPlayerControls()
                    view.restorePlayerControlFocus(playerControlFocusToRestoreId)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (message == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .padding(top = 112.dp, bottom = 176.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                DialogActionButton(
                    text = uiText(UiStringKey.Retry),
                    primary = true,
                    modifier = Modifier.focusRequester(retryFocusRequester),
                    onClick = onRetry,
                )
            }
        }
    }
}
