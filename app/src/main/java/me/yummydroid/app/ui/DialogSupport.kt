package me.yummydroid.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.AppUpdateInfo
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

internal enum class AppModalInputOwner {
    ProfileDialog,
    SettingsDialog,
}

internal class YummyDroidAppDialogRuntime(
    val context: Context,
    val actions: YummyDroidAppActions,
    val openProfileNotificationsRequest: Long,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val pendingUpdate: AppUpdateInfo?,
    val onLoginDialogOpenChange: (Boolean) -> Unit,
    val onProfileDialogOpenChange: (Boolean) -> Unit,
    val onSettingsDialogOpenChange: (Boolean) -> Unit,
    val onAutoUpdatePromptDismissed: () -> Unit,
    val onRegisterModalInputActionHandler: (Any, ((InputAction) -> Boolean)?) -> Unit,
)

@Composable
internal fun YummyDroidAppDialogHost(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    AppLoginDialog(state, runtime)
    AppProfileDialog(state, runtime)
    AppSettingsDialog(state, runtime)
    AppLocalWatchHistoryMergeDialog(state, runtime)
    AppUpdateDialog(runtime)
}

@Composable
private fun AppLoginDialog(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    if (!runtime.loginDialogOpen) return
    val actions = runtime.actions
    LoginDialog(
        auth = state.auth,
        siteBaseUrl = state.siteBaseUrl,
        onLogin = actions.onLogin,
        onDismiss = { runtime.onLoginDialogOpenChange(false) },
    )
}

@Composable
private fun AppProfileDialog(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    if (!runtime.profileDialogOpen) return
    val actions = runtime.actions
    ProfileDialog(
        state = ProfileDialogState(
            auth = state.auth,
            siteBaseUrl = state.siteBaseUrl,
            subscriptions = state.globalSubscriptions,
            notifications = state.profileNotifications,
            openNotificationsRequest = runtime.openProfileNotificationsRequest,
        ),
        callbacks = ProfileDialogCallbacks(
            onOpenLogin = {
                runtime.onProfileDialogOpenChange(false)
                runtime.onLoginDialogOpenChange(true)
            },
            onOpenLibrary = {
                runtime.onProfileDialogOpenChange(false)
                actions.onOpenLibraryFilter()
            },
            onOpenAnime = { animeId ->
                runtime.onProfileDialogOpenChange(false)
                actions.onOpenAnime(animeId)
            },
            onUnsubscribeVideoSubscription = actions.onUnsubscribeVideoSubscription,
            onRefreshVideoSubscriptions = actions.onRefreshVideoSubscriptions,
            onRefreshProfileNotifications = actions.onRefreshProfileNotifications,
            onMarkProfileNotificationRead = actions.onMarkProfileNotificationRead,
            onMarkAllProfileNotificationsRead = actions.onMarkAllProfileNotificationsRead,
            onDeleteProfileNotification = actions.onDeleteProfileNotification,
            onOpenNotificationsRequestConsumed = actions.onProfileNotificationsRequestConsumed,
            onLogout = {
                runtime.onProfileDialogOpenChange(false)
                actions.onLogout()
            },
            onRegisterModalInputActionHandler = { handler ->
                runtime.onRegisterModalInputActionHandler(AppModalInputOwner.ProfileDialog, handler)
            },
            onDismiss = { runtime.onProfileDialogOpenChange(false) },
        ),
    )
}

@Composable
private fun AppSettingsDialog(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    if (!runtime.settingsDialogOpen) return
    val actions = runtime.actions
    SettingsDialog(
        settings = state.settings,
        offlineEntries = state.offlineEntries,
        appContentCacheSizeBytes = state.appContentCacheSizeBytes,
        updateState = state.updateState,
        onSettingsChange = actions.onSettingsChange,
        onDeleteOfflineVideo = actions.onDeleteOfflineVideo,
        onDeleteOfflineAnime = actions.onDeleteOfflineAnime,
        onRefreshOfflineDownloads = actions.onRefreshOfflineDownloads,
        onClearAppContentCache = actions.onClearAppContentCache,
        onCheckForUpdates = actions.onCheckForUpdates,
        onRegisterModalInputActionHandler = { handler ->
            runtime.onRegisterModalInputActionHandler(AppModalInputOwner.SettingsDialog, handler)
        },
        onDismiss = { runtime.onSettingsDialogOpenChange(false) },
    )
}

@Composable
private fun AppLocalWatchHistoryMergeDialog(
    state: YummyDroidUiState,
    runtime: YummyDroidAppDialogRuntime,
) {
    if (state.localWatchHistoryMergePrompt == null) return
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = runtime.actions.onDismissLocalWatchHistoryMerge,
        title = { Text(uiText(UiStringKey.LocalWatchHistoryMergeTitle)) },
        text = {
            Text(uiText(UiStringKey.LocalWatchHistoryMergeMessage))
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.DoNotAdd),
                    onClick = runtime.actions.onDismissLocalWatchHistoryMerge,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.SupplementProfile),
                    primary = true,
                    onClick = runtime.actions.onConfirmLocalWatchHistoryMerge,
                )
            }
        },
    )
}

@Composable
private fun AppUpdateDialog(runtime: YummyDroidAppDialogRuntime) {
    val pendingUpdate = runtime.pendingUpdate ?: return
    UpdateCheckDialog(
        updateState = LoadState.Ready(pendingUpdate),
        onInstallUpdate = { info ->
            runtime.onAutoUpdatePromptDismissed()
            UpdateDownloadService.start(runtime.context, info.apkUrl, info.version)
        },
        onDismiss = runtime.onAutoUpdatePromptDismissed,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DialogActionRow(
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    badgeText: String? = null,
) {
    val shape = YummyRadii.smallShape
    val availability = resolveDialogActionAvailability(enabled = enabled, loading = loading)
    val interaction = rememberDialogActionInteraction(
        focusable = availability.focusable,
        actionable = availability.actionable,
        shape = shape,
        onClick = onClick,
    )
    Surface(
        modifier = modifier
            .dialogActionSize(compact = compact, primary = primary)
            .defaultMinSize(minWidth = 0.dp, minHeight = YummySizes.dialogButtonHeight)
            .then(interaction.modifier),
        color = yummyActionSurfaceColor(
            enabled = enabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        contentColor = yummyActionContentColor(
            enabled = enabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        border = yummyActionBorder(
            enabled = enabled,
            selected = primary,
            focused = interaction.focusVisible,
        ),
        shadowElevation = if (interaction.focusVisible) 0.dp else 2.dp,
        shape = shape,
    ) {
        DialogActionButtonContent(
            text = text,
            loading = loading,
            compact = compact,
            buttonEnabled = availability.actionable,
            focusVisible = interaction.focusVisible,
            badgeText = badgeText,
        )
    }
}

private data class DialogActionInteraction(
    val modifier: Modifier,
    val focusVisible: Boolean,
)

internal data class DialogActionAvailability(
    val focusable: Boolean,
    val actionable: Boolean,
)

internal fun resolveDialogActionAvailability(
    enabled: Boolean,
    loading: Boolean,
): DialogActionAvailability = DialogActionAvailability(
    focusable = enabled,
    actionable = enabled && !loading,
)

private fun Modifier.dialogActionSize(compact: Boolean, primary: Boolean): Modifier {
    if (compact) return this
    val minWidth = if (primary) {
        YummySizes.primaryDialogButtonMinWidth
    } else {
        YummySizes.dialogButtonMinWidth
    }
    return widthIn(min = minWidth)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberDialogActionInteraction(
    focusable: Boolean,
    actionable: Boolean,
    shape: Shape,
    onClick: () -> Unit,
): DialogActionInteraction {
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val uiControls = LocalUiControlCoordinator.current
    val controlOwner = remember { Any() }
    val interactionSource = remember { MutableInteractionSource() }
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val focusModifier = if (focusable) {
        Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged { focusState ->
                val focusedNow = focusState.isFocused || focusState.hasFocus
                focused = focusedNow
                if (focusedNow && inputModeManager.inputMode != InputMode.Touch) {
                    uiControls.launch(scope, controlOwner, UiControlOperation.RelocationLatest) {
                        withFrameNanos { }
                        bringIntoViewRequester.bringIntoView()
                    }
                } else {
                    uiControls.cancel(controlOwner, UiControlOperation.RelocationLatest)
                }
            }
            .clearFocusAfterTouch()
    } else {
        Modifier
    }
    val interactionModifier = when {
        focusable -> focusModifier
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (actionable) onClick() },
            )
            .semantics {
                if (!actionable) disabled()
            }
        else -> Modifier.clip(shape)
    }
    return DialogActionInteraction(interactionModifier, focusVisible)
}

@Composable
private fun DialogActionButtonContent(
    text: String,
    loading: Boolean,
    compact: Boolean,
    buttonEnabled: Boolean,
    focusVisible: Boolean,
    badgeText: String?,
) {
    val contentPadding = if (compact) {
        PaddingValues(horizontal = 6.dp, vertical = YummySpacing.xs)
    } else {
        PaddingValues(horizontal = YummySpacing.md, vertical = YummySpacing.sm)
    }
    Box(
        modifier = Modifier.defaultMinSize(minHeight = YummySizes.dialogButtonHeight),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) DialogActionLoadingIndicator(focusVisible)
            DialogActionLabel(text = text, compact = compact)
        }
        if (buttonEnabled && badgeText != null) DialogActionBadge(badgeText)
    }
}

@Composable
private fun DialogActionLoadingIndicator(focusVisible: Boolean) {
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = if (focusVisible) YummyColors.onFocus else YummyColors.focus,
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun DialogActionLabel(text: String, compact: Boolean) {
    Text(
        text = text,
        style = if (compact) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.titleSmall
        },
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
        textAlign = if (compact) TextAlign.Center else TextAlign.Unspecified,
    )
}

@Composable
private fun BoxScope.DialogActionBadge(text: String) {
    Surface(
        color = YummyColors.offline,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 2.dp, end = 2.dp)
            .widthIn(min = 16.dp)
            .height(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}
