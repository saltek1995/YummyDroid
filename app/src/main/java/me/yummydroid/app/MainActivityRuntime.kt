package me.yummydroid.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage

abstract class MainActivityRuntime : ComponentActivity() {
    private val inputRouter = MainActivityInputRouter()
    private val windowController by lazy(LazyThreadSafetyMode.NONE) {
        MainActivityWindowController(this)
    }
    private val pictureInPictureController by lazy(LazyThreadSafetyMode.NONE) {
        MainActivityPictureInPictureController(this) { isPlayerRoute }
    }
    private var viewModelRef: YummyDroidViewModel? = null
    private var isPlayerRoute = false
    private var isPlayerPictureInPicture by mutableStateOf(false)
    private var pendingSystemSearchQuery by mutableStateOf<String?>(null)
    private var pendingProfileNotificationsOpenRequest by mutableLongStateOf(0L)

    override fun attachBaseContext(newBase: Context) {
        val interfaceScale = AppSettingsStorage(newBase).readInterfaceScale()
        super.attachBaseContext(newBase.withAppUiConfiguration(interfaceScale))
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        inputRouter.interceptKeyEvent(event)?.let { return it }
        return inputRouter.recoverAfterSystemDispatch(event, super.dispatchKeyEvent(event))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        inputRouter.recordTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return inputRouter.consumeGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        windowController.captureSystemBarColors()
        windowController.configureForAppContent()
        requestNotificationPermissionIfNeeded()
        DownloadCenter.initialize(applicationContext)
        pictureInPictureController.start()
        configureDecorFocus()
        registerBackHandler()

        val initialRequest = intent.toMainActivityRequest()
        pendingSystemSearchQuery = initialRequest.searchQuery
        if (initialRequest.openProfileNotifications) {
            requestOpenProfileNotifications()
        }

        setContent {
            MainActivityContent(
                initialRequest = initialRequest,
                systemSearchQuery = pendingSystemSearchQuery,
                profileNotificationsOpenRequest = pendingProfileNotificationsOpenRequest,
                isInPictureInPicture = isPlayerPictureInPicture,
                canUsePictureInPicture = pictureInPictureController.isSupported(),
                onViewModelAvailable = { viewModelRef = it },
                onSystemSearchConsumed = { pendingSystemSearchQuery = null },
                onPlayerRouteChanged = ::handlePlayerRouteChanged,
                onSettingsChange = ::handleSettingsChange,
                onEnterPictureInPicture = pictureInPictureController::enter,
                onExitApp = ::finish,
                onProfileNotificationsRequestConsumed = {
                    pendingProfileNotificationsOpenRequest = 0L
                },
                registerInputActionHandler = inputRouter::setHandler,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post(::applyCurrentWindowMode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post(::applyCurrentWindowMode)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post(::applyCurrentWindowMode)
    }

    override fun onDestroy() {
        pictureInPictureController.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.toMainActivityRequest()
        pendingSystemSearchQuery = request.searchQuery
        if (request.openProfileNotifications) {
            requestOpenProfileNotifications()
            return
        }
        request.video?.let { video ->
            viewModelRef?.playVideo(video, request.animeTitle)
            return
        }
        if (request.animeId > 0L) {
            viewModelRef?.openAnime(request.animeId)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pictureInPictureController.shouldEnterOnUserLeaveHint(Build.VERSION.SDK_INT)) {
            pictureInPictureController.enter(showMessage = false)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPlayerPictureInPicture = isInPictureInPictureMode
        PlayerPipController.setPictureInPictureMode(isInPictureInPictureMode)
        if (!isPlayerRoute) return

        if (isInPictureInPictureMode) {
            windowController.configureForAppContent()
        } else {
            window.decorView.post {
                applyCurrentWindowMode()
                window.decorView.requestLayout()
            }
        }
        pictureInPictureController.updateParams()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            val unreadCount = viewModelRef?.uiState?.value?.auth?.profile?.unreadNotifications ?: 0
            SubscriptionNotificationBadge.update(this, unreadCount)
            SubscriptionNotificationScheduler.configureFromStoredStateAsync(
                context = this,
                runImmediately = true,
            )
        }
    }

    private fun configureDecorFocus() {
        window.decorView.isFocusable = true
        window.decorView.isFocusableInTouchMode = true
        window.decorView.requestFocus()
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = inputRouter.handleBackPressed()
            },
        )
    }

    private fun handlePlayerRouteChanged(playerRoute: Boolean) {
        isPlayerRoute = playerRoute
        applyCurrentWindowMode()
        pictureInPictureController.updateParams()
        if (!playerRoute) {
            PlayerPipController.setPictureInPictureMode(false)
        }
    }

    private fun handleSettingsChange(updatedSettings: AppSettings) {
        val settingsStorage = AppSettingsStorage(this)
        val previousInterfaceScale = settingsStorage.readInterfaceScale()
        val interfaceScaleChanged = previousInterfaceScale != updatedSettings.interfaceScale
        if (interfaceScaleChanged) {
            settingsStorage.saveInterfaceScale(updatedSettings.interfaceScale)
        }
        viewModelRef?.updateSettings(updatedSettings)
        if (interfaceScaleChanged) {
            window.decorView.post(::recreate)
        }
    }

    private fun applyCurrentWindowMode() {
        windowController.applyCurrentMode(
            isPlayerRoute = isPlayerRoute,
            isInPictureInPictureMode = isInPictureInPictureMode,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
    }

    private fun requestOpenProfileNotifications() {
        pendingProfileNotificationsOpenRequest = SystemClock.uptimeMillis()
            .coerceAtLeast(pendingProfileNotificationsOpenRequest + 1L)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 9105
    }
}
