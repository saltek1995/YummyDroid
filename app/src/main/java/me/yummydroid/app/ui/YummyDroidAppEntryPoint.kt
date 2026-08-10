package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.YummyDroidUiState

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    canUsePictureInPicture: Boolean,
    openProfileNotificationsRequest: Long,
    actions: YummyDroidAppActions,
) {
    YummyDroidAppRuntime(
        state = state,
        isInPictureInPicture = isInPictureInPicture,
        canUsePictureInPicture = canUsePictureInPicture,
        openProfileNotificationsRequest = openProfileNotificationsRequest,
        actions = actions,
    )
}
