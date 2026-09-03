package me.yummydroid.app.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.VideoVariant

// PlayerShellPane
internal data class PlayerShellModel(
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val settings: AppSettings,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val canUsePictureInPicture: Boolean,
)

internal data class PlayerShellActions(
    val onToggleSubscription: () -> Unit,
    val onSelectGroup: (String, VideoVariant?) -> Unit,
    val onSelectSource: (VideoVariant) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
    val onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
)

@Composable
@OptIn(UnstableApi::class)
internal fun PlayerShellPane(
    model: PlayerShellModel,
    actions: PlayerShellActions,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onPlayerControlFocusRestored: () -> Unit = {},
    message: String? = null,
) {
    val playerControlTexts = rememberPlayerControlTexts()
    val retryFocusRequester = rememberPlayerShellRetryFocus(message)
    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    RegisterPlayerShellInputController(
        playerView = { playerView.value },
        canInitializeFocus = message == null,
        onRegisterPlayerInputActionHandler = actions.onRegisterPlayerInputActionHandler,
    )
    Box(modifier = modifier.background(Color.Black)) {
        PlayerShellAndroidView(
            model = model,
            actions = actions,
            texts = playerControlTexts,
            showCenterControls = message == null,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onPlayerControlFocusRestored = onPlayerControlFocusRestored,
            onPlayerViewChanged = { playerView.value = it },
        )
        PlayerShellStatus(
            message = message,
            retryFocusRequester = retryFocusRequester,
            onRetry = actions.onRetry,
        )
    }
}

@Composable
private fun RegisterPlayerShellInputController(
    playerView: () -> PlayerView?,
    canInitializeFocus: Boolean,
    onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
) {
    DisposableEffect(canInitializeFocus, onRegisterPlayerInputActionHandler) {
        onRegisterPlayerInputActionHandler(
            createPlayerInputController(
                playerView = playerView,
                canInitializeFocus = { canInitializeFocus },
            ),
        )
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
}

@Composable
private fun rememberPlayerShellRetryFocus(message: String?): FocusRequester {
    val focusRequester = remember(message) { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current
    UiControlEffect(
        message,
        inputModeManager.inputMode,
        enabled = message != null && inputModeManager.inputMode != InputMode.Touch,
    ) {
        repeat(4) {
            withFrameNanos { }
            if (focusRequester.requestFocusSafely()) return@UiControlEffect
        }
    }
    return focusRequester
}

@Composable
@OptIn(UnstableApi::class)
private fun PlayerShellAndroidView(
    model: PlayerShellModel,
    actions: PlayerShellActions,
    texts: PlayerControlTexts,
    showCenterControls: Boolean,
    playerControlFocusToRestoreId: Int?,
    onRememberPlayerControlFocus: (Int) -> Unit,
    onPlayerControlFocusRestored: () -> Unit,
    onPlayerViewChanged: (PlayerView) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    val latestActions = rememberUpdatedState(actions)
    val latestOnRememberPlayerControlFocus = rememberUpdatedState(onRememberPlayerControlFocus)
    val bindingToken = remember(model, texts, showCenterControls) { Any() }
    key(
        configuration.orientation,
        windowSize.width,
        windowSize.height,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val playerContext = ContextThemeWrapper(viewContext, R.style.Theme_YummyDroid_Player)
                val parent = FrameLayout(playerContext)
                (LayoutInflater.from(playerContext)
                    .inflate(R.layout.yummy_player_view, parent, false) as PlayerView)
                    .apply { configureShellPlayerView() }
            },
            update = { view ->
                onPlayerViewChanged(view)
                view.bindYummyShellController(
                    bindingToken = bindingToken,
                    animeTitle = model.animeTitle,
                    currentVideo = model.currentVideo,
                    settings = model.settings,
                    groups = model.groups,
                    selectedKey = model.selectedKey,
                    sourceOptions = model.sourceOptions,
                    selectedSourceKey = model.selectedSourceKey,
                    previousVideo = model.previousVideo,
                    nextVideo = model.nextVideo,
                    allowSubscription = model.allowSubscription,
                    subscriptionActive = model.subscriptionActive,
                    canUsePictureInPicture = model.canUsePictureInPicture,
                    showCenterControls = showCenterControls,
                    texts = texts,
                    onToggleSubscription = { latestActions.value.onToggleSubscription() },
                    onSelectGroup = { groupKey, replacement ->
                        latestActions.value.onSelectGroup(groupKey, replacement)
                    },
                    onSelectSource = { source -> latestActions.value.onSelectSource(source) },
                    onPlayVideo = { video -> latestActions.value.onPlayVideo(video) },
                    onBack = { latestActions.value.onBack() },
                    onRememberPlayerControlFocus = { controlId ->
                        latestOnRememberPlayerControlFocus.value(controlId)
                    },
                )
                view.restorePlayerControlFocusWhenReady(
                    controlId = playerControlFocusToRestoreId,
                    onRestored = onPlayerControlFocusRestored,
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.configureShellPlayerView() {
    player = null
    useController = true
    controllerAutoShow = false
    setControllerAnimationEnabled(false)
    setControllerShowTimeoutMs(0)
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    keepScreenOn = true
    installPlayerControlsVisibilitySync()
    showPlayerControls()
}

@Composable
private fun BoxScope.PlayerShellStatus(
    message: String?,
    retryFocusRequester: FocusRequester,
    onRetry: () -> Unit,
) {
    if (message == null) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(44.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
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
