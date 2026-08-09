package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class AppModalBackTarget {
    Update,
    Settings,
    Profile,
    Login,
}

internal fun resolveAppModalBackTarget(
    pendingUpdateVisible: Boolean,
    settingsDialogOpen: Boolean,
    profileDialogOpen: Boolean,
    loginDialogOpen: Boolean,
): AppModalBackTarget? = when {
    pendingUpdateVisible -> AppModalBackTarget.Update
    settingsDialogOpen -> AppModalBackTarget.Settings
    profileDialogOpen -> AppModalBackTarget.Profile
    loginDialogOpen -> AppModalBackTarget.Login
    else -> null
}

@Stable
internal class YummyDroidAppModalState {
    var loginDialogOpen by mutableStateOf(false)
    var profileDialogOpen by mutableStateOf(false)
    var settingsDialogOpen by mutableStateOf(false)
    var autoUpdatePromptDismissed by mutableStateOf(false)

    fun openProfileNotifications() {
        loginDialogOpen = false
        settingsDialogOpen = false
        profileDialogOpen = true
    }

    fun closeTopModal(pendingUpdateVisible: Boolean): Boolean {
        val target = resolveAppModalBackTarget(
            pendingUpdateVisible = pendingUpdateVisible,
            settingsDialogOpen = settingsDialogOpen,
            profileDialogOpen = profileDialogOpen,
            loginDialogOpen = loginDialogOpen,
        ) ?: return false
        when (target) {
            AppModalBackTarget.Update -> autoUpdatePromptDismissed = true
            AppModalBackTarget.Settings -> settingsDialogOpen = false
            AppModalBackTarget.Profile -> profileDialogOpen = false
            AppModalBackTarget.Login -> loginDialogOpen = false
        }
        return true
    }

    fun closeAllDialogs() {
        loginDialogOpen = false
        profileDialogOpen = false
        settingsDialogOpen = false
    }
}

@Composable
internal fun rememberYummyDroidAppModalState(
    openProfileNotificationsRequest: Long,
    onSettingsOpened: () -> Unit,
): YummyDroidAppModalState {
    val modalState = remember { YummyDroidAppModalState() }
    LaunchedEffect(openProfileNotificationsRequest) {
        if (openProfileNotificationsRequest > 0L) {
            modalState.openProfileNotifications()
        }
    }
    LaunchedEffect(modalState.settingsDialogOpen) {
        if (modalState.settingsDialogOpen) {
            onSettingsOpened()
        }
    }
    return modalState
}
